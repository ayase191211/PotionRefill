package dev.swench.potionrefill.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.registry.DynamicRegistryManager;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dev-only test: drives the real client with a real X11 middle-button press. Never shipped in the mod jar. */
public class InGameTest implements ClientModInitializer {
    private static final int SETTLE = 30;
    private static final int TIMEOUT_TICKS = 12000;

    private final List<String> results = new ArrayList<>();
    private boolean failed;
    private int step;
    private int waited;
    private int total;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (++total > TIMEOUT_TICKS) {
            check("timeout before finishing, stuck at step " + step, false);
            finish();
        }
        if (waited > 0) {
            waited--;
            return;
        }
        switch (step) {
            case 0 -> {
                if (client.getOverlay() == null && client.currentScreen instanceof TitleScreen) {
                    client.options.pauseOnLostFocus = false;
                    client.options.viewDistance = 2;
                    DynamicRegistryManager registries = DynamicRegistryManager.BUILTIN.get();
                    LevelInfo info = new LevelInfo("potion-test", GameMode.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), DataPackSettings.SAFE_MODE);
                    client.createWorld("potion-test", info, registries, GeneratorOptions.getDefaultOptions(registries));
                    step++;
                }
            }
            case 1 -> {
                if (client.player != null && client.world != null && client.getServer() != null && client.currentScreen == null) {
                    fillStandard(client);
                    next(SETTLE);
                }
            }
            case 2 -> {
                check("setup: potions synced to client", standardUnmoved(client));
                press(client);
                next(SETTLE);
            }
            case 3 -> {
                check("X11 press reaches GLFW", GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_3) == GLFW.GLFW_PRESS);
                release();
                check("inventory closed + middle button: nothing moves", standardUnmoved(client));
                client.setScreen(new InventoryScreen(client.player));
                next(SETTLE);
            }
            case 4 -> {
                check("inventory open, no button: nothing moves", standardUnmoved(client));
                press(client);
                next(SETTLE);
            }
            case 5 -> {
                release();
                next(SETTLE);
            }
            case 6 -> {
                PlayerInventory c = client.player.getInventory();
                PlayerInventory s = serverPlayer(client).getInventory();
                for (PlayerInventory inv : List.of(c, s)) {
                    String side = inv == c ? "client" : "server";
                    check(side + ": Healing II moved first to hotbar 0", is(inv.getStack(0), Items.SPLASH_POTION, Potions.STRONG_HEALING));
                    check(side + ": Healing I moved next to hotbar 1", is(inv.getStack(1), Items.SPLASH_POTION, Potions.HEALING));
                    check(side + ": source slots 9,10 empty", inv.getStack(9).isEmpty() && inv.getStack(10).isEmpty());
                    check(side + ": splash Regeneration stays", is(inv.getStack(11), Items.SPLASH_POTION, Potions.REGENERATION));
                    check(side + ": drinkable Healing stays", is(inv.getStack(12), Items.POTION, Potions.HEALING));
                }
                client.getServer().execute(() -> {
                    PlayerInventory inv = serverPlayer(client).getInventory();
                    inv.clear();
                    for (int i = 0; i < 9; i++) inv.setStack(i, new ItemStack(Items.STONE));
                    inv.setStack(9, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
                });
                next(SETTLE);
            }
            case 7 -> {
                press(client);
                next(SETTLE);
            }
            case 8 -> {
                release();
                next(SETTLE);
            }
            case 9 -> {
                PlayerInventory s = serverPlayer(client).getInventory();
                check("full hotbar: potion stays in slot 9", is(s.getStack(9), Items.SPLASH_POTION, Potions.STRONG_HEALING));
                finish();
            }
            default -> { }
        }
    }

    private void next(int ticks) {
        waited = ticks;
        step++;
    }

    private void fillStandard(MinecraftClient client) {
        client.getServer().execute(() -> {
            PlayerInventory inv = serverPlayer(client).getInventory();
            inv.clear();
            inv.setStack(9, potion(Items.SPLASH_POTION, Potions.HEALING));
            inv.setStack(10, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
            inv.setStack(11, potion(Items.SPLASH_POTION, Potions.REGENERATION));
            inv.setStack(12, potion(Items.POTION, Potions.HEALING));
        });
    }

    private boolean standardUnmoved(MinecraftClient client) {
        PlayerInventory inv = client.player.getInventory();
        return is(inv.getStack(9), Items.SPLASH_POTION, Potions.HEALING)
                && is(inv.getStack(10), Items.SPLASH_POTION, Potions.STRONG_HEALING)
                && is(inv.getStack(11), Items.SPLASH_POTION, Potions.REGENERATION)
                && is(inv.getStack(12), Items.POTION, Potions.HEALING)
                && inv.getStack(0).isEmpty();
    }

    private static ServerPlayerEntity serverPlayer(MinecraftClient client) {
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }

    private static ItemStack potion(Item item, Potion potion) {
        return PotionUtil.setPotion(new ItemStack(item), potion);
    }

    private static boolean is(ItemStack stack, Item item, Potion potion) {
        return stack.getItem() == item && PotionUtil.getPotion(stack) == potion;
    }

    private void press(MinecraftClient client) {
        xdotool("mousemove", "300", "300", "mousedown", "2");
    }

    private void release() {
        xdotool("mouseup", "2");
    }

    private void xdotool(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "xdotool";
            System.arraycopy(args, 0, cmd, 1, args.length);
            check("xdotool " + String.join(" ", args), new ProcessBuilder(cmd).inheritIO().start().waitFor() == 0);
        } catch (IOException | InterruptedException e) {
            check("xdotool failed: " + e, false);
        }
    }

    private void check(String name, boolean ok) {
        results.add((ok ? "PASS " : "FAIL ") + name);
        failed |= !ok;
        System.out.println("[PotionRefillTest] " + results.get(results.size() - 1));
    }

    private void finish() {
        results.add(failed ? "RESULT FAIL" : "RESULT PASS");
        try {
            Files.write(Path.of("potionrefill-test-result.txt"), results);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Runtime.getRuntime().halt(failed ? 1 : 0);
    }
}

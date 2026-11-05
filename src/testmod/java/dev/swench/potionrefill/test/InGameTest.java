package dev.swench.potionrefill.test;

import dev.swench.potionrefill.ModMenuIntegration;
import dev.swench.potionrefill.PotionRefill;
import dev.swench.potionrefill.config.PotionRefillConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Dev-only test: drives the real client with real X11 input. Never shipped in the mod jar. */
public class InGameTest implements ClientModInitializer {
    private static final int SETTLE = 30;
    private static final int TIMEOUT_TICKS = 24000;

    private record Scenario(String name, Consumer<PotionRefillConfig> config, Consumer<ServerPlayerEntity> setup, BiConsumer<String, PlayerInventory> verify) {
    }

    private final List<String> results = new ArrayList<>();
    private final List<Scenario> scenarios = new ArrayList<>();
    private boolean failed;
    private int step;
    private int phase;
    private int scenarioIndex;
    private int waited;
    private int total;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        defineScenarios();
    }

    private void defineScenarios() {
        add("Lv1 excluded, Healing II splash before drinkable", c -> { }, p -> {
            give(p, 9, potion(Items.SPLASH_POTION, Potions.HEALING));
            give(p, 10, potion(Items.POTION, Potions.HEALING));
            give(p, 11, potion(Items.POTION, Potions.STRONG_HEALING));
            give(p, 12, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
            give(p, 13, potion(Items.SPLASH_POTION, Potions.REGENERATION));
        }, (s, i) -> {
            check(s + "hotbar 1 = splash Healing II", isPotion(i, 0, Items.SPLASH_POTION, Potions.STRONG_HEALING));
            check(s + "hotbar 2 = drinkable Healing II", isPotion(i, 1, Items.POTION, Potions.STRONG_HEALING));
            check(s + "Lv1 splash stays", isPotion(i, 9, Items.SPLASH_POTION, Potions.HEALING));
            check(s + "Lv1 drinkable stays", isPotion(i, 10, Items.POTION, Potions.HEALING));
            check(s + "splash Regeneration stays", isPotion(i, 13, Items.SPLASH_POTION, Potions.REGENERATION));
        });
        add("config off moves nothing", c -> { }, p -> {
            give(p, 9, new ItemStack(Items.FISHING_ROD));
            give(p, 10, new ItemStack(Items.GOLDEN_APPLE));
            give(p, 11, new ItemStack(Items.SUGAR));
        }, (s, i) -> {
            check(s + "items stay", is(i, 9, Items.FISHING_ROD, 1) && is(i, 10, Items.GOLDEN_APPLE, 1) && is(i, 11, Items.SUGAR, 1));
            check(s + "hotbar empty", hotbarEmpty(i));
        });
        add("grapple goes to slot 2 before potions, only one", c -> c.autoGrappleRefill = true, p -> {
            give(p, 9, new ItemStack(Items.FISHING_ROD));
            give(p, 10, new ItemStack(Items.FISHING_ROD));
            give(p, 11, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
            give(p, 12, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
        }, (s, i) -> {
            check(s + "hotbar 2 = rod", is(i, 1, Items.FISHING_ROD, 1));
            check(s + "one rod stays in inventory", count(i, 9, 36, Items.FISHING_ROD) == 1);
            check(s + "hotbar 1 and 3 = potions", isPotion(i, 0, Items.SPLASH_POTION, Potions.STRONG_HEALING) && isPotion(i, 2, Items.SPLASH_POTION, Potions.STRONG_HEALING));
        });
        add("grapple skipped when rod already in hotbar", c -> c.autoGrappleRefill = true, p -> {
            give(p, 5, new ItemStack(Items.FISHING_ROD));
            give(p, 9, new ItemStack(Items.FISHING_ROD));
        }, (s, i) -> check(s + "rod stays", is(i, 9, Items.FISHING_ROD, 1) && i.getStack(1).isEmpty()));
        add("configured slot occupied: no move, no swap", c -> c.autoGrappleRefill = true, p -> {
            give(p, 1, new ItemStack(Items.STONE));
            give(p, 9, new ItemStack(Items.FISHING_ROD));
        }, (s, i) -> check(s + "unchanged", is(i, 1, Items.STONE, 1) && is(i, 9, Items.FISHING_ROD, 1)));
        add("custom slot number honoured", c -> {
            c.autoGrappleRefill = true;
            c.autoGrappleRefillSlot = 9;
        }, p -> give(p, 9, new ItemStack(Items.FISHING_ROD)), (s, i) -> check(s + "hotbar 9 = rod", is(i, 8, Items.FISHING_ROD, 1)));
        add("gapple moves a whole stack to slot 4, enchanted excluded", c -> c.autoGappleRefill = true, p -> {
            give(p, 9, new ItemStack(Items.GOLDEN_APPLE, 16));
            give(p, 10, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        }, (s, i) -> {
            check(s + "hotbar 4 = 16 gapples", is(i, 3, Items.GOLDEN_APPLE, 16));
            check(s + "enchanted stays", is(i, 10, Items.ENCHANTED_GOLDEN_APPLE, 1));
        });
        add("gapple skipped when already in hotbar", c -> c.autoGappleRefill = true, p -> {
            give(p, 7, new ItemStack(Items.GOLDEN_APPLE));
            give(p, 9, new ItemStack(Items.GOLDEN_APPLE));
        }, (s, i) -> check(s + "stays", is(i, 9, Items.GOLDEN_APPLE, 1) && i.getStack(3).isEmpty()));
        add("sugar group: sugar first, only one", c -> c.autoSugarRefill = true, p -> {
            give(p, 9, new ItemStack(Items.TOTEM_OF_UNDYING));
            give(p, 10, potion(Items.POTION, Potions.STRONG_SWIFTNESS));
            give(p, 11, new ItemStack(Items.SUGAR, 64));
        }, (s, i) -> {
            check(s + "hotbar 3 = 64 sugar", is(i, 2, Items.SUGAR, 64));
            check(s + "others stay", is(i, 9, Items.TOTEM_OF_UNDYING, 1) && isPotion(i, 10, Items.POTION, Potions.STRONG_SWIFTNESS));
        });
        add("sugar group: swiftness II second, splash excluded", c -> c.autoSugarRefill = true, p -> {
            give(p, 9, new ItemStack(Items.TOTEM_OF_UNDYING));
            give(p, 10, potion(Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS));
            give(p, 11, potion(Items.POTION, Potions.STRONG_SWIFTNESS));
        }, (s, i) -> {
            check(s + "hotbar 3 = drinkable swiftness II", isPotion(i, 2, Items.POTION, Potions.STRONG_SWIFTNESS));
            check(s + "others stay", is(i, 9, Items.TOTEM_OF_UNDYING, 1) && isPotion(i, 10, Items.SPLASH_POTION, Potions.STRONG_SWIFTNESS));
        });
        add("sugar group: totem third", c -> c.autoSugarRefill = true, p -> {
            give(p, 9, new ItemStack(Items.TOTEM_OF_UNDYING));
            give(p, 10, potion(Items.POTION, Potions.SWIFTNESS));
        }, (s, i) -> {
            check(s + "hotbar 3 = totem", is(i, 2, Items.TOTEM_OF_UNDYING, 1));
            check(s + "swiftness I stays", isPotion(i, 10, Items.POTION, Potions.SWIFTNESS));
        });
        add("sugar group: any member in hotbar blocks", c -> c.autoSugarRefill = true, p -> {
            give(p, 6, new ItemStack(Items.TOTEM_OF_UNDYING));
            give(p, 9, new ItemStack(Items.SUGAR));
        }, (s, i) -> check(s + "sugar stays", is(i, 9, Items.SUGAR, 1) && i.getStack(2).isEmpty()));
        add("sugar group: two already in hotbar are left alone", c -> c.autoSugarRefill = true, p -> {
            give(p, 5, new ItemStack(Items.TOTEM_OF_UNDYING));
            give(p, 6, new ItemStack(Items.SUGAR));
            give(p, 9, new ItemStack(Items.SUGAR));
        }, (s, i) -> check(s + "unchanged", is(i, 5, Items.TOTEM_OF_UNDYING, 1) && is(i, 6, Items.SUGAR, 1) && is(i, 9, Items.SUGAR, 1)));
        add("Speed I does not block sugar", c -> c.autoSugarRefill = true, p -> {
            speed(p, 0);
            give(p, 9, new ItemStack(Items.SUGAR));
        }, (s, i) -> check(s + "hotbar 3 = sugar", is(i, 2, Items.SUGAR, 1)));
        add("Speed II blocks sugar", c -> c.autoSugarRefill = true, p -> {
            speed(p, 1);
            give(p, 9, new ItemStack(Items.SUGAR));
        }, (s, i) -> check(s + "sugar stays", is(i, 9, Items.SUGAR, 1)));
        add("Speed III blocks sugar", c -> c.autoSugarRefill = true, p -> {
            speed(p, 2);
            give(p, 9, new ItemStack(Items.SUGAR));
        }, (s, i) -> check(s + "sugar stays", is(i, 9, Items.SUGAR, 1)));
        add("potions may use empty configured slots", c -> {
            c.autoGrappleRefill = true;
            c.autoGrappleRefillSlot = 1;
        }, p -> give(p, 9, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING)),
                (s, i) -> check(s + "hotbar 1 = potion", isPotion(i, 0, Items.SPLASH_POTION, Potions.STRONG_HEALING)));
    }

    private void add(String name, Consumer<PotionRefillConfig> config, Consumer<ServerPlayerEntity> setup, BiConsumer<String, PlayerInventory> verify) {
        scenarios.add(new Scenario(name, config, setup, verify));
    }

    private void tick(MinecraftClient client) {
        if (++total > TIMEOUT_TICKS) {
            check("timeout at step " + step + " scenario " + scenarioIndex, false);
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
                    checkDefaults();
                    client.getServer().execute(() -> {
                        ServerPlayerEntity p = serverPlayer(client);
                        p.getInventory().clear();
                        give(p, 9, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
                    });
                    next(SETTLE);
                }
            }
            case 2 -> {
                pressMouse();
                next(SETTLE);
            }
            case 3 -> {
                check("X11 press reaches GLFW", GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS);
                releaseMouse();
                check("inventory closed + key: nothing moves", hotbarEmpty(client.player.getInventory()));
                step = 30;
                waited = SETTLE;
            }
            case 30 -> {
                client.setScreen(new InventoryScreen(client.player));
                step = 4;
                waited = SETTLE;
            }
            case 4 -> {
                check("inventory open, no key: nothing moves", hotbarEmpty(client.player.getInventory()));
                step++;
            }
            case 5 -> runScenario(client);
            case 6 -> {
                applyDefaults();
                setRefill(client, InputUtil.fromKeyCode(GLFW.GLFW_KEY_G, 0));
                client.getServer().execute(() -> {
                    ServerPlayerEntity p = serverPlayer(client);
                    p.clearStatusEffects();
                    p.getInventory().clear();
                    give(p, 9, potion(Items.SPLASH_POTION, Potions.STRONG_HEALING));
                });
                next(SETTLE);
            }
            case 7 -> {
                pressMouse();
                next(SETTLE);
            }
            case 8 -> {
                releaseMouse();
                check("rebound refill: middle mouse no longer refills", hotbarEmpty(client.player.getInventory()));
                xdotool("mousemove", "300", "300", "keydown", "g");
                next(SETTLE);
            }
            case 9 -> {
                xdotool("keyup", "g");
                check("rebound refill: G key refills", isPotion(client.player.getInventory(), 0, Items.SPLASH_POTION, Potions.STRONG_HEALING));
                setRefill(client, PotionRefill.refillKey.getDefaultKey());
                client.setScreen(null);
                PotionRefill.configKey.setBoundKey(InputUtil.fromKeyCode(GLFW.GLFW_KEY_J, 0));
                KeyBinding.updateKeysByCode();
                next(SETTLE);
            }
            case 10 -> {
                xdotool("mousemove", "300", "300", "key", "j");
                next(SETTLE);
            }
            case 11 -> {
                Screen screen = client.currentScreen;
                check("config key opens Cloth Config screen: " + (screen == null ? "none" : screen.getClass().getName()), screen != null && screen.getClass().getName().contains("clothconfig"));
                check("Mod Menu factory builds config screen", new ModMenuIntegration().getModConfigScreenFactory().create(null) != null);
                finish();
            }
            default -> { }
        }
    }

    private void runScenario(MinecraftClient client) {
        if (scenarioIndex >= scenarios.size()) {
            step++;
            return;
        }
        Scenario scenario = scenarios.get(scenarioIndex);
        switch (phase) {
            case 0 -> {
                applyDefaults();
                scenario.config.accept(config());
                client.getServer().execute(() -> {
                    ServerPlayerEntity p = serverPlayer(client);
                    p.clearStatusEffects();
                    p.getInventory().clear();
                    scenario.setup.accept(p);
                });
                pause(SETTLE);
            }
            case 1 -> {
                pressMouse();
                pause(SETTLE);
            }
            case 2 -> {
                releaseMouse();
                pause(SETTLE);
            }
            default -> {
                scenario.verify.accept("[" + scenario.name + "] client: ", client.player.getInventory());
                scenario.verify.accept("[" + scenario.name + "] server: ", serverPlayer(client).getInventory());
                phase = 0;
                scenarioIndex++;
            }
        }
    }

    private void checkDefaults() {
        PotionRefillConfig c = config();
        check("defaults: all OFF", !c.autoGrappleRefill && !c.autoGappleRefill && !c.autoSugarRefill);
        check("defaults: slots 2, 4, 3", c.autoGrappleRefillSlot == 2 && c.autoGappleRefillSlot == 4 && c.autoSugarRefillSlot == 3);
        check("defaults: refill key is middle mouse", PotionRefill.refillKey.getDefaultKey().equals(InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_MIDDLE)));
        check("defaults: config key unbound", PotionRefill.configKey.isUnbound());
    }

    private static PotionRefillConfig config() {
        return AutoConfig.getConfigHolder(PotionRefillConfig.class).getConfig();
    }

    private static void applyDefaults() {
        PotionRefillConfig c = config();
        c.autoGrappleRefill = false;
        c.autoGappleRefill = false;
        c.autoSugarRefill = false;
        c.autoGrappleRefillSlot = 2;
        c.autoGappleRefillSlot = 4;
        c.autoSugarRefillSlot = 3;
    }

    private static void setRefill(MinecraftClient client, InputUtil.Key key) {
        PotionRefill.refillKey.setBoundKey(key);
        KeyBinding.updateKeysByCode();
    }

    private void next(int ticks) {
        waited = ticks;
        step++;
    }

    private void pause(int ticks) {
        waited = ticks;
        phase++;
    }

    private static void give(ServerPlayerEntity player, int slot, ItemStack stack) {
        player.getInventory().setStack(slot, stack);
    }

    private static void speed(ServerPlayerEntity player, int amplifier) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20000, amplifier));
    }

    private static ServerPlayerEntity serverPlayer(MinecraftClient client) {
        return client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
    }

    private static ItemStack potion(Item item, Potion potion) {
        return PotionUtil.setPotion(new ItemStack(item), potion);
    }

    private static boolean isPotion(PlayerInventory inv, int slot, Item item, Potion potion) {
        ItemStack stack = inv.getStack(slot);
        return stack.getItem() == item && PotionUtil.getPotion(stack) == potion;
    }

    private static boolean is(PlayerInventory inv, int slot, Item item, int count) {
        ItemStack stack = inv.getStack(slot);
        return stack.getItem() == item && stack.getCount() == count;
    }

    private static int count(PlayerInventory inv, int from, int to, Item item) {
        int n = 0;
        for (int i = from; i < to; i++) {
            if (inv.getStack(i).getItem() == item) n += inv.getStack(i).getCount();
        }
        return n;
    }

    private static boolean hotbarEmpty(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (!inv.getStack(i).isEmpty()) return false;
        }
        return true;
    }

    private void pressMouse() {
        xdotool("mousemove", "300", "300", "mousedown", "2");
    }

    private void releaseMouse() {
        xdotool("mouseup", "2");
    }

    private void xdotool(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "xdotool";
            System.arraycopy(args, 0, cmd, 1, args.length);
            if (new ProcessBuilder(cmd).inheritIO().start().waitFor() != 0) {
                check("xdotool " + String.join(" ", args), false);
            }
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

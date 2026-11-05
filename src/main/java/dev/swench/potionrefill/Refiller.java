package dev.swench.potionrefill;

import dev.swench.potionrefill.config.PotionRefillConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionUtil;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

/** Performs at most one inventory move per client tick while the refill key is held in the inventory screen. */
public final class Refiller {
    private static final Identifier POTION = new Identifier("minecraft:potion");
    private static final Identifier SPLASH_POTION = new Identifier("minecraft:splash_potion");
    private static final Identifier STRONG_HEALING = new Identifier("minecraft:strong_healing");
    private static final Identifier STRONG_SWIFTNESS = new Identifier("minecraft:strong_swiftness");
    private static final Identifier FISHING_ROD = new Identifier("minecraft:fishing_rod");
    private static final Identifier GOLDEN_APPLE = new Identifier("minecraft:golden_apple");
    private static final Identifier SUGAR = new Identifier("minecraft:sugar");
    private static final Identifier TOTEM = new Identifier("minecraft:totem_of_undying");

    private static final Predicate<ItemStack> IS_ROD = stack -> isItem(stack, FISHING_ROD);
    private static final Predicate<ItemStack> IS_GAPPLE = stack -> isItem(stack, GOLDEN_APPLE);
    private static final Predicate<ItemStack> IS_SUGAR = stack -> isItem(stack, SUGAR);
    private static final Predicate<ItemStack> IS_SWIFTNESS_2 = stack -> isPotion(stack, POTION, STRONG_SWIFTNESS);
    private static final Predicate<ItemStack> IS_TOTEM = stack -> isItem(stack, TOTEM);
    private static final Predicate<ItemStack> IS_SUGAR_GROUP = IS_SUGAR.or(IS_SWIFTNESS_2).or(IS_TOTEM);

    private Refiller() {
    }

    public static void tick(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof InventoryScreen) || !isPressed(mc, PotionRefill.refillKey)) {
            return;
        }
        if (!refillConfiguredItem(mc)) {
            refillHealingPotion(mc);
        }
    }

    private static boolean refillConfiguredItem(MinecraftClient mc) {
        PotionRefillConfig config = AutoConfig.getConfigHolder(PotionRefillConfig.class).getConfig();
        PlayerInventory inventory = mc.player.getInventory();

        if (config.autoGrappleRefill && refillWhenMissing(mc, inventory, IS_ROD, IS_ROD, config.autoGrappleRefillSlot)) {
            return true;
        }
        if (config.autoGappleRefill && refillWhenMissing(mc, inventory, IS_GAPPLE, IS_GAPPLE, config.autoGappleRefillSlot)) {
            return true;
        }
        if (config.autoSugarRefill && !hasSpeed2(mc)) {
            return refillWhenMissing(mc, inventory, IS_SUGAR_GROUP, IS_SUGAR, config.autoSugarRefillSlot)
                    || refillWhenMissing(mc, inventory, IS_SUGAR_GROUP, IS_SWIFTNESS_2, config.autoSugarRefillSlot)
                    || refillWhenMissing(mc, inventory, IS_SUGAR_GROUP, IS_TOTEM, config.autoSugarRefillSlot);
        }
        return false;
    }

    private static boolean refillWhenMissing(MinecraftClient mc, PlayerInventory inventory, Predicate<ItemStack> hotbarGroup, Predicate<ItemStack> wanted, int configuredSlot) {
        int hotbarSlot = configuredSlot - 1;
        if (hotbarContains(inventory, hotbarGroup) || !inventory.getStack(hotbarSlot).isEmpty()) {
            return false;
        }
        int source = findInMainInventory(inventory, wanted);
        if (source == -1) {
            return false;
        }
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, source, hotbarSlot, SlotActionType.SWAP, mc.player);
        return true;
    }

    private static void refillHealingPotion(MinecraftClient mc) {
        PlayerInventory inventory = mc.player.getInventory();
        int source = findInMainInventory(inventory, stack -> isPotion(stack, SPLASH_POTION, STRONG_HEALING));
        if (source == -1) {
            source = findInMainInventory(inventory, stack -> isPotion(stack, POTION, STRONG_HEALING));
        }
        if (source != -1) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, source, 1, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    private static boolean hasSpeed2(MinecraftClient mc) {
        StatusEffectInstance speed = mc.player.getStatusEffect(StatusEffects.SPEED);
        return speed != null && speed.getAmplifier() >= 1;
    }

    private static boolean hotbarContains(PlayerInventory inventory, Predicate<ItemStack> predicate) {
        for (int i = 0; i < 9; i++) {
            if (predicate.test(inventory.getStack(i))) {
                return true;
            }
        }
        return false;
    }

    private static int findInMainInventory(PlayerInventory inventory, Predicate<ItemStack> predicate) {
        int found = -1;
        for (int i = 9; i < 36; i++) {
            if (predicate.test(inventory.getStack(i))) {
                found = i;
            }
        }
        return found;
    }

    private static boolean isItem(ItemStack stack, Identifier id) {
        return !stack.isEmpty() && Registry.ITEM.getId(stack.getItem()).equals(id);
    }

    private static boolean isPotion(ItemStack stack, Identifier itemId, Identifier potionId) {
        return isItem(stack, itemId) && Registry.POTION.getId(PotionUtil.getPotion(stack)).equals(potionId);
    }

    private static boolean isPressed(MinecraftClient mc, KeyBinding binding) {
        InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(binding);
        long window = mc.getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        }
        return key.getCategory() == InputUtil.Type.KEYSYM && key.getCode() != InputUtil.UNKNOWN_KEY.getCode() && InputUtil.isKeyPressed(window, key.getCode());
    }
}

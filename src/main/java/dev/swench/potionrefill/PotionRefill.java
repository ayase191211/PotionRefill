package dev.swench.potionrefill;

import dev.swench.potionrefill.config.PotionRefillConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class PotionRefill implements ClientModInitializer {
    private static final String CATEGORY = "key.categories.potionrefill";

    public static KeyBinding refillKey;
    public static KeyBinding configKey;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(PotionRefillConfig.class, GsonConfigSerializer::new);

        refillKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.potionrefill.refill", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, CATEGORY));
        configKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.potionrefill.config", InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (configKey.wasPressed()) {
                client.setScreen(AutoConfig.getConfigScreen(PotionRefillConfig.class, client.currentScreen).get());
            }
        });
    }
}

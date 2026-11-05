package dev.swench.potionrefill.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "potionrefill")
public class PotionRefillConfig implements ConfigData {
    public boolean autoGrappleRefill = false;
    @ConfigEntry.BoundedDiscrete(min = 1, max = 9)
    public int autoGrappleRefillSlot = 2;

    public boolean autoGappleRefill = false;
    @ConfigEntry.BoundedDiscrete(min = 1, max = 9)
    public int autoGappleRefillSlot = 4;

    public boolean autoSugarRefill = false;
    @ConfigEntry.BoundedDiscrete(min = 1, max = 9)
    public int autoSugarRefillSlot = 3;

    @Override
    public void validatePostLoad() {
        autoGrappleRefillSlot = clamp(autoGrappleRefillSlot);
        autoGappleRefillSlot = clamp(autoGappleRefillSlot);
        autoSugarRefillSlot = clamp(autoSugarRefillSlot);
    }

    private static int clamp(int slot) {
        return Math.max(1, Math.min(9, slot));
    }
}

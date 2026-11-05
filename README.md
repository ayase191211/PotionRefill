# 🧪 Potion Refill

Potion Refill is a convenient Minecraft mod that quickly moves health potions to your hotbar.

How to use:
1. Open your inventory 🎒
2. Click the middle mouse button 🖱️
3. Health potions are instantly moved to the bottom 9 slots 💨🧪

This mod saves time during gameplay and helps you stay prepared for battles.

![Preview](https://cdn.modrinth.com/data/cached_images/c3e5324c1e8fee3d30d73a477abcf51fb3bc3b4a.gif)

## Fork for Minecraft 1.18.2 (Fabric)

Required: Fabric API, Cloth Config API. Optional: Mod Menu.

- Refill key: rebindable in Controls, default middle mouse button. Used while the inventory is open.
- Config key: rebindable in Controls, unbound by default. The config screen is also available from Mod Menu.
- Healing potions: Healing II only, splash (`minecraft:splash_potion`) first, then drinkable (`minecraft:potion`), content `minecraft:strong_healing`.
- `auto grapple refill` (default slot 2): `minecraft:fishing_rod`, when none is in the hotbar.
- `auto gapple refill` (default slot 4): `minecraft:golden_apple`, when none is in the hotbar.
- `auto sugar refill` (default slot 3): `minecraft:sugar`, then drinkable `minecraft:strong_swiftness`, then `minecraft:totem_of_undying`, when none of the three is in the hotbar and you do not have Speed II or higher.

All three are off by default, move one stack, run before healing potions, and only move into an empty configured slot.

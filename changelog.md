# Development Build changelog

## 1.3.3 — in progress

- Ra Blade («Гелиос»): enchanted golden apples, and nether stars instead of netherite swords in the bottom corners, as posted in #крафты.

## 1.3.2 — 2026-11-19

- Singularity Chain Core (green) takes enchanted golden apples, not plain golden apples.

## 1.3.1 — 2026-11-19

- Recipes now match the announcements in Discord (#новости, 24.09):
  - Impulse Core (orange): copper block, ancient debris, copper block / netherite ingot, piglin head, netherite ingot / totem, eye of ender, totem.
  - Core of the Eternal Cycle (purple): netherite scrap and ingots around an Impulse Core.
  - Singularity Chain Core (green): blue ice in the corners, golden apples on the sides, netherite ingot in the centre.
  - Frost Spear: blue ice instead of packed ice and a netherite spear in the centre (was trident or netherite sword).
- Cores may now be used in any plugin recipe, including other cores, but still not in vanilla recipes.

## 1.3.0 — 2026-11-17

- Restored the full plugin: Chaos Boomerang, Warden Helmet, Magnet, Time Clock, Soul Bow, Default Mask, Thompson and Freeze are back (they were removed in 610ee83). The source now matches the jar running on the server (FokendLegendaryWepons-all-weapons-fixed.jar).
- Kept all later fixes: /check lock, souls.yml loading, Death Note async chat and relog handling, Fate Lightning inventory containment, and the MockBukkit tests.
- Warden Helmet: right-click with an item that already has a vanilla use (food, shield, bow, pearls, buckets, armor, tools...) no longer fires the Sonic Boom unless sneaking; Scales of Fate and Death Note are treated as items with their own right-click ability.
- Version is now 1.3.0 (was 1.0-SNAPSHOT for every build), and startup logs the loaded item set so it is easy to check which build a server runs.
- Model and texture files are ignored by git: the resource pack is shared with projects privately.

## 2026-10-19

- Added enchantment cores to the anvil: 10%, 15%, and 17% success rates. A successful attempt raises a random existing enchantment by exactly one level, including beyond vanilla maximum levels.
- Failed core attempts remove 80% of the item's remaining durability. The item is destroyed if the remaining durability is too low to survive that damage. The core and target item are consumed on failure.
- The success roll happens only when the player takes the anvil result, so inspecting the preview cannot be used to reroll the chance.
- Twins Sword summons one clone for 30 seconds; it follows the owner and adds 4 damage to owner hits.

## 2026-10-20

- Fixed Warden helmet Sonic Boom activation on right-click in air and Shift+right-click on an interactable block. Its beam stops at walls and does not require aiming at a mob.
- Warden blindness is refreshed only when its remaining duration is low, avoiding repeated effect resets while the helmet is worn.
- Fixed cleanup so an older Twins summon cannot remove a newer summon from the owner's active list.
- Magnet right-click pulls nearby entities toward the point in front of the player.
- Time Clock restores the selected position and health frame.

## 2026-10-23

- Removed startup copying of a missing default config file; the plugin does not use a bundled config.
- Allowed the Time Clock's internal player teleport to the selected frame while the target is frozen, so position and health rewind together.
- Remove a player's cooldown entries when they leave the server.

## 2026-10-23 (Particle fix)

- Fixed the Twins Sword particle task: DRAGON_BREATH now receives its required Float particle data, preventing a Paper exception from being logged every tick while the clone is active.

## 2026-10-23 (Homing chance)

- Increased the Chaos Boomerang homing-target selection chance from 30% to 63%.

## 2026-10-24 (Warden and Mask display fixes)

- Improved Warden Helmet Sonic Boom activation for empty main hand, including an off-hand event fallback, while preserving Shift+right-click behavior on interactable blocks.
- Corrected Thompson display orientation by removing the unintended 90-degree rotation.

## 2026-10-24 (Legendary item protection)

- Blocked legendary items from producing outputs in grindstones and smithing tables, using their existing PDC IDs.
- Blocked ordinary anvil operations on legendary items while preserving supported enchantment-core upgrades.
- Added per-target block line-of-sight checks to the Warden Helmet Sonic Boom so its area scan cannot damage targets behind walls.
- Stopped refreshing the Warden Helmet owner's blindness every two seconds; the effect now refreshes infrequently and is removed when the helmet is unequipped or the player disconnects.
- Allowed Sonic Boom clicks from either hand, with same-tick duplicate suppression, so empty-hand air clicks are not rejected by hand filtering.
# 2026-10-24
- Added Shift+F as a fallback to activate Warden Helmet Sonic Boom with both hands empty. The normal right-click path and existing cooldown remain unchanged.

## 2026-11-09 (Paper 1.21.11 review)

- Death Note chat cipher no longer reads potion effects or attributes from the async chat thread; it uses main-thread snapshots (invisible players, god-eye holders) refreshed on potion changes and every 10 ticks.
- Delayed Death Note entries ("Name ticks") look the victim up again by UUID, so they still fire after the victim relogs.
- `/dnunlock` uses online/cached players only instead of `getOfflinePlayer(String)`, which can block the main thread with a profile lookup.
- Fate Lightning can no longer be moved into containers with number keys, the off-hand key or dragging (it could escape expiry there).
- Restored loading of `souls.yml` on enable: it was dropped with the removed weapons, so `crafted_uniques` (one craft per server) was never loaded and every save (craft, shutdown) threw `File cannot be null`.
- Added MockBukkit unit tests (`gradle test`) and real-server integration tests (`integration-test/`).

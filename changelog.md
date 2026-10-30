# Development Build changelog

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

package FuckFokend.fokendLegendaryWepons;

import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

final class CleanupListener implements Listener {
    private final FokendLegendaryWepons plugin;

    CleanupListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    private void dropSessions(UUID id) {
        FokendLegendaryWepons.TimeSession so = plugin.timeSessions.get(id);
        if (so != null) plugin.endTimeSession(so, null);
        FokendLegendaryWepons.TimeSession st = plugin.timeByTarget.get(id);
        if (st != null) plugin.endTimeSession(st, "Цель покинула игру.");
        plugin.unlockEntity(id);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();

        // Return any active Chaos Boomerangs directly to the quitting player's inventory
        plugin.returnAllBoomerangsFor(p);

        String cooldownPrefix = id + ":";
        plugin.cooldowns.keySet().removeIf(key -> key.startsWith(cooldownPrefix));
        plugin.cooldownHud.keySet().removeIf(key -> key.startsWith(cooldownPrefix));
        plugin.actionBarPriorityUntil.remove(id);
        plugin.selectedHudCooldown.remove(id);

        UUID dollId = plugin.raDolls.remove(id);
        if (dollId != null) {
            Entity ent = plugin.getServer().getEntity(dollId);
            if (ent != null) ent.remove();
        }
        List<UUID> activeNpcIds = plugin.activeTwins.remove(id);
        if (activeNpcIds != null) {
            for (UUID npcId : activeNpcIds) {
                Entity ent = plugin.getServer().getEntity(npcId);
                if (ent != null && ent.isValid()) ent.remove();
            }
        }
        dropSessions(id);
        plugin.releaseHover(p);
        p.setGravity(true);
        plugin.fallingFree.remove(id);
        plugin.lastY.remove(id);
        plugin.setRunBoost(p, false);
        WardenHelmetListener.syncWardenHealthAttribute(plugin, p, false);
        plugin.maskSneakStart.remove(id);
        plugin.maskRunUntil.remove(id);
        if (plugin.wardenBlindness.remove(id)) {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        }
        plugin.lastInteractTick.entrySet().removeIf(entry -> entry.getKey().startsWith(id.toString()));
        plugin.lastThompsonHitTick.remove(id);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        UUID id = p.getUniqueId();
        dropSessions(id);
        plugin.releaseHover(p);
        p.setGravity(true);
        plugin.fallingFree.remove(id);
        plugin.setRunBoost(p, false);
        plugin.maskSneakStart.remove(id);
        plugin.maskRunUntil.remove(id);
        WardenHelmetListener.syncWardenHealthAttribute(plugin, p, false);

        UUID dollId = plugin.raDolls.remove(id);
        if (dollId != null) {
            Entity ent = plugin.getServer().getEntity(dollId);
            if (ent != null) ent.remove();
        }
        List<UUID> activeNpcIds = plugin.activeTwins.remove(id);
        if (activeNpcIds != null) {
            for (UUID npcId : activeNpcIds) {
                Entity ent = plugin.getServer().getEntity(npcId);
                if (ent != null && ent.isValid()) ent.remove();
            }
        }

        // Revert any dropped Default Mask from worn (20006) back to normal (20005)
        for (ItemStack drop : e.getDrops()) {
            if (plugin.isLegendary(drop, FokendLegendaryWepons.ID_DEFAULT_MASK)) {
                ItemMeta meta = drop.getItemMeta();
                if (meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == 20006) {
                    meta.setCustomModelData(20005);
                    meta.displayName(Component.text("🎭 Лик Безумия • Маска Хаоса", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                    drop.setItemMeta(meta);
                }
            }
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        cleanupUnloadingEntities(List.of(e.getChunk().getEntities()));
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent e) {
        cleanupUnloadingEntities(e.getEntities());
    }

    private void cleanupUnloadingEntities(Iterable<Entity> entities) {
        for (Entity entity : entities) {
            if (entity == null) continue;
            UUID entityId = entity.getUniqueId();
            PersistentDataContainer pdc = entity.getPersistentDataContainer();

            if (entity instanceof ItemDisplay display && plugin.boomerangs.containsKey(entityId)) {
                FokendLegendaryWepons.ThrownBoomerang tb = plugin.boomerangs.remove(entityId);
                if (tb != null) {
                    if (tb.throwSessionId() != null) {
                        plugin.activeThrowSessions.remove(tb.throwSessionId());
                    }
                    ItemStack saved = plugin.cleanBoomerangItem(tb.item());
                    if (saved != null) {
                        Player owner = tb.owner() != null ? Bukkit.getPlayer(tb.owner()) : null;
                        if (owner != null && owner.isOnline()) {
                            plugin.giveOrDrop(owner, saved);
                        } else if (display.getWorld() != null) {
                            Item itemEnt = display.getWorld().dropItemNaturally(display.getLocation(), saved);
                            plugin.protectDroppedItem(itemEnt);
                        }
                    }
                }
                plugin.tempEntities.remove(entityId);
                display.remove();
                continue;
            }

            if (plugin.tempEntities.remove(entityId)
                    || pdc.has(new NamespacedKey(plugin, "ra_doll_owner"), PersistentDataType.STRING)
                    || pdc.has(new NamespacedKey(plugin, "twins_npc_owner"), PersistentDataType.STRING)) {
                plugin.raDolls.values().removeIf(v -> v.equals(entityId));
                entity.remove();
            }

            if (plugin.lockStates.containsKey(entityId)) {
                plugin.unlockEntity(entityId);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        p.setGravity(true);
        boolean wearingWarden = plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET);
        WardenHelmetListener.syncWardenHealthAttribute(plugin, p, wearingWarden);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        p.setGravity(true);
        plugin.setRunBoost(p, false);
        boolean wearingWarden = plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET);
        WardenHelmetListener.syncWardenHealthAttribute(plugin, p, wearingWarden);
        for (String k : plugin.RECIPE_KEYS) {
            if (!plugin.craftedUniques.contains(k)) {
                p.discoverRecipe(new NamespacedKey(plugin, k));
            }
        }
    }
}

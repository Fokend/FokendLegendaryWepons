package FuckFokend.fokendLegendaryWepons;

import java.util.List;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

final class CleanupListener implements Listener {
    private final FokendLegendaryWepons plugin;
    CleanupListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

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
                    String cooldownPrefix = id + ":";
                    plugin.cooldownHud.keySet().removeIf(key -> key.startsWith(cooldownPrefix));
                    plugin.actionBarPriorityUntil.remove(id);
                    plugin.selectedHudCooldown.remove(id);

                    UUID dollId = plugin.raDolls.remove(id);
                    if (dollId != null) {
                        Entity ent = plugin.getServer().getEntity(dollId);
                        if (ent != null) ent.remove();
                    }
                    List<LivingEntity> activeNpcs = plugin.activeTwins.remove(id);
                    if (activeNpcs != null) {
                        for (LivingEntity ent : activeNpcs) {
                            if (ent != null && ent.isValid()) ent.remove();
                        }
                    }
                    dropSessions(id);
                    plugin.releaseHover(p);
                    p.setGravity(true);
                    plugin.fallingFree.remove(id);
                    plugin.lastY.remove(id);
                    plugin.setRunBoost(p, false);
                    plugin.maskSneakStart.remove(id);
                    plugin.maskRunUntil.remove(id);
                    plugin.lastInteractTick.entrySet().removeIf(entry -> entry.getKey().startsWith(id.toString()));
                }

                @EventHandler
                public void onDeath(PlayerDeathEvent e) {
                    Player p = e.getEntity();
                    UUID id = p.getUniqueId();
                    dropSessions(id);
                    plugin.releaseHover(p);
                    p.setGravity(true);
                    plugin.fallingFree.remove(id);
                }

                @EventHandler
                public void onRespawn(PlayerRespawnEvent e) {
                    e.getPlayer().setGravity(true);
                }

                @EventHandler
                public void onJoin(PlayerJoinEvent e) {
                    Player p = e.getPlayer();
                    p.setGravity(true);
                    plugin.setRunBoost(p, false);
                    for (String k : plugin.RECIPE_KEYS) {
                        if (!plugin.craftedUniques.contains(k)) {
                            p.discoverRecipe(new NamespacedKey(plugin, k));
                        }
                    }
                }
            
}

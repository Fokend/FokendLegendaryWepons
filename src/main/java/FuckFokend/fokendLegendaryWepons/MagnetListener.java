package FuckFokend.fokendLegendaryWepons;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.RayTraceResult;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

final class MagnetListener implements Listener {
    private final FokendLegendaryWepons plugin;
    MagnetListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

                @EventHandler
                public void onInteract(PlayerInteractEvent e) {
                    if (!plugin.isAbilityClick(e)) return;
                    Player p = e.getPlayer();
                    if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_MAGNET)) return;
                    e.setCancelled(true);
                    if (!plugin.checkAndMarkTick(p, "magnet")) return;

                    if (plugin.onCooldown(p, "magnet", 5000L)) {
                        long s = plugin.cooldownRemaining(p, "magnet", 5000L) / 1000L + 1L;
                        plugin.sendPriorityActionBar(p, Component.text("Магнитник: " + s + "с", NamedTextColor.RED));
                        return;
                    }
                    plugin.setCooldown(p, "magnet", 5000L, "Магнитник");

                    Location c = p.getLocation();
                    World w = p.getWorld();
                    if (!p.isSneaking()) {
                        Location eye = p.getEyeLocation();
                        Vector look = eye.getDirection().normalize();
                        RayTraceResult block = w.rayTraceBlocks(eye, look, 24.0D, FluidCollisionMode.NEVER, true);
                        Location focal = block == null ? eye.clone().add(look.clone().multiply(12.0D))
                                : block.getHitPosition().toLocation(w);
                        for (Entity ent : w.getNearbyEntities(c, 12.0D, 12.0D, 12.0D)) {
                            if (plugin.isTargetable(ent, p) && !plugin.lockStates.containsKey(ent.getUniqueId())) {
                                LivingEntity le = (LivingEntity) ent;
                                Vector pull = focal.toVector().subtract(le.getLocation().toVector());
                                if (pull.lengthSquared() >= 0.01D) {
                                    double speed = Math.min(2.4D, Math.max(1.1D, pull.length() * 0.28D));
                                    le.setVelocity(pull.normalize().multiply(speed).setY(0.35D));
                                }
                            }
                        }
                        w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1.2F, 0.7F);
                        w.spawnParticle(Particle.END_ROD, c.clone().add(0.0D, 1.0D, 0.0D), 40, 1.5D, 0.5D, 1.5D, 0.05D);
                    } else {
                        for (Entity ent : w.getNearbyEntities(c, 10.0D, 10.0D, 10.0D)) {
                            if (ent instanceof Projectile proj && (proj instanceof AbstractArrow || proj instanceof Fireball)) {
                                if (proj instanceof AbstractArrow arr && arr.isInBlock()) continue;
                                ProjectileSource source = proj.getShooter();
                                if (p.equals(source)) continue;
                                Vector backToShooter = source instanceof Entity shooter
                                        ? shooter.getLocation().toVector().subtract(proj.getLocation().toVector())
                                        : proj.getVelocity().clone().multiply(-1.0D);
                                double speed = Math.max(1.8D, proj.getVelocity().length() * 1.25D);
                                if (backToShooter.lengthSquared() < 1.0E-4D) backToShooter = proj.getVelocity().clone().multiply(-1.0D);
                                proj.setVelocity(backToShooter.normalize().multiply(speed));
                                proj.setShooter(p);
                            } else if (plugin.isTargetable(ent, p) && !plugin.lockStates.containsKey(ent.getUniqueId())) {
                                LivingEntity le = (LivingEntity) ent;
                                Vector push = le.getLocation().toVector().subtract(c.toVector());
                                if (push.lengthSquared() >= 0.01D) {
                                    le.setVelocity(push.normalize().multiply(2.0D).setY(0.6D));
                                }
                            }
                        }
                        w.playSound(c, Sound.ENTITY_BREEZE_WIND_BURST, 1.4F, 0.7F);
                        w.spawnParticle(Particle.CLOUD, c.clone().add(0.0D, 1.0D, 0.0D), 40, 1.5D, 0.5D, 1.5D, 0.1D);
                    }
                }
            
}

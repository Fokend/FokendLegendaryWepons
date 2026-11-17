package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class MagnetListener implements Listener {
    private final FokendLegendaryWepons plugin;

    MagnetListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        Player p = e.getPlayer();
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_MAGNET)) return;

        e.setCancelled(true);
        if (!this.plugin.checkAndMarkTick(p, "magnet")) return;

        if (this.plugin.onCooldown(p, "magnet", 5000L)) {
            long sec = this.plugin.cooldownRemaining(p, "magnet", 5000L) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(p, Component.text("Магнит на перезарядке: " + sec + "с", NamedTextColor.RED));
            return;
        }

        this.plugin.setCooldown(p, "magnet", 5000L, "Магнитник");
        Location center = p.getLocation();
        final World world = p.getWorld();

        if (!p.isSneaking()) {
            Location eye = p.getEyeLocation();
            Vector look = eye.getDirection().normalize();
            RayTraceResult blockHit = world.rayTraceBlocks(eye, look, 12.0D, FluidCollisionMode.NEVER, true);
            final Location focal = blockHit == null
                    ? eye.clone().add(look.clone().multiply(10.0D))
                    : blockHit.getHitPosition().subtract(look.clone().multiply(0.5D)).toLocation(world);

            BoundingBox searchBox = BoundingBox.of(center, 12.0D, 12.0D, 12.0D)
                    .union(BoundingBox.of(focal, 10.0D, 10.0D, 10.0D));
            final List<UUID> pulledIds = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();

            for (Entity ent : world.getNearbyEntities(
                    searchBox,
                    target -> this.plugin.isTargetable(target, p)
                            && !this.plugin.lockStates.containsKey(target.getUniqueId()))) {
                if (!seen.add(ent.getUniqueId())) continue;
                LivingEntity le = (LivingEntity) ent;
                if (le.getLocation().distanceSquared(center) > 256.0D
                        && le.getLocation().distanceSquared(focal) > 144.0D) {
                    continue;
                }
                pulledIds.add(le.getUniqueId());
                Vector pull = focal.toVector().subtract(le.getLocation().toVector());
                if (pull.lengthSquared() >= 0.01D) {
                    double speed = Math.min(2.4D, Math.max(1.1D, pull.length() * 0.28D));
                    le.setVelocity(pull.normalize().multiply(speed).setY(0.35D));
                }
            }

            if (!pulledIds.isEmpty()) {
                final UUID ownerId = p.getUniqueId();
                new BukkitRunnable() {
                    int pulse = 0;

                    @Override
                    public void run() {
                        this.pulse++;
                        Player owner = MagnetListener.this.plugin.getServer().getPlayer(ownerId);
                        if (this.pulse <= 2 && owner != null && owner.isOnline()) {
                            for (UUID targetId : pulledIds) {
                                Entity ent = MagnetListener.this.plugin.getServer().getEntity(targetId);
                                if (ent instanceof LivingEntity le
                                        && le.isValid()
                                        && !le.isDead()
                                        && le.getWorld().equals(world)
                                        && !MagnetListener.this.plugin.lockStates.containsKey(targetId)) {
                                    Vector pull = focal.toVector().subtract(le.getLocation().toVector());
                                    if (pull.lengthSquared() >= 0.25D) {
                                        double speed = Math.min(1.6D, Math.max(0.6D, pull.length() * 0.2D));
                                        le.setVelocity(pull.normalize().multiply(speed).setY(0.2D));
                                    }
                                }
                            }
                        } else {
                            this.cancel();
                        }
                    }
                }.runTaskTimer(this.plugin, 5L, 5L);
            }

            world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2F, 0.7F);
            world.spawnParticle(
                    Particle.END_ROD, center.clone().add(0.0D, 1.0D, 0.0D), 40, 1.5D, 0.5D, 1.5D, 0.05D);
        } else {
            BoundingBox box = BoundingBox.of(center, 10.0D, 10.0D, 10.0D);
            for (Entity ent : world.getNearbyEntities(
                    box, target -> target instanceof Projectile || this.plugin.isTargetable(target, p))) {
                if (ent instanceof Projectile proj && (proj instanceof AbstractArrow || proj instanceof Fireball)) {
                    if (proj instanceof AbstractArrow arr && arr.isInBlock()) {
                        continue;
                    }
                    Vector toOwner = p.getEyeLocation().toVector().subtract(proj.getLocation().toVector());
                    if (toOwner.lengthSquared() >= 0.01D && proj.getVelocity().dot(toOwner) > 0.0D) {
                        ProjectileSource source = proj.getShooter();
                        if (!p.equals(source)) {
                            Vector reflectDir = source instanceof Entity shooter
                                    ? shooter.getLocation().toVector().subtract(proj.getLocation().toVector())
                                    : proj.getVelocity().clone().multiply(-1.0D);
                            double speed = Math.max(1.8D, proj.getVelocity().length() * 1.25D);
                            if (reflectDir.lengthSquared() < 1.0E-4D) {
                                reflectDir = proj.getVelocity().clone().multiply(-1.0D);
                            }
                            proj.setVelocity(reflectDir.normalize().multiply(speed));
                            // Do not reassign shooter on Tridents to avoid stealing Loyalty tridents
                            if (!(proj instanceof Trident)) {
                                proj.setShooter(p);
                            }
                        }
                    }
                    continue;
                }

                if (this.plugin.isTargetable(ent, p) && !this.plugin.lockStates.containsKey(ent.getUniqueId())) {
                    LivingEntity le = (LivingEntity) ent;
                    Vector push = le.getLocation().toVector().subtract(center.toVector());
                    if (push.lengthSquared() >= 0.01D) {
                        le.setVelocity(push.normalize().multiply(2.0D).setY(0.6D));
                    }
                }
            }

            world.playSound(center, Sound.ENTITY_BREEZE_WIND_BURST, 1.4F, 0.7F);
            world.spawnParticle(
                    Particle.CLOUD, center.clone().add(0.0D, 1.0D, 0.0D), 40, 1.5D, 0.5D, 1.5D, 0.1D);
        }
    }
}

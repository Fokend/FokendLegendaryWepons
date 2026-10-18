package FuckFokend.fokendLegendaryWepons;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class ChaosBoomerangListener implements Listener {
    private final FokendLegendaryWepons plugin;
    ChaosBoomerangListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

        @EventHandler
        public void onInteract(PlayerInteractEvent e) {
            if (!plugin.isAbilityClick(e)) return;
            final Player p = e.getPlayer();
            ItemStack held = p.getInventory().getItemInMainHand();
            if (!plugin.isLegendary(held, plugin.ID_CHAOS_BOOMERANG)) return;
            if (!plugin.checkAndMarkTick(p, "chaos_boomerang")) return;
            e.setCancelled(true);

            if (plugin.onCooldown(p, "chaos_boomerang", 8000L)) {
                long sec = plugin.cooldownRemaining(p, "chaos_boomerang", 8000L) / 1000L + 1L;
                plugin.sendPriorityActionBar(p, Component.text("Бумеранг на перезарядке: " + sec + "с", NamedTextColor.RED));
                return;
            }
            plugin.setCooldown(p, "chaos_boomerang", 8000L, "Бумеранг хаоса");

            final ItemStack thrownBoomerang = held.clone();
            thrownBoomerang.setAmount(1);
            if (held.getAmount() > 1) {
                held.setAmount(held.getAmount() - 1);
            } else {
                p.getInventory().setItemInMainHand(null);
            }

            Location startLoc = p.getEyeLocation().subtract(0.0D, 0.3D, 0.0D);
            final World world = p.getWorld();
            final ItemDisplay stand = plugin.track(world.spawn(startLoc, ItemDisplay.class, d -> {
                d.setItemStack(thrownBoomerang);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55F), new Quaternionf()));
            }));
            world.playSound(startLoc, Sound.ENTITY_ENDER_PEARL_THROW, 1.0F, 1.3F);

            // Иногда бумеранг выбирает ближайшую подходящую цель и слегка корректирует к ней курс.
            final LivingEntity homingTarget = Math.random() < 0.30D
                    ? world.getNearbyEntities(p.getLocation(), 16.0D, 8.0D, 16.0D).stream()
                            .filter(LivingEntity.class::isInstance)
                            .map(LivingEntity.class::cast)
                            .filter(entity -> plugin.isTargetable(entity, p))
                            .min((a, b) -> Double.compare(a.getLocation().distanceSquared(p.getLocation()),
                                    b.getLocation().distanceSquared(p.getLocation())))
                            .orElse(null)
                    : null;

            BukkitRunnable task = new BukkitRunnable() {
                int tick = 0;
                int flightTicks = 0;
                int bounces = 0;
                int nextForcedBounceTick = 101;
                int spinBoost = 0;
                int stuckElapsed = 0;
                int stuckDuration = 0;
                boolean stuck = false;
                boolean returning = false;
                boolean done = false;
                Vector direction = p.getEyeLocation().getDirection().clone().normalize();
                Vector pendingDirection = null;
                final Map<UUID, Integer> hitCooldowns = new HashMap<>();

                private void giveBackBoomerang() {
                    if (done) return;
                    done = true;
                    Location at = stand.getLocation();
                    plugin.boomerangs.remove(stand.getUniqueId());
                    plugin.removeTracked(stand);
                    cancel();
                    if (p.isOnline() && !p.isDead()) {
                        if (p.getInventory().getItemInMainHand().getType().isAir()) {
                            p.getInventory().setItemInMainHand(thrownBoomerang);
                        } else {
                            plugin.giveOrDrop(p, thrownBoomerang);
                        }
                        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0F, 1.4F);
                        plugin.sendPriorityActionBar(p, Component.text("Бумеранг пойман! Отскоков: " + bounces, NamedTextColor.GREEN));
                    } else {
                        at.getWorld().dropItemNaturally(at, thrownBoomerang);
                    }
                }

                @Override
                public void run() {
                    if (done) return;
                    if (!stand.isValid() || !p.isOnline() || p.isDead()) {
                        giveBackBoomerang();
                        return;
                    }
                    tick++;
                    hitCooldowns.replaceAll((k, v) -> v - 1);
                    hitCooldowns.values().removeIf(v -> v <= 0);

                    if (tick > 700) {
                        giveBackBoomerang();
                        return;
                    }

                    if (stuck) {
                        stuckElapsed++;
                        if (stuckElapsed % 5 == 0) {
                            stand.getWorld().spawnParticle(Particle.CRIT, stand.getLocation(), 2, 0.1D, 0.1D, 0.1D, 0.01D);
                        }
                        if (stuckElapsed >= stuckDuration) {
                            stuck = false;
                            direction = pendingDirection != null ? pendingDirection : direction.clone().multiply(-1);
                            pendingDirection = null;
                        }
                        return;
                    }

                    flightTicks++;
                    Location current = stand.getLocation();
                    if (!returning && homingTarget != null && homingTarget.isValid()
                            && homingTarget.getWorld().equals(world)) {
                        Vector towardTarget = homingTarget.getEyeLocation().toVector().subtract(current.toVector());
                        if (towardTarget.lengthSquared() > 0.01D) {
                            direction.multiply(0.78D).add(towardTarget.normalize().multiply(0.22D)).normalize();
                        }
                    }
                    if (!returning && bounces >= plugin.BOOMERANG_MAX_BOUNCES) returning = true;
                    if (!returning && flightTicks > 100) {
                        if (bounces >= plugin.BOOMERANG_MIN_BOUNCES) {
                            returning = true;
                        } else if (flightTicks >= nextForcedBounceTick) {
                            // Guarantee three ricochets even when the flight path finds no targets or walls.
                            direction = direction.clone().rotateAroundY(Math.toRadians(110.0D + Math.random() * 100.0D)).normalize();
                            nextForcedBounceTick += 12;
                            bounces++;
                            spinBoost = 8;
                            Location ricochet = stand.getLocation();
                            world.playSound(ricochet, Sound.ITEM_TRIDENT_HIT, 0.8F, 1.2F);
                            world.spawnParticle(Particle.CRIT, ricochet, 8, 0.15D, 0.15D, 0.15D, 0.04D);
                        }
                    }
                    if (returning) {
                        if (!current.getWorld().equals(p.getWorld())) {
                            giveBackBoomerang();
                            return;
                        }
                        Vector toPlayer = p.getEyeLocation().toVector().subtract(current.toVector());
                        if (toPlayer.lengthSquared() < 2.5D) {
                            giveBackBoomerang();
                            return;
                        }
                        direction = toPlayer.normalize();
                    }

                        double spin = (tick * 0.65D + spinBoost * 1.8D) % (Math.PI * 2.0D);
                        stand.setInterpolationDelay(0);
                        stand.setInterpolationDuration(1);
                        stand.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateZ((float) spin),
                                new Vector3f(0.55F), new Quaternionf()));
                        if (spinBoost > 0) spinBoost--;

                        RayTraceResult entityHit = world.rayTraceEntities(current, direction, 1.3D, 0.8D, ent ->
                                plugin.isTargetable(ent, p) && !hitCooldowns.containsKey(ent.getUniqueId()));
                        LivingEntity targetLe = null;
                        if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity le) {
                            targetLe = le;
                        } else {
                            Location mid = current.clone().add(direction.clone().multiply(0.65D));
                            for (Entity ent : world.getNearbyEntities(mid, 1.1D, 1.1D, 1.1D)) {
                                if (plugin.isTargetable(ent, p) && !hitCooldowns.containsKey(ent.getUniqueId())) {
                                    targetLe = (LivingEntity) ent;
                                    break;
                                }
                            }
                        }

                        if (targetLe != null) {
                            hitCooldowns.put(targetLe.getUniqueId(), 20);
                            targetLe.setNoDamageTicks(0);
                            double dmg = 10.0D + Math.min(bounces, 10) * 2.5D;
                            targetLe.damage(dmg, p);
                            PotionEffectType[] chaos = {
                                    PotionEffectType.SLOWNESS,
                                    PotionEffectType.POISON,
                                    PotionEffectType.WEAKNESS,
                                    PotionEffectType.NAUSEA,
                                    PotionEffectType.BLINDNESS
                            };
                            targetLe.addPotionEffect(new PotionEffect(chaos[(int) (Math.random() * chaos.length)], 80, 1, false, true));
                            plugin.spawnBoom(world, targetLe.getLocation());
                            spinBoost = 8;
                            bounces++;
                            stand.teleport(targetLe.getLocation().add(0.0D, targetLe.getHeight() * 0.5D, 0.0D));
                            if (bounces >= plugin.BOOMERANG_MAX_BOUNCES) {
                                returning = true;
                            } else if (!returning) {
                                direction = direction.clone().rotateAroundY(Math.toRadians(-45.0D + Math.random() * 90.0D)).normalize();
                            }
                            return;
                        }

                        RayTraceResult blockHit = world.rayTraceBlocks(current, direction, 1.3D, FluidCollisionMode.NEVER, true);
                        if (blockHit != null && blockHit.getHitBlockFace() != null) {
                            if (returning) {
                                giveBackBoomerang();
                                return;
                            }
                            Vector normal = blockHit.getHitBlockFace().getDirection().normalize();
                            double dot = direction.dot(normal);
                            pendingDirection = direction.clone().subtract(normal.clone().multiply(2.0D * dot)).normalize();
                            Location hitLoc = blockHit.getHitPosition().toLocation(world).add(normal.clone().multiply(0.15D));
                            stand.teleport(hitLoc);
                            stuck = true;
                            stuckElapsed = 0;
                            stuckDuration = 2 + (int) (Math.random() * 3.0D);
                            bounces++;
                            world.playSound(hitLoc, Sound.ITEM_TRIDENT_HIT_GROUND, 1.2F, 1.0F);
                            world.spawnParticle(Particle.CRIT, hitLoc, 10, 0.2D, 0.2D, 0.2D, 0.05D);
                            if (bounces >= plugin.BOOMERANG_MAX_BOUNCES) returning = true;
                        } else {
                            Location next = current.clone().add(direction.clone().multiply(1.3D));
                            stand.teleport(next);
                            world.spawnParticle(Particle.PORTAL, next.clone().add(0.0D, 0.4D, 0.0D), 2, 0.05D, 0.05D, 0.05D, 0.02D);
                        }
                    }
                    };
                    task.runTaskTimer(plugin, 0L, 1L);
                    plugin.boomerangs.put(stand.getUniqueId(), new FokendLegendaryWepons.ThrownBoomerang(p.getUniqueId(), thrownBoomerang));
                }
            
}

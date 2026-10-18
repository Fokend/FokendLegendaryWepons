package FuckFokend.fokendLegendaryWepons;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class TwinsSwordListener implements Listener {
    private final FokendLegendaryWepons plugin;
    TwinsSwordListener(FokendLegendaryWepons plugin) { this.plugin = plugin; }

                private final Set<UUID> processing = new HashSet<>();

                @EventHandler
                public void onInteract(PlayerInteractEvent e) {
                    if (!plugin.isAbilityClick(e)) return;
                    Player p = e.getPlayer();
                    if (p.isSneaking() || !plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_TWINS_SWORD)) return;
                    if (!plugin.checkAndMarkTick(p, "twins_sword")) return;
                    e.setCancelled(true);

                    if (plugin.onCooldown(p, "twins_summon", 30000L)) {
                        long s = plugin.cooldownRemaining(p, "twins_summon", 30000L) / 1000L + 1L;
                        plugin.sendPriorityActionBar(p, Component.text("Призыв близнецов на перезарядке: " + s + "с", NamedTextColor.RED));
                        return;
                    }
                    plugin.setCooldown(p, "twins_summon", 30000L, "Призыв близнецов");

                    List<LivingEntity> old = plugin.activeTwins.remove(p.getUniqueId());
                    if (old != null) {
                        for (LivingEntity ent : old) {
                            plugin.removeTracked(ent);
                        }
                    }

                    final List<LivingEntity> twinsList = new ArrayList<>();
                    Vector right = plugin.flatRight(p.getLocation().getYaw());
                    ResolvableProfile.Builder pb = ResolvableProfile.resolvableProfile().uuid(p.getUniqueId()).name(p.getName());
                    for (ProfileProperty prop : p.getPlayerProfile().getProperties()) {
                        pb.addProperty(prop);
                    }
                    final ResolvableProfile skin = pb.build();

                    for (int i = 0; i < 2; i++) {
                        final int side = i == 0 ? -1 : 1;
                        Location spawnLoc = p.getLocation().clone().add(right.clone().multiply(1.4D * side));
                        Mannequin npc = plugin.track(p.getWorld().spawn(spawnLoc, Mannequin.class, m -> {
                            m.setProfile(skin);
                            m.setDescription(null);
                            m.setImmovable(false);
                            m.setGravity(false);
                            m.setSilent(true);
                            m.setCollidable(false);
                            m.setInvulnerable(true);
                            m.setCanPickupItems(false);
                            m.setRemoveWhenFarAway(false);
                            m.setPersistent(false);
                            if (m.getEquipment() != null) {
                                m.getEquipment().setHelmet(p.getInventory().getHelmet());
                                m.getEquipment().setChestplate(p.getInventory().getChestplate());
                                m.getEquipment().setLeggings(p.getInventory().getLeggings());
                                m.getEquipment().setBoots(p.getInventory().getBoots());
                                m.getEquipment().setItemInMainHand(plugin.twinsSword());
                            }
                            m.customName(Component.text(p.getName() + " [Клон " + (side == -1 ? "I" : "II") + "]",
                                    NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
                            m.setCustomNameVisible(true);
                            m.setMetadata(plugin.META_TWIN, new FixedMetadataValue(plugin, p.getUniqueId().toString()));
                        }));
                        twinsList.add(npc);
                    }
                    plugin.activeTwins.put(p.getUniqueId(), twinsList);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2F, 1.0F);
                    plugin.sendPriorityActionBar(p, Component.text("Близнецы призваны на 15 секунд!", NamedTextColor.LIGHT_PURPLE));

                    new BukkitRunnable() {
                        int tick = 0;
                        float formYaw = p.getLocation().getYaw();
                        Location lastPl = p.getLocation().clone();

                        @Override
                        public void run() {
                            if (!p.isOnline() || p.isDead() || tick++ > 600) {
                                for (LivingEntity npc : twinsList) {
                                    if (npc != null && npc.isValid()) {
                                        npc.getWorld().spawnParticle(Particle.PORTAL, npc.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.2D, 0.4D, 0.2D, 0.05D);
                                        plugin.removeTracked(npc);
                                    }
                                }
                                plugin.activeTwins.remove(p.getUniqueId());
                                cancel();
                                return;
                            }

                            Location pl = p.getLocation();
                            World world = pl.getWorld();
                            double dx = pl.getX() - lastPl.getX();
                            double dz = pl.getZ() - lastPl.getZ();
                            boolean moving = dx * dx + dz * dz > 0.0025D;
                            lastPl = pl.clone();
                            if (moving) {
                                formYaw = plugin.lerpAngle(formYaw, pl.getYaw(), 0.25F);
                            }
                            Vector rightNow = plugin.flatRight(formYaw);
                            boolean sneaking = p.isSneaking();

                            for (int i = 0; i < twinsList.size(); i++) {
                                LivingEntity npc = twinsList.get(i);
                                if (npc == null || !npc.isValid()) continue;
                                int side = i == 0 ? -1 : 1;

                                Location want = pl.clone().add(rightNow.clone().multiply(1.4D * side));
                                Location from = pl.clone().add(0.0D, 1.0D, 0.0D);
                                Vector v = want.clone().add(0.0D, 1.0D, 0.0D).toVector().subtract(from.toVector());
                                double len = v.length();
                                if (len > 0.01D) {
                                    Vector dirV = v.clone().normalize();
                                    RayTraceResult wall = world.rayTraceBlocks(from, dirV, len, FluidCollisionMode.NEVER, true);
                                    if (wall != null) {
                                        Vector hp = wall.getHitPosition().clone().subtract(dirV.clone().multiply(0.4D));
                                        want = new Location(world, hp.getX(), hp.getY() - 1.0D, hp.getZ());
                                    }
                                }

                                RayTraceResult g = world.rayTraceBlocks(want.clone().add(0.0D, 1.5D, 0.0D), new Vector(0, -1, 0), 4.0D, FluidCollisionMode.NEVER, true);
                                if (g != null && Math.abs(g.getHitPosition().getY() - pl.getY()) <= 2.0D) {
                                    want.setY(g.getHitPosition().getY());
                                }

                                Location cur = npc.getLocation();
                                Location next;
                                if (!cur.getWorld().equals(world) || cur.distanceSquared(want) > 64.0D) {
                                    next = want.clone();
                                } else {
                                    next = cur.clone().add(want.toVector().subtract(cur.toVector()).multiply(0.45D));
                                }
                                next.setYaw(pl.getYaw());
                                next.setPitch(pl.getPitch());
                                npc.teleport(next);

                                try {
                                    npc.setPose(sneaking ? Pose.SNEAKING : Pose.STANDING, sneaking);
                                } catch (Exception ignored) {
                                }

                                if (tick % 10 == 0 && npc.getEquipment() != null) {
                                    npc.getEquipment().setHelmet(p.getInventory().getHelmet());
                                    npc.getEquipment().setChestplate(p.getInventory().getChestplate());
                                    npc.getEquipment().setLeggings(p.getInventory().getLeggings());
                                    npc.getEquipment().setBoots(p.getInventory().getBoots());
                                }
                                npc.getWorld().spawnParticle(Particle.DRAGON_BREATH, next.clone().add(0.0D, 0.8D, 0.0D), 1, 0.05D, 0.05D, 0.05D, 0.01D);
                            }
                        }
                    }.runTaskTimer(plugin, 0L, 1L);
                }

                @EventHandler
                public void onSwing(PlayerAnimationEvent e) {
                    if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
                    List<LivingEntity> t = plugin.activeTwins.get(e.getPlayer().getUniqueId());
                    if (t != null) {
                        for (LivingEntity npc : t) {
                            if (npc != null && npc.isValid()) npc.swingMainHand();
                        }
                    }
                }

                @EventHandler(ignoreCancelled = true)
                public void onOwnerHurt(EntityDamageEvent e) {
                    if (!(e.getEntity() instanceof Player p)) return;
                    List<LivingEntity> t = plugin.activeTwins.get(p.getUniqueId());
                    if (t != null) {
                        for (LivingEntity npc : t) {
                            if (npc != null && npc.isValid()) npc.playHurtAnimation(0F);
                        }
                    }
                }

                @EventHandler
                public void onCombust(EntityCombustEvent e) {
                    if (e.getEntity().hasMetadata(plugin.META_TWIN)) e.setCancelled(true);
                }

                @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
                public void onTwinDamaged(EntityDamageEvent e) {
                    if (e.getEntity().hasMetadata(plugin.META_TWIN)) e.setCancelled(true);
                }

                @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
                public void onDamage(EntityDamageByEntityEvent e) {
                    if (!(e.getDamager() instanceof Player p)) return;
                    if (e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;
                    if (!plugin.isLegendary(p.getInventory().getItemInMainHand(), plugin.ID_TWINS_SWORD)) return;
                    if (!plugin.isTargetable(e.getEntity(), p)) return;

                    final LivingEntity victim = (LivingEntity) e.getEntity();
                    final UUID victimId = victim.getUniqueId();
                    if (processing.contains(victimId)) return;

                    final List<LivingEntity> twins = plugin.activeTwins.get(p.getUniqueId());
                    int validTwins = 0;
                    if (twins != null) {
                        for (LivingEntity npc : twins) {
                            if (npc != null && npc.isValid()) {
                                npc.swingMainHand();
                                validTwins++;
                            }
                        }
                    }

                    final boolean extra = Math.random() <= 0.25D;
                    final double extraDmg = e.getDamage() * 0.7D;
                    final double twinDmg = validTwins * 4.0D;
                    final double totalBonus = twinDmg + (extra ? extraDmg : 0.0D);

                    if (totalBonus > 0) {
                        processing.add(victimId);
                        final int twinsCount = validTwins;
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            try {
                                if (!victim.isDead() && victim.isValid()) {
                                    victim.setNoDamageTicks(0);
                                    victim.damage(totalBonus, p);
                                    if (twinsCount > 0) {
                                        victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0.0D, 1.0D, 0.0D), twinsCount, 0.3D, 0.2D, 0.3D, 0);
                                    }
                                    if (extra) {
                                        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.3D, 0.3D, 0.3D, 0.1D);
                                        p.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.2F);
                                    }
                                }
                            } finally {
                                processing.remove(victimId);
                            }
                        });
                    }
                }
            
}

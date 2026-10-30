package FuckFokend.fokendLegendaryWepons;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class TwinsSwordListener implements Listener {
    final FokendLegendaryWepons plugin;
    final Set<UUID> processing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> swapCooldowns = new ConcurrentHashMap<>();

    TwinsSwordListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        boolean hasTwins = this.plugin.isLegendary(e.getMainHandItem(), FokendLegendaryWepons.ID_TWINS_SWORD)
                || this.plugin.isLegendary(e.getOffHandItem(), FokendLegendaryWepons.ID_TWINS_SWORD)
                || this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_TWINS_SWORD)
                || this.plugin.isLegendary(p.getInventory().getItemInOffHand(), FokendLegendaryWepons.ID_TWINS_SWORD);
        if (hasTwins) {
            long now = System.currentTimeMillis();
            Long last = this.swapCooldowns.get(p.getUniqueId());
            if (last != null && now - last < 500L) {
                e.setCancelled(true);
            } else {
                this.swapCooldowns.put(p.getUniqueId(), now);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        Player p = e.getPlayer();
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_TWINS_SWORD)) return;
        if (!this.plugin.checkAndMarkTick(p, "twins_sword")) return;

        e.setCancelled(true);
        if (this.plugin.onCooldown(p, "twins_summon", 30000L)) {
            long sec = this.plugin.cooldownRemaining(p, "twins_summon", 30000L) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(
                    p, Component.text("Близнецы на перезарядке: " + sec + "с", NamedTextColor.RED));
            return;
        }

        this.plugin.setCooldown(p, "twins_summon", 30000L, "Призыв близнецов");
        List<UUID> oldTwins = this.plugin.activeTwins.remove(p.getUniqueId());
        if (oldTwins != null) {
            for (UUID oldId : oldTwins) {
                Entity ent = this.plugin.getServer().getEntity(oldId);
                if (ent != null) {
                    this.plugin.removeTracked(ent);
                } else {
                    this.plugin.tempEntities.remove(oldId);
                }
            }
        }

        final List<UUID> twinIds = new ArrayList<>();
        Vector right = this.plugin.flatRight(p.getLocation().getYaw());
        ResolvableProfile.Builder profileBuilder =
                ResolvableProfile.resolvableProfile().uuid(p.getUniqueId()).name(p.getName());
        for (ProfileProperty prop : p.getPlayerProfile().getProperties()) {
            profileBuilder.addProperty(prop);
        }
        ResolvableProfile profile = profileBuilder.build();
        final UUID ownerId = p.getUniqueId();

        for (int i = 0; i < 1; i++) {
            int side = i == 0 ? -1 : 1;
            Location spawnLoc = p.getLocation().clone().add(right.clone().multiply(1.4D * side));
            Mannequin clone = this.plugin.track(p.getWorld().spawn(spawnLoc, Mannequin.class, m -> {
                m.setProfile(profile);
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
                    m.getEquipment().setHelmet(this.plugin.visualCopy(p.getInventory().getHelmet()));
                    m.getEquipment().setChestplate(this.plugin.visualCopy(p.getInventory().getChestplate()));
                    m.getEquipment().setLeggings(this.plugin.visualCopy(p.getInventory().getLeggings()));
                    m.getEquipment().setBoots(this.plugin.visualCopy(p.getInventory().getBoots()));
                    m.getEquipment().setItemInMainHand(this.plugin.visualCopy(this.plugin.twinsSword()));
                }
                m.customName(Component.text(
                        "✦ " + p.getName() + " " + (side == -1 ? "I" : "II"),
                        NamedTextColor.DARK_PURPLE,
                        TextDecoration.BOLD));
                m.setCustomNameVisible(true);
                m.setMetadata(FokendLegendaryWepons.META_TWIN, new FixedMetadataValue(this.plugin, ownerId.toString()));
            }));
            twinIds.add(clone.getUniqueId());
        }

        this.plugin.activeTwins.put(ownerId, twinIds);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2F, 1.0F);
        this.plugin.sendPriorityActionBar(p, Component.text("Близнецы призваны на 30 секунд!", NamedTextColor.LIGHT_PURPLE));

        final float startYaw = p.getLocation().getYaw();
        final Location startLoc = p.getLocation().clone();

        new BukkitRunnable() {
            int tick = 0;
            float formYaw = startYaw;
            Location lastPl = startLoc;

            @Override
            public void run() {
                Player owner = TwinsSwordListener.this.plugin.getServer().getPlayer(ownerId);
                if (owner != null && owner.isOnline() && !owner.isDead() && this.tick++ <= 600) {
                    Location pl = owner.getLocation();
                    World world = pl.getWorld();
                    if (world == null) return;

                    double dx = pl.getX() - this.lastPl.getX();
                    double dz = pl.getZ() - this.lastPl.getZ();
                    boolean moving = dx * dx + dz * dz > 0.0025D;
                    this.lastPl = pl.clone();
                    if (moving) {
                        this.formYaw = TwinsSwordListener.this.plugin.lerpAngle(this.formYaw, pl.getYaw(), 0.25F);
                    }

                    Vector rightNow = TwinsSwordListener.this.plugin.flatRight(this.formYaw);
                    boolean sneaking = owner.isSneaking();

                    for (int i = 0; i < twinIds.size(); i++) {
                        Entity ent = TwinsSwordListener.this.plugin.getServer().getEntity(twinIds.get(i));
                        if (ent instanceof LivingEntity npc && npc.isValid()) {
                            int side = i == 0 ? -1 : 1;
                            Location want = pl.clone().add(rightNow.clone().multiply(1.4D * side));
                            Location from = pl.clone().add(0.0D, 1.0D, 0.0D);
                            Vector toWant = want.clone().add(0.0D, 1.0D, 0.0D).toVector().subtract(from.toVector());
                            double len = toWant.length();
                            if (len > 0.01D) {
                                Vector dirV = toWant.clone().normalize();
                                RayTraceResult wall = world.rayTraceBlocks(from, dirV, len, FluidCollisionMode.NEVER, true);
                                if (wall != null) {
                                    Vector hp = wall.getHitPosition().clone().subtract(dirV.clone().multiply(0.4D));
                                    want = new Location(world, hp.getX(), hp.getY() - 1.0D, hp.getZ());
                                }
                            }

                            RayTraceResult ground = world.rayTraceBlocks(
                                    want.clone().add(0.0D, 1.5D, 0.0D),
                                    new Vector(0, -1, 0),
                                    4.0D,
                                    FluidCollisionMode.NEVER,
                                    true);
                            if (ground != null && Math.abs(ground.getHitPosition().getY() - pl.getY()) <= 2.0D) {
                                want.setY(ground.getHitPosition().getY());
                            }

                            Location cur = npc.getLocation();
                            Location next;
                            if (cur.getWorld().equals(world) && cur.distanceSquared(want) <= 64.0D) {
                                next = cur.clone().add(want.toVector().subtract(cur.toVector()).multiply(0.45D));
                            } else {
                                next = want.clone();
                            }

                            next.setYaw(pl.getYaw());
                            next.setPitch(pl.getPitch());
                            npc.teleport(next);

                            try {
                                npc.setPose(sneaking ? Pose.SNEAKING : Pose.STANDING, sneaking);
                            } catch (Exception ignored) {
                            }

                            if (this.tick % 10 == 0 && npc.getEquipment() != null) {
                                npc.getEquipment().setHelmet(
                                        TwinsSwordListener.this.plugin.visualCopy(owner.getInventory().getHelmet()));
                                npc.getEquipment().setChestplate(
                                        TwinsSwordListener.this.plugin.visualCopy(owner.getInventory().getChestplate()));
                                npc.getEquipment().setLeggings(
                                        TwinsSwordListener.this.plugin.visualCopy(owner.getInventory().getLeggings()));
                                npc.getEquipment().setBoots(
                                        TwinsSwordListener.this.plugin.visualCopy(owner.getInventory().getBoots()));
                            }

                            npc.getWorld().spawnParticle(
                                    Particle.DRAGON_BREATH,
                                    next.clone().add(0.0D, 0.8D, 0.0D),
                                    1, 0.05D, 0.05D, 0.05D, 0.01D, 1.0F);
                        }
                    }
                } else {
                    for (UUID twinId : twinIds) {
                        Entity ent = TwinsSwordListener.this.plugin.getServer().getEntity(twinId);
                        if (ent instanceof LivingEntity npc && npc.isValid()) {
                            npc.getWorld().spawnParticle(
                                    Particle.PORTAL, npc.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.2D, 0.4D, 0.2D, 0.05D);
                            TwinsSwordListener.this.plugin.removeTracked(npc);
                        } else {
                            TwinsSwordListener.this.plugin.tempEntities.remove(twinId);
                        }
                    }
                    TwinsSwordListener.this.plugin.activeTwins.remove(ownerId, twinIds);
                    this.cancel();
                }
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        List<UUID> twinIds = this.plugin.activeTwins.get(e.getPlayer().getUniqueId());
        if (twinIds != null) {
            for (UUID twinId : twinIds) {
                Entity ent = this.plugin.getServer().getEntity(twinId);
                if (ent instanceof LivingEntity npc && npc.isValid()) {
                    npc.swingMainHand();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        List<UUID> twinIds = this.plugin.activeTwins.get(p.getUniqueId());
        if (twinIds != null) {
            for (UUID twinId : twinIds) {
                Entity ent = this.plugin.getServer().getEntity(twinId);
                if (ent instanceof LivingEntity npc && npc.isValid()) {
                    npc.playHurtAnimation(0.0F);
                }
            }
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (e.getEntity().hasMetadata(FokendLegendaryWepons.META_TWIN)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onTwinDamaged(EntityDamageEvent e) {
        if (e.getEntity().hasMetadata(FokendLegendaryWepons.META_TWIN)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_TWINS_SWORD)) return;
        if (!this.plugin.isTargetable(e.getEntity(), p)) return;

        LivingEntity victim = (LivingEntity) e.getEntity();
        UUID victimId = victim.getUniqueId();
        if (this.processing.contains(victimId)) return;

        List<UUID> twinIds = this.plugin.activeTwins.get(p.getUniqueId());
        int validTwins = 0;
        if (twinIds != null) {
            for (UUID twinId : twinIds) {
                Entity ent = this.plugin.getServer().getEntity(twinId);
                if (ent instanceof LivingEntity npc && npc.isValid()) {
                    npc.swingMainHand();
                    validTwins++;
                }
            }
        }

        boolean extra = Math.random() < 0.25D;
        double extraDmg = e.getDamage() * 0.7D;
        double twinDmg = validTwins * 4.0D;
        double totalBonus = twinDmg + (extra ? extraDmg : 0.0D);

        if (totalBonus > 0.0D) {
            this.processing.add(victimId);
            UUID ownerId = p.getUniqueId();
            int twinCount = validTwins;
            this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
                try {
                    Entity targetEnt = this.plugin.getServer().getEntity(victimId);
                    Player owner = this.plugin.getServer().getPlayer(ownerId);
                    if (targetEnt instanceof LivingEntity le && !le.isDead() && le.isValid() && owner != null) {
                        le.setNoDamageTicks(0);
                        le.damage(totalBonus, owner);
                        if (twinCount > 0) {
                            le.getWorld().spawnParticle(
                                    Particle.SWEEP_ATTACK,
                                    le.getLocation().add(0.0D, 1.0D, 0.0D),
                                    twinCount, 0.3D, 0.2D, 0.3D, 0.0D);
                        }
                        if (extra) {
                            le.getWorld().spawnParticle(
                                    Particle.CRIT,
                                    le.getLocation().add(0.0D, 1.0D, 0.0D),
                                    12, 0.3D, 0.3D, 0.3D, 0.1D);
                            owner.playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8F, 1.2F);
                        }
                    }
                } finally {
                    this.processing.remove(victimId);
                }
            });
        }
    }
}

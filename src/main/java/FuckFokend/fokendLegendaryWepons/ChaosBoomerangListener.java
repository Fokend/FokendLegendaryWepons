package FuckFokend.fokendLegendaryWepons;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
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

    ChaosBoomerangListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        final Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.plugin.isLegendary(held, FokendLegendaryWepons.ID_CHAOS_BOOMERANG)) return;
        if (!this.plugin.checkAndMarkTick(p, "chaos_boomerang")) return;

        e.setCancelled(true);
        if (this.plugin.onCooldown(p, "chaos_boomerang", 8000L)) {
            long sec = this.plugin.cooldownRemaining(p, "chaos_boomerang", 8000L) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(p, Component.text("Бумеранг на перезарядке: " + sec + "с", NamedTextColor.RED));
            return;
        }

        final ItemStack thrownItem = held.clone();
        thrownItem.setAmount(1);
        if (held.getAmount() > 1) {
            ItemStack remainder = held.clone();
            remainder.setAmount(held.getAmount() - 1);
            p.getInventory().setItemInMainHand(remainder);
        } else {
            p.getInventory().setItemInMainHand(null);
        }

        final String sessionId = UUID.randomUUID().toString();
        ItemMeta meta = thrownItem.getItemMeta();
        if (meta != null && this.plugin.throwSessionKey != null) {
            meta.getPersistentDataContainer().set(this.plugin.throwSessionKey, PersistentDataType.STRING, sessionId);
            thrownItem.setItemMeta(meta);
        }

        this.plugin.activeThrowSessions.add(sessionId);
        this.plugin.setCooldown(p, "chaos_boomerang", 8000L, "Бумеранг хаоса");

        final Location startLoc = p.getEyeLocation().subtract(0.0D, 0.3D, 0.0D);
        final World world = p.getWorld();
        ItemDisplay display = this.plugin.track(world.spawn(startLoc, ItemDisplay.class, d -> {
            d.setItemStack(thrownItem);
            d.setItemDisplayTransform(ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55F), new Quaternionf()));
            if (this.plugin.throwSessionKey != null) {
                d.getPersistentDataContainer().set(this.plugin.throwSessionKey, PersistentDataType.STRING, sessionId);
            }
        }));

        final UUID displayId = display.getUniqueId();
        final UUID ownerId = p.getUniqueId();
        this.plugin.boomerangs.put(displayId, new FokendLegendaryWepons.ThrownBoomerang(ownerId, thrownItem, sessionId));
        world.playSound(startLoc, Sound.ENTITY_ENDER_PEARL_THROW, 1.0F, 1.3F);

        LivingEntity initialHoming = null;
        if (Math.random() < 0.63D) {
            initialHoming = world.getNearbyEntities(p.getLocation(), 16.0D, 8.0D, 16.0D).stream()
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast)
                    .filter(ent -> this.plugin.isTargetable(ent, p))
                    .min(Comparator.comparingDouble(ent -> ent.getLocation().distanceSquared(p.getLocation())))
                    .orElse(null);
        }
        final UUID initialHomingId = initialHoming != null ? initialHoming.getUniqueId() : null;

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
            UUID currentHomingId = initialHomingId;
            Vector direction = p.getEyeLocation().getDirection().clone().normalize();
            Vector pendingDirection = null;
            final Map<UUID, Integer> hitCooldowns = new HashMap<>();

            private void giveBackBoomerang(Location fallbackLoc) {
                if (this.done) return;
                this.done = true;
                ChaosBoomerangListener.this.plugin.activeThrowSessions.remove(sessionId);
                FokendLegendaryWepons.ThrownBoomerang tb =
                        ChaosBoomerangListener.this.plugin.boomerangs.remove(displayId);
                Entity dispEnt = ChaosBoomerangListener.this.plugin.getServer().getEntity(displayId);
                Location dropLoc = dispEnt != null ? dispEnt.getLocation() : fallbackLoc;
                if (dispEnt != null) {
                    ChaosBoomerangListener.this.plugin.removeTracked(dispEnt);
                } else {
                    ChaosBoomerangListener.this.plugin.tempEntities.remove(displayId);
                }
                this.cancel();

                if (tb != null) {
                    ItemStack cleanItem = ChaosBoomerangListener.this.plugin.cleanBoomerangItem(thrownItem);
                    Player owner = ChaosBoomerangListener.this.plugin.getServer().getPlayer(ownerId);
                    if (owner != null && owner.isOnline() && !owner.isDead()) {
                        if (owner.getInventory().getItemInMainHand().getType().isAir()) {
                            owner.getInventory().setItemInMainHand(cleanItem);
                        } else {
                            ChaosBoomerangListener.this.plugin.giveOrDrop(owner, cleanItem);
                        }
                        owner.getWorld().playSound(owner.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0F, 1.4F);
                        ChaosBoomerangListener.this.plugin.sendPriorityActionBar(
                                owner, Component.text("Бумеранг пойман! Отскоков: " + this.bounces, NamedTextColor.GREEN));
                    } else if (owner != null && owner.isDead()) {
                        Location ownerDeathLoc = owner.getLocation();
                        if (ownerDeathLoc.getWorld() != null) {
                            Item dropped = ownerDeathLoc.getWorld().dropItemNaturally(ownerDeathLoc, cleanItem);
                            ChaosBoomerangListener.this.plugin.protectDroppedItem(dropped);
                        }
                    } else if (dropLoc != null && dropLoc.getWorld() != null) {
                        Item dropped = dropLoc.getWorld().dropItemNaturally(dropLoc, cleanItem);
                        ChaosBoomerangListener.this.plugin.protectDroppedItem(dropped);
                    }
                }
            }

            @Override
            public void run() {
                if (this.done) return;
                if (!ChaosBoomerangListener.this.plugin.boomerangs.containsKey(displayId)) {
                    this.done = true;
                    ChaosBoomerangListener.this.plugin.activeThrowSessions.remove(sessionId);
                    this.cancel();
                    return;
                }

                Entity dispEnt = ChaosBoomerangListener.this.plugin.getServer().getEntity(displayId);
                Player owner = ChaosBoomerangListener.this.plugin.getServer().getPlayer(ownerId);
                if (!(dispEnt instanceof ItemDisplay stand)
                        || !stand.isValid()
                        || owner == null
                        || !owner.isOnline()
                        || owner.isDead()
                        || !stand.getWorld().equals(owner.getWorld())) {
                    this.giveBackBoomerang(dispEnt != null ? dispEnt.getLocation() : startLoc);
                    return;
                }

                this.tick++;
                this.hitCooldowns.replaceAll((k, v) -> v - 1);
                this.hitCooldowns.values().removeIf(v -> v <= 0);
                if (this.tick > 700) {
                    this.giveBackBoomerang(stand.getLocation());
                    return;
                }

                if (this.stuck) {
                    this.stuckElapsed++;
                    if (this.stuckElapsed % 5 == 0) {
                        stand.getWorld().spawnParticle(Particle.CRIT, stand.getLocation(), 2, 0.1D, 0.1D, 0.1D, 0.01D);
                    }
                    if (this.stuckElapsed >= this.stuckDuration) {
                        this.stuck = false;
                        this.direction = this.pendingDirection != null
                                ? this.pendingDirection
                                : this.direction.clone().multiply(-1);
                        this.pendingDirection = null;
                    }
                    return;
                }

                this.flightTicks++;
                Location current = stand.getLocation();

                if (!this.returning && this.currentHomingId != null && !this.hitCooldowns.containsKey(this.currentHomingId)) {
                    Entity homingEnt = ChaosBoomerangListener.this.plugin.getServer().getEntity(this.currentHomingId);
                    if (homingEnt instanceof LivingEntity homingLe
                            && ChaosBoomerangListener.this.plugin.isTargetable(homingLe, owner)
                            && homingLe.getWorld().equals(world)) {
                        Vector toward = homingLe.getEyeLocation().toVector().subtract(current.toVector());
                        if (toward.lengthSquared() > 0.01D) {
                            this.direction.multiply(0.78D).add(toward.normalize().multiply(0.22D)).normalize();
                        }
                    } else {
                        this.currentHomingId = null;
                    }
                }

                if (!this.returning && this.bounces >= FokendLegendaryWepons.BOOMERANG_MAX_BOUNCES) {
                    this.returning = true;
                }

                if (!this.returning && this.flightTicks > 100) {
                    if (this.bounces >= FokendLegendaryWepons.BOOMERANG_MIN_BOUNCES) {
                        this.returning = true;
                    } else if (this.flightTicks >= this.nextForcedBounceTick) {
                        this.direction = this.direction.clone()
                                .rotateAroundY(Math.toRadians(110.0D + Math.random() * 100.0D))
                                .normalize();
                        this.nextForcedBounceTick += 12;
                        this.bounces++;
                        this.spinBoost = 8;
                        Location ricochet = stand.getLocation();
                        world.playSound(ricochet, Sound.ITEM_TRIDENT_HIT, 0.8F, 1.2F);
                        world.spawnParticle(Particle.CRIT, ricochet, 8, 0.15D, 0.15D, 0.15D, 0.04D);
                    }
                }

                if (this.returning) {
                    Vector toOwner = owner.getEyeLocation().toVector().subtract(current.toVector());
                    if (toOwner.lengthSquared() < 2.5D) {
                        this.giveBackBoomerang(current);
                        return;
                    }
                    this.direction = toOwner.normalize();
                }

                double spin = (this.tick * 0.65D + this.spinBoost * 1.8D) % (Math.PI * 2.0D);
                stand.setInterpolationDelay(0);
                stand.setInterpolationDuration(1);
                stand.setTransformation(new Transformation(
                        new Vector3f(), new Quaternionf().rotateZ((float) spin), new Vector3f(0.55F), new Quaternionf()));
                if (this.spinBoost > 0) {
                    this.spinBoost--;
                }

                LivingEntity hitTarget = null;
                RayTraceResult entityHit = world.rayTraceEntities(
                        current,
                        this.direction,
                        1.3D,
                        0.8D,
                        ent -> !ent.getUniqueId().equals(ownerId)
                                && ChaosBoomerangListener.this.plugin.isTargetable(ent, owner)
                                && !this.hitCooldowns.containsKey(ent.getUniqueId()));
                if (entityHit != null && entityHit.getHitEntity() instanceof LivingEntity le
                        && !le.getUniqueId().equals(ownerId)) {
                    hitTarget = le;
                } else {
                    Location mid = current.clone().add(this.direction.clone().multiply(0.65D));
                    for (Entity ent : world.getNearbyEntities(mid, 1.1D, 1.1D, 1.1D)) {
                        if (!ent.getUniqueId().equals(ownerId)
                                && ChaosBoomerangListener.this.plugin.isTargetable(ent, owner)
                                && !this.hitCooldowns.containsKey(ent.getUniqueId())) {
                            hitTarget = (LivingEntity) ent;
                            break;
                        }
                    }
                }

                if (hitTarget != null && !hitTarget.getUniqueId().equals(ownerId)) {
                    this.hitCooldowns.put(hitTarget.getUniqueId(), 20);
                    hitTarget.setNoDamageTicks(0);
                    double dmg = 10.0D + Math.min(this.bounces, 10) * 2.5D;
                    hitTarget.damage(dmg, owner);
                    PotionEffectType[] debuffs = new PotionEffectType[]{
                            PotionEffectType.SLOWNESS,
                            PotionEffectType.POISON,
                            PotionEffectType.WEAKNESS,
                            PotionEffectType.NAUSEA,
                            PotionEffectType.BLINDNESS
                    };
                    hitTarget.addPotionEffect(
                            new PotionEffect(debuffs[(int) (Math.random() * debuffs.length)], 80, 1, false, true));
                    ChaosBoomerangListener.this.plugin.spawnBoom(world, hitTarget.getLocation());
                    this.spinBoost = 8;
                    this.bounces++;
                    stand.teleport(hitTarget.getLocation().add(0.0D, hitTarget.getHeight() * 0.5D, 0.0D));

                    if (this.bounces >= FokendLegendaryWepons.BOOMERANG_MAX_BOUNCES) {
                        this.returning = true;
                    } else if (!this.returning) {
                        final LivingEntity justHit = hitTarget;
                        LivingEntity nextHoming = world.getNearbyEntities(justHit.getLocation(), 12.0D, 8.0D, 12.0D).stream()
                                .filter(LivingEntity.class::isInstance)
                                .map(LivingEntity.class::cast)
                                .filter(ent -> !ent.getUniqueId().equals(justHit.getUniqueId())
                                        && !this.hitCooldowns.containsKey(ent.getUniqueId())
                                        && ChaosBoomerangListener.this.plugin.isTargetable(ent, owner))
                                .min(Comparator.comparingDouble(ent -> ent.getLocation().distanceSquared(justHit.getLocation())))
                                .orElse(null);
                        if (nextHoming != null && Math.random() < 0.63D) {
                            this.currentHomingId = nextHoming.getUniqueId();
                            Vector toNext = nextHoming.getEyeLocation().toVector().subtract(stand.getLocation().toVector());
                            if (toNext.lengthSquared() > 0.01D) {
                                this.direction = toNext.normalize();
                            }
                        } else {
                            this.currentHomingId = null;
                            this.direction = this.direction.clone()
                                    .rotateAroundY(Math.toRadians(-45.0D + Math.random() * 90.0D))
                                    .normalize();
                        }
                    }
                    return;
                }

                RayTraceResult blockHit = world.rayTraceBlocks(current, this.direction, 1.3D, FluidCollisionMode.NEVER, true);
                if (blockHit != null && blockHit.getHitBlockFace() != null) {
                    if (this.returning) {
                        this.giveBackBoomerang(current);
                        return;
                    }
                    Vector normal = blockHit.getHitBlockFace().getDirection().normalize();
                    double dot = this.direction.dot(normal);
                    this.pendingDirection = this.direction.clone().subtract(normal.clone().multiply(2.0D * dot)).normalize();
                    Location hitLoc = blockHit.getHitPosition().toLocation(world).add(normal.clone().multiply(0.15D));
                    stand.teleport(hitLoc);
                    this.stuck = true;
                    this.stuckElapsed = 0;
                    this.stuckDuration = 2 + (int) (Math.random() * 3.0D);
                    this.bounces++;
                    world.playSound(hitLoc, Sound.ITEM_TRIDENT_HIT_GROUND, 1.2F, 1.0F);
                    world.spawnParticle(Particle.CRIT, hitLoc, 10, 0.2D, 0.2D, 0.2D, 0.05D);
                    if (this.bounces >= FokendLegendaryWepons.BOOMERANG_MAX_BOUNCES) {
                        this.returning = true;
                    }
                } else {
                    Location next = current.clone().add(this.direction.clone().multiply(1.3D));
                    stand.teleport(next);
                    world.spawnParticle(
                            Particle.PORTAL, next.clone().add(0.0D, 0.4D, 0.0D), 2, 0.05D, 0.05D, 0.05D, 0.02D);
                }
            }
        };
        task.runTaskTimer(this.plugin, 0L, 1L);
    }

    private boolean isInActiveThrowPhase(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && this.plugin.throwSessionKey != null) {
                String sessionId = meta.getPersistentDataContainer().get(
                        this.plugin.throwSessionKey, PersistentDataType.STRING);
                return sessionId != null && this.plugin.activeThrowSessions.contains(sessionId);
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent e) {
        Item itemDrop = e.getItemDrop();
        ItemStack stack = itemDrop.getItemStack();
        if (this.isInActiveThrowPhase(stack)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent e) {
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (this.isInActiveThrowPhase(stack)) {
                it.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        Entity damager = e.getDamager();
        UUID victimId = victim.getUniqueId();
        FokendLegendaryWepons.ThrownBoomerang tb = this.plugin.boomerangs.get(damager.getUniqueId());
        if (tb != null && victimId.equals(tb.owner())) {
            e.setCancelled(true);
        }
    }
}

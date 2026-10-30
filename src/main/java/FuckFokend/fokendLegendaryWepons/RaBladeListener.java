package FuckFokend.fokendLegendaryWepons;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ArmorStand.LockType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class RaBladeListener implements Listener {
    private static final long DOLL_LIFETIME_TICKS = 900L;
    private static final long CD = 60000L;
    private static final long REFLECT_CD_MS = 400L;
    final FokendLegendaryWepons plugin;
    private boolean reflecting = false;
    private final Map<UUID, Long> lastDollReflect = new ConcurrentHashMap<>();

    RaBladeListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        Player p = e.getPlayer();
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_RA_BLADE)) return;
        if (!this.plugin.checkAndMarkTick(p, "ra_blade")) return;

        e.setCancelled(true);
        UUID existingId = this.plugin.raDolls.remove(p.getUniqueId());
        if (existingId != null) {
            this.lastDollReflect.remove(existingId);
            Entity existing = this.plugin.getServer().getEntity(existingId);
            if (existing != null) {
                existing.getWorld().spawnParticle(
                        Particle.POOF, existing.getLocation().add(0.0D, 1.0D, 0.0D), 15, 0.2D, 0.4D, 0.2D, 0.05D);
                this.plugin.removeTracked(existing);
            } else {
                this.plugin.tempEntities.remove(existingId);
            }
            this.plugin.sendPriorityActionBar(p, Component.text("Манекен Ра отозван", NamedTextColor.YELLOW));
            return;
        }

        if (this.plugin.onCooldown(p, "ra_blade_spawn", CD)) {
            long sec = this.plugin.cooldownRemaining(p, "ra_blade_spawn", CD) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(
                    p, Component.text("Клинок Ра на перезарядке: " + sec + "с", NamedTextColor.RED));
            return;
        }

        this.plugin.setCooldown(p, "ra_blade_spawn", CD, "Призыв манекена Ра");
        Vector forward = this.plugin.flatDir(p.getLocation().getYaw());
        Location eyeFrom = p.getLocation().add(0.0D, 1.0D, 0.0D);
        RayTraceResult wall = p.getWorld().rayTraceBlocks(eyeFrom, forward, 1.8D, FluidCollisionMode.NEVER, true);
        double dist = wall != null
                ? Math.max(0.4D, wall.getHitPosition().distance(eyeFrom.toVector()) - 0.4D)
                : 1.8D;
        Location spawnLoc = p.getLocation().add(forward.clone().multiply(dist));
        spawnLoc.setYaw(p.getLocation().getYaw() + 180.0F);
        spawnLoc.setPitch(0.0F);

        final UUID ownerId = p.getUniqueId();
        ArmorStand doll = this.plugin.track(p.getWorld().spawn(spawnLoc, ArmorStand.class, as -> {
            as.setArms(true);
            as.setBasePlate(true);
            as.setGravity(false);
            as.setCanPickupItems(false);
            as.setRemoveWhenFarAway(false);
            as.setPersistent(false);
            as.customName(Component.text("Манекен Бога Ра", NamedTextColor.GOLD, TextDecoration.BOLD));
            as.setCustomNameVisible(true);
            as.setMetadata(FokendLegendaryWepons.META_RA_DOLL, new FixedMetadataValue(this.plugin, ownerId.toString()));

            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                try {
                    as.addEquipmentLock(slot, LockType.REMOVING_OR_CHANGING);
                } catch (Exception ignored) {
                }
            }

            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                as.getEquipment().setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));
                as.getEquipment().setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));
                as.getEquipment().setBoots(new ItemStack(Material.GOLDEN_BOOTS));
                as.getEquipment().setItemInMainHand(this.plugin.visualCopy(this.plugin.raBlade()));
            }
        }));

        final UUID dollId = doll.getUniqueId();
        this.plugin.raDolls.put(ownerId, dollId);
        this.plugin.sendPriorityActionBar(p, Component.text("Манекен Бога Ра призван на 45 сек!", NamedTextColor.GOLD));
        p.getWorld().playSound(spawnLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.5F);

        new BukkitRunnable() {
            @Override
            public void run() {
                UUID activeId = RaBladeListener.this.plugin.raDolls.get(ownerId);
                if (activeId != null && activeId.equals(dollId)) {
                    RaBladeListener.this.plugin.raDolls.remove(ownerId);
                    RaBladeListener.this.lastDollReflect.remove(dollId);
                    Entity ent = RaBladeListener.this.plugin.getServer().getEntity(dollId);
                    if (ent instanceof ArmorStand as && as.isValid()) {
                        as.getWorld().spawnParticle(
                                Particle.POOF, as.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.2D, 0.5D, 0.2D, 0.05D);
                        as.getWorld().playSound(as.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 1.2F);
                        RaBladeListener.this.plugin.removeTracked(as);
                    } else {
                        RaBladeListener.this.plugin.tempEntities.remove(dollId);
                    }

                    Player owner = RaBladeListener.this.plugin.getServer().getPlayer(ownerId);
                    if (owner != null && owner.isOnline()) {
                        RaBladeListener.this.plugin.sendPriorityActionBar(
                                owner, Component.text("Время действия манекена Ра истекло!", NamedTextColor.GRAY));
                    }
                }
            }
        }.runTaskLater(this.plugin, DOLL_LIFETIME_TICKS);
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().hasMetadata(FokendLegendaryWepons.META_RA_DOLL)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDollDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (!victim.hasMetadata(FokendLegendaryWepons.META_RA_DOLL)) return;

        e.setCancelled(true);
        if (this.reflecting) return;
        if (e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(e instanceof EntityDamageByEntityEvent be)) return;

        long now = System.currentTimeMillis();
        Long lastReflect = this.lastDollReflect.get(victim.getUniqueId());
        if (lastReflect != null && now - lastReflect < REFLECT_CD_MS) {
            return;
        }
        this.lastDollReflect.put(victim.getUniqueId(), now);

        Player attacker = null;
        if (be.getDamager() instanceof Player pl) {
            attacker = pl;
        } else if (be.getDamager() instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player pl) {
                attacker = pl;
            }
        }

        UUID ownerId;
        try {
            ownerId = UUID.fromString(victim.getMetadata(FokendLegendaryWepons.META_RA_DOLL).get(0).asString());
        } catch (Exception ex) {
            return;
        }

        double reflectedDmg = Math.max(e.getDamage(), 1.0D);
        Location center = victim.getLocation();
        World world = center.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0.0D, 1.0D, 0.0D), 2);
        world.spawnParticle(Particle.FLAME, center.clone().add(0.0D, 1.0D, 0.0D), 35, 0.4D, 0.6D, 0.4D, 0.1D);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.2F, 1.8F);

        Player owner = this.plugin.getServer().getPlayer(ownerId);
        int hits = 0;
        this.reflecting = true;
        try {
            BoundingBox box = BoundingBox.of(center, 10.0D, 10.0D, 10.0D);
            for (Entity target : world.getNearbyEntities(box)) {
                if (!target.getUniqueId().equals(ownerId)
                        && this.plugin.isTargetable(target, owner)
                        && target.getLocation().distanceSquared(center) <= 100.0D) {
                    LivingEntity le = (LivingEntity) target;
                    le.setNoDamageTicks(0);
                    le.damage(reflectedDmg, owner != null ? owner : attacker);
                    le.setFireTicks(80);
                    hits++;
                    le.getWorld().spawnParticle(
                            Particle.SOUL_FIRE_FLAME, le.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.2D, 0.4D, 0.2D, 0.05D);
                }
            }
        } finally {
            this.reflecting = false;
        }

        Player notify = attacker != null ? attacker : owner;
        if (notify != null) {
            this.plugin.sendPriorityActionBar(
                    notify,
                    Component.text(
                            "☀ Гнев Ра: " + String.format(Locale.US, "%.1f", reflectedDmg) + " урона по " + hits + " целям!",
                            NamedTextColor.GOLD));
        }
    }
}

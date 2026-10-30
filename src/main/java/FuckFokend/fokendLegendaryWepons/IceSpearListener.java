package FuckFokend.fokendLegendaryWepons;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

final class IceSpearListener implements Listener {
    private static final long CD = 75000L;
    private static final long FREEZE_HIT_CD = 6000L;
    private static final double RADIUS = 15.0D;
    final FokendLegendaryWepons plugin;

    IceSpearListener(FokendLegendaryWepons plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        Player p = e.getPlayer();
        if (!p.isSneaking()) return;
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.plugin.isLegendary(held, FokendLegendaryWepons.ID_ICE_SPEAR)) return;
        if (p.getInventory().getHeldItemSlot() != 0) return;
        if (!this.plugin.checkAndMarkTick(p, "ice_spear")) return;

        e.setCancelled(true);
        if (this.plugin.onCooldown(p, "ice_spear", CD)) {
            long sec = this.plugin.cooldownRemaining(p, "ice_spear", CD) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(
                    p, Component.text("Ледяное копьё на перезарядке: " + sec + "с", NamedTextColor.RED));
            return;
        }

        this.plugin.setCooldown(p, "ice_spear", CD, "Морозные пики");
        Location center = p.getLocation();
        World world = p.getWorld();
        BoundingBox box = BoundingBox.of(center, RADIUS, 8.0D, RADIUS);

        for (Entity ent : world.getNearbyEntities(box, target -> this.plugin.isTargetable(target, p))) {
            LivingEntity le = (LivingEntity) ent;
            double dx = le.getLocation().getX() - center.getX();
            double dz = le.getLocation().getZ() - center.getZ();
            if (dx * dx + dz * dz <= RADIUS * RADIUS) {
                this.plugin.spawnSpike(le.getLocation(), 1.6F);
                le.damage(4.0D, p);
                if (!this.plugin.lockStates.containsKey(le.getUniqueId())) {
                    Vector vel = le.getVelocity();
                    le.setVelocity(new Vector(vel.getX(), 0.8D, vel.getZ()));
                }
            }
        }

        for (int i = 0; i < 25; i++) {
            double angle = Math.random() * Math.PI * 2.0D;
            double dist = 2.0D + Math.random() * 13.0D;
            this.plugin.spawnSpike(
                    center.clone().add(Math.cos(angle) * dist, 0.0D, Math.sin(angle) * dist),
                    1.2F + (float) Math.random() * 0.8F);
        }

        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 1.2F, 0.6F);
        world.playSound(center, Sound.BLOCK_POWDER_SNOW_BREAK, 1.2F, 0.6F);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (e.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (p.getInventory().getHeldItemSlot() != 1) return;
        if (!this.plugin.isLegendary(p.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_ICE_SPEAR)) return;
        if (!this.plugin.isTargetable(e.getEntity(), p)) return;

        LivingEntity victim = (LivingEntity) e.getEntity();
        if (this.plugin.lockStates.containsKey(victim.getUniqueId())) return;
        if (this.plugin.onCooldown(p, "ice_spear_freeze", FREEZE_HIT_CD)) return;
        if (!this.plugin.lockEntity(victim, false)) return;

        this.plugin.setCooldown(p, "ice_spear_freeze", FREEZE_HIT_CD, "Заморозка копья");
        final Location anchorLoc = victim.getLocation().clone();
        Location iceLoc = anchorLoc.clone();
        iceLoc.setYaw(0.0F);
        iceLoc.setPitch(0.0F);
        ItemDisplay iceDisplay = this.plugin.spawnModel(
                iceLoc.clone().add(0.0D, 1.0D, 0.0D),
                this.plugin.iceSpearIce(),
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(1.5F, 1.5F, 1.5F));

        victim.getWorld().playSound(iceLoc, Sound.BLOCK_GLASS_PLACE, 1.0F, 0.8F);
        victim.getWorld().spawnParticle(
                Particle.SNOWFLAKE, iceLoc.clone().add(0.0D, 1.0D, 0.0D), 25, 0.4D, 0.6D, 0.4D, 0.03D);

        final UUID victimId = victim.getUniqueId();
        final UUID displayId = iceDisplay.getUniqueId();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                this.ticks++;
                Entity targetEnt = IceSpearListener.this.plugin.getServer().getEntity(victimId);
                Entity dispEnt = IceSpearListener.this.plugin.getServer().getEntity(displayId);

                if (targetEnt instanceof LivingEntity le
                        && le.isValid()
                        && !le.isDead()
                        && IceSpearListener.this.plugin.lockStates.containsKey(victimId)
                        && this.ticks < 60) {
                    // Counteract post-event melee knockback so the frozen target stays anchored in the ice
                    le.setVelocity(new Vector(0, 0, 0));
                    if (!(le instanceof Player) && le.getWorld().equals(anchorLoc.getWorld())) {
                        Location cur = le.getLocation();
                        if (cur.distanceSquared(anchorLoc) > 0.01D) {
                            Location snap = anchorLoc.clone();
                            snap.setYaw(cur.getYaw());
                            snap.setPitch(cur.getPitch());
                            le.teleport(snap);
                        }
                    }
                    if (dispEnt instanceof ItemDisplay disp && disp.isValid()) {
                        Location modelLoc = le.getLocation().add(0.0D, 1.0D, 0.0D);
                        modelLoc.setYaw(0.0F);
                        modelLoc.setPitch(0.0F);
                        disp.teleport(modelLoc);
                    }
                    return;
                }

                IceSpearListener.this.plugin.unlockEntity(victimId);
                if (dispEnt instanceof ItemDisplay disp && disp.isValid()) {
                    disp.getWorld().playSound(disp.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 1.2F);
                    disp.getWorld().spawnParticle(
                            Particle.SNOWFLAKE, disp.getLocation(), 20, 0.4D, 0.5D, 0.4D, 0.05D);
                    IceSpearListener.this.plugin.removeTracked(disp);
                } else {
                    IceSpearListener.this.plugin.tempEntities.remove(displayId);
                }
                this.cancel();
            }
        }.runTaskTimer(this.plugin, 1L, 1L);
    }
}

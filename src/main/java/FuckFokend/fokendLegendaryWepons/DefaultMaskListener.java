package FuckFokend.fokendLegendaryWepons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

final class DefaultMaskListener implements Listener {
    final FokendLegendaryWepons plugin;
    final Map<UUID, List<UUID>> gunDisplays = new ConcurrentHashMap<>();
    final Map<UUID, Long> gunUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> swapCooldowns = new ConcurrentHashMap<>();

    DefaultMaskListener(final FokendLegendaryWepons plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    DefaultMaskListener.this.applyMaskAppearance(p.getInventory().getHelmet(), true);
                    for (int slot = 0; slot < 36; slot++) {
                        DefaultMaskListener.this.applyMaskAppearance(p.getInventory().getItem(slot), false);
                    }
                    DefaultMaskListener.this.applyMaskAppearance(p.getInventory().getItemInOffHand(), false);
                    DefaultMaskListener.this.applyMaskAppearance(p.getItemOnCursor(), false);
                }
            }
        }.runTaskTimer(plugin, 1L, 10L);
    }

    @SuppressWarnings("deprecation")
    void applyMaskAppearance(ItemStack item, boolean worn) {
        if (!this.plugin.isLegendary(item, FokendLegendaryWepons.ID_DEFAULT_MASK)) return;
        int cmd = worn ? FokendLegendaryWepons.CMD_HAPPY_MASK : FokendLegendaryWepons.CMD_DEFAULT_MASK;
        String title = worn
                ? "<gradient:#FFE000:#799F0C><bold>☺ Эйфория • Счастливая Маска</bold></gradient>"
                : "<gradient:#56ab2f:#a8e063><bold>🎭 Лик Безумия • Маска Хаоса</bold></gradient>";
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            boolean sameCmd = meta.hasCustomModelData() && meta.getCustomModelData() == cmd;
            Component expectedName = this.plugin.mm.deserialize(title).decoration(TextDecoration.ITALIC, false);
            if (!sameCmd || !meta.hasDisplayName() || !expectedName.equals(meta.displayName())) {
                this.plugin.applyCustomModelData(meta, cmd);
                meta.displayName(expectedName);
                item.setItemMeta(meta);
            }
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (this.plugin.isLocked(p)) return;
        if (p.isSneaking() && this.plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_DEFAULT_MASK)) {
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            Long last = this.swapCooldowns.get(p.getUniqueId());
            if (last != null && now - last < 350L) {
                return;
            }
            this.swapCooldowns.put(p.getUniqueId(), now);
            if (!this.plugin.checkAndMarkTick(p, "mask_hammer")) {
                return;
            }
            this.triggerHammer(p);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!this.plugin.isAbilityClick(e)) return;

        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        boolean holdingMask = this.plugin.isLegendary(held, FokendLegendaryWepons.ID_DEFAULT_MASK);
        boolean emptyHand = held == null || held.getType().isAir();

        // Only trigger Mask Hammer on Shift+RMB if main hand is empty or holding default_mask,
        // so other weapons' Shift+RMB abilities and block placement work normally!
        if (p.isSneaking()
                && this.plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_DEFAULT_MASK)
                && (emptyHand || holdingMask)) {
            e.setCancelled(true);
            if (this.plugin.checkAndMarkTick(p, "mask_hammer")) {
                this.triggerHammer(p);
            }
            return;
        }

        if (holdingMask && !p.isSneaking()) {
            e.setCancelled(true);
            if (this.plugin.checkAndMarkTick(p, "mask_tompson")) {
                this.triggerThompson(p);
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) {
            this.plugin.maskSneakStart.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
        } else {
            this.plugin.maskSneakStart.remove(e.getPlayer().getUniqueId());
        }
    }

    private void triggerHammer(Player p) {
        if (this.plugin.onCooldown(p, "mask_hammer", FokendLegendaryWepons.MASK_HAMMER_CD)) {
            long sec = this.plugin.cooldownRemaining(p, "mask_hammer", FokendLegendaryWepons.MASK_HAMMER_CD) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(p, Component.text("Молот на перезарядке: " + sec + "с", NamedTextColor.RED));
            return;
        }

        this.plugin.setCooldown(p, "mask_hammer", FokendLegendaryWepons.MASK_HAMMER_CD, "Молот маски");
        float yaw = p.getLocation().getYaw();
        final Location impactLoc = p.getLocation().add(this.plugin.flatDir(yaw).multiply(3));
        final World world = impactLoc.getWorld();
        if (world == null) return;

        final Location startAbove = impactLoc.clone().add(0.0D, 4.0D, 0.0D);
        startAbove.setYaw(yaw);
        startAbove.setPitch(0.0F);

        ItemDisplay hammerDisplay = this.plugin.track(world.spawn(startAbove, ItemDisplay.class, d -> {
            d.setItemStack(this.plugin.maskMace());
            d.setItemDisplayTransform(ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(
                    new Vector3f(), new AxisAngle4f((float) Math.PI, 0.0F, 0.0F, 1.0F), new Vector3f(5.0F), new AxisAngle4f()));
        }));

        final UUID hammerId = hammerDisplay.getUniqueId();
        final UUID ownerId = p.getUniqueId();
        world.playSound(startAbove, Sound.ITEM_MACE_SMASH_AIR, 1.5F, 0.7F);

        new BukkitRunnable() {
            int tick = 0;

            @Override
            public void run() {
                this.tick++;
                double prog = Math.min(1.0D, this.tick / 8.0D);
                Entity dispEnt = DefaultMaskListener.this.plugin.getServer().getEntity(hammerId);
                if (dispEnt instanceof ItemDisplay disp && disp.isValid()) {
                    Location pos = startAbove.clone().subtract(0.0D, 3.0D * prog * prog, 0.0D);
                    disp.teleport(pos);
                    world.spawnParticle(Particle.CLOUD, pos, 3, 0.2D, 0.1D, 0.2D, 0.01D);
                }

                if (this.tick >= 8) {
                    if (dispEnt != null) {
                        DefaultMaskListener.this.plugin.removeTracked(dispEnt);
                    } else {
                        DefaultMaskListener.this.plugin.tempEntities.remove(hammerId);
                    }
                    this.cancel();

                    world.playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.8F, 0.6F);
                    world.playSound(impactLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5F, 0.8F);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, impactLoc, 2);
                    world.spawnParticle(Particle.CLOUD, impactLoc, 40, 2.0D, 0.3D, 2.0D, 0.05D);

                    for (int i = 0; i < 36; i++) {
                        double angle = i * Math.PI / 18.0D;
                        for (double r = 1.0D; r <= 5.0D; r += 2.0D) {
                            world.spawnParticle(
                                    Particle.CLOUD,
                                    impactLoc.clone().add(Math.cos(angle) * r, 0.2D, Math.sin(angle) * r),
                                    1, 0.0D, 0.0D, 0.0D, 0.03D);
                        }
                    }

                    Player owner = DefaultMaskListener.this.plugin.getServer().getPlayer(ownerId);
                    if (owner == null || !owner.isOnline()) {
                        return;
                    }
                    int hits = 0;
                    BoundingBox box = BoundingBox.of(impactLoc, 5.0D, 5.0D, 5.0D);
                    for (Entity ent : world.getNearbyEntities(
                            box, e -> (FokendLegendaryWepons.HAMMER_HITS_MOBS || e instanceof Player)
                                    && DefaultMaskListener.this.plugin.isTargetable(e, owner))) {
                        LivingEntity le = (LivingEntity) ent;
                        Location mid = le.getLocation().add(0.0D, le.getHeight() / 2.0D, 0.0D);
                        if (mid.distanceSquared(impactLoc) <= 25.0D) {
                            DefaultMaskListener.this.plugin.markCustomDamage(le, owner, "mask_hammer", 20.0D, true);
                            if (!DefaultMaskListener.this.plugin.lockStates.containsKey(le.getUniqueId())) {
                                Vector away = le.getLocation().toVector().subtract(impactLoc.toVector()).setY(0);
                                if (away.lengthSquared() > 1.0E-4D) {
                                    away.normalize().multiply(0.6D);
                                }
                                le.setVelocity(away.setY(0.5D));
                            }
                            hits++;
                        }
                    }
                    DefaultMaskListener.this.plugin.sendPriorityActionBar(
                            owner, Component.text("Удар молота! Поражено целей: " + hits, NamedTextColor.GOLD));
                }
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    private void triggerThompson(Player p) {
        if (this.plugin.onCooldown(p, "mask_tompson_shot", 250L)) return;
        this.plugin.cooldowns.put(p.getUniqueId() + ":mask_tompson_shot", System.currentTimeMillis());

        Vector dir = p.getEyeLocation().getDirection();
        Vector side = this.plugin.sideVector(dir).multiply(0.3D);
        for (Location origin : List.of(p.getEyeLocation().subtract(side), p.getEyeLocation().add(side))) {
            this.plugin.fireThompsonShot(p, origin, dir, FokendLegendaryWepons.META_MASK_EXPLOSIVE);
        }
        this.showGuns(p);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35F, 2.0F);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.35F, 1.8F);
    }

    private void showGuns(Player p) {
        final UUID ownerId = p.getUniqueId();
        this.gunUntil.put(ownerId, System.currentTimeMillis() + 1200L);
        if (this.gunDisplays.containsKey(ownerId)) return;

        final List<UUID> gunIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            ItemDisplay disp = this.plugin.track(p.getWorld().spawn(p.getEyeLocation(), ItemDisplay.class, d -> {
                d.setItemStack(this.plugin.maskThompson());
                d.setItemDisplayTransform(ItemDisplayTransform.FIXED);
                d.setPersistent(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(1);
                d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.0F), new AxisAngle4f()));
            }));
            gunIds.add(disp.getUniqueId());
        }

        this.gunDisplays.put(ownerId, gunIds);
        new BukkitRunnable() {
            @Override
            public void run() {
                Long until = DefaultMaskListener.this.gunUntil.get(ownerId);
                Player owner = DefaultMaskListener.this.plugin.getServer().getPlayer(ownerId);
                if (owner != null
                        && owner.isOnline()
                        && !owner.isDead()
                        && until != null
                        && System.currentTimeMillis() <= until
                        && DefaultMaskListener.this.plugin.isLegendary(
                                owner.getInventory().getItemInMainHand(), FokendLegendaryWepons.ID_DEFAULT_MASK)) {
                    Location eye = owner.getEyeLocation();
                    Vector dir = eye.getDirection();
                    Vector side = DefaultMaskListener.this.plugin.sideVector(dir);
                    for (int i = 0; i < gunIds.size(); i++) {
                        int sgn = i == 0 ? -1 : 1;
                        Location pos = eye.clone()
                                .add(dir.clone().multiply(0.7D))
                                .add(side.clone().multiply(0.5D * sgn))
                                .subtract(0.0D, 0.3D, 0.0D);
                        pos.setYaw(eye.getYaw());
                        pos.setPitch(eye.getPitch());
                        Entity ent = DefaultMaskListener.this.plugin.getServer().getEntity(gunIds.get(i));
                        if (ent instanceof ItemDisplay disp && disp.isValid() && disp.getWorld().equals(pos.getWorld())) {
                            disp.teleport(pos);
                        }
                    }
                } else {
                    for (UUID gunId : gunIds) {
                        Entity ent = DefaultMaskListener.this.plugin.getServer().getEntity(gunId);
                        if (ent != null) {
                            DefaultMaskListener.this.plugin.removeTracked(ent);
                        } else {
                            DefaultMaskListener.this.plugin.tempEntities.remove(gunId);
                        }
                    }
                    DefaultMaskListener.this.gunDisplays.remove(ownerId);
                    DefaultMaskListener.this.gunUntil.remove(ownerId);
                    this.cancel();
                }
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }
}

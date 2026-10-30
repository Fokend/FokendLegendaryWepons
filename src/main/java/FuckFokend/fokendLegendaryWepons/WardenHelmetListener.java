package FuckFokend.fokendLegendaryWepons;

import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

final class WardenHelmetListener implements Listener {
    private static final long CD = 10000L;
    final FokendLegendaryWepons plugin;
    private final Map<UUID, Long> swapCooldowns = new ConcurrentHashMap<>();

    WardenHelmetListener(final FokendLegendaryWepons plugin) {
        this.plugin = plugin;
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                this.ticks += 2;
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    boolean wearing = plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET);
                    if (this.ticks % 10 == 0) {
                        WardenHelmetListener.syncWardenHealthAttribute(plugin, p, wearing);
                    }

                    if (wearing) {
                        if (p.hasPotionEffect(PotionEffectType.GLOWING)) {
                            p.removePotionEffect(PotionEffectType.GLOWING);
                        }
                        PotionEffect blindness = p.getPotionEffect(PotionEffectType.BLINDNESS);
                        if (blindness == null
                                || (!blindness.isInfinite() && blindness.getDuration() >= 0 && blindness.getDuration() <= 220)) {
                            p.addPotionEffect(new PotionEffect(
                                    PotionEffectType.BLINDNESS,
                                    PotionEffect.INFINITE_DURATION,
                                    0,
                                    false,
                                    false,
                                    false));
                            plugin.wardenBlindness.add(id);
                        }

                        if (this.ticks % 10 == 0) {
                            Location loc = p.getLocation();
                            for (Entity ent : p.getWorld().getNearbyEntities(loc, 15.0D, 15.0D, 15.0D)) {
                                if (ent instanceof LivingEntity le
                                        && !ent.getUniqueId().equals(id)
                                        && !(ent instanceof ArmorStand)
                                        && !ent.hasMetadata(FokendLegendaryWepons.META_RA_DOLL)
                                        && !ent.hasMetadata(FokendLegendaryWepons.META_TWIN)
                                        && !(ent instanceof Player other
                                                && plugin.isLegendary(other.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET))) {
                                    le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false, true));
                                }
                            }
                        }

                        for (Entity ent : p.getWorld().getNearbyEntities(p.getLocation(), 32.0D, 32.0D, 32.0D)) {
                            if (ent instanceof Warden warden) {
                                WardenHelmetListener.pacifyWarden(warden, p);
                            }
                        }
                    } else if (plugin.wardenBlindness.remove(id)) {
                        p.removePotionEffect(PotionEffectType.BLINDNESS);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
    }

    static void pacifyWarden(Warden warden, Player wearer) {
        if (warden == null || wearer == null) return;

        boolean targetedWearer = false;
        try {
            if (wearer.equals(warden.getTarget()) || wearer.equals(warden.getEntityAngryAt())) {
                targetedWearer = true;
            }
        } catch (Throwable ignored) {
        }

        try {
            UUID angryAtMem = warden.getMemory(MemoryKey.ANGRY_AT);
            if (wearer.getUniqueId().equals(angryAtMem)) {
                targetedWearer = true;
                warden.setMemory(MemoryKey.ANGRY_AT, null);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (warden.getAnger(wearer) > 0) {
                warden.clearAnger(wearer);
                warden.setAnger(wearer, 0);
            }
        } catch (Throwable ignored) {
            try {
                warden.clearAnger(wearer);
            } catch (Throwable ignored2) {
            }
        }

        if (!targetedWearer) {
            return;
        }

        try {
            warden.setTarget(null);
        } catch (Throwable ignored) {
        }

        try {
            Method getHandle = warden.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(warden);
            if (handle != null) {
                Method getBrain = handle.getClass().getMethod("getBrain");
                Object brain = getBrain.invoke(handle);
                if (brain != null) {
                    Class<?> memTypeClass = Class.forName("net.minecraft.world.entity.ai.memory.MemoryModuleType");
                    Method eraseMemory = brain.getClass().getMethod("eraseMemory", memTypeClass);
                    for (String fieldName : new String[]{"ATTACK_TARGET", "ROAR_TARGET", "ANGRY_AT", "DISTURBANCE_LOCATION"}) {
                        try {
                            Field field = memTypeClass.getField(fieldName);
                            Object memModule = field.get(null);
                            eraseMemory.invoke(brain, memModule);
                        } catch (Throwable ignored) {
                        }
                    }
                }
                try {
                    Class<?> wardenAiClass = Class.forName("net.minecraft.world.entity.monster.warden.WardenAi");
                    Method updateActivity = wardenAiClass.getMethod("updateActivity", handle.getClass());
                    updateActivity.invoke(null, handle);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    static void syncWardenHealthAttribute(FokendLegendaryWepons plugin, Player player, boolean wearing) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            NamespacedKey key = new NamespacedKey(plugin, "warden_hp");
            boolean found = false;
            for (AttributeModifier mod : new ArrayList<>(maxHealth.getModifiers())) {
                if (key.equals(mod.getKey())) {
                    if (wearing && !found && Double.compare(mod.getAmount(), 6.0D) == 0) {
                        found = true;
                    } else {
                        maxHealth.removeModifier(mod);
                    }
                }
            }
            if (wearing) {
                if (!found) {
                    maxHealth.addModifier(new AttributeModifier(key, 6.0D, Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                }
            } else if (player.getHealth() > maxHealth.getValue()) {
                player.setHealth(maxHealth.getValue());
            }
        }
    }

    private void scheduleAttributeSync(Player player) {
        UUID id = player.getUniqueId();
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            Player online = this.plugin.getServer().getPlayer(id);
            if (online != null && online.isOnline()) {
                boolean wearing = this.plugin.isLegendary(
                        online.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET);
                syncWardenHealthAttribute(this.plugin, online, wearing);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            this.scheduleAttributeSync(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent e) {
        this.scheduleAttributeSync(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent e) {
        syncWardenHealthAttribute(this.plugin, e.getEntity(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        this.scheduleAttributeSync(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent e) {
        this.scheduleAttributeSync(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWardenAnger(WardenAngerChangeEvent e) {
        if (e.getTarget() instanceof Player p
                && this.plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET)) {
            e.setNewAnger(0);
            e.setCancelled(true);
            pacifyWarden(e.getEntity(), p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getEntity() instanceof Warden warden
                && e.getTarget() instanceof Player p
                && this.plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET)) {
            e.setCancelled(true);
            pacifyWarden(warden, p);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWardenDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Warden warden
                && e.getEntity() instanceof Player p
                && this.plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET)) {
            e.setCancelled(true);
            pacifyWarden(warden, p);
        }
    }

    private boolean hasRightClickAbility(String id, boolean sneaking) {
        if (id == null) return false;
        return switch (id) {
            case FokendLegendaryWepons.ID_RA_BLADE,
                    FokendLegendaryWepons.ID_CHAOS_BOOMERANG,
                    FokendLegendaryWepons.ID_TWINS_SWORD,
                    FokendLegendaryWepons.ID_MAGNET,
                    FokendLegendaryWepons.ID_TIME_CLOCK,
                    FokendLegendaryWepons.ID_DEFAULT_MASK,
                    FokendLegendaryWepons.ID_MASK_TOMPSON,
                    FokendLegendaryWepons.ID_SOUL_BOW -> true;
            case FokendLegendaryWepons.ID_ICE_SPEAR -> sneaking;
            default -> false;
        };
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (this.plugin.isLocked(p)) return;
        if (!this.plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET)) return;

        ItemStack mainHand = p.getInventory().getItemInMainHand();
        ItemStack offHand = p.getInventory().getItemInOffHand();
        boolean emptyMain = mainHand == null || mainHand.getType().isAir();
        String mainId = this.plugin.getId(mainHand);
        String offId = this.plugin.getId(offHand);

        Action action = e.getAction();
        if (action == Action.LEFT_CLICK_AIR || (action == Action.LEFT_CLICK_BLOCK && p.isSneaking())) {
            if (!emptyMain && !p.isSneaking()) {
                return;
            }
            if (FokendLegendaryWepons.ID_SOUL_BOW.equals(mainId)) {
                return;
            }
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (action == Action.RIGHT_CLICK_BLOCK
                    && e.getClickedBlock() != null
                    && e.getClickedBlock().getType().isInteractable()
                    && !p.isSneaking()) {
                return;
            }
            if (this.hasRightClickAbility(mainId, p.isSneaking())) {
                return;
            }
            if (emptyMain && this.hasRightClickAbility(offId, p.isSneaking())) {
                return;
            }
            if (action == Action.RIGHT_CLICK_BLOCK
                    && !p.isSneaking()
                    && !emptyMain
                    && mainHand.getType().isBlock()) {
                return;
            }
        } else {
            return;
        }

        e.setCancelled(true);
        if (this.plugin.checkAndMarkTick(p, "warden_boom")) {
            this.activateSonicBoom(p);
        }
    }

    @EventHandler
    public void onEmptyHandFallback(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        boolean bothHandsEmpty = p.getInventory().getItemInMainHand().getType().isAir()
                && p.getInventory().getItemInOffHand().getType().isAir();
        if (!p.isSneaking() && !bothHandsEmpty) return;
        if (this.plugin.isLocked(p)) return;
        if (!this.plugin.isLegendary(p.getInventory().getHelmet(), FokendLegendaryWepons.ID_WARDEN_HELMET)) return;

        e.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = this.swapCooldowns.get(p.getUniqueId());
        if (last == null || now - last >= 500L) {
            this.swapCooldowns.put(p.getUniqueId(), now);
            if (this.plugin.checkAndMarkTick(p, "warden_boom")) {
                this.activateSonicBoom(p);
            }
        }
    }

    private void activateSonicBoom(Player p) {
        if (this.plugin.isLocked(p)) return;
        if (this.plugin.onCooldown(p, "warden_boom", CD)) {
            long sec = this.plugin.cooldownRemaining(p, "warden_boom", CD) / 1000L + 1L;
            this.plugin.sendPriorityActionBar(p, Component.text("Sonic Boom на перезарядке: " + sec + "с", NamedTextColor.RED));
            return;
        }

        this.plugin.setCooldown(p, "warden_boom", CD, "Sonic Boom");
        Location eye = p.getEyeLocation();
        World world = p.getWorld();
        Vector dir = eye.getDirection().normalize();
        double range = 32.0D;
        RayTraceResult wall = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (wall != null) {
            range = Math.max(0.0D, wall.getHitPosition().distance(eye.toVector()) - 0.5D);
        }

        for (double d = 1.0D; d <= range; d += 1.0D) {
            world.spawnParticle(Particle.SONIC_BOOM, eye.clone().add(dir.clone().multiply(d)), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        Set<UUID> hitTargets = new HashSet<>();
        for (double d = 1.0D; d <= range; d += 1.0D) {
            Location point = eye.clone().add(dir.clone().multiply(d));
            for (Entity ent : world.getNearbyEntities(point, 1.5D, 1.5D, 1.5D)) {
                if (!this.plugin.isTargetable(ent, p) || hitTargets.contains(ent.getUniqueId())) continue;
                LivingEntity le = (LivingEntity) ent;
                Location targetPoint = le.getLocation().add(0.0D, le.getHeight() * 0.5D, 0.0D);
                Vector toTarget = targetPoint.toVector().subtract(eye.toVector());
                double distSq = toTarget.lengthSquared();
                if (distSq < 1.0E-6D || distSq > range * range) continue;
                double targetDist = Math.sqrt(distSq);
                RayTraceResult obstruction = world.rayTraceBlocks(
                        eye, toTarget.clone().multiply(1.0D / targetDist), targetDist, FluidCollisionMode.NEVER, true);
                if (obstruction != null
                        && obstruction.getHitPosition().distanceSquared(eye.toVector()) < distSq - 0.09D) {
                    continue;
                }
                hitTargets.add(le.getUniqueId());
                this.plugin.trueDamage(le, 10.0D, p);
                if (!this.plugin.lockStates.containsKey(le.getUniqueId())) {
                    le.setVelocity(dir.clone().multiply(2.2D).setY(0.5D));
                }
            }
        }
        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5F, 1.0F);
    }
}

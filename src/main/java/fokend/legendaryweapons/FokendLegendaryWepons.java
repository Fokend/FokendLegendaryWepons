package fokend.legendaryweapons;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class FokendLegendaryWepons extends JavaPlugin {
    static final String ID_CB_ORANGE = "cb_orange";
    static final String ID_CB_PURPLE = "cb_purple";
    static final String ID_CB_GREEN = "cb_green";
    static final String ID_RA_BLADE = "ra_god_blade";
    static final String ID_CHAOS_BOOMERANG = "chaos_boomerang";
    static final String ID_WARDEN_HELMET = "warden_helmet";
    static final String ID_TWINS_SWORD = "twins_sword";
    static final String ID_MAGNET = "magnet";
    static final String ID_TIME_CLOCK = "time_clock";
    static final String ID_ICE_SPEAR = "ice_spear";
    static final String ID_ICE_SPEAR_ICE = "ice_spear_ice";
    static final String ID_ICE_SPEAR_SPIKE = "ice_spear_spike";
    static final String ID_DEFAULT_MASK = "default_mask";
    static final String ID_HAPPY_MASK = "happy_mask";
    static final String ID_MASK_BLOCK = "mask_block";
    static final String ID_MASK_MACE = "mask_mace";
    static final String ID_MASK_TOMPSON = "mask_tompson";
    static final String ID_SOUL_BOW = "soul_bow";

    static final int CMD_RA_BLADE = 10001;
    static final int CMD_CHAOS_BOOMERANG = 10002;
    static final int CMD_WARDEN_HELMET = 10003;
    static final int CMD_TWINS_SWORD = 10004;
    static final int CMD_MAGNET = 10005;
    static final int CMD_TIME_CLOCK = 20001;
    static final int CMD_ICE_SPEAR = 20002;
    static final int CMD_ICE_SPEAR_ICE = 20003;
    static final int CMD_ICE_SPEAR_SPIKE = 20004;
    static final int CMD_DEFAULT_MASK = 20005;
    static final int CMD_HAPPY_MASK = 20006;
    static final int CMD_MASK_BLOCK = 20007;
    static final int CMD_MASK_MACE = 20008;
    static final int CMD_MASK_TOMPSON = 20009;
    static final int CMD_SOUL_BOW = 20010;
    static final int CMD_CB_ORANGE = 30001;
    static final int CMD_CB_PURPLE = 30002;
    static final int CMD_CB_GREEN = 30003;

    static final String META_TOMPSON = "mask_explosive";
    static final String META_SOUL_ARROW = "soul_arrow";
    static final String META_RA_DOLL = "ra_doll_owner";
    static final String META_MASK_EXPLOSIVE = "default_mask_explosive";
    static final String META_TWIN = "twins_npc_owner";
    static final String META_DEATH_REASON = "legendary_death_reason";

    static final boolean HAMMER_HITS_MOBS = false;


    final Object fileLock = new Object();
    final MiniMessage mm = MiniMessage.miniMessage();
    final Map<UUID, UUID> raDolls = new ConcurrentHashMap<>();
    final Map<UUID, List<UUID>> activeTwins = new ConcurrentHashMap<>();
    final Balance balance = new Balance(this);
    final LegendaryItems items = new LegendaryItems(this);
    final RecipeManager recipes = new RecipeManager(this);
    final TimeClockSessions timeClock = new TimeClockSessions(this);
    final CooldownManager cooldowns = new CooldownManager(this, this.balance);
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();
    final Map<UUID, Long> maskSneakStart = new ConcurrentHashMap<>();
    final Map<UUID, Long> maskRunUntil = new ConcurrentHashMap<>();
    final Set<UUID> hovering = ConcurrentHashMap.newKeySet();
    final Set<UUID> fallingFree = ConcurrentHashMap.newKeySet();
    final Set<UUID> wardenBlindness = ConcurrentHashMap.newKeySet();
    final Map<UUID, Double> lastY = new ConcurrentHashMap<>();
    final Set<UUID> tempEntities = ConcurrentHashMap.newKeySet();
    final Map<UUID, ThrownBoomerang> boomerangs = new ConcurrentHashMap<>();
    final Set<String> activeThrowSessions = ConcurrentHashMap.newKeySet();
    final Map<UUID, LockState> lockStates = new ConcurrentHashMap<>();
    final Map<UUID, Integer> souls = new ConcurrentHashMap<>();
    final Map<UUID, UUID> soulless = new ConcurrentHashMap<>();
    final Map<UUID, UUID> revived = new ConcurrentHashMap<>();
    final Map<UUID, UUID> revivers = new ConcurrentHashMap<>();
    final Set<UUID> pendingNormal = ConcurrentHashMap.newKeySet();
    final Set<String> craftedUniques = ConcurrentHashMap.newKeySet();
    final Map<String, Integer> lastInteractTick = new ConcurrentHashMap<>();
    final Map<UUID, Integer> lastThompsonHitTick = new ConcurrentHashMap<>();

    File soulFile;
    /** Legend Chronicle + Legend Call; null when disabled or when its database could not be opened. */
    Chronicle chronicle;
    DeathNoteListener deathNote;
    /** The one key that marks an item as a legendary; its value is the legendary id. */
    NamespacedKey weaponKey;
    /** Older builds also wrote the id here. Read only to migrate old items, see {@link #migrateLegacyId}. */
    NamespacedKey legacyIdKey;
    NamespacedKey throwSessionKey;
    NamespacedKey frozenKey;
    NamespacedKey runKey;
    NamespacedKey wardenHpKey;


    /** Kill switch: name used in /legendary disable|enable -> legendary id checked by the ability listeners. */
    static final Map<String, String> ABILITY_SWITCHES = Map.ofEntries(
            Map.entry("ra_blade", ID_RA_BLADE), Map.entry("chaos_boomerang", ID_CHAOS_BOOMERANG),
            Map.entry("warden_helmet", ID_WARDEN_HELMET), Map.entry("twins_sword", ID_TWINS_SWORD),
            Map.entry("magnet", ID_MAGNET), Map.entry("time_clock", ID_TIME_CLOCK), Map.entry("ice_spear", ID_ICE_SPEAR),
            Map.entry("default_mask", ID_DEFAULT_MASK), Map.entry("mask_tompson", ID_MASK_TOMPSON),
            Map.entry("soul_bow", ID_SOUL_BOW), Map.entry("death_note", "death_note"),
            Map.entry("fate_scales", "fate_scales"), Map.entry("fate_lightning", "fate_lightning"));
    /** Legendary ids whose active abilities are switched off by an admin (persisted in disabled-abilities.yml). */
    final Set<String> disabledAbilities = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> disabledNotice = new ConcurrentHashMap<>();

    static final List<String> RECIPE_KEYS = List.of(
            "cb_orange", "cb_purple", "cb_green", "time_clock", "ice_spear",
            "default_mask", "warden_helmet", "ra_blade", "chaos_boomerang",
            "twins_sword", "magnet", "death_note", "fate_scales"
    );

    @Override
    public void onEnable() {
        this.balance.reload();
        this.weaponKey = new NamespacedKey(this, "weapon_id");
        this.legacyIdKey = new NamespacedKey(this, "legendary_id");
        this.throwSessionKey = new NamespacedKey(this, "throw_session_id");
        this.frozenKey = new NamespacedKey(this, "time_frozen");
        this.runKey = new NamespacedKey(this, "mask_run");
        this.wardenHpKey = new NamespacedKey(this, "warden_hp");
        // Loads souls.yml (also holds crafted_uniques) and sets soulFile; without it saveSouls() throws
        // and the one-craft-per-server limit is lost on restart.
        this.loadSouls();
        this.loadDisabledAbilities();

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new RaBladeListener(this), this);
        pm.registerEvents(new WardenHelmetListener(this), this);
        pm.registerEvents(new IceSpearListener(this), this);
        pm.registerEvents(new MaskThompsonListener(this), this);
        pm.registerEvents(new SoulBowListener(this), this);
        pm.registerEvents(new TimeClockListener(this), this);
        pm.registerEvents(new FreezeListener(this), this);
        pm.registerEvents(new ChaosBoomerangListener(this), this);
        pm.registerEvents(new TwinsSwordListener(this), this);
        pm.registerEvents(new MagnetListener(this), this);
        pm.registerEvents(new DefaultMaskListener(this), this);
        pm.registerEvents(new CleanupListener(this), this);
        pm.registerEvents(new CraftProtectionListener(this), this);
        pm.registerEvents(new CoreUpgradeListener(this), this);
        pm.registerEvents(new ItemProtectionListener(this), this);
        pm.registerEvents(new EntityRightClickBridge(this), this);
        this.deathNote = new DeathNoteListener(this);
        pm.registerEvents(this.deathNote, this);
        pm.registerEvents(new ScalesOfFateListener(this), this);
        pm.registerEvents(new LegacyItemMigrationListener(this), this);

        PluginCommand legendaryCmd = this.getCommand("legendary");
        final LegendaryCommand legendaryExec = new LegendaryCommand(this);
        if (legendaryCmd != null) {
            legendaryCmd.setExecutor(legendaryExec);
            legendaryCmd.setTabCompleter(legendaryExec);
        }
        this.warnAboutLegacyPlugin();

        final ReviveCommand reviveExec = new ReviveCommand(this);
        PluginCommand reviveCmd = this.getCommand("revive");
        if (reviveCmd != null) {
            reviveCmd.setExecutor(reviveExec);
            reviveCmd.setTabCompleter(reviveExec);
        } else {
            Command fallback = new Command("revive") {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return reviveExec.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return reviveExec.onTabComplete(sender, this, alias, args);
                }
            };
            fallback.setDescription("Возродить бездушного игрока (цена: 5 сердец)");
            fallback.setUsage("/revive <ник>");
            this.getServer().getCommandMap().register("fokendlegendary", fallback);
        }

        this.recipes.registerRecipes();
        this.recipes.logRecipes();
        this.chronicle = Chronicle.start(this);
        this.getLogger().info("FokendLegendaryWepons " + this.getPluginMeta().getVersion()
                + ": 10 легендарок (Клинок Ра, Бумеранг Хаоса, Шлем Вардена, Меч Близнецов, Магнитник,"
                + " Часы, Морозное копьё, Маска, Томпсон, Лук Душ) + Тетрадь Смерти, Весы Судьбы, 3 ядра.");

        for (World world : this.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                this.restoreStaleFrozen(entity);
            }
        }

        // Happy mask passive effects (Warden helmet is handled by WardenHelmetListener's task)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : FokendLegendaryWepons.this.getServer().getOnlinePlayers()) {
                    if (FokendLegendaryWepons.this.isLegendary(p.getInventory().getHelmet(), ID_HAPPY_MASK)) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0, false, false, false));
                    }
                }
            }
        }.runTaskTimer(this, 0L, 40L);

        // Default mask sneak-to-sprint charge & speed boost
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : FokendLegendaryWepons.this.getServer().getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (!FokendLegendaryWepons.this.isLegendary(p.getInventory().getHelmet(), ID_DEFAULT_MASK)
                            || FokendLegendaryWepons.this.isLocked(p)) {
                        FokendLegendaryWepons.this.maskRunUntil.remove(id);
                        FokendLegendaryWepons.this.setRunBoost(p, false);
                    } else {
                        Long until = FokendLegendaryWepons.this.maskRunUntil.get(id);
                        boolean active = until != null && now < until;
                        if (p.isSneaking()) {
                            Long start = FokendLegendaryWepons.this.maskSneakStart.get(id);
                            if (start == null) {
                                start = now;
                                FokendLegendaryWepons.this.maskSneakStart.put(id, now);
                            }
                            long elapsed = now - start;
                            if (elapsed >= FokendLegendaryWepons.this.balance.seconds("default_mask.run_delay_seconds")) {
                                if (!active) {
                                    FokendLegendaryWepons.this.cooldowns.sendPriorityActionBar(
                                            p, Component.text("Бег активирован: +20% скорости!", NamedTextColor.GREEN));
                                    p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0F, 1.4F);
                                }
                                FokendLegendaryWepons.this.maskRunUntil.put(id, now + FokendLegendaryWepons.this.balance.seconds("default_mask.run_linger_seconds"));
                            } else if (!active) {
                                FokendLegendaryWepons.this.cooldowns.sendPriorityActionBar(
                                        p,
                                        Component.text(
                                                String.format(Locale.US, "Заряд бега: %.1f / %d с", elapsed / 1000.0D, 5L),
                                                NamedTextColor.YELLOW));
                            }
                        }

                        Long updatedUntil = FokendLegendaryWepons.this.maskRunUntil.get(id);
                        boolean boostNow = updatedUntil != null && now < updatedUntil;
                        FokendLegendaryWepons.this.setRunBoost(p, boostNow);
                        if (boostNow) {
                            p.getWorld().spawnParticle(
                                    Particle.CLOUD, p.getLocation().add(0.0D, 0.1D, 0.0D), 2, 0.2D, 0.02D, 0.2D, 0.01D);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 5L);

        // Default mask mid-air hover
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : FokendLegendaryWepons.this.getServer().getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    double curY = p.getLocation().getY();
                    Double prevY = FokendLegendaryWepons.this.lastY.put(id, curY);
                    boolean descending = prevY != null && curY < prevY - 0.05D;
                    boolean wearingMask = FokendLegendaryWepons.this.isLegendary(
                            p.getInventory().getHelmet(), ID_DEFAULT_MASK);

                    if (wearingMask
                            && !p.isOnGround()
                            && !p.isFlying()
                            && !p.isGliding()
                            && !p.isInWater()
                            && !p.isInLava()
                            && !p.isClimbing()
                            && !p.isInsideVehicle()
                            && p.getGameMode() != GameMode.SPECTATOR
                            && !FokendLegendaryWepons.this.lockStates.containsKey(id)) {
                        if (FokendLegendaryWepons.this.hovering.contains(id)) {
                            if (p.getLocation().getPitch() > 50.0F) {
                                FokendLegendaryWepons.this.releaseHover(p);
                                FokendLegendaryWepons.this.fallingFree.add(id);
                                p.setFallDistance(0.0F);
                                FokendLegendaryWepons.this.cooldowns.sendPriorityActionBar(
                                        p, Component.text("Падение", NamedTextColor.GRAY));
                            } else {
                                p.setFallDistance(0.0F);
                                Vector vel = p.getVelocity();
                                if (Math.abs(vel.getY()) > 1.0E-4D) {
                                    p.setVelocity(new Vector(vel.getX() * 0.8D, 0.0D, vel.getZ() * 0.8D));
                                }
                                p.getWorld().spawnParticle(
                                        Particle.CLOUD,
                                        p.getLocation().add(0.0D, 0.1D, 0.0D),
                                        2, 0.25D, 0.02D, 0.25D, 0.01D);
                                if (p.getTicksLived() % 10 == 0) {
                                    FokendLegendaryWepons.this.cooldowns.sendPriorityActionBar(
                                            p,
                                            Component.text("Парение: посмотри вниз, чтобы упасть", NamedTextColor.AQUA));
                                }
                            }
                        } else if (!FokendLegendaryWepons.this.fallingFree.contains(id) && descending) {
                            RayTraceResult ground = p.getWorld().rayTraceBlocks(
                                    p.getLocation(), new Vector(0, -1, 0), 21.0D, FluidCollisionMode.NEVER, true);
                            if (ground == null) {
                                FokendLegendaryWepons.this.hovering.add(id);
                                p.setGravity(false);
                                p.setFallDistance(0.0F);
                                p.setVelocity(new Vector(0, 0, 0));
                                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, 1.0F, 1.2F);
                                p.getWorld().spawnParticle(
                                        Particle.CLOUD, p.getLocation(), 25, 0.4D, 0.1D, 0.4D, 0.05D);
                            }
                        }
                    } else {
                        FokendLegendaryWepons.this.releaseHover(p);
                        FokendLegendaryWepons.this.fallingFree.remove(id);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        this.cooldowns.startTasks();
    }

    /** The old LegendaryWeapons plugin registers the same /legendary command; tell the admin to remove it. */
    private void warnAboutLegacyPlugin() {
        for (org.bukkit.plugin.Plugin other : this.getServer().getPluginManager().getPlugins()) {
            if (other != this && ("LegendaryWeapons".equalsIgnoreCase(other.getName())
                    || other.getClass().getName().startsWith("ru.legendary.weapons."))) {
                this.getLogger().warning("Old plugin " + other.getName() + " is installed and conflicts with this one"
                        + " (both register /legendary). Remove it from the plugins folder.");
            }
        }
    }

    public void restoreAllEntities() {
        for (TimeClockSessions.TimeSession session : new ArrayList<>(this.timeClock.timeSessions.values())) {
            this.timeClock.endTimeSession(session, null);
        }
        for (UUID id : new ArrayList<>(this.lockStates.keySet())) {
            this.unlockEntity(id);
        }
        for (World world : this.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                this.restoreStaleFrozen(entity);
            }
        }
    }

    @Override
    public void onDisable() {
        this.restoreAllEntities();
        this.getServer().getScheduler().cancelTasks(this);
        this.cooldowns.shutdown();

        for (UUID dollId : this.raDolls.values()) {
            Entity entity = this.getServer().getEntity(dollId);
            if (entity != null) {
                entity.remove();
            }
        }
        this.raDolls.clear();

        for (List<UUID> twinIds : this.activeTwins.values()) {
            for (UUID twinId : twinIds) {
                Entity entity = this.getServer().getEntity(twinId);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            }
        }
        this.activeTwins.clear();

        for (Map.Entry<UUID, ThrownBoomerang> entry : new ArrayList<>(this.boomerangs.entrySet())) {
            Entity display = this.getServer().getEntity(entry.getKey());
            ThrownBoomerang tb = entry.getValue();
            this.activeThrowSessions.remove(tb.throwSessionId());
            ItemStack cleanItem = this.cleanBoomerangItem(tb.item());
            Player owner = this.getServer().getPlayer(tb.owner());
            if (owner != null && owner.isOnline() && !owner.isDead()) {
                this.giveOrDrop(owner, cleanItem);
            } else if (display != null) {
                Item dropped = display.getWorld().dropItemNaturally(display.getLocation(), cleanItem);
                this.protectDroppedItem(dropped);
            }
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        this.boomerangs.clear();
        this.activeThrowSessions.clear();

        for (UUID entityId : new ArrayList<>(this.tempEntities)) {
            Entity entity = this.getServer().getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        this.tempEntities.clear();

        for (Player p : this.getServer().getOnlinePlayers()) {
            this.releaseHover(p);
            this.setRunBoost(p, false);
            WardenHelmetListener.syncWardenHealthAttribute(this, p, false);
            if (this.wardenBlindness.remove(p.getUniqueId())) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }

        this.wardenBlindness.clear();
        this.hovering.clear();
        this.maskSneakStart.clear();
        this.maskRunUntil.clear();
        this.saveSouls();
        // Last: everything above may still move legendaries (boomerangs returned, items dropped).
        if (this.chronicle != null) {
            this.chronicle.close();
            this.chronicle = null;
        }
    }

    ItemStack cleanBoomerangItem(ItemStack item) {
        if (item == null) return null;
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null && this.throwSessionKey != null) {
            meta.getPersistentDataContainer().remove(this.throwSessionKey);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    void returnAllBoomerangsFor(Player owner) {
        if (owner == null) return;
        UUID ownerId = owner.getUniqueId();
        for (Map.Entry<UUID, ThrownBoomerang> entry : new ArrayList<>(this.boomerangs.entrySet())) {
            ThrownBoomerang tb = entry.getValue();
            if (ownerId.equals(tb.owner())) {
                if (this.boomerangs.remove(entry.getKey(), tb)) {
                    this.activeThrowSessions.remove(tb.throwSessionId());
                    Entity display = this.getServer().getEntity(entry.getKey());
                    if (display != null) {
                        this.removeTracked(display);
                    } else {
                        this.tempEntities.remove(entry.getKey());
                    }
                    ItemStack clean = this.cleanBoomerangItem(tb.item());
                    if (owner.getInventory().getItemInMainHand().getType().isAir()) {
                        owner.getInventory().setItemInMainHand(clean);
                    } else {
                        this.giveOrDrop(owner, clean);
                    }
                }
            }
        }
    }

    ItemStack visualCopy(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return item;
        }
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.remove(this.weaponKey);
            pdc.remove(this.legacyIdKey);
            // Display copies must not carry the chronicle instance either.
            pdc.remove(new NamespacedKey(this, "legend_uid"));
            pdc.remove(new NamespacedKey(this, "legend_gen"));
            if (this.throwSessionKey != null) pdc.remove(this.throwSessionKey);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    /**
     * Logs a failure with its stack trace the first time it happens at {@code where}.
     * Used for best-effort calls inside per-tick tasks, where logging every failure would flood the console.
     */
    void warnOnce(String where, Throwable t) {
        if (this.reportedFailures.add(where)) {
            this.getLogger().log(java.util.logging.Level.WARNING,
                    where + " failed; later failures here are not logged", t);
        }
    }

    boolean isCraftingCore(String id) {
        return ID_CB_ORANGE.equals(id) || ID_CB_PURPLE.equals(id) || ID_CB_GREEN.equals(id);
    }

    void protectDroppedItem(Item item) {
        if (item != null && this.getId(item.getItemStack()) != null) {
            item.setInvulnerable(true);
            item.setUnlimitedLifetime(true);
            item.setGlowing(true);
            item.setWillAge(false);
            ItemMeta meta = item.getItemStack().getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                item.customName(meta.displayName());
                item.setCustomNameVisible(true);
            }
        }
    }

    void stampId(ItemStack item, String id) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(this.weaponKey, PersistentDataType.STRING, id);
            pdc.remove(this.legacyIdKey);
            item.setItemMeta(meta);
        }
    }

    String getId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String id = pdc.get(this.weaponKey, PersistentDataType.STRING);
        // Items that have not been through migrateLegacyId yet (e.g. sitting in an unopened chest).
        return id != null ? id : this.legacyId(pdc);
    }

    /**
     * Rewrites an item that still carries an id under one of the old keys so that it carries it under
     * {@link #weaponKey} only. Returns true when the item was changed.
     */
    boolean migrateLegacyId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey oldestKey = new NamespacedKey("legendaryweapons", "weapon");
        if (!pdc.has(this.legacyIdKey) && !pdc.has(oldestKey)) {
            return false;
        }
        String id = pdc.get(this.weaponKey, PersistentDataType.STRING);
        if (id == null) {
            id = this.legacyId(pdc);
        }
        pdc.remove(this.legacyIdKey);
        pdc.remove(oldestKey);
        if (id != null) {
            pdc.set(this.weaponKey, PersistentDataType.STRING, id);
        }
        item.setItemMeta(meta);
        return true;
    }

    private String legacyId(PersistentDataContainer pdc) {
        String id = pdc.get(this.legacyIdKey, PersistentDataType.STRING);
        if (id != null) {
            return id;
        }
        // The original LegendaryWeapons plugin, before this one replaced it.
        String oldest = pdc.get(new NamespacedKey("legendaryweapons", "weapon"), PersistentDataType.STRING);
        if (oldest == null) {
            return null;
        }
        return switch (oldest) {
            case "warden" -> ID_WARDEN_HELMET;
            case "ra" -> ID_RA_BLADE;
            case "chaos" -> ID_CHAOS_BOOMERANG;
            case "twins" -> ID_TWINS_SWORD;
            case "magnet" -> ID_MAGNET;
            default -> oldest;
        };
    }

    /**
     * Kill switch check for ability triggers. Item protections and passive bookkeeping do not call this,
     * so a disabled legendary still cannot be dropped, duped or lost; only its active ability stops.
     */
    boolean abilityDisabled(Player p, String id) {
        if (!this.disabledAbilities.contains(id)) {
            return false;
        }
        if (p != null) {
            long now = System.currentTimeMillis();
            Long last = this.disabledNotice.get(p.getUniqueId());
            if (last == null || now - last > 2000L) {
                this.disabledNotice.put(p.getUniqueId(), now);
                p.sendActionBar(Component.text("⛔ Способность временно отключена администрацией", NamedTextColor.RED));
            }
        }
        return true;
    }

    File disabledAbilitiesFile() {
        return new File(this.getDataFolder(), "disabled-abilities.yml");
    }

    void loadDisabledAbilities() {
        this.disabledAbilities.clear();
        File f = this.disabledAbilitiesFile();
        if (!f.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        for (String id : cfg.getStringList("disabled")) {
            if (ABILITY_SWITCHES.containsValue(id)) {
                this.disabledAbilities.add(id);
            }
        }
        if (!this.disabledAbilities.isEmpty()) {
            this.getLogger().warning("Отключённые способности (kill switch): " + String.join(", ", this.disabledAbilities));
        }
    }

    void saveDisabledAbilities() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("disabled", new ArrayList<>(new java.util.TreeSet<>(this.disabledAbilities)));
        try {
            this.getDataFolder().mkdirs();
            cfg.save(this.disabledAbilitiesFile());
        } catch (IOException ex) {
            this.getLogger().warning("Не удалось сохранить disabled-abilities.yml: " + ex.getMessage());
        }
    }

    boolean isLegendary(ItemStack item, String id) {
        if (item != null && item.hasItemMeta()) {
            return id.equals(this.getId(item));
        }
        return false;
    }

    <T extends Entity> T track(T entity) {
        if (entity != null) {
            this.tempEntities.add(entity.getUniqueId());
        }
        return entity;
    }

    void removeTracked(Entity entity) {
        if (entity != null) {
            this.tempEntities.remove(entity.getUniqueId());
            if (entity.isValid()) {
                entity.remove();
            }
        }
    }

    void giveOrDrop(Player p, ItemStack item) {
        HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(item);
        for (ItemStack drop : leftover.values()) {
            Item dropped = p.getWorld().dropItemNaturally(p.getLocation(), drop);
            this.protectDroppedItem(dropped);
        }
    }

    boolean isLocked(Player p) {
        return this.lockStates.containsKey(p.getUniqueId());
    }

    boolean isAbilityClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return false;
        }
        Player p = e.getPlayer();
        if (this.isLocked(p)) {
            return false;
        }
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        Block clicked = e.getClickedBlock();
        return clicked == null
                || !clicked.getType().isInteractable()
                || (p.isSneaking() && e.getItem() != null && !e.getItem().getType().isAir());
    }

    boolean checkAndMarkTick(Player p, String key) {
        int currentTick = this.getServer().getCurrentTick();
        String mapKey = p.getUniqueId() + ":" + key;
        Integer prev = this.lastInteractTick.put(mapKey, currentTick);
        return prev == null || prev != currentTick;
    }

    boolean isTargetable(Entity entity, Player attacker) {
        if (!(entity instanceof LivingEntity le)) {
            return false;
        }
        if (attacker != null && (entity.equals(attacker) || entity.getUniqueId().equals(attacker.getUniqueId()))) {
            return false;
        }
        if (entity instanceof ArmorStand || entity.hasMetadata(META_RA_DOLL) || entity.hasMetadata(META_TWIN)) {
            return false;
        }
        if (le.isDead() || !le.isValid()) {
            return false;
        }
        if (le instanceof Player pl && (pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE)) {
            return false;
        }
        if (attacker != null && le instanceof Tameable tameable && attacker.getUniqueId().equals(tameable.getOwnerUniqueId())) {
            return false;
        }
        return true;
    }

    Vector sideVector(Vector dir) {
        Vector side = new Vector(-dir.getZ(), 0.0D, dir.getX());
        return side.lengthSquared() < 1.0E-6D ? new Vector(1, 0, 0) : side.normalize();
    }

    Vector flatDir(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.sin(rad), 0.0D, Math.cos(rad));
    }

    Vector flatRight(float yaw) {
        double rad = Math.toRadians(yaw);
        return new Vector(-Math.cos(rad), 0.0D, -Math.sin(rad));
    }

    float lerpAngle(float from, float to, float factor) {
        float diff = (to - from + 540.0F) % 360.0F - 180.0F;
        return from + diff * factor;
    }

    void markCustomDamage(LivingEntity target, Player attacker, String reason, double amount, boolean useTrueDamage) {
        if (!target.isDead()) {
            target.setMetadata(
                    META_DEATH_REASON,
                    new FixedMetadataValue(this, reason + ":" + (attacker != null ? attacker.getName() : "Игрок")));
            try {
                if (useTrueDamage) {
                    this.trueDamage(target, amount, attacker);
                } else {
                    target.setNoDamageTicks(0);
                    target.damage(amount, attacker);
                }
            } finally {
                if (!target.isDead()) {
                    target.removeMetadata(META_DEATH_REASON, this);
                }
            }
        }
    }

    void trueDamage(LivingEntity target, double amount, Player attacker) {
        if (target.isDead()) return;
        if (target instanceof Player pl && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) {
            return;
        }
        LockState lock = this.lockStates.get(target.getUniqueId());
        if (lock != null && lock.invulnerable()) {
            return;
        }
        double hpBefore = target.getHealth();
        target.setLastDamageCause(null);
        target.setNoDamageTicks(0);
        target.damage(0.01D, attacker);
        target.setNoDamageTicks(0);
        EntityDamageEvent lastCause = target.getLastDamageCause();
        if (!target.isDead() && lastCause != null && !lastCause.isCancelled()) {
            AttributeInstance maxAttr = target.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxAttr != null ? maxAttr.getValue() : hpBefore;
            double nextHp = hpBefore - amount;
            if (nextHp <= 0.0D) {
                target.setNoDamageTicks(0);
                target.setHealth(0.0D);
            } else {
                target.setHealth(Math.min(maxHp, nextHp));
            }
        }
    }

    void spawnBoom(World world, Location loc) {
        world.spawnParticle(Particle.EXPLOSION, loc.clone().add(0.0D, 1.0D, 0.0D), 1);
        world.spawnParticle(Particle.FLASH, loc.clone().add(0.0D, 1.0D, 0.0D), 1, Color.ORANGE);
        TextDisplay text = world.spawn(loc.clone().add(0.0D, 1.4D, 0.0D), TextDisplay.class, td -> {
            td.text(Component.text("БУМ!", NamedTextColor.RED, TextDecoration.BOLD));
            td.setBillboard(Billboard.CENTER);
            td.setShadowed(false);
            td.setSeeThrough(false);
            td.setDefaultBackground(false);
            td.setPersistent(false);
        });
        this.track(text);
        final UUID id = text.getUniqueId();
        new BukkitRunnable() {
            @Override
            public void run() {
                Entity ent = FokendLegendaryWepons.this.getServer().getEntity(id);
                if (ent != null) {
                    FokendLegendaryWepons.this.removeTracked(ent);
                } else {
                    FokendLegendaryWepons.this.tempEntities.remove(id);
                }
            }
        }.runTaskLater(this, 12L);
    }

    ItemDisplay spawnModel(Location loc, ItemStack item, Vector3f translation, Vector3f scale) {
        ItemDisplay display = loc.getWorld().spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplayTransform.FIXED);
            d.setPersistent(false);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(translation, new AxisAngle4f(), scale, new AxisAngle4f()));
        });
        return this.track(display);
    }

    Location groundBelow(Location loc) {
        RayTraceResult hit = loc.getWorld().rayTraceBlocks(
                loc.clone().add(0.0D, 1.0D, 0.0D), new Vector(0, -1, 0), 12.0D, FluidCollisionMode.NEVER, true);
        Location ground = hit != null ? hit.getHitPosition().toLocation(loc.getWorld()) : loc.clone();
        ground.setYaw((float) (Math.random() * 360.0D));
        ground.setPitch(0.0F);
        return ground;
    }

    void spawnSpike(Location loc, final float scale) {
        final Location base = this.groundBelow(loc);
        World world = base.getWorld();
        if (world != null) {
            ItemDisplay display = this.spawnModel(
                    base.clone().subtract(0.0D, 1.2D * scale, 0.0D),
                    this.items.iceSpearSpike(),
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new Vector3f(scale, scale, scale));
            final UUID id = display.getUniqueId();
            world.spawnParticle(Particle.SNOWFLAKE, base.clone().add(0.0D, 0.5D, 0.0D), 12, 0.3D, 0.4D, 0.3D, 0.02D);
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    this.t++;
                    Entity ent = FokendLegendaryWepons.this.getServer().getEntity(id);
                    if (ent instanceof ItemDisplay disp && disp.isValid() && this.t <= 60) {
                        if (this.t <= 6) {
                            double progress = this.t / 6.0D;
                            disp.teleport(base.clone().subtract(0.0D, (1.0D - progress) * 1.2D * scale, 0.0D));
                        } else if (this.t >= 50) {
                            double progress = (this.t - 50) / 10.0D;
                            disp.teleport(base.clone().subtract(0.0D, progress * 1.2D * scale, 0.0D));
                        }
                        return;
                    }
                    if (ent != null) {
                        FokendLegendaryWepons.this.removeTracked(ent);
                    } else {
                        FokendLegendaryWepons.this.tempEntities.remove(id);
                    }
                    this.cancel();
                }
            }.runTaskTimer(this, 0L, 1L);
        }
    }

    void fireThompsonShot(Player shooter, Location origin, Vector dir, String metaKey) {
        World world = origin.getWorld();
        if (world != null) {
            Vector vel = dir.clone()
                    .normalize()
                    .add(new Vector(
                            (Math.random() - 0.5D) * 0.06D,
                            (Math.random() - 0.5D) * 0.06D,
                            (Math.random() - 0.5D) * 0.06D))
                    .normalize()
                    .multiply(2.8D);
            Snowball bullet = world.spawn(origin, Snowball.class, sb -> {
                sb.setShooter(shooter);
                sb.setVelocity(vel);
                sb.setGravity(false);
                sb.setMetadata(metaKey, new FixedMetadataValue(this, Boolean.TRUE));
            });
            this.track(bullet);
            final UUID bulletId = bullet.getUniqueId();
            world.spawnParticle(Particle.SMOKE, origin, 4, 0.05D, 0.05D, 0.05D, 0.01D);
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    Entity ent = FokendLegendaryWepons.this.getServer().getEntity(bulletId);
                    if (ent instanceof Snowball sb && sb.isValid() && !sb.isDead() && ++this.ticks <= 60) {
                        sb.getWorld().spawnParticle(Particle.CRIT, sb.getLocation(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                        return;
                    }
                    if (ent != null) {
                        FokendLegendaryWepons.this.removeTracked(ent);
                    } else {
                        FokendLegendaryWepons.this.tempEntities.remove(bulletId);
                    }
                    this.cancel();
                }
            }.runTaskTimer(this, 1L, 1L);
        }
    }

    void explodeSmall(Location loc, Player shooter, double damage, double radius) {
        World world = loc.getWorld();
        if (world != null) {
            this.spawnBoom(world, loc);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 1.4F);
            double radiusSq = radius * radius;
            BoundingBox box = BoundingBox.of(loc, radius, radius + 1.0D, radius);
            int currentTick = this.getServer().getCurrentTick();

            for (Entity ent : world.getNearbyEntities(box, e -> e instanceof LivingEntity)) {
                if (this.isTargetable(ent, shooter)) {
                    LivingEntity le = (LivingEntity) ent;
                    Location center = le.getLocation().add(0.0D, le.getHeight() * 0.5D, 0.0D);
                    double distSq = Math.min(le.getLocation().distanceSquared(loc), center.distanceSquared(loc));
                    if (distSq <= radiusSq) {
                        Integer prevTick = this.lastThompsonHitTick.put(le.getUniqueId(), currentTick);
                        if (prevTick == null || prevTick != currentTick) {
                            this.markCustomDamage(le, shooter, "mask_thompson", damage, false);
                        }
                    }
                }
            }
        }
    }

    void setRunBoost(Player p, boolean enabled) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            for (AttributeModifier mod : new ArrayList<>(speed.getModifiers())) {
                if (this.runKey.equals(mod.getKey())) {
                    speed.removeModifier(mod);
                }
            }
            if (enabled) {
                speed.addModifier(new AttributeModifier(this.runKey, 0.2D, Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
            }
        }
    }

    void releaseHover(Player p) {
        if (this.hovering.remove(p.getUniqueId())) {
            p.setGravity(true);
        }
    }

    boolean lockEntity(LivingEntity entity, boolean invulnerable) {
        UUID id = entity.getUniqueId();
        if (this.lockStates.containsKey(id)) {
            return false;
        }
        this.lockStates.put(id, new LockState(entity.hasAI(), entity.hasGravity(), invulnerable));
        entity.setGravity(false);
        entity.setFallDistance(0.0F);
        if (!(entity instanceof Player)) {
            entity.setAI(false);
            entity.getPersistentDataContainer().set(this.frozenKey, PersistentDataType.BYTE, (byte) 1);
        }
        entity.setVelocity(new Vector(0, 0, 0));
        return true;
    }

    void unlockEntity(UUID id) {
        LockState state = this.lockStates.remove(id);
        if (state != null) {
            Entity ent = this.getServer().getEntity(id);
            if (ent instanceof LivingEntity le) {
                le.setGravity(state.gravity());
                le.setFallDistance(0.0F);
                if (!(le instanceof Player)) {
                    le.setAI(state.ai());
                    le.getPersistentDataContainer().remove(this.frozenKey);
                }
            }
        }
    }

    void restoreStaleFrozen(Entity ent) {
        if (ent instanceof LivingEntity le && !(ent instanceof Player)) {
            if (!le.getPersistentDataContainer().has(this.frozenKey, PersistentDataType.BYTE)) {
                return;
            }
            if (this.lockStates.containsKey(le.getUniqueId())) {
                return;
            }
            le.setAI(true);
            le.setGravity(true);
            le.getPersistentDataContainer().remove(this.frozenKey);
        }
    }

    int getSouls(UUID id) {
        return this.souls.getOrDefault(id, 0);
    }

    boolean isSoulless(UUID id) {
        return this.soulless.containsKey(id);
    }

    void loadSouls() {
        synchronized (this.fileLock) {
            this.soulFile = new File(this.getDataFolder(), "souls.yml");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.soulFile);
            ConfigurationSection soulsSec = cfg.getConfigurationSection("souls");
            if (soulsSec != null) {
                for (String key : soulsSec.getKeys(false)) {
                    try {
                        this.souls.put(UUID.fromString(key), soulsSec.getInt(key));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ConfigurationSection soullessSec = cfg.getConfigurationSection("soulless");
            if (soullessSec != null) {
                for (String key : soullessSec.getKeys(false)) {
                    try {
                        this.soulless.put(UUID.fromString(key), UUID.fromString(soullessSec.getString(key, "")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ConfigurationSection revivedSec = cfg.getConfigurationSection("revived");
            if (revivedSec != null) {
                for (String key : revivedSec.getKeys(false)) {
                    try {
                        this.revived.put(UUID.fromString(key), UUID.fromString(revivedSec.getString(key, "")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            ConfigurationSection reviversSec = cfg.getConfigurationSection("revivers");
            if (reviversSec != null) {
                for (String key : reviversSec.getKeys(false)) {
                    try {
                        this.revivers.put(UUID.fromString(key), UUID.fromString(reviversSec.getString(key, "")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            for (String raw : cfg.getStringList("pending")) {
                try {
                    this.pendingNormal.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
            this.craftedUniques.addAll(cfg.getStringList("crafted_uniques"));
        }
    }

    void saveSouls() {
        synchronized (this.fileLock) {
            YamlConfiguration cfg = new YamlConfiguration();
            this.souls.forEach((k, v) -> cfg.set("souls." + k, v));
            this.soulless.forEach((k, v) -> cfg.set("soulless." + k, v.toString()));
            this.revived.forEach((k, v) -> cfg.set("revived." + k, v.toString()));
            this.revivers.forEach((k, v) -> cfg.set("revivers." + k, v.toString()));
            List<String> pendingList = new ArrayList<>();
            for (UUID id : this.pendingNormal) {
                pendingList.add(id.toString());
            }
            cfg.set("pending", pendingList);
            cfg.set("crafted_uniques", new ArrayList<>(this.craftedUniques));
            try {
                this.getDataFolder().mkdirs();
                cfg.save(this.soulFile);
            } catch (IOException e) {
                this.getLogger().warning("Не удалось сохранить souls.yml: " + e.getMessage());
            }
        }
    }

    void setHealthModifier(Player p, String name, double amount, boolean add) {
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            NamespacedKey key = new NamespacedKey(this, name);
            for (AttributeModifier mod : new ArrayList<>(maxHealth.getModifiers())) {
                if (key.equals(mod.getKey())) {
                    maxHealth.removeModifier(mod);
                }
            }
            if (add) {
                maxHealth.addModifier(new AttributeModifier(key, amount, Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
            }
            if (p.getHealth() > maxHealth.getValue()) {
                p.setHealth(Math.max(1.0D, maxHealth.getValue()));
            }
        }
    }

    void clearHealthModifiersByPrefix(Player p, String prefix) {
        AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            for (AttributeModifier mod : new ArrayList<>(maxHealth.getModifiers())) {
                if (mod.getKey().getNamespace().equals(this.getName().toLowerCase(Locale.ROOT))
                        && mod.getKey().getKey().startsWith(prefix)) {
                    maxHealth.removeModifier(mod);
                }
            }
        }
    }

    void applySoulless(Player p) {
        if (p.getGameMode() != GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SPECTATOR);
        }
        p.sendMessage(Component.text(
                "Ваша душа похищена. Вас может возродить другой игрок командой /revive " + p.getName(),
                NamedTextColor.DARK_PURPLE));
    }

    void applyNormal(Player p) {
        this.setHealthModifier(p, "soul_cap", -10.0D, false);
        this.clearHealthModifiersByPrefix(p, "soul_gift_");
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
            Location respawn = p.getRespawnLocation();
            if (respawn == null) {
                respawn = p.getWorld().getSpawnLocation();
            }
            p.teleport(respawn);
            AttributeInstance maxHealth = p.getAttribute(Attribute.MAX_HEALTH);
            p.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0D);
        }
        p.sendMessage(Component.text("Лук душ потерял владельца: ваше состояние восстановлено.", NamedTextColor.GREEN));
    }

    void normalize(UUID id) {
        this.soulless.remove(id);
        this.revived.remove(id);
        this.revivers.remove(id);
        Player p = this.getServer().getPlayer(id);
        if (p == null) {
            this.pendingNormal.add(id);
        } else {
            this.applyNormal(p);
        }
    }

    void releaseAllFrom(UUID bowOwnerId) {
        this.souls.remove(bowOwnerId);
        Player owner = this.getServer().getPlayer(bowOwnerId);
        if (owner != null) {
            this.setHealthModifier(owner, "soul_consume", 0.0D, false);
        }

        for (UUID victimId : new ArrayList<>(this.soulless.keySet())) {
            if (bowOwnerId.equals(this.soulless.get(victimId))) {
                this.normalize(victimId);
            }
        }
        for (UUID revivedId : new ArrayList<>(this.revived.keySet())) {
            if (bowOwnerId.equals(this.revived.get(revivedId))) {
                this.normalize(revivedId);
            }
        }
        for (UUID reviverId : new ArrayList<>(this.revivers.keySet())) {
            if (bowOwnerId.equals(this.revivers.get(reviverId))) {
                this.normalize(reviverId);
            }
        }
        this.saveSouls();
    }

    boolean hasSoulDependents(UUID bowOwnerId) {
        if (this.getSouls(bowOwnerId) > 0) {
            return true;
        }
        Player owner = this.getServer().getPlayer(bowOwnerId);
        if (owner != null) {
            AttributeInstance maxHealth = owner.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) {
                NamespacedKey key = new NamespacedKey(this, "soul_consume");
                for (AttributeModifier mod : maxHealth.getModifiers()) {
                    if (key.equals(mod.getKey())) {
                        return true;
                    }
                }
            }
        }
        return this.soulless.containsValue(bowOwnerId)
                || this.revived.containsValue(bowOwnerId)
                || this.revivers.containsValue(bowOwnerId);
    }

    void takeSoul(UUID bowOwnerId, UUID victimId) {
        this.revived.remove(victimId);
        Player victim = this.getServer().getPlayer(victimId);
        if (victim != null) {
            this.setHealthModifier(victim, "soul_cap", -10.0D, false);
        }
        this.soulless.put(victimId, bowOwnerId);
        this.souls.put(bowOwnerId, Math.min(this.balance.getInt("soul_bow.max_souls"), this.getSouls(bowOwnerId) + 1));
        this.saveSouls();
        Player owner = this.getServer().getPlayer(bowOwnerId);
        if (owner != null) {
            this.cooldowns.sendPriorityActionBar(
                    owner,
                    Component.text("Душа похищена! Душ: " + this.getSouls(bowOwnerId) + "/5", NamedTextColor.DARK_PURPLE));
        }
    }

    static record ThrownBoomerang(UUID owner, ItemStack item, String throwSessionId) {
        ThrownBoomerang(UUID owner, ItemStack item) {
            this(owner, item, UUID.randomUUID().toString());
        }
    }

    static record LockState(boolean ai, boolean gravity, boolean invulnerable) {
    }

}

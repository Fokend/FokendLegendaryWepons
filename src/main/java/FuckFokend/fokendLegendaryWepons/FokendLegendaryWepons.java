package FuckFokend.fokendLegendaryWepons;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
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
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public final class FokendLegendaryWepons extends JavaPlugin {
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
    private static final int CMD_CB_ORANGE = 30001;
    private static final int CMD_CB_PURPLE = 30002;
    private static final int CMD_CB_GREEN = 30003;

    static final String META_TOMPSON = "mask_explosive";
    static final String META_SOUL_ARROW = "soul_arrow";
    static final String META_RA_DOLL = "ra_doll_owner";
    static final String META_MASK_EXPLOSIVE = "default_mask_explosive";
    static final String META_TWIN = "twins_npc_owner";
    static final String META_DEATH_REASON = "legendary_death_reason";

    static final long MASK_HAMMER_CD = 300000L;
    static final int BOOMERANG_MIN_BOUNCES = 3;
    static final int BOOMERANG_MAX_BOUNCES = 10;
    static final long MASK_RUN_DELAY = 5000L;
    static final long MASK_RUN_LINGER = 10000L;
    static final int MAX_SOULS = 5;
    static final double REVIVE_HP = 5.0D;
    static final boolean HAMMER_HITS_MOBS = false;
    static final int COOLDOWN_BAR_LENGTH = 10;

    public static NamespacedKey KEY;
    private static FokendLegendaryWepons instance;

    final Object fileLock = new Object();
    final MiniMessage mm = MiniMessage.miniMessage();
    final Map<UUID, UUID> raDolls = new ConcurrentHashMap<>();
    final Map<UUID, List<UUID>> activeTwins = new ConcurrentHashMap<>();
    final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    final Map<String, HudCooldown> cooldownHud = new ConcurrentHashMap<>();
    final Map<UUID, Long> actionBarPriorityUntil = new ConcurrentHashMap<>();
    final Map<UUID, String> selectedHudCooldown = new ConcurrentHashMap<>();
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
    final Map<UUID, TimeSession> timeSessions = new ConcurrentHashMap<>();
    final Map<UUID, TimeSession> timeByTarget = new ConcurrentHashMap<>();
    final Set<UUID> timeTeleporting = ConcurrentHashMap.newKeySet();
    final Map<UUID, Integer> souls = new ConcurrentHashMap<>();
    final Map<UUID, UUID> soulless = new ConcurrentHashMap<>();
    final Map<UUID, UUID> revived = new ConcurrentHashMap<>();
    final Map<UUID, UUID> revivers = new ConcurrentHashMap<>();
    final Set<UUID> pendingNormal = ConcurrentHashMap.newKeySet();
    final Set<String> craftedUniques = ConcurrentHashMap.newKeySet();
    final Map<String, Integer> lastInteractTick = new ConcurrentHashMap<>();
    final Map<UUID, Integer> lastThompsonHitTick = new ConcurrentHashMap<>();

    File soulFile;
    NamespacedKey idKey;
    NamespacedKey weaponKey;
    NamespacedKey throwSessionKey;
    NamespacedKey frozenKey;
    NamespacedKey runKey;
    NamespacedKey wardenHpKey;

    static final List<String> RECIPE_KEYS = List.of(
            "cb_orange", "cb_purple", "cb_green", "ra_blade", "twins_sword",
            "ice_spear", "death_note", "fate_scales"
    );

    public FokendLegendaryWepons() {
        instance = this;
    }

    @Override
    public void onEnable() {
        instance = this;
        this.idKey = new NamespacedKey(this, "legendary_id");
        this.weaponKey = new NamespacedKey(this, "weapon_id");
        KEY = this.weaponKey;
        this.throwSessionKey = new NamespacedKey(this, "throw_session_id");
        this.frozenKey = new NamespacedKey(this, "time_frozen");
        this.runKey = new NamespacedKey(this, "mask_run");
        this.wardenHpKey = new NamespacedKey(this, "warden_hp");

        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents(new RaBladeListener(this), this);
        pm.registerEvents(new IceSpearListener(this), this);
        pm.registerEvents(new TwinsSwordListener(this), this);
        pm.registerEvents(new CleanupListener(this), this);
        pm.registerEvents(new CraftProtectionListener(this), this);
        pm.registerEvents(new CoreUpgradeListener(this), this);
        pm.registerEvents(new ItemProtectionListener(this), this);
        pm.registerEvents(new EntityRightClickBridge(this), this);
        pm.registerEvents(new DeathNoteListener(this), this);
        pm.registerEvents(new ScalesOfFateListener(this), this);

        PluginCommand legendaryCmd = this.getCommand("legendary");
        final LegendaryCommand legendaryExec = new LegendaryCommand();
        if (legendaryCmd != null) {
            legendaryCmd.setExecutor(legendaryExec);
            legendaryCmd.setTabCompleter(legendaryExec);
        }
        this.disableConflictingLegacyPlugins(legendaryExec);
        this.getServer().getScheduler().runTask(this, () -> this.disableConflictingLegacyPlugins(legendaryExec));

        this.registerRecipes();
        this.logRecipes();

        // Periodic cleanup of expired cooldown entries
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                FokendLegendaryWepons.this.cooldowns.entrySet().removeIf(entry -> now - entry.getValue() > 600000L);
            }
        }.runTaskTimer(this, 6000L, 6000L);

        // ActionBar Cooldown HUD
        new BukkitRunnable() {
            @Override
            public void run() {
                this.runSyncHud();
            }

            private void runSyncHud() {
                long now = System.currentTimeMillis();
                FokendLegendaryWepons.this.cooldownHud.entrySet().removeIf(entry -> {
                    Long started = FokendLegendaryWepons.this.cooldowns.get(entry.getKey());
                    if (started == null) {
                        return true;
                    } else if (now - started < entry.getValue().durationMs()) {
                        return false;
                    } else {
                        int sep = entry.getKey().indexOf(':');
                        if (sep > 0) {
                            try {
                                Player p = FokendLegendaryWepons.this.getServer().getPlayer(
                                        UUID.fromString(entry.getKey().substring(0, sep)));
                                if (p != null && p.isOnline()) {
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.25F, 1.65F);
                                }
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        return true;
                    }
                });

                for (Player p : FokendLegendaryWepons.this.getServer().getOnlinePlayers()) {
                    UUID id = p.getUniqueId();
                    if (FokendLegendaryWepons.this.actionBarPriorityUntil.getOrDefault(id, 0L) <= now) {
                        String prefix = id + ":";
                        String selected = FokendLegendaryWepons.this.selectedHudCooldown.get(id);
                        if (selected != null && FokendLegendaryWepons.this.isHudCooldownActive(prefix + selected, now)) {
                            FokendLegendaryWepons.this.sendCooldownHud(p, selected, now);
                        } else {
                            FokendLegendaryWepons.this.selectedHudCooldown.remove(id);
                            String next = FokendLegendaryWepons.this.cooldownHud.keySet().stream()
                                    .filter(k -> k.startsWith(prefix) && FokendLegendaryWepons.this.isHudCooldownActive(k, now))
                                    .max((a, b) -> {
                                        int cmp = Long.compare(
                                                FokendLegendaryWepons.this.cooldowns.getOrDefault(a, 0L),
                                                FokendLegendaryWepons.this.cooldowns.getOrDefault(b, 0L));
                                        return cmp != 0 ? cmp : a.compareTo(b);
                                    })
                                    .map(k -> k.substring(prefix.length()))
                                    .orElse(null);
                            if (next != null) {
                                FokendLegendaryWepons.this.selectedHudCooldown.put(id, next);
                                FokendLegendaryWepons.this.sendCooldownHud(p, next, now);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 3L);
    }

    private void disableConflictingLegacyPlugins(LegendaryCommand legendaryExec) {
        PluginManager pm = this.getServer().getPluginManager();
        for (org.bukkit.plugin.Plugin other : pm.getPlugins()) {
            if (other != null && other != this) {
                String name = other.getName();
                String mainClass = other.getClass().getName();
                if ("LegendaryWeapons".equalsIgnoreCase(name)
                        || mainClass.startsWith("ru.legendary.weapons.")) {
                    this.getLogger().warning("Обнаружен старый конфликтующий плагин " + name + " — отключаем его.");
                    org.bukkit.event.HandlerList.unregisterAll(other);
                    this.getServer().getScheduler().cancelTasks(other);
                    if (other.isEnabled()) {
                        pm.disablePlugin(other);
                    }
                }
            }
        }
        PluginCommand legendaryCmd = this.getCommand("legendary");
        if (legendaryCmd != null) {
            legendaryCmd.setExecutor(legendaryExec);
            legendaryCmd.setTabCompleter(legendaryExec);
        }
        Command mapCmd = this.getServer().getCommandMap().getCommand("legendary");
        if (mapCmd instanceof PluginCommand pc && pc.getPlugin() != this) {
            pc.setExecutor(legendaryExec);
            pc.setTabCompleter(legendaryExec);
        }
    }

    public void restoreAllEntities() {
        for (TimeSession session : new ArrayList<>(this.timeSessions.values())) {
            this.endTimeSession(session, null);
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
        this.cooldownHud.clear();
        this.actionBarPriorityUntil.clear();
        this.selectedHudCooldown.clear();

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
            if (this.wardenBlindness.remove(p.getUniqueId())) {
                p.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        }

        this.wardenBlindness.clear();
        this.hovering.clear();
        this.maskSneakStart.clear();
        this.maskRunUntil.clear();
        this.saveSouls();
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
            if (this.idKey != null) pdc.remove(this.idKey);
            if (this.weaponKey != null) pdc.remove(this.weaponKey);
            if (this.throwSessionKey != null) pdc.remove(this.throwSessionKey);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    public static boolean isWeapon(ItemStack item, String id) {
        if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (KEY != null) {
                String val = pdc.get(KEY, PersistentDataType.STRING);
                if (id.equals(val)) {
                    return true;
                }
            }
            if (instance != null && instance.idKey != null) {
                String val = pdc.get(instance.idKey, PersistentDataType.STRING);
                return id.equals(val);
            }
        }
        return false;
    }

    boolean onCooldown(Player p, String key, long durationMs) {
        return this.cooldownRemaining(p, key, durationMs) > 0L;
    }

    long cooldownRemaining(Player p, String key, long durationMs) {
        Long started = this.cooldowns.get(p.getUniqueId() + ":" + key);
        return started == null ? 0L : Math.max(0L, started + durationMs - System.currentTimeMillis());
    }

    void setCooldown(Player p, String key, long durationMs, String displayName) {
        String mapKey = p.getUniqueId() + ":" + key;
        this.cooldowns.put(mapKey, System.currentTimeMillis());
        this.cooldownHud.put(mapKey, new HudCooldown(durationMs, displayName));
        this.selectedHudCooldown.put(p.getUniqueId(), key);
    }

    boolean isHudCooldownActive(String fullKey, long now) {
        HudCooldown hud = this.cooldownHud.get(fullKey);
        Long started = this.cooldowns.get(fullKey);
        return hud != null && started != null && now - started < hud.durationMs();
    }

    void sendCooldownHud(Player p, String key, long now) {
        String fullKey = p.getUniqueId() + ":" + key;
        HudCooldown hud = this.cooldownHud.get(fullKey);
        Long started = this.cooldowns.get(fullKey);
        if (hud != null && started != null) {
            long rem = Math.max(0L, hud.durationMs() - (now - started));
            if (rem != 0L && hud.durationMs() > 0L) {
                double ratio = Math.max(0.0D, Math.min(1.0D, (double) rem / (double) hud.durationMs()));
                int filled = (int) Math.round(ratio * COOLDOWN_BAR_LENGTH);
                String bar = "▰".repeat(filled) + "▱".repeat(COOLDOWN_BAR_LENGTH - filled);
                Component msg = ((TextComponent) Component.text(
                                "⚔ " + hud.name() + "  " + String.format(Locale.US, "%.1fs ", rem / 1000.0D),
                                NamedTextColor.GRAY)
                        .append(Component.text(bar.substring(0, filled), NamedTextColor.AQUA)))
                        .append(Component.text(bar.substring(filled), NamedTextColor.DARK_GRAY));
                p.sendActionBar(msg);
            }
        }
    }

    void sendPriorityActionBar(Player p, Component msg) {
        this.actionBarPriorityUntil.put(p.getUniqueId(), System.currentTimeMillis() + 2000L);
        p.sendActionBar(msg);
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
            if (this.idKey != null) {
                pdc.set(this.idKey, PersistentDataType.STRING, id);
            }
            if (this.weaponKey != null) {
                pdc.set(this.weaponKey, PersistentDataType.STRING, id);
            }
            item.setItemMeta(meta);
        }
    }

    String getId(ItemStack item) {
        if (item != null && !item.getType().isAir() && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return null;
            }
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (this.weaponKey != null) {
                String val = pdc.get(this.weaponKey, PersistentDataType.STRING);
                if (val != null) {
                    return val;
                }
            }
            if (this.idKey != null) {
                String val = pdc.get(this.idKey, PersistentDataType.STRING);
                if (val != null) {
                    return val;
                }
            }
            for (NamespacedKey k : pdc.getKeys()) {
                if ("legendaryweapons".equals(k.getNamespace()) && "weapon".equals(k.getKey())) {
                    String legacy = pdc.get(k, PersistentDataType.STRING);
                    if (legacy != null) {
                        return switch (legacy) {
                            case "warden" -> ID_WARDEN_HELMET;
                            case "ra" -> ID_RA_BLADE;
                            case "chaos" -> ID_CHAOS_BOOMERANG;
                            case "twins" -> ID_TWINS_SWORD;
                            case "magnet" -> ID_MAGNET;
                            default -> legacy;
                        };
                    }
                }
            }
        }
        return null;
    }

    boolean isLegendary(ItemStack item, String id) {
        if (item != null && item.hasItemMeta()) {
            return isWeapon(item, id) || id.equals(this.getId(item));
        }
        return false;
    }

    Attribute attackDamageAttribute() {
        Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("attack_damage"));
        return attr != null ? attr : Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.attack_damage"));
    }

    void applyBaseDamage(ItemStack item, double amount, String id) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Attribute attr = this.attackDamageAttribute();
            if (attr != null) {
                AttributeModifier mod = new AttributeModifier(
                        new NamespacedKey(this, "legendary_dmg_" + id),
                        amount,
                        Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND);
                meta.addAttributeModifier(attr, mod);
                item.setItemMeta(meta);
            }
        }
    }

    void setLore(ItemMeta meta, String... lines) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("✦ ЛЕГЕНДАРНЫЙ АРТЕФАКТ ✦", NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        for (String line : lines) {
            if (line.isEmpty()) {
                lore.add(Component.empty());
            } else if (line.startsWith("~")) {
                lore.add(Component.text(line.substring(1))
                        .color(NamedTextColor.DARK_PURPLE)
                        .decoration(TextDecoration.ITALIC, true));
            } else if (line.startsWith("#")) {
                lore.add(Component.text(line.substring(1))
                        .color(NamedTextColor.LIGHT_PURPLE)
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(line)
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
    }

    @SuppressWarnings("deprecation")
    void applyCustomModelData(ItemMeta meta, int cmd) {
        try {
            meta.setCustomModelData(cmd);
        } catch (Throwable ignored) {
        }
        try {
            CustomModelDataComponent comp = meta.getCustomModelDataComponent();
            comp.setFloats(List.of((float) cmd));
            meta.setCustomModelDataComponent(comp);
        } catch (Throwable ignored) {
        }
    }

    ItemStack base(Material mat, int cmd, String id, String title, boolean applySwordDamage) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        this.applyCustomModelData(meta, cmd);
        if (title.contains("<")) {
            meta.displayName(this.mm.deserialize(title).decoration(TextDecoration.ITALIC, false));
        } else {
            String[] style = switch (id) {
                case "ra_god_blade" -> new String[]{"#fff3a3", "#ff8c00", "☀"};
                case "chaos_boomerang" -> new String[]{"#ff4d9d", "#8a2be2", "⟲"};
                case "warden_helmet" -> new String[]{"#83a4d4", "#b6fbff", "◉"};
                case "twins_sword" -> new String[]{"#ff758c", "#7b2cbf", "⚔"};
                case "magnet" -> new String[]{"#00d2ff", "#3a7bd5", "✧"};
                case "time_clock" -> new String[]{"#a18cd1", "#fbc2eb", "⌛"};
                case "ice_spear", "ice_spear_ice", "ice_spear_spike" -> new String[]{"#83a4d4", "#b6fbff", "❄"};
                case "default_mask", "mask_block" -> new String[]{"#434343", "#d7d2cc", "☠"};
                case "happy_mask" -> new String[]{"#fddb92", "#d1fdff", "☻"};
                case "mask_mace" -> new String[]{"#f7971e", "#ffd200", "✹"};
                case "mask_tompson" -> new String[]{"#f953c6", "#b91d73", "✦"};
                case "soul_bow" -> new String[]{"#8e2de2", "#4a00e0", "♜"};
                default -> new String[]{"#f6d365", "#fda085", "✦"};
            };
            meta.displayName(this.mm.deserialize(
                            "<gradient:" + style[0] + ":" + style[1] + "><bold>" + style[2] + " " + title + "</bold></gradient>")
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        this.stampId(item, id);
        if (applySwordDamage) {
            this.applyBaseDamage(item, 4.0D, id);
        }
        return item;
    }

    Material getSpearMaterial() {
        for (String name : List.of("NETHERITE_SPEAR", "TRIDENT", "NETHERITE_SWORD")) {
            try {
                return Material.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Material.NETHERITE_SWORD;
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
                    this.iceSpearSpike(),
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

    void startTimeSession(Player owner, LivingEntity target) {
        final UUID ownerId = owner.getUniqueId();
        final UUID targetId = target.getUniqueId();
        final TimeSession session = new TimeSession(ownerId, targetId);
        this.timeSessions.put(session.owner, session);
        this.timeByTarget.put(session.target, session);
        this.setCooldown(owner, "time_clock", 180000L, "Часы времени");
        this.sendPriorityActionBar(owner, Component.text("Часы записывают движения цели: 10 секунд...", NamedTextColor.AQUA));
        owner.getWorld().playSound(owner.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.8F);

        session.recordTask = new BukkitRunnable() {
            @Override
            public void run() {
                Entity ent = FokendLegendaryWepons.this.getServer().getEntity(targetId);
                Player pl = FokendLegendaryWepons.this.getServer().getPlayer(ownerId);
                if (ent instanceof LivingEntity le && le.isValid() && !le.isDead() && pl != null && pl.isOnline()) {
                    session.frames.add(new TimeFrame(le.getLocation().clone(), le.getHealth()));
                    if (session.frames.size() >= 200) {
                        this.cancel();
                        FokendLegendaryWepons.this.beginTimeFreeze(session, pl, le);
                    }
                    return;
                }
                FokendLegendaryWepons.this.endTimeSession(session, "Запись прервана: цель потеряна.");
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    void beginTimeFreeze(TimeSession session, Player owner, LivingEntity target) {
        if (!this.lockEntity(target, true)) {
            this.endTimeSession(session, "Не удалось остановить цель.");
        } else {
            session.frozen = true;
            session.index = session.frames.size() - 1;
            target.getWorld().spawnParticle(
                    Particle.END_ROD, target.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.4D, 0.8D, 0.4D, 0.05D);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 0.5F);
            if (owner != null && owner.isOnline()) {
                this.sendPriorityActionBar(
                        owner,
                        Component.text("Время остановлено! Колёсико: листать кадры, ПКМ: вернуть время.", NamedTextColor.AQUA));
            }
            session.timeoutTask = this.getServer().getScheduler().runTaskLater(
                    this, () -> this.endTimeSession(session, "Время цели возобновилось само."), 3600L);
        }
    }

    void applyTimeFrame(Entity target, TimeFrame frame) {
        if (target == null || frame == null || !target.isValid() || target.isDead()) return;
        UUID targetId = target.getUniqueId();
        if (target instanceof Player) {
            this.timeTeleporting.add(targetId);
        }
        boolean teleported;
        try {
            teleported = target.teleport(frame.location());
        } finally {
            this.timeTeleporting.remove(targetId);
        }
        if (teleported && target instanceof LivingEntity le && !le.isDead()) {
            AttributeInstance maxAttr = le.getAttribute(Attribute.MAX_HEALTH);
            double maxHp = maxAttr == null ? le.getHealth() : maxAttr.getValue();
            le.setHealth(Math.max(0.5D, Math.min(frame.health(), maxHp)));
        }
    }

    void endTimeSession(TimeSession session, String message) {
        if (session.recordTask != null) {
            session.recordTask.cancel();
        }
        if (session.timeoutTask != null) {
            session.timeoutTask.cancel();
        }
        this.timeSessions.remove(session.owner);
        this.timeByTarget.remove(session.target);
        if (session.frozen) {
            Entity target = this.getServer().getEntity(session.target);
            if (target != null && !session.frames.isEmpty()
                    && session.index >= 0 && session.index < session.frames.size()) {
                this.applyTimeFrame(target, session.frames.get(session.index));
            }
            this.unlockEntity(session.target);
            if (target != null) {
                target.getWorld().spawnParticle(
                        Particle.END_ROD, target.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.6D, 0.3D, 0.05D);
            }
        }
        session.frozen = false;
        Player owner = this.getServer().getPlayer(session.owner);
        if (owner != null && message != null) {
            this.sendPriorityActionBar(owner, Component.text(message, NamedTextColor.YELLOW));
        }
    }

    void scrubTime(TimeSession session, Player owner, int delta) {
        if (!session.frames.isEmpty()) {
            Entity target = this.getServer().getEntity(session.target);
            if (target != null && target.isValid() && !target.isDead()) {
                session.index = Math.max(0, Math.min(session.frames.size() - 1, session.index + delta));
                TimeFrame frame = session.frames.get(session.index);
                this.applyTimeFrame(target, frame);
                double secondsBack = (session.frames.size() - 1 - session.index) / 20.0D;
                this.sendPriorityActionBar(
                        owner,
                        Component.text(
                                String.format(
                                        Locale.US,
                                        "Кадр %d/%d (-%.2f с)",
                                        session.index + 1,
                                        session.frames.size(),
                                        secondsBack),
                                NamedTextColor.AQUA));
                owner.playSound(
                        owner.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_HAT,
                        0.5F,
                        1.0F + (float) session.index / (float) session.frames.size());
            } else {
                this.endTimeSession(session, "Цель потеряна.");
            }
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
        this.souls.put(bowOwnerId, Math.min(MAX_SOULS, this.getSouls(bowOwnerId) + 1));
        this.saveSouls();
        Player owner = this.getServer().getPlayer(bowOwnerId);
        if (owner != null) {
            this.sendPriorityActionBar(
                    owner,
                    Component.text("Душа похищена! Душ: " + this.getSouls(bowOwnerId) + "/5", NamedTextColor.DARK_PURPLE));
        }
    }

    ItemStack cbOrange() {
        ItemStack item = this.base(
                Material.COMMAND_BLOCK,
                CMD_CB_ORANGE,
                ID_CB_ORANGE,
                "<gradient:#FF8008:#FFC837><bold>✴ Импульсное Ядро Древних</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Сгусток первозданной энергии, пульсирующий в такт сердцу мира.",
                "",
                "#ПРИМЕНЕНИЕ",
                "Рецепты легендарных артефактов; наковальня:",
                "10% шанс повысить случайное зачарование на уровень.",
                "При неудаче теряется 80% оставшейся прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack cbPurple() {
        ItemStack item = this.base(
                Material.REPEATING_COMMAND_BLOCK,
                CMD_CB_PURPLE,
                ID_CB_PURPLE,
                "<gradient:#8E2DE2:#4A00E0><bold>۞ Ядро Вечного Цикла</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Бесконечный вихрь хроно-материи, способный искажать реальность.",
                "",
                "#ПРИМЕНЕНИЕ",
                "Рецепты легендарных артефактов; наковальня:",
                "15% шанс повысить случайное зачарование на уровень.",
                "При неудаче теряется 80% оставшейся прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack cbGreen() {
        ItemStack item = this.base(
                Material.CHAIN_COMMAND_BLOCK,
                CMD_CB_GREEN,
                ID_CB_GREEN,
                "<gradient:#11998e:#38ef7d><bold>❖ Цепное Ядро Сингулярности</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Холодный кристалл абсолютного порядка, связывающий стихии воедино.",
                "",
                "#ПРИМЕНЕНИЕ",
                "Рецепты легендарных артефактов; наковальня:",
                "17% шанс повысить случайное зачарование на уровень.",
                "При неудаче теряется 80% оставшейся прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack raBlade() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                CMD_RA_BLADE,
                ID_RA_BLADE,
                "<gradient:#FFD700:#FF4500><bold>☀ Гелиос, Клинок Бога Ра</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Ослепительный свет древнего божества заточён в этом лезвии.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: призвать или отозвать манекен Ра (кд 60с).",
                "Манекен стоит 45 сек. Удар по нему отражает",
                "весь урон на всех врагов вокруг в 10 блоках.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack chaosBoomerang() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                CMD_CHAOS_BOOMERANG,
                ID_CHAOS_BOOMERANG,
                "<gradient:#FF0099:#79095D><bold>🌀 Потрошитель Бездны • Бумеранг Хаоса</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Оружие, не знающее покоя. Несёт в себе частицу чистого безумия.",
                "",
                "#ЗАПУСК",
                "ПКМ: отправить бумеранг в полёт (кд 8с).",
                "#РИКОШЕТЫ",
                "Отскакивает от блоков и врагов (макс. 10 раз),",
                "совершает минимум 3 и максимум 10 отскоков.",
                "При отскоках сила удара растёт.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack wardenHelmet() {
        ItemStack item = this.base(
                Material.CARVED_PUMPKIN,
                CMD_WARDEN_HELMET,
                ID_WARDEN_HELMET,
                "<gradient:#00C9FF:#003366><bold>👁 Корона Глубин • Лик Хранителя</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Эхо глубин лишает владельца зрения, но отмечает окружение светом.",
                "",
                "#ПОКА НАДЕТ",
                "Слепота владельцу; свечение существ",
                "рядом (до 15 блоков). Вардены не выбирают владельца целью.",
                "#АКТИВАЦИЯ",
                "Пустой рукой ПКМ / SHIFT+F: Sonic Boom",
                "Выстрел летит по направлению взгляда",
                "до 32 блоков (урон 10, отбрасывание; кд 10с).",
                "Луч останавливается о стены; наводиться на моба не нужно.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack twinsSword() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                CMD_TWINS_SWORD,
                ID_TWINS_SWORD,
                "<gradient:#B24592:#F15F79><bold>⚔ Эхо Дуальности • Меч Близнецов</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Клинок, отзывающийся эхом в одном верном союзнике.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: призвать близнеца на 30с (кд 30с).",
                "Близнец следует за вами и повторяет ваши удары;",
                "активный близнец добавляет 4 урона.",
                "#ПАССИВНО",
                "25% шанс нанести дополнительный удар.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack magnet() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                CMD_MAGNET,
                ID_MAGNET,
                "<gradient:#FF416C:#3A7BD5><bold>🧲 Полюс Гравитации • Магнитник</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Соткан из гравитационных аномалий. Никто не избежит его притяжения.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: притянуть врагов к точке взгляда перед вами.",
                "SHIFT+ПКМ: оттолкнуть врагов и отразить снаряды.",
                "Перезарядка: 5с.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack timeClock() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                CMD_TIME_CLOCK,
                ID_TIME_CLOCK,
                "<gradient:#00F2FE:#4FACFE><bold>⏳ Хроносфера • Часы Вечности</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Шестерёнки судьбы тикают внутри, позволяя повелевать самим временем.",
                "",
                "#ЗАПИСЬ",
                "ЛКМ по сущности: записать 10 секунд её движений,",
                "затем остановка до 3 минут. Цель неуязвима.",
                "#УПРАВЛЕНИЕ",
                "Колёсико мыши: листать записанные кадры",
                "(SHIFT: сразу по 10 кадров).",
                "ПКМ: вернуть время с выбранного кадра (кд 3 мин).");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack iceSpear() {
        ItemStack item = this.base(
                this.getSpearMaterial(),
                CMD_ICE_SPEAR,
                ID_ICE_SPEAR,
                "<gradient:#83a4d4:#b6fbff><bold>❄ Гласиус • Копьё Вечной Мерзлоты</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Сковано в сердце вечной мерзлоты. Его прикосновение останавливает жизнь.",
                "",
                "#АКТИВАЦИЯ",
                "1-й слот + SHIFT+ПКМ: вызвать морозные пики (кд 75с).",
                "#ПАССИВНО",
                "2-й слот: удар замораживает цель на 3с",
                "(перезарядка эффекта 6с).");
        if (meta != null) {
            Enchantment lunge = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("lunge"));
            if (lunge != null) {
                meta.addEnchant(lunge, 4, true);
            } else {
                meta.addEnchant(Enchantment.RIPTIDE, 4, true);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    ItemStack iceSpearIce() {
        ItemStack item = this.base(Material.NETHERITE_SWORD, CMD_ICE_SPEAR_ICE, ID_ICE_SPEAR_ICE, "<aqua>Лёд копья</aqua>", true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(meta, "~След вечной мерзлоты, оставленный Морозным копьём.", "", "#АРТЕФАКТ", "Ледяной образ из набора Морозного копья.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack iceSpearSpike() {
        ItemStack item = this.base(Material.NETHERITE_SWORD, CMD_ICE_SPEAR_SPIKE, ID_ICE_SPEAR_SPIKE, "<aqua>Морозные пики</aqua>", true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(meta, "~Осколки льда, пробуждённые силой Морозного копья.", "", "#АРТЕФАКТ", "Ледяной образ из набора Морозного копья.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack defaultMask() {
        ItemStack item = this.base(
                Material.CARVED_PUMPKIN,
                CMD_DEFAULT_MASK,
                ID_DEFAULT_MASK,
                "<gradient:#56ab2f:#a8e063><bold>🎭 Лик Безумия • Маска Хаоса</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Безликая маска, скрывающая истинные намерения и дарующая безумную силу.",
                "",
                "#КОГДА НАДЕТА",
                "Бег и парение при падении.",
                "В приседе + F или SHIFT+ПКМ: Молот — сокрушающий",
                "удар радиусом 5 блоков, 10 сердец игрокам (кд 5 мин).",
                "Бег: 5 сек в приседе дают ускорение +20% на 10 сек.",
                "Падение: на высоте более 20 блоков паришь",
                "в воздухе, пока не посмотришь вниз.",
                "",
                "#В РУКЕ",
                "ПКМ: Томпсоны — парный взрывной выстрел");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack happyMask() {
        ItemStack item = this.base(
                Material.CARVED_PUMPKIN,
                CMD_HAPPY_MASK,
                ID_HAPPY_MASK,
                "<gradient:#FFE000:#799F0C><bold>☺ Эйфория • Счастливая Маска</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Её улыбка неестественна, но она дарует невиданную жизненную силу.",
                "",
                "#ПОКА НАДЕТА",
                "Регенерация и ночное зрение.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack maskBlock() {
        ItemStack item = this.base(Material.CARVED_PUMPKIN, CMD_MASK_BLOCK, ID_MASK_BLOCK, "<gold>Блок маски</gold>", false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(meta, "~Фрагмент легенды о Маске.", "", "#АРТЕФАКТ", "Самостоятельной способности нет.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack maskMace() {
        ItemStack item = this.base(
                Material.MACE,
                CMD_MASK_MACE,
                ID_MASK_MACE,
                "<gradient:#FF512F:#DD2476><bold>🔨 Крушитель Черепов • Молот Маски</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Игрушечный на вид, но сокрушительно смертоносный на деле.",
                "",
                "#АРТЕФАКТ",
                "Булава, используемая как образ молота",
                "во время способности Маски. Отдельной способности нет.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack maskThompson() {
        ItemStack item = this.base(
                Material.NETHERITE_SWORD,
                CMD_MASK_TOMPSON,
                ID_MASK_TOMPSON,
                "<gradient:#D31027:#EA384D><bold>🔫 Гнев Чикаго • Парные Томпсоны</bold></gradient>",
                true);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Свинцовый дождь с привкусом пороха и хаоса.",
                "",
                "#АКТИВАЦИЯ",
                "ПКМ: выстрелить взрывной пулей.",
                "Урон взрыва: 5 в радиусе 2 блоков.",
                "#ИЗНОС",
                "Каждый выстрел расходует 10 прочности.");
        item.setItemMeta(meta);
        return item;
    }

    ItemStack soulBow() {
        ItemStack item = this.base(
                Material.BOW,
                CMD_SOUL_BOW,
                ID_SOUL_BOW,
                "<gradient:#43cea2:#185a9d><bold>👻 Шёпот Павших • Лук Душ</bold></gradient>",
                false);
        ItemMeta meta = item.getItemMeta();
        this.setLore(
                meta,
                "~Тетива сплетена из стонов павших.",
                "",
                "#ПОХИЩЕНИЕ ДУШ",
                "Убийство игрока луком забирает его душу:",
                "жертва теряет тело и ничего не может делать.",
                "#СИЛА ВЫСТРЕЛА",
                "Урон = 5 + число душ (максимум 5 душ).",
                "Выстрел на 5-й душе тратит 1 душу.",
                "#ДУШИ",
                "SHIFT+ЛКМ: поглотить душу (+4 сердца).",
                "/revive НИК: возродить бездушного за 5 сердец.",
                "Смерть владельца возвращает всех в норму.");
        item.setItemMeta(meta);
        return item;
    }

    void logRecipes() {
        int count = 0;
        for (String key : RECIPE_KEYS) {
            if (this.getServer().getRecipe(new NamespacedKey(this, key)) != null) {
                count++;
            } else {
                this.getLogger().warning("Рецепт НЕ зарегистрирован: " + key);
            }
        }
        this.getLogger().info("Рецептов зарегистрировано: " + count + "/" + RECIPE_KEYS.size());
    }

    void registerRecipes() {
        NamespacedKey kOrange = new NamespacedKey(this, "cb_orange");
        this.getServer().removeRecipe(kOrange);
        ShapedRecipe rOrange = new ShapedRecipe(kOrange, this.cbOrange());
        rOrange.shape("CSC", "SNS", "CSC");
        rOrange.setIngredient('C', Material.COPPER_BLOCK);
        rOrange.setIngredient('S', Material.ECHO_SHARD);
        rOrange.setIngredient('N', Material.NETHER_STAR);
        this.getServer().addRecipe(rOrange);

        NamespacedKey kPurple = new NamespacedKey(this, "cb_purple");
        this.getServer().removeRecipe(kPurple);
        ShapedRecipe rPurple = new ShapedRecipe(kPurple, this.cbPurple());
        rPurple.shape("ANA", "NHN", "ANA");
        rPurple.setIngredient('A', Material.AMETHYST_BLOCK);
        rPurple.setIngredient('N', Material.NETHERITE_INGOT);
        rPurple.setIngredient('H', Material.HEART_OF_THE_SEA);
        this.getServer().addRecipe(rPurple);

        NamespacedKey kGreen = new NamespacedKey(this, "cb_green");
        this.getServer().removeRecipe(kGreen);
        ShapedRecipe rGreen = new ShapedRecipe(kGreen, this.cbGreen());
        rGreen.shape("ESE", "STS", "ESE");
        rGreen.setIngredient('E', Material.EMERALD_BLOCK);
        rGreen.setIngredient('S', Material.PRISMARINE_SHARD);
        rGreen.setIngredient('T', Material.TOTEM_OF_UNDYING);
        this.getServer().addRecipe(rGreen);

        RecipeChoice.ExactChoice cOrange = new RecipeChoice.ExactChoice(this.cbOrange());
        RecipeChoice.ExactChoice cPurple = new RecipeChoice.ExactChoice(this.cbPurple());
        RecipeChoice.ExactChoice cGreen = new RecipeChoice.ExactChoice(this.cbGreen());

        NamespacedKey kSpear = new NamespacedKey(this, "ice_spear");
        this.getServer().removeRecipe(kSpear);
        ShapedRecipe rSpear = new ShapedRecipe(kSpear, this.iceSpear());
        rSpear.shape("IBI", "GSG", "INI");
        rSpear.setIngredient('I', Material.PACKED_ICE);
        rSpear.setIngredient('B', cOrange);
        rSpear.setIngredient('G', cGreen);
        rSpear.setIngredient('S', new RecipeChoice.MaterialChoice(Material.TRIDENT, Material.NETHERITE_SWORD));
        rSpear.setIngredient('N', Material.NETHERITE_INGOT);
        this.getServer().addRecipe(rSpear);

        NamespacedKey kRa = new NamespacedKey(this, "ra_blade");
        this.getServer().removeRecipe(kRa);
        ShapedRecipe rRa = new ShapedRecipe(kRa, this.raBlade());
        rRa.shape("ASA", "TMT", "SBS");
        rRa.setIngredient('A', Material.GOLDEN_APPLE);
        rRa.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        rRa.setIngredient('T', Material.TOTEM_OF_UNDYING);
        rRa.setIngredient('M', Material.MACE);
        rRa.setIngredient('B', Material.BLAZE_ROD);
        this.getServer().addRecipe(rRa);

        NamespacedKey kTwins = new NamespacedKey(this, "twins_sword");
        this.getServer().removeRecipe(kTwins);
        ShapedRecipe rTwins = new ShapedRecipe(kTwins, this.twinsSword());
        rTwins.shape("S S", "WNW", "RBR");
        rTwins.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        rTwins.setIngredient('W', Material.WITHER_SKELETON_SKULL);
        rTwins.setIngredient('N', Material.MUSIC_DISC_5);
        rTwins.setIngredient('R', Material.NETHER_STAR);
        rTwins.setIngredient('B', Material.END_ROD);
        this.getServer().addRecipe(rTwins);

        NamespacedKey kDeath = new NamespacedKey(this, "death_note");
        this.getServer().removeRecipe(kDeath);
        ShapedRecipe rDeath = new ShapedRecipe(kDeath, DeathNoteListener.create(this));
        rDeath.shape("SWS", "NPN", "COC");
        rDeath.setIngredient('S', Material.WITHER_SKELETON_SKULL);
        rDeath.setIngredient('W', Material.WRITABLE_BOOK);
        rDeath.setIngredient('N', Material.NETHERITE_INGOT);
        rDeath.setIngredient('P', cPurple);
        rDeath.setIngredient('C', Material.CRYING_OBSIDIAN);
        rDeath.setIngredient('O', cOrange);
        this.getServer().addRecipe(rDeath);

        NamespacedKey kScales = new NamespacedKey(this, "fate_scales");
        this.getServer().removeRecipe(kScales);
        ShapedRecipe rScales = new ShapedRecipe(kScales, ScalesOfFateListener.createScales(this));
        rScales.shape("GNG", "PSA", "GTG");
        rScales.setIngredient('G', Material.GOLD_BLOCK);
        rScales.setIngredient('N', Material.NETHER_STAR);
        rScales.setIngredient('P', cPurple);
        rScales.setIngredient('S', new RecipeChoice.MaterialChoice(Material.NETHERITE_SWORD));
        rScales.setIngredient('A', cOrange);
        rScales.setIngredient('T', Material.TOTEM_OF_UNDYING);
        this.getServer().addRecipe(rScales);
    }

    static record ThrownBoomerang(UUID owner, ItemStack item, String throwSessionId) {
        ThrownBoomerang(UUID owner, ItemStack item) {
            this(owner, item, UUID.randomUUID().toString());
        }
    }

    private static record HudCooldown(long durationMs, String name) {
    }

    static record LockState(boolean ai, boolean gravity, boolean invulnerable) {
    }

    static final class TimeSession {
        final UUID owner;
        final UUID target;
        final List<TimeFrame> frames = new ArrayList<>();
        BukkitTask recordTask;
        BukkitTask timeoutTask;
        boolean frozen = false;
        int index = 0;

        TimeSession(UUID owner, UUID target) {
            this.owner = owner;
            this.target = target;
        }
    }

    static record TimeFrame(Location location, double health) {
    }

    private class LegendaryCommand implements CommandExecutor, TabCompleter {
        private final List<String> IDS = List.of(
                "cb_orange", "cb_purple", "cb_green", "ra_blade", "twins_sword",
                "ice_spear", "ice_spear_ice", "ice_spear_spike",
                "death_note", "fate_scales", "fate_lightning", "resetcrafts"
        );

        @Override
        public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("Только для игроков.");
                return true;
            }
            if (!p.hasPermission("legendary.admin")) {
                p.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 1) {
                p.sendMessage("Использование: /legendary <id|resetcrafts>");
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("resetcrafts")) {
                FokendLegendaryWepons.this.craftedUniques.clear();
                for (Player online : FokendLegendaryWepons.this.getServer().getOnlinePlayers()) {
                    for (String rKey : RECIPE_KEYS) {
                        online.discoverRecipe(new NamespacedKey(FokendLegendaryWepons.this, rKey));
                    }
                }
                p.sendMessage(Component.text("Лимиты крафта легендарных предметов сброшены.", NamedTextColor.GREEN));
                return true;
            }

            Player targetPlayer = p;
            int amount = 1;
            if (sub.equals("give")) {
                if (args.length == 2) {
                    sub = args[1].toLowerCase(Locale.ROOT);
                } else if (args.length >= 3) {
                    Player found = FokendLegendaryWepons.this.getServer().getPlayerExact(args[1]);
                    if (found == null) {
                        p.sendMessage(Component.text("Игрок не найден.", NamedTextColor.RED));
                        return true;
                    }
                    targetPlayer = found;
                    sub = args[2].toLowerCase(Locale.ROOT);
                    if (args.length >= 4) {
                        try {
                            amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    p.sendMessage("Использование: /legendary <id|resetcrafts> или /legendary give <ник> <id>");
                    return true;
                }
            }

            ItemStack item = switch (sub) {
                case "cb_orange" -> FokendLegendaryWepons.this.cbOrange();
                case "cb_purple" -> FokendLegendaryWepons.this.cbPurple();
                case "cb_green" -> FokendLegendaryWepons.this.cbGreen();
                case "ra_blade", "ra", "ra_god_blade" -> FokendLegendaryWepons.this.raBlade();
                case "twins_sword", "twins" -> FokendLegendaryWepons.this.twinsSword();
                case "ice_spear", "spear" -> FokendLegendaryWepons.this.iceSpear();
                case "ice_spear_ice" -> FokendLegendaryWepons.this.iceSpearIce();
                case "ice_spear_spike" -> FokendLegendaryWepons.this.iceSpearSpike();
                case "death_note", "deathnote" -> DeathNoteListener.create(FokendLegendaryWepons.this);
                case "fate_scales", "scales_of_fate", "scales" -> ScalesOfFateListener.createScales(FokendLegendaryWepons.this);
                case "fate_lightning", "lightning" -> ScalesOfFateListener.createLightning(FokendLegendaryWepons.this);
                default -> null;
            };
            if (item == null) {
                p.sendMessage("Неизвестный ID. Доступно: " + String.join(", ", this.IDS));
                return true;
            }
            for (int i = 0; i < amount; i++) {
                FokendLegendaryWepons.this.giveOrDrop(targetPlayer, item.clone());
            }
            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
            List<String> out = new ArrayList<>();
            if (!sender.hasPermission("legendary.admin")) {
                return out;
            }
            if (args.length == 1) {
                String prefix = args[0].toLowerCase(Locale.ROOT);
                for (String id : this.IDS) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(id);
                    }
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                for (Player online : FokendLegendaryWepons.this.getServer().getOnlinePlayers()) {
                    if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(online.getName());
                    }
                }
            } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                for (String id : this.IDS) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        out.add(id);
                    }
                }
            }
            return out;
        }
    }
}

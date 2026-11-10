package fokend.lit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Integration-test harness for FokendLegendaryWepons. Load it ONLY on a disposable test server.
 * Every check prints a machine-readable line: "LIT RESULT <name> PASS|FAIL <details>".
 * The harness never clears a player's inventory and never replaces an occupied slot.
 */
public final class LitPlugin extends JavaPlugin {
    private static final String NS = "fokendlegendarywepons";

    private int axeSlot = -1;
    private ItemStack axeOriginal;
    private final Map<Integer, ItemStack> inventoryBefore = new HashMap<>();
    private final Map<String, Location> marks = new HashMap<>();
    private boolean helmetPlacedByHarness;
    private int failures;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("/lit <step> <player> [arg]");
            return true;
        }
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) {
            this.result(sender, "player", false, "player " + args[1] + " is offline");
            return true;
        }
        String arg = args.length > 2 ? args[2] : "";
        switch (args[0]) {
            case "prepare" -> this.prepare(sender, p);
            case "axe-create" -> this.axeCreate(sender, p);
            case "axe-verify" -> this.axeVerify(sender, p, arg);
            case "area" -> this.area(sender, p);
            case "area-verify" -> this.areaVerify(sender, p, arg);
            case "warden" -> this.warden(sender, p, arg);
            case "anvil" -> this.anvil(sender, p);
            case "grindstone" -> this.grindstone(sender, p);
            case "clean" -> this.clean(sender, p);
            case "summary" -> {
                this.result(sender, "summary", this.failures == 0, "failures=" + this.failures);
            }
            default -> sender.sendMessage("unknown step " + args[0]);
        }
        return true;
    }

    private void result(CommandSender sender, String name, boolean pass, String details) {
        if (!pass) {
            this.failures++;
        }
        String line = "LIT RESULT " + name + " " + (pass ? "PASS" : "FAIL") + " " + details;
        this.getLogger().info(line);
        if (sender instanceof Player) {
            sender.sendMessage(line);
        }
    }

    private Plugin target() {
        return Bukkit.getPluginManager().getPlugin("FokendLegendaryWepons");
    }

    private ItemStack legendary(String factory) {
        try {
            Plugin plugin = this.target();
            Method m = plugin.getClass().getDeclaredMethod(factory);
            m.setAccessible(true);
            return (ItemStack) m.invoke(plugin);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("cannot create " + factory, ex);
        }
    }

    private static List<NamespacedKey> pluginKeys(ItemStack item) {
        List<NamespacedKey> out = new ArrayList<>();
        if (item != null && item.hasItemMeta()) {
            for (NamespacedKey key : item.getItemMeta().getPersistentDataContainer().getKeys()) {
                if (NS.equals(key.getNamespace())) {
                    out.add(key);
                }
            }
        }
        return out;
    }

    private void prepare(CommandSender sender, Player p) {
        World world = p.getWorld();
        Location base = new Location(world, 0.5D, world.getHighestBlockYAt(0, 0) + 1, 0.5D, -90.0F, 0.0F);
        p.teleport(base);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());
        p.setFoodLevel(20);
        world.setTime(6000L);
        world.setStorm(false);
        this.result(sender, "prepare", true, "at " + fmt(base));
    }

    private void axeCreate(CommandSender sender, Player p) {
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            this.result(sender, "axe-create", false, "no empty hotbar slot; harness never replaces items");
            return;
        }
        this.inventoryBefore.clear();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && !it.getType().isAir()) {
                this.inventoryBefore.put(i, it.clone());
            }
        }
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
        meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        axe.setItemMeta(meta);
        inv.setItem(slot, axe);
        inv.setHeldItemSlot(slot);
        this.axeSlot = slot;
        this.axeOriginal = axe.clone();

        boolean noKeys = pluginKeys(inv.getItem(slot)).isEmpty();
        boolean same = axe.equals(inv.getItem(slot));
        this.result(sender, "axe-create", noKeys && same,
                "slot=" + slot + " enchants=" + axe.getEnchantments() + " pluginKeys=" + pluginKeys(inv.getItem(slot)));
        // The plugin runs inventory-scanning tasks every 2-10 ticks; confirm none of them touch a normal axe.
        final int s = slot;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            ItemStack now = p.getInventory().getItem(s);
            this.result(sender, "axe-untouched-by-tasks", this.axeOriginal.equals(now),
                    "after 40 ticks item=" + describe(now));
        }, 40L);
    }

    private void area(CommandSender sender, Player p) {
        World world = p.getWorld();
        Location base = p.getLocation().getBlock().getLocation();
        Location logA = base.clone().add(2, 0, 0);
        Location logB = base.clone().add(2, 0, 2);
        Location logC = base.clone().add(2, 0, -2);
        for (Location l : List.of(logA, logB, logC)) {
            Block b = l.getBlock();
            if (!b.getType().isAir()) {
                this.result(sender, "area", false, "block at " + fmt(l) + " is " + b.getType() + ", refusing to overwrite");
                return;
            }
        }
        logA.getBlock().setType(Material.OAK_LOG);
        logB.getBlock().setType(Material.OAK_LOG);
        logC.getBlock().setType(Material.OAK_LOG);
        this.marks.put("logA", logA);
        this.marks.put("logB", logB);
        this.marks.put("logC", logC);
        Cow cow = world.spawn(base.clone().add(-2.5, 0, 0.5), Cow.class, c -> {
            c.setAI(false);
            c.setPersistent(false);
        });
        this.marks.put("cow", cow.getLocation());
        this.getLogger().info("LIT AREA cow=" + cow.getUniqueId());
        this.result(sender, "area", true,
                "logA=" + fmt(logA) + " logB=" + fmt(logB) + " logC=" + fmt(logC) + " cow=" + fmt(cow.getLocation()));
    }

    private void areaVerify(CommandSender sender, Player p, String what) {
        switch (what) {
            case "dig" -> {
                Material m = this.marks.get("logA").getBlock().getType();
                this.result(sender, "axe-dig-log", m.isAir(), "logA is now " + m);
            }
            case "strip" -> {
                Material m = this.marks.get("logB").getBlock().getType();
                this.result(sender, "axe-strip-log", m == Material.STRIPPED_OAK_LOG, "logB is now " + m);
            }
            case "strip-warden" -> {
                Material m = this.marks.get("logC").getBlock().getType();
                this.result(sender, "axe-strip-log-with-warden-helmet", m == Material.STRIPPED_OAK_LOG, "logC is now " + m);
            }
            case "cow" -> {
                long alive = p.getWorld().getEntitiesByClass(Cow.class).stream()
                        .filter(c -> c.getLocation().distanceSquared(this.marks.get("cow")) < 9.0D && !c.isDead())
                        .count();
                this.result(sender, "axe-kill-cow", alive == 0, "cows alive near mark=" + alive);
            }
            default -> sender.sendMessage("dig|strip|strip-warden|cow");
        }
    }

    private void warden(CommandSender sender, Player p, String mode) {
        PlayerInventory inv = p.getInventory();
        if ("on".equals(mode)) {
            ItemStack helmet = inv.getHelmet();
            if (helmet != null && !helmet.getType().isAir()) {
                this.result(sender, "warden-on", false, "helmet slot occupied by " + helmet.getType());
                return;
            }
            inv.setHelmet(this.legendary("wardenHelmet"));
            this.helmetPlacedByHarness = true;
            this.result(sender, "warden-on", true, "helmet=" + describe(inv.getHelmet()));
        } else {
            ItemStack helmet = inv.getHelmet();
            boolean isWarden = helmet != null && "warden_helmet".equals(helmet.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(NS, "weapon_id"), org.bukkit.persistence.PersistentDataType.STRING));
            if (this.helmetPlacedByHarness && isWarden) {
                inv.setHelmet(null);
                this.helmetPlacedByHarness = false;
            }
            Bukkit.getScheduler().runTaskLater(this, () -> {
                boolean blind = p.hasPotionEffect(PotionEffectType.BLINDNESS);
                this.result(sender, "warden-off-cleans-blindness", !blind, "blindness=" + blind);
            }, 10L);
        }
    }

    private void anvil(CommandSender sender, Player p) {
        if (this.axeOriginal == null) {
            this.result(sender, "anvil", false, "run axe-create first");
            return;
        }
        InventoryView view = p.openAnvil(null, true);
        if (!(view instanceof AnvilView anvil)) {
            this.result(sender, "anvil", false, "openAnvil returned " + view);
            return;
        }
        ItemStack a = this.axeOriginal.clone();
        ItemStack b = this.axeOriginal.clone();
        Damageable dm = (Damageable) a.getItemMeta();
        dm.setDamage(500);
        a.setItemMeta(dm);
        anvil.getTopInventory().setFirstItem(a);
        anvil.getTopInventory().setSecondItem(b);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            ItemStack res = anvil.getTopInventory().getResult();
            boolean ok = res != null && res.getType() == Material.DIAMOND_AXE && pluginKeys(res).isEmpty();
            this.result(sender, "anvil-normal-repair-allowed", ok, "result=" + describe(res));

            ItemStack core = this.legendary("cbGreen");
            anvil.getTopInventory().setSecondItem(core);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                ItemStack preview = anvil.getTopInventory().getResult();
                boolean previewOk = preview != null && preview.getType() == Material.DIAMOND_AXE;
                this.result(sender, "anvil-core-preview-for-axe", previewOk, "preview=" + describe(preview));
                // Empty the anvil before closing so nothing is returned into the test player's inventory.
                anvil.getTopInventory().setFirstItem(null);
                anvil.getTopInventory().setSecondItem(null);
                anvil.getTopInventory().setResult(null);
                p.closeInventory();
            }, 2L);
        }, 2L);
    }

    private void grindstone(CommandSender sender, Player p) {
        if (this.axeOriginal == null) {
            this.result(sender, "grindstone", false, "run axe-create first");
            return;
        }
        InventoryView view = p.openGrindstone(null, true);
        if (view == null) {
            this.result(sender, "grindstone", false, "openGrindstone returned null");
            return;
        }
        org.bukkit.inventory.GrindstoneInventory inv = (org.bukkit.inventory.GrindstoneInventory) view.getTopInventory();
        inv.setUpperItem(this.axeOriginal.clone());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            ItemStack res = inv.getResult();
            boolean ok = res != null && res.getType() == Material.DIAMOND_AXE && res.getEnchantments().isEmpty();
            this.result(sender, "grindstone-normal-allowed", ok, "result=" + describe(res));
            inv.setUpperItem(null);
            inv.setLowerItem(null);
            p.closeInventory();
        }, 2L);
    }

    private void axeVerify(CommandSender sender, Player p, String phase) {
        String name = "axe-preserved-" + (phase.isEmpty() ? "now" : phase);
        if (this.axeSlot < 0 || this.axeOriginal == null) {
            // After a server restart the harness forgets the slot: locate the axe by its exact enchantments.
            this.relocateAxe(p);
        }
        if (this.axeSlot < 0) {
            this.result(sender, name, false, "no axe recorded/found");
            return;
        }
        ItemStack now = p.getInventory().getItem(this.axeSlot);
        List<String> problems = new ArrayList<>();
        if (now == null || now.getType() != Material.DIAMOND_AXE) {
            problems.add("slot " + this.axeSlot + " holds " + describe(now));
        } else {
            if (!now.getEnchantments().equals(this.axeOriginal.getEnchantments())) {
                problems.add("enchantments changed " + now.getEnchantments());
            }
            if (!pluginKeys(now).isEmpty()) {
                problems.add("plugin PDC keys added " + pluginKeys(now));
            }
            ItemMeta a = now.getItemMeta();
            ItemMeta b = this.axeOriginal.getItemMeta();
            if (!Objects.equals(a.displayName(), b.displayName()) || !Objects.equals(a.lore(), b.lore())) {
                problems.add("name/lore changed");
            }
            if (a.hasCustomModelData() || a.hasAttributeModifiers() || a.isUnbreakable()) {
                problems.add("unexpected meta (cmd/attributes/unbreakable)");
            }
            ItemStack normalized = now.clone();
            Damageable dn = (Damageable) normalized.getItemMeta();
            dn.setDamage(((Damageable) this.axeOriginal.getItemMeta()).getDamage());
            normalized.setItemMeta(dn);
            if (!normalized.equals(this.axeOriginal)) {
                problems.add("item differs beyond vanilla durability: " + describe(now));
            }
        }
        // Other items that were in the inventory before the test must still be there unchanged.
        for (Map.Entry<Integer, ItemStack> e : this.inventoryBefore.entrySet()) {
            if (!e.getValue().equals(p.getInventory().getItem(e.getKey()))) {
                problems.add("pre-existing slot " + e.getKey() + " changed");
            }
        }
        int axes = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == Material.DIAMOND_AXE) {
                axes++;
            }
        }
        if (axes != 1) {
            problems.add("expected exactly 1 diamond axe in inventory, found " + axes);
        }
        int damage = now != null && now.getItemMeta() instanceof Damageable d ? d.getDamage() : -1;
        this.result(sender, name, problems.isEmpty(),
                "slot=" + this.axeSlot + " damage=" + damage + "/" + Material.DIAMOND_AXE.getMaxDurability()
                        + (problems.isEmpty() ? "" : " problems=" + problems));
    }

    private void relocateAxe(Player p) {
        ItemStack ref = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = ref.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
        meta.addEnchant(Enchantment.UNBREAKING, 2, true);
        ref.setItemMeta(meta);
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it != null && it.getType() == Material.DIAMOND_AXE && it.getEnchantments().equals(ref.getEnchantments())) {
                this.axeSlot = i;
                this.axeOriginal = ref;
                return;
            }
        }
    }

    private void clean(CommandSender sender, Player p) {
        List<String> problems = new ArrayList<>();
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        for (AttributeInstance inst : new AttributeInstance[]{max, speed}) {
            if (inst == null) continue;
            for (AttributeModifier mod : inst.getModifiers()) {
                if (NS.equals(mod.getKey().getNamespace())) {
                    problems.add("modifier " + mod.getKey());
                }
            }
        }
        for (PotionEffect eff : p.getActivePotionEffects()) {
            problems.add("effect " + eff.getType().getKey().getKey());
        }
        if (!p.hasGravity()) problems.add("gravity disabled");
        if (p.getGameMode() != GameMode.SURVIVAL) problems.add("gamemode " + p.getGameMode());
        if (p.isInvulnerable()) problems.add("invulnerable");
        int leftovers = 0;
        for (Entity e : p.getWorld().getEntities()) {
            switch (e.getType()) {
                case ITEM_DISPLAY, TEXT_DISPLAY, ARMOR_STAND, MANNEQUIN -> leftovers++;
                default -> { }
            }
        }
        if (leftovers > 0) problems.add("leftover display/stand entities=" + leftovers);
        this.result(sender, "player-has-no-side-effects", problems.isEmpty(), problems.toString());
    }

    private static String describe(ItemStack it) {
        if (it == null) return "null";
        String dmg = it.getItemMeta() instanceof Damageable d ? " dmg=" + d.getDamage() : "";
        return it.getType() + "x" + it.getAmount() + dmg + " ench=" + it.getEnchantments();
    }

    private static String fmt(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
}

package fokend.legendaryweapons;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Feeds sightings of chronicled legendaries to the registry and removes the copies it rejects.
 *
 * <p>Items are seen when they matter (join, quit, death, drop, pickup, container open, hopper moves, item
 * entities spawning or loading) and, as a safety net for code that moves items without events (for example
 * AxGraves' sneak-click pickup, which calls {@code addItem} directly), every online player is scanned on a
 * short interval. A scan collects every copy first and only then judges, so two copies of one uid seen in
 * the same scan are caught even when neither is at the recorded place.
 */
final class ChronicleTracker implements Listener {
    interface Slot {
        ItemStack get();

        void set(ItemStack item);
    }

    record Found(Slot slot, ItemStack item, LegendRegistry.Sighting sighting) {
    }

    private final FokendLegendaryWepons plugin;
    private final LegendRegistry registry;
    private final ActivityTracker activity;
    private final Set<UUID> dirty = new HashSet<>();
    private final List<Inventory> dirtyInventories = new ArrayList<>();
    private boolean flushScheduled;

    ChronicleTracker(FokendLegendaryWepons plugin, LegendRegistry registry, ActivityTracker activity) {
        this.plugin = plugin;
        this.registry = registry;
        this.activity = activity;
    }

    // ------------------------------------------------------------------ collecting

    static Slot inventorySlot(Inventory inv, int index) {
        return new Slot() {
            @Override
            public ItemStack get() {
                return inv.getItem(index);
            }

            @Override
            public void set(ItemStack item) {
                inv.setItem(index, item);
            }
        };
    }

    static Slot cursorSlot(HumanEntity p) {
        return new Slot() {
            @Override
            public ItemStack get() {
                return p.getItemOnCursor();
            }

            @Override
            public void set(ItemStack item) {
                p.setItemOnCursor(item);
            }
        };
    }

    static Slot entitySlot(Item entity) {
        return new Slot() {
            @Override
            public ItemStack get() {
                return entity.getItemStack();
            }

            @Override
            public void set(ItemStack item) {
                if (item == null || item.getType().isAir()) {
                    entity.remove();
                } else {
                    entity.setItemStack(item);
                }
            }
        };
    }

    private static Slot shulkerSlot(Slot outer, int index) {
        return new Slot() {
            @Override
            public ItemStack get() {
                ItemStack o = outer.get();
                if (o != null && o.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
                    return box.getInventory().getItem(index);
                }
                return null;
            }

            @Override
            public void set(ItemStack item) {
                ItemStack o = outer.get();
                if (o != null && o.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
                    box.getInventory().setItem(index, item);
                    bsm.setBlockState(box);
                    o.setItemMeta(bsm);
                    outer.set(o);
                }
            }
        };
    }

    private static Slot bundleSlot(Slot outer, int index) {
        return new Slot() {
            @Override
            public ItemStack get() {
                ItemStack o = outer.get();
                if (o != null && o.getItemMeta() instanceof BundleMeta bm && index < bm.getItems().size()) {
                    return bm.getItems().get(index);
                }
                return null;
            }

            @Override
            public void set(ItemStack item) {
                ItemStack o = outer.get();
                if (o != null && o.getItemMeta() instanceof BundleMeta bm && index < bm.getItems().size()) {
                    List<ItemStack> items = new ArrayList<>(bm.getItems());
                    if (item == null || item.getType().isAir()) {
                        items.remove(index);
                    } else {
                        items.set(index, item);
                    }
                    bm.setItems(items);
                    o.setItemMeta(bm);
                    outer.set(o);
                }
            }
        };
    }

    void collect(ItemStack item, Slot slot, LegendRegistry.Sighting s, List<Found> out, int depth) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (this.registry.isChronicled(item)) {
            out.add(new Found(slot, item, s));
            return;
        }
        if (depth > 0 || !item.hasItemMeta()) {
            return;
        }
        // Legendaries cannot be put into these any more, but older ones may already be inside.
        try {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
                ItemStack[] inner = box.getInventory().getContents();
                for (int i = 0; i < inner.length; i++) {
                    this.collect(inner[i], shulkerSlot(slot, i), s, out, depth + 1);
                }
            } else if (meta instanceof BundleMeta bm && bm.hasItems()) {
                List<ItemStack> inner = bm.getItems();
                for (int i = 0; i < inner.size(); i++) {
                    this.collect(inner.get(i), bundleSlot(slot, i), s, out, depth + 1);
                }
            }
        } catch (RuntimeException e) {
            this.plugin.warnOnce("chronicle nested scan", e);
        }
    }

    void collect(Inventory inv, LegendRegistry.Sighting s, List<Found> out) {
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            this.collect(contents[i], inventorySlot(inv, i), s, out, 0);
        }
    }

    /** Holder without forcing a block-state snapshot where the server supports that. */
    static InventoryHolder holder(Inventory inv) {
        try {
            return inv.getHolder(false);
        } catch (RuntimeException | AbstractMethodError e) {
            try {
                return inv.getHolder();
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    /** Sighting for a non-player inventory a player (or nobody) is looking at. */
    LegendRegistry.Sighting containerSighting(Inventory inv, Player viewer) {
        InventoryHolder h = holder(inv);
        if (h instanceof Player owner) {
            // Another player's inventory opened by an inventory-viewer plugin: it is that player's.
            return new LegendRegistry.Sighting(LegendRecord.Holder.PLAYER, owner.getUniqueId(), null, owner.getLocation());
        }
        Location l = null;
        try {
            l = inv.getLocation();
        } catch (RuntimeException ignored) {
        }
        if (h instanceof Entity e) {
            l = e.getLocation();
        }
        if (h != null && l != null) {
            return new LegendRegistry.Sighting(LegendRecord.Holder.CONTAINER, viewer == null ? null : viewer.getUniqueId(), null, l);
        }
        // No world holder: a plugin GUI such as an AxGraves grave. Located at whoever is looking at it.
        UUID v = viewer == null ? null : viewer.getUniqueId();
        return new LegendRegistry.Sighting(LegendRecord.Holder.VIRTUAL, v, null,
                viewer != null ? viewer.getLocation() : l);
    }

    void collectPlayer(Player p, List<Found> out, List<Inventory> seenTops) {
        Location loc = p.getLocation();
        LegendRegistry.Sighting own = new LegendRegistry.Sighting(LegendRecord.Holder.PLAYER, p.getUniqueId(), null, loc);
        this.collect(p.getInventory(), own, out);
        this.collect(p.getItemOnCursor(), cursorSlot(p), own, out, 0);
        InventoryView view = p.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        if (top == null) {
            // nothing open beyond the player's own inventory
        } else if (top.getType() == InventoryType.CRAFTING) {
            this.collect(top, own, out);
        } else if (top.getType() != InventoryType.ENDER_CHEST && top.getType() != InventoryType.PLAYER
                && !(holder(top) instanceof LegendsMenu)) {
            boolean already = false;
            for (Inventory t : seenTops) {
                if (t.equals(top)) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                seenTops.add(top);
                this.collect(top, this.containerSighting(top, p), out);
            }
        }
        this.collect(p.getEnderChest(), new LegendRegistry.Sighting(LegendRecord.Holder.ENDER_CHEST, p.getUniqueId(), null, loc), out);
    }

    // ------------------------------------------------------------------ judging

    /** Judges everything found in one scan: duplicates within the scan first, then each remaining copy. */
    void process(List<Found> found) {
        Map<UUID, List<Found>> byUid = new LinkedHashMap<>();
        List<Found> unstamped = new ArrayList<>();
        for (Found f : found) {
            UUID uid = this.registry.stamp.uid(f.item());
            if (uid == null) {
                unstamped.add(f);
            } else {
                byUid.computeIfAbsent(uid, k -> new ArrayList<>()).add(f);
            }
        }
        for (Map.Entry<UUID, List<Found>> e : byUid.entrySet()) {
            LegendRecord rec = this.registry.byUid.get(e.getKey());
            // Copies that are invalid on their own (stale, retired…) are judged for that reason; only copies
            // that would each be valid are duplicates of one another.
            List<Found> current = new ArrayList<>();
            for (Found f : e.getValue()) {
                if (rec == null || (rec.live() && rec.gen == this.registry.stamp.gen(f.item()))) {
                    current.add(f);
                } else {
                    this.judge(f);
                }
            }
            if (current.isEmpty()) {
                continue;
            }
            Found keeper = current.get(0);
            if (current.size() > 1) {
                keeper = this.pickKeeper(e.getKey(), current);
                for (Found f : current) {
                    if (f != keeper) {
                        this.registry.quarantineDuplicate(f.item(), f.sighting());
                        f.slot().set(null);
                    }
                }
            }
            this.judge(keeper);
        }
        // Unstamped ones one by one: the first may be adopted, the rest then are copies of it.
        for (Found f : unstamped) {
            this.judge(f);
        }
    }

    private Found pickKeeper(UUID uid, List<Found> copies) {
        LegendRecord rec = this.registry.byUid.get(uid);
        if (rec != null) {
            for (Found f : copies) {
                if (f.sighting().personal() && f.sighting().player().equals(rec.owner)) {
                    return f;
                }
            }
            String key = rec.placeKey();
            for (Found f : copies) {
                if (f.sighting().key().equals(key)) {
                    return f;
                }
            }
        }
        return copies.get(0);
    }

    private void judge(Found f) {
        LegendRegistry.Verdict v = this.registry.observe(f.item(), f.sighting());
        if (v == LegendRegistry.Verdict.REWRITE) {
            f.slot().set(f.item());
        } else if (v == LegendRegistry.Verdict.QUARANTINE) {
            f.slot().set(null);
        }
    }

    void scanPlayer(Player p) {
        if (!p.isOnline()) {
            return;
        }
        List<Found> found = new ArrayList<>();
        this.collectPlayer(p, found, new ArrayList<>());
        this.process(found);
    }

    void scanInventory(Inventory inv, Player viewer) {
        List<Found> found = new ArrayList<>();
        this.collect(inv, this.containerSighting(inv, viewer), found);
        this.process(found);
    }

    /** The periodic safety net: all online players (inventory, cursor, open container, ender chest) in one pass. */
    void scanAll() {
        List<Found> found = new ArrayList<>();
        List<Inventory> seenTops = new ArrayList<>();
        for (Player p : this.plugin.getServer().getOnlinePlayers()) {
            this.collectPlayer(p, found, seenTops);
        }
        this.process(found);
    }

    void markDirty(Player p) {
        this.dirty.add(p.getUniqueId());
        this.scheduleFlush();
    }

    private void markDirty(Inventory inv) {
        this.dirtyInventories.add(inv);
        this.scheduleFlush();
    }

    private void scheduleFlush() {
        if (!this.flushScheduled && this.plugin.isEnabled()) {
            this.flushScheduled = true;
            this.plugin.getServer().getScheduler().runTask(this.plugin, this::flushDirty);
        }
    }

    /** Runs one tick after inventory activity, when the server has finished moving the items. */
    void flushDirty() {
        this.flushScheduled = false;
        List<Found> found = new ArrayList<>();
        List<Inventory> seenTops = new ArrayList<>();
        for (UUID id : new ArrayList<>(this.dirty)) {
            Player p = this.plugin.getServer().getPlayer(id);
            if (p != null) {
                this.collectPlayer(p, found, seenTops);
            }
        }
        this.dirty.clear();
        for (Inventory inv : new ArrayList<>(this.dirtyInventories)) {
            boolean already = false;
            for (Inventory t : seenTops) {
                if (t.equals(inv)) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                seenTops.add(inv);
                this.collect(inv, this.containerSighting(inv, null), found);
            }
        }
        this.dirtyInventories.clear();
        this.process(found);
    }

    private void observeEntity(Item entity) {
        ItemStack stack = entity.getItemStack();
        if (!this.registry.isChronicled(stack)) {
            return;
        }
        List<Found> found = new ArrayList<>();
        found.add(new Found(entitySlot(entity), stack,
                new LegendRegistry.Sighting(LegendRecord.Holder.GROUND, null, entity.getUniqueId(), entity.getLocation())));
        this.process(found);
    }

    // ------------------------------------------------------------------ lifecycle events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        this.registry.names.put(p.getUniqueId(), p.getName());
        String ip = p.getAddress() != null && p.getAddress().getAddress() != null
                ? p.getAddress().getAddress().getHostAddress() : null;
        if (ip != null) {
            this.registry.ips.put(p.getUniqueId(), ip);
        }
        this.registry.db.savePlayer(p.getUniqueId().toString(), p.getName(), ip, this.registry.now());
        this.activity.join(p.getUniqueId());
        // After the legacy id migration (LOWEST) and every other join handler.
        this.markDirty(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        // Records the logout spot: Legend Call releases items held offline here.
        this.scanPlayer(e.getPlayer());
        this.dirty.remove(e.getPlayer().getUniqueId());
        this.activity.quit(e.getPlayer().getUniqueId());
    }

    /**
     * LOWEST, so it runs before any grave plugin (AxGraves takes the drops at MONITOR by default, or at whatever
     * priority it is configured to) and reads the victim's own inventory, which is still intact at this point.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Player killer = p.getKiller();
        List<ItemStack> carried = new ArrayList<>();
        for (ItemStack it : p.getInventory().getContents()) {
            carried.add(it);
        }
        carried.add(p.getItemOnCursor());
        Inventory top = p.getOpenInventory() == null ? null : p.getOpenInventory().getTopInventory();
        if (top != null && top.getType() == InventoryType.CRAFTING) {
            for (ItemStack it : top.getContents()) {
                carried.add(it);
            }
        }
        for (ItemStack it : carried) {
            if (this.registry.isChronicled(it)) {
                this.registry.markDeath(it, p, killer);
            }
        }
        // keepInventory without a grave plugin: the items stay, and the next scan gives them back to the owner.
        this.markDirty(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        this.observeEntity(e.getItemDrop());
        this.markDirty(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        this.observeEntity(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (entity instanceof Item item) {
                this.observeEntity(item);
            }
        }
    }

    /** Mobs (zombies, foxes, allays…) must not carry a legendary off and despawn with it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player) && this.registry.isChronicled(e.getItem().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && this.registry.isChronicled(e.getItem().getItemStack())) {
            this.markDirty(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (this.registry.isChronicled(e.getItem().getItemStack())) {
            this.markDirty(e.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) {
            return;
        }
        Inventory top = e.getInventory();
        if (holder(top) instanceof LegendsMenu) {
            return;
        }
        if (top.getType() == InventoryType.ENDER_CHEST) {
            List<Found> found = new ArrayList<>();
            this.collect(p.getEnderChest(), new LegendRegistry.Sighting(LegendRecord.Holder.ENDER_CHEST,
                    p.getUniqueId(), null, p.getLocation()), found);
            this.process(found);
            return;
        }
        if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER) {
            this.scanInventory(top, p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            this.markDirty(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            this.markDirty(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.markDirty(p);
            Inventory top = e.getInventory();
            if (top.getType() != InventoryType.CRAFTING && top.getType() != InventoryType.PLAYER
                    && top.getType() != InventoryType.ENDER_CHEST && !(holder(top) instanceof LegendsMenu)) {
                this.markDirty(top);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent e) {
        if (this.registry.isChronicled(e.getItem())) {
            this.markDirty(e.getDestination());
        }
    }

    // ------------------------------------------------------------------ storage restrictions

    static boolean isBundle(ItemStack item) {
        return item != null && item.getType().name().endsWith("BUNDLE");
    }

    static boolean isShulker(ItemStack item) {
        return item != null && Tag.SHULKER_BOXES.isTagged(item.getType());
    }

    private boolean chronicled(ItemStack item) {
        return item != null && item.getType() != Material.AIR && this.registry.isChronicled(item);
    }

    private void deny(HumanEntity who) {
        who.sendMessage(Component.text("Легендарки нельзя класть в шалкеры и мешки: Летопись должна их видеть.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRestrictClick(InventoryClickEvent e) {
        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();
        if ((isBundle(cursor) && this.chronicled(current)) || (this.chronicled(cursor) && isBundle(current))) {
            e.setCancelled(true);
            this.deny(e.getWhoClicked());
            return;
        }
        Inventory top = e.getView().getTopInventory();
        if (top.getType() != InventoryType.SHULKER_BOX) {
            return;
        }
        Inventory clicked = e.getClickedInventory();
        boolean inTop = clicked != null && clicked.equals(top);
        boolean blocked = false;
        if (inTop && this.chronicled(cursor)) {
            blocked = true;
        } else if (!inTop && e.isShiftClick() && this.chronicled(current)) {
            blocked = true;
        } else if (inTop && e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                && this.chronicled(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()))) {
            blocked = true;
        } else if (inTop && e.getClick() == ClickType.SWAP_OFFHAND
                && this.chronicled(e.getWhoClicked().getInventory().getItemInOffHand())) {
            blocked = true;
        }
        if (blocked) {
            e.setCancelled(true);
            this.deny(e.getWhoClicked());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRestrictDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getType() != InventoryType.SHULKER_BOX || !this.chronicled(e.getOldCursor())) {
            return;
        }
        for (int raw : e.getRawSlots()) {
            if (raw < top.getSize()) {
                e.setCancelled(true);
                this.deny(e.getWhoClicked());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRestrictHopper(InventoryMoveItemEvent e) {
        if (e.getDestination().getType() == InventoryType.SHULKER_BOX && this.chronicled(e.getItem())) {
            e.setCancelled(true);
        }
    }
}

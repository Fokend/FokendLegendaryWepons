package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Storage restrictions, grave-plugin flow, database threading. */
class ChronicleSafetyTest extends ChronicleTestBase {

    @Test
    void cannotPutALegendaryIntoAShulkerBox() {
        PlayerMock a = this.player("Alice");
        Inventory shulker = this.server.createInventory(null, InventoryType.SHULKER_BOX);
        InventoryView view = a.openInventory(shulker);
        a.setItemOnCursor(this.blade());
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL);
        this.server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void cannotPutALegendaryIntoABundle() {
        PlayerMock a = this.player("Alice");
        Inventory chest = this.server.createInventory(null, InventoryType.CHEST);
        chest.setItem(0, new ItemStack(Material.BUNDLE));
        InventoryView view = a.openInventory(chest);
        a.setItemOnCursor(this.blade());
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.RIGHT, InventoryAction.SWAP_WITH_CURSOR);
        this.server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void ordinaryItemsIntoShulkersStillWork() {
        PlayerMock a = this.player("Alice");
        Inventory shulker = this.server.createInventory(null, InventoryType.SHULKER_BOX);
        InventoryView view = a.openInventory(shulker);
        a.setItemOnCursor(new ItemStack(Material.DIAMOND_SWORD));
        InventoryClickEvent e = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PLACE_ALL);
        this.server.getPluginManager().callEvent(e);
        assertFalse(e.isCancelled());
    }

    @Test
    void realDeathEventMarksTheItemAndKeepInventoryKeepsTheOwner() throws Exception {
        PlayerMock a = this.player("Alice");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        a.setHealth(0); // fires PlayerDeathEvent through every listener, the chronicle's at LOWEST
        assertEquals(LegendRecord.Hint.DEATH, this.ra().hint);
        // MockBukkit keeps the inventory (like keepInventory with no grave plugin): next tick the owner keeps it.
        if (this.countLegend(a) == 1) {
            this.settle();
            assertEquals(a.getUniqueId(), this.ra().owner);
            assertEquals(null, this.ra().hint);
            assertEquals(1, this.history(FokendLegendaryWepons.ID_RA_BLADE).size(), "no transfer for keeping it");
        }
    }

    @Test
    void graveGuiIsNotOwnershipAndTakingFromItIs() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        ItemStack it = a.getInventory().getItem(0);
        a.getInventory().setItem(0, null);
        this.registry().markDeath(it, a, b);
        // AxGraves: a holder-less GUI inventory with the drops; Bob opens it.
        Inventory grave = this.server.createInventory(null, 27);
        grave.setItem(0, it);
        b.openInventory(grave);
        this.chronicle().tracker.scanInventory(grave, b);
        this.settle();
        assertEquals(a.getUniqueId(), this.ra().owner, "looking into a grave changes nothing");
        assertEquals(LegendRecord.Hint.DEATH, this.ra().hint);
        // Bob clicks it out of the grave.
        grave.setItem(0, null);
        b.getInventory().addItem(it);
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        assertEquals(b.getUniqueId(), this.ra().owner);
        assertEquals("KILL", this.history(FokendLegendaryWepons.ID_RA_BLADE).get(0).reason());
    }

    @Test
    void graveGuiOpenByTwoPlayersIsNotADuplicate() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        PlayerMock c = this.player("Carol");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        ItemStack it = a.getInventory().getItem(0);
        a.getInventory().setItem(0, null);
        this.registry().markDeath(it, a, b);
        Inventory grave = this.server.createInventory(null, 27);
        grave.setItem(0, it);
        b.openInventory(grave);
        c.openInventory(grave);
        this.chronicle().tracker.scanAll();
        this.settle();
        assertTrue(this.quarantine().isEmpty(), "one inventory seen by two viewers is one copy");
        assertEquals(it.getType(), grave.getItem(0).getType());
    }

    @Test
    void mobsCannotPickUpLegendaries() {
        this.withoutDropProtection();
        PlayerMock a = this.player("Alice");
        var world = a.getWorld();
        var zombie = world.spawn(a.getLocation(), org.bukkit.entity.Zombie.class);
        var item = world.dropItem(a.getLocation(), this.blade());
        var e = new org.bukkit.event.entity.EntityPickupItemEvent(zombie, item, 0);
        this.server.getPluginManager().callEvent(e);
        assertTrue(e.isCancelled());
    }

    @Test
    void manyConcurrentWritesAreAllStoredInOrder() throws Exception {
        PlayerMock a = this.player("Alice");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        this.settle();
        LegendRecord rec = this.ra();
        ChronicleDatabase db = this.chronicle().db;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<Void>> writes = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                go.await();
                for (int i = 0; i < 125; i++) {
                    writes.add(db.commit(List.of(), List.of(new ChronicleDatabase.HistoryRow(0, rec.uid.toString(), rec.type,
                            null, UUID.randomUUID().toString(), "OTHER", this.now, "world", 0, 0, 0, null))));
                    writes.add(db.saveLegend(rec.toRow()));
                }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        CompletableFuture.allOf(writes.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
        assertEquals(1001, db.historyCount(rec.type).get(10, TimeUnit.SECONDS), "1 adoption + 1000 concurrent rows");
    }

    @Test
    void theServerThreadNeverWaitsForTheDatabase() throws Exception {
        PlayerMock a = this.player("Alice");
        ChronicleDatabase db = this.chronicle().db;
        CountDownLatch release = new CountDownLatch(1);
        // Block the database thread completely.
        db.submit(c -> {
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        long t0 = System.nanoTime();
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a); // adopts, stamps, queues writes
        this.chronicle().tracker.scanAll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(ms < 1000, "gameplay path returned in " + ms + " ms while the DB was blocked");
        assertEquals(a.getUniqueId(), this.ra().owner, "state is already correct in memory");
        release.countDown();
        this.settle();
        assertEquals(1, this.history(FokendLegendaryWepons.ID_RA_BLADE).size());
    }

    @Test
    void stateIsOnlyTouchedOnTheServerThread() throws Exception {
        CompletableFuture<Throwable> f = new CompletableFuture<>();
        Thread t = new Thread(() -> {
            try {
                this.registry().observe(this.blade(), this.container(null));
                f.complete(null);
            } catch (Throwable e) {
                f.complete(e);
            }
        });
        t.start();
        Throwable e = f.get(10, TimeUnit.SECONDS);
        assertTrue(e instanceof IllegalStateException, "off-thread access is refused: " + e);
    }
}

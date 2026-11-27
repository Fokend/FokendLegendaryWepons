package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Duplicate scenarios. A copy is never destroyed: it goes to quarantine (with its full bytes) for an admin,
 * and only clear-cut cases are taken out of play automatically.
 */
class ChronicleDupeTest extends ChronicleTestBase {

    private ItemStack stampedBladeFor(PlayerMock owner) {
        owner.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(owner);
        return owner.getInventory().getItem(0);
    }

    @Test
    void twoCopiesInOneScanKeepTheOwnersAndQuarantineTheOther() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        ItemStack original = this.stampedBladeFor(a);
        b.getInventory().addItem(original.clone()); // e.g. a creative middle-click clone or a dupe glitch
        this.chronicle().tracker.scanAll();
        this.settle();
        assertEquals(1, this.countLegend(a), "the owner keeps theirs");
        assertEquals(0, this.countLegend(b), "the copy is taken out of play");
        List<ChronicleDatabase.QuarantineRow> q = this.quarantine();
        assertEquals(1, q.size());
        assertEquals("DUPLICATE", q.get(0).reason());
        assertNotNull(LegendStamp.deserialize(q.get(0).item()), "the copy is kept in full for review");
        assertEquals(a.getUniqueId(), this.ra().owner);
    }

    @Test
    void copyFoundElsewhereWhileTheRecordedHolderStillHasItIsQuarantined() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        ItemStack original = this.stampedBladeFor(a);
        b.getInventory().addItem(original.clone());
        this.chronicle().tracker.scanPlayer(b); // event-driven scan of Bob only
        this.settle();
        assertEquals(0, this.countLegend(b));
        assertEquals(1, this.countLegend(a));
        assertEquals("DUPLICATE", this.quarantine().get(0).reason());
    }

    @Test
    void ambiguousMoveFromAnOfflineHolderIsFlaggedNotRemoved() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        ItemStack original = this.stampedBladeFor(a);
        ItemStack copy = original.clone();
        a.disconnect();
        b.getInventory().addItem(copy);
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        assertEquals(1, this.countLegend(b), "cannot prove a duplicate without seeing the other copy: keep it");
        assertTrue(this.quarantine().isEmpty());
    }

    @Test
    void staleGenerationGoesToQuarantine() throws Exception {
        PlayerMock a = this.player("Alice");
        this.stampedBladeFor(a);
        LegendRecord rec = this.ra();
        rec.gen += 1; // e.g. released by Legend Call while the owner was away
        this.registry().save(rec);
        this.chronicle().tracker.scanPlayer(a);
        this.settle();
        assertEquals(0, this.countLegend(a));
        assertEquals("STALE_GENERATION", this.quarantine().get(0).reason());
    }

    @Test
    void unstampedCopyOfALiveTypeGoesToQuarantine() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        this.stampedBladeFor(a);
        b.getInventory().addItem(this.blade()); // fresh, unstamped
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        assertEquals(0, this.countLegend(b));
        assertEquals("UNSTAMPED_COPY", this.quarantine().get(0).reason());
    }

    @Test
    void twoUnstampedInOneScanAdoptOneAndHoldTheOther() throws Exception {
        PlayerMock a = this.player("Alice");
        a.getInventory().addItem(this.blade());
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        this.settle();
        assertEquals(1, this.countLegend(a));
        assertEquals(1, this.quarantine().size());
    }

    @Test
    void approveMakesTheQuarantinedCopyTheOnlyLiveOne() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        PlayerMock admin = this.player("Admin");
        admin.setOp(true);
        ItemStack original = this.stampedBladeFor(a);
        b.getInventory().addItem(original.clone());
        this.chronicle().tracker.scanAll();
        this.settle();
        long id = this.quarantine().get(0).id();
        int genBefore = this.ra().gen;

        admin.performCommand("legends approve " + id + " Bob");
        this.settle();
        assertTrue(this.quarantine().isEmpty(), "resolved");
        assertEquals(1, this.countLegend(b));
        assertEquals(b.getUniqueId(), this.ra().owner);
        assertEquals(genBefore + 1, this.ra().gen, "a new generation: every other copy is now stale");

        // Alice's copy (the previous live one) is stale now and goes to review on the next scan.
        this.chronicle().tracker.scanAll();
        this.settle();
        assertEquals(0, this.countLegend(a));
        assertEquals(1, this.countLegend(b));
        assertEquals("STALE_GENERATION", this.quarantine().get(0).reason());

        // Approving the same entry twice is impossible.
        admin.performCommand("legends approve " + id + " Bob");
        this.settle();
        assertEquals(1, this.countLegend(b));
    }

    @Test
    void craftingIsRefusedWhileALiveInstanceExists() {
        PlayerMock a = this.player("Alice");
        assertTrue(this.registry().canCreate(FokendLegendaryWepons.ID_RA_BLADE));
        this.stampedBladeFor(a);
        assertTrue(!this.registry().canCreate(FokendLegendaryWepons.ID_RA_BLADE));
        assertTrue(this.registry().canCreate(FokendLegendaryWepons.ID_CB_ORANGE), "cores are not chronicled");
    }

    @Test
    void legendariesInsideAShulkerAreSeen() throws Exception {
        PlayerMock a = this.player("Alice");
        ItemStack shulker = new ItemStack(org.bukkit.Material.SHULKER_BOX);
        var meta = (org.bukkit.inventory.meta.BlockStateMeta) shulker.getItemMeta();
        var box = (org.bukkit.block.ShulkerBox) meta.getBlockState();
        box.getInventory().addItem(this.blade());
        meta.setBlockState(box);
        shulker.setItemMeta(meta);
        a.getInventory().addItem(shulker);
        this.chronicle().tracker.scanPlayer(a);
        this.settle();
        assertNotNull(this.ra(), "found inside the shulker");
        assertEquals(a.getUniqueId(), this.ra().owner);
        // and the stamp was written back into the shulker
        var box2 = (org.bukkit.block.ShulkerBox) ((org.bukkit.inventory.meta.BlockStateMeta) a.getInventory().getItem(0).getItemMeta()).getBlockState();
        assertEquals(this.ra().uid, this.registry().stamp.uid(box2.getInventory().getItem(0)));
    }
}

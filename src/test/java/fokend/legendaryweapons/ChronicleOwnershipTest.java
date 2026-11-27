package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Ownership, transfer reasons, persistence across a restart and no duplicate history. */
class ChronicleOwnershipTest extends ChronicleTestBase {

    @Test
    void firstSightingAdoptsAndStampsTheItem() throws Exception {
        PlayerMock a = this.player("Alice");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        this.settle();

        LegendRecord rec = this.ra();
        assertNotNull(rec);
        assertEquals(a.getUniqueId(), rec.owner);
        assertEquals(LegendRecord.Holder.PLAYER, rec.holder);
        ItemStack held = a.getInventory().getItem(0);
        assertEquals(rec.uid, this.registry().stamp.uid(held), "the item in the inventory carries the instance id");
        assertEquals(1, this.registry().stamp.gen(held));
        List<ChronicleDatabase.HistoryRow> h = this.history(FokendLegendaryWepons.ID_RA_BLADE);
        assertEquals(1, h.size());
        assertEquals("ADOPTED", h.get(0).reason());
    }

    @Test
    void rescanningNeverDuplicatesHistory() throws Exception {
        PlayerMock a = this.player("Alice");
        a.getInventory().addItem(this.blade());
        for (int i = 0; i < 20; i++) {
            this.chronicle().tracker.scanPlayer(a);
            this.chronicle().tracker.scanAll();
        }
        this.settle();
        assertEquals(1, this.history(FokendLegendaryWepons.ID_RA_BLADE).size());
        assertEquals(1, this.countLegend(a));
    }

    @Test
    void survivesRestart() throws Exception {
        PlayerMock a = this.player("Alice");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        UUID uid = this.ra().uid;
        this.restartChronicle();
        LegendRecord rec = this.ra();
        assertNotNull(rec, "record is read back from chronicle.db");
        assertEquals(uid, rec.uid);
        assertEquals(a.getUniqueId(), rec.owner);
        assertNotNull(rec.snapshot, "snapshot persisted");
        assertEquals(1, this.history(FokendLegendaryWepons.ID_RA_BLADE).size());
        // The item is still valid after the restart: no quarantine.
        this.chronicle().tracker.scanPlayer(a);
        this.settle();
        assertEquals(1, this.countLegend(a));
        assertTrue(this.quarantine().isEmpty());
    }

    private ItemStack takeFrom(PlayerMock p) {
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (this.registry().isChronicled(it)) {
                p.getInventory().setItem(i, null);
                return it;
            }
        }
        throw new AssertionError("no legendary on " + p.getName());
    }

    private String lastReason() throws Exception {
        return this.history(FokendLegendaryWepons.ID_RA_BLADE).get(0).reason();
    }

    @Test
    void killByTheKillerVsLootByAnotherPlayer() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        PlayerMock c = this.player("Carol");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);

        // Bob kills Alice; the grave plugin takes the items; Bob loots them.
        ItemStack it = this.takeFrom(a);
        this.registry().markDeath(it, a, b);
        assertEquals(LegendRecord.Holder.GRAVE, this.ra().holder);
        b.getInventory().addItem(it);
        this.chronicle().tracker.scanAll(); // AxGraves sneak-pickup uses addItem: only the periodic scan sees it
        this.settle();
        assertEquals(b.getUniqueId(), this.ra().owner);
        assertEquals("KILL", this.lastReason());

        // Bob dies to the void with no killer; Carol picks the item up.
        it = this.takeFrom(b);
        this.registry().markDeath(it, b, null);
        c.getInventory().addItem(it);
        this.chronicle().tracker.scanPlayer(c);
        this.settle();
        assertEquals(c.getUniqueId(), this.ra().owner);
        assertEquals("LOOT", this.lastReason());
    }

    @Test
    void deathMarkSurvivesARestartUntilPickup() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        ItemStack it = this.takeFrom(a);
        this.registry().markDeath(it, a, b);
        this.restartChronicle(); // item sits in a grave across the restart
        assertEquals(LegendRecord.Hint.DEATH, this.ra().hint);
        b.getInventory().addItem(it);
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        assertEquals("KILL", this.lastReason());
    }

    @Test
    void dropAndPickupIsAGift() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        ItemStack it = this.takeFrom(a);
        assertEquals(LegendRegistry.Verdict.OK, this.registry().observe(it, this.ground(a.getLocation(), UUID.randomUUID())));
        assertEquals(a.getUniqueId(), this.ra().owner, "lying on the ground does not change the owner");
        b.getInventory().addItem(it);
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        assertEquals("GIFT", this.lastReason());
    }

    @Test
    void chestAndEnderChestDoNotChangeOwnerButTakingFromAChestDoes() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        a.getEnderChest().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        assertEquals(LegendRecord.Holder.ENDER_CHEST, this.ra().holder);
        assertEquals(a.getUniqueId(), this.ra().owner);

        ItemStack it = a.getEnderChest().getItem(0);
        a.getEnderChest().setItem(0, null);
        assertEquals(LegendRegistry.Verdict.OK, this.registry().observe(it, this.container(a.getLocation().add(3, 0, 0))));
        this.settle();
        assertEquals(a.getUniqueId(), this.ra().owner, "a chest keeps the owner");
        assertEquals(1, this.history(FokendLegendaryWepons.ID_RA_BLADE).size(), "no transfer row for storing");

        b.getInventory().addItem(it);
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        assertEquals(b.getUniqueId(), this.ra().owner);
        assertEquals("CONTAINER", this.lastReason());
    }

    @Test
    void ownerTakingBackFromOwnChestIsNoTransfer() throws Exception {
        PlayerMock a = this.player("Alice");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        ItemStack it = this.takeFrom(a);
        this.registry().observe(it, this.container(a.getLocation().add(2, 0, 0)));
        a.getInventory().addItem(it);
        this.chronicle().tracker.scanPlayer(a);
        this.settle();
        assertEquals(1, this.history(FokendLegendaryWepons.ID_RA_BLADE).size());
        assertNull(this.ra().hint);
    }

    @Test
    void sameIpTransferIsFlaggedNotBlocked() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("AliceAlt");
        this.registry().ips.put(a.getUniqueId(), "10.0.0.7");
        this.registry().ips.put(b.getUniqueId(), "10.0.0.7");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        ItemStack it = this.takeFrom(a);
        this.registry().observe(it, this.ground(a.getLocation(), UUID.randomUUID()));
        b.getInventory().addItem(it);
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        ChronicleDatabase.HistoryRow last = this.history(FokendLegendaryWepons.ID_RA_BLADE).get(0);
        assertEquals("GIFT", last.reason());
        assertEquals("same_ip", last.flag());
        assertEquals(b.getUniqueId(), this.ra().owner);
    }

    @Test
    void deathNoteOwnerIsHiddenFromPlayersButNotAdmins() {
        UUID someone = UUID.randomUUID();
        this.registry().names.put(someone, "Kira");
        assertEquals("???", this.registry().publicOwner("death_note", someone, false));
        assertEquals("Kira", this.registry().publicOwner("death_note", someone, true));
        assertEquals("Kira", this.registry().publicOwner(FokendLegendaryWepons.ID_RA_BLADE, someone, false));
    }
}

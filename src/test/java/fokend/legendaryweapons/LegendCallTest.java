package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class LegendCallTest extends ChronicleTestBase {
    static final long H = 3_600_000L;

    @org.junit.jupiter.api.BeforeEach
    void noMockDropProtection() {
        this.withoutDropProtection();
    }

    private LegendRecord bladeHeldBy(PlayerMock a) {
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        return this.ra();
    }

    private String lastMessage(PlayerMock p) {
        String last = null;
        Component c;
        while ((c = p.nextComponentMessage()) != null) {
            last = PlainTextComponentSerializer.plainText().serialize(c);
        }
        return last;
    }

    // ---------------------------------------------------------------- the inactivity rule

    @Test
    void inactivityRule() {
        LegendCall call = this.chronicle().call;
        LegendRecord rec = new LegendRecord(java.util.UUID.randomUUID(), "x", 1);
        rec.ownerSince = this.now - 10 * 24 * H;
        long now = this.now;
        assertTrue(call.inactive(rec, new ChronicleDatabase.Activity(0, now - 73 * H), now), "not seen for 73 h");
        assertFalse(call.inactive(rec, new ChronicleDatabase.Activity(60 * 60_000L, now - 71 * H), now), "seen 71 h ago, played an hour");
        assertTrue(call.inactive(rec, new ChronicleDatabase.Activity(5 * 60_000L, now - 2 * H), now),
                "an alt popping in for 5 minutes does not keep the item");
        rec.ownerSince = now - 2 * H;
        assertFalse(call.inactive(rec, new ChronicleDatabase.Activity(5 * 60_000L, now - 2 * H), now),
                "got the item two hours ago: the window is not covered yet");
        rec.ownerSince = now - 10 * 24 * H;
        call.installedAt = now - 10 * H;
        assertFalse(call.inactive(rec, new ChronicleDatabase.Activity(0, 0), now),
                "nobody is inactive just because the chronicle was installed today");
    }

    // ---------------------------------------------------------------- release of an offline-held item

    @Test
    void offlineHeldItemIsReleasedAnnouncedAndFoundByAnother() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        LegendRecord rec = this.bladeHeldBy(a);
        int gen = rec.gen;
        int ax = a.getLocation().getBlockX();
        int az = a.getLocation().getBlockZ();
        ItemStack aliceCopy = a.getInventory().getItem(0).clone();
        a.disconnect();
        this.lastMessage(b);

        this.chronicle().call.start(rec).get(10, TimeUnit.SECONDS);
        this.settle();
        assertEquals(LegendRecord.Status.CALLED, rec.status);
        assertTrue(rec.released);
        assertFalse(rec.spawnPending);
        assertEquals(gen + 1, rec.gen, "new generation: Alice's copy is stale");
        assertNull(rec.owner);
        assertEquals(LegendRecord.Holder.GROUND, rec.holder);
        Item dropped = (Item) this.server.getEntity(rec.holderId);
        assertNotNull(dropped, "a findable copy is in the world");
        assertEquals(rec.gen, this.registry().stamp.gen(dropped.getItemStack()));
        assertEquals("RELEASED", this.history(FokendLegendaryWepons.ID_RA_BLADE).get(0).reason());

        String msg = this.lastMessage(b);
        assertNotNull(msg);
        assertTrue(msg.contains("X≈") && msg.contains("Z≈"), msg);
        assertTrue(Math.abs(rec.callDx) <= 200 && Math.abs(rec.callDz) <= 200);
        int[] shown = this.chronicle().call.shownPoint(rec);
        assertTrue(Math.abs(shown[0] - ax) <= 205 && Math.abs(shown[1] - az) <= 205, "within the stated error");
        assertFalse(msg.contains(" " + ax + " ") && msg.contains(" " + az + " "), "the exact spot is never printed");

        // Bob finds it.
        b.getInventory().addItem(dropped.getItemStack());
        dropped.remove();
        this.chronicle().tracker.scanPlayer(b);
        this.settle();
        assertEquals(LegendRecord.Status.ACTIVE, rec.status);
        assertEquals(b.getUniqueId(), rec.owner);
        assertEquals("LEGEND_CALL", this.history(FokendLegendaryWepons.ID_RA_BLADE).get(0).reason());

        // Alice comes back with her old copy: it is stale and goes to review, Bob keeps his.
        a.reconnect();
        PlayerMock a2 = a;
        a2.getInventory().clear();
        a2.getInventory().addItem(aliceCopy);
        this.chronicle().tracker.scanAll();
        this.settle();
        assertEquals(0, this.countLegend(a2));
        assertEquals(1, this.countLegend(b));
        assertEquals("STALE_GENERATION", this.quarantine().get(0).reason());
    }

    @Test
    void offsetIsChosenOnceAndAnnouncementsAreLimited() throws Exception {
        PlayerMock a = this.player("Alice");
        PlayerMock watcher = this.player("Watcher");
        LegendRecord rec = this.bladeHeldBy(a);
        a.disconnect();
        this.chronicle().call.start(rec).get(10, TimeUnit.SECONDS);
        this.settle();
        int dx = rec.callDx;
        int dz = rec.callDz;
        int announced = 0;
        for (int i = 0; i < 20; i++) {
            this.now += 2 * H;
            this.chronicle().call.tick();
            this.settle();
            assertEquals(dx, rec.callDx, "the offset never changes, so averaging announcements reveals nothing");
            assertEquals(dz, rec.callDz);
        }
        String m;
        while (watcher.nextComponentMessage() != null) {
            announced++;
        }
        assertEquals(4, rec.announces, "at most max_announcements");
        assertTrue(announced <= 4, "no spam: " + announced);
    }

    @Test
    void announcementCountSurvivesRestartSoNoReSpam() throws Exception {
        PlayerMock a = this.player("Alice");
        LegendRecord rec = this.bladeHeldBy(a);
        a.disconnect();
        this.chronicle().call.start(rec).get(10, TimeUnit.SECONDS);
        this.settle();
        this.restartChronicle();
        LegendRecord again = this.ra();
        assertEquals(1, again.announces);
        assertEquals(rec.callDx, again.callDx);
        this.now += H; // less than repeat_hours
        this.chronicle().call.tick();
        this.settle();
        assertEquals(1, again.announces);
    }

    @Test
    void reCheckOnTheServerThreadStopsTheCallIfTheOwnerCameBack() throws Exception {
        PlayerMock a = this.player("Alice");
        LegendRecord rec = this.bladeHeldBy(a);
        // Activity was read while Alice looked gone, but she is online by the time the result arrives.
        this.chronicle().call.evaluate(rec, a.getUniqueId(), rec.gen, new ChronicleDatabase.Activity(0, 0));
        this.settle();
        assertEquals(LegendRecord.Status.ACTIVE, rec.status);
        assertEquals(a.getUniqueId(), rec.owner);
    }

    @Test
    void itemInAChestIsAnnouncedNotDuplicatedAndOwnerReturnCancels() throws Exception {
        PlayerMock a = this.player("Alice");
        LegendRecord rec = this.bladeHeldBy(a);
        ItemStack it = a.getInventory().getItem(0);
        a.getInventory().setItem(0, null);
        this.registry().observe(it, this.container(a.getLocation().add(4, 0, 0)));
        int gen = rec.gen;
        a.disconnect();
        this.chronicle().call.start(rec).get(10, TimeUnit.SECONDS);
        this.settle();
        assertEquals(LegendRecord.Status.CALLED, rec.status);
        assertFalse(rec.released, "already in the world: nothing is spawned");
        assertEquals(gen, rec.gen);
        a.reconnect();
        PlayerMock back = a;
        this.settle();
        assertEquals(LegendRecord.Status.ACTIVE, rec.status, "owner came back before anyone found it");
        assertEquals(back.getUniqueId(), rec.owner);
    }

    @Test
    void pendingSpawnIsRedoneAfterACrash() throws Exception {
        PlayerMock a = this.player("Alice");
        LegendRecord rec = this.bladeHeldBy(a);
        a.disconnect();
        // Simulate: the release was committed, then the server died before the item was dropped.
        rec.status = LegendRecord.Status.CALLED;
        rec.released = true;
        rec.spawnPending = true;
        rec.gen += 1;
        rec.owner = null;
        this.registry().save(rec);
        this.restartChronicle();
        LegendRecord again = this.ra();
        assertTrue(again.spawnPending);
        this.chronicle().call.tick();
        this.settle();
        assertFalse(again.spawnPending);
        assertEquals(LegendRecord.Holder.GROUND, again.holder);
        assertNotNull(this.server.getEntity(again.holderId));
    }

    @Test
    void deathNoteCallDoesNotNameTheOwner() throws Exception {
        PlayerMock a = this.player("Kira");
        PlayerMock w = this.player("Watcher");
        a.getInventory().addItem(DeathNoteListener.create(this.plugin));
        this.chronicle().tracker.scanPlayer(a);
        LegendRecord rec = this.registry().live("death_note");
        a.disconnect();
        this.lastMessage(w);
        this.chronicle().call.start(rec).get(10, TimeUnit.SECONDS);
        this.settle();
        String msg = this.lastMessage(w);
        assertNotNull(msg);
        assertFalse(msg.contains("Kira"), msg);
    }
}

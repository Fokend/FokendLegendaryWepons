package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** /legendary give for chronicled legendaries: one at a time, and replacing needs /legendary confirm. */
class ChronicleGiveTest extends ChronicleTestBase {

    @Test
    void giveWithoutLiveInstanceRegistersExactlyOne() throws Exception {
        PlayerMock admin = this.player("Admin");
        admin.setOp(true);
        PlayerMock b = this.player("Bob");
        admin.performCommand("legendary give Bob ra_blade 5");
        this.settle();
        assertEquals(1, this.countLegend(b), "never more than one live instance, whatever the amount");
        assertEquals(b.getUniqueId(), this.ra().owner);
        assertEquals("ADMIN_GIVE", this.history(FokendLegendaryWepons.ID_RA_BLADE).get(0).reason());
    }

    @Test
    void replacingNeedsConfirmation() throws Exception {
        PlayerMock admin = this.player("Admin");
        admin.setOp(true);
        PlayerMock a = this.player("Alice");
        PlayerMock b = this.player("Bob");
        a.getInventory().addItem(this.blade());
        this.chronicle().tracker.scanPlayer(a);
        UUID oldUid = this.ra().uid;

        admin.performCommand("legendary give Bob ra_blade");
        this.settle();
        assertEquals(0, this.countLegend(b), "nothing is given before confirmation");
        assertEquals(oldUid, this.ra().uid);

        admin.performCommand("legendary confirm");
        this.settle();
        assertEquals(1, this.countLegend(b));
        assertNotEquals(oldUid, this.ra().uid, "a new live instance");
        assertEquals(LegendRecord.Status.RETIRED, this.registry().byUid.get(oldUid).status);

        // The old instance is not deleted on sight: it goes to review.
        this.chronicle().tracker.scanAll();
        this.settle();
        assertEquals(0, this.countLegend(a));
        assertEquals("RETIRED", this.quarantine().get(0).reason());
    }

    @Test
    void confirmWithoutPendingDoesNothing() throws Exception {
        PlayerMock admin = this.player("Admin");
        admin.setOp(true);
        admin.performCommand("legendary confirm");
        this.settle();
        assertEquals(0, this.countLegend(admin));
    }

    @Test
    void coresAreStillGivenAsBefore() throws Exception {
        PlayerMock admin = this.player("Admin");
        admin.setOp(true);
        admin.performCommand("legendary give Admin cb_orange 3");
        this.settle();
        int cores = 0;
        for (var it : admin.getInventory().getContents()) {
            if (it != null && FokendLegendaryWepons.ID_CB_ORANGE.equals(this.plugin.getId(it))) {
                cores += it.getAmount();
            }
        }
        assertEquals(3, cores);
        assertTrue(this.quarantine().isEmpty());
    }
}

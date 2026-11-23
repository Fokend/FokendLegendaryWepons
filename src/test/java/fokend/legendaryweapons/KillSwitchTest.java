package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** /legendary disable|enable switches an ability off without touching the items, and survives a restart. */
class KillSwitchTest extends PluginTestBase {

    @Test
    void disableEnableAndPersist() {
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.setOp(true);

        assertFalse(this.plugin.abilityDisabled(admin, FokendLegendaryWepons.ID_CHAOS_BOOMERANG));

        admin.performCommand("legendary disable chaos_boomerang");
        assertTrue(this.plugin.abilityDisabled(admin, FokendLegendaryWepons.ID_CHAOS_BOOMERANG));
        assertFalse(this.plugin.abilityDisabled(admin, FokendLegendaryWepons.ID_MAGNET), "other abilities stay on");

        // simulate a restart: state is read back from disabled-abilities.yml
        this.plugin.disabledAbilities.clear();
        this.plugin.loadDisabledAbilities();
        assertTrue(this.plugin.abilityDisabled(null, FokendLegendaryWepons.ID_CHAOS_BOOMERANG));

        admin.performCommand("legendary enable chaos_boomerang");
        assertFalse(this.plugin.abilityDisabled(admin, FokendLegendaryWepons.ID_CHAOS_BOOMERANG));
        this.plugin.disabledAbilities.clear();
        this.plugin.loadDisabledAbilities();
        assertFalse(this.plugin.abilityDisabled(null, FokendLegendaryWepons.ID_CHAOS_BOOMERANG));
    }

    @Test
    void friendlyNamesMapToListenerIds() {
        PlayerMock admin = this.server.addPlayer("Admin");
        admin.setOp(true);
        admin.performCommand("legendary disable ra_blade");
        assertTrue(this.plugin.abilityDisabled(null, FokendLegendaryWepons.ID_RA_BLADE), "ra_blade -> ra_god_blade");
        admin.performCommand("legendary disable fate_scales");
        assertTrue(this.plugin.abilityDisabled(null, "fate_scales"));
    }

    @Test
    void nonAdminCannotSwitch() {
        PlayerMock player = this.server.addPlayer("Player");
        player.performCommand("legendary disable magnet");
        assertFalse(this.plugin.abilityDisabled(null, FokendLegendaryWepons.ID_MAGNET));
    }
}

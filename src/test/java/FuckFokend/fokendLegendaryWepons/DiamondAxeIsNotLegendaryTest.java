package FuckFokend.fokendLegendaryWepons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class DiamondAxeIsNotLegendaryTest extends PluginTestBase {

    private static ItemStack axe() {
        ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 3, true);
        axe.setItemMeta(meta);
        return axe;
    }

    @Test
    void axeHasNoLegendaryIdentity() {
        ItemStack axe = axe();
        assertNull(this.plugin.getId(axe));
        for (String id : new String[]{FokendLegendaryWepons.ID_RA_BLADE, FokendLegendaryWepons.ID_WARDEN_HELMET,
                FokendLegendaryWepons.ID_CHAOS_BOOMERANG, "death_note", "fate_scales"}) {
            assertFalse(this.plugin.isLegendary(axe, id), id);
        }
    }

    @Test
    void periodicInventoryTasksLeaveAxeUntouched() {
        PlayerMock player = this.server.addPlayer();
        ItemStack axe = axe();
        player.getInventory().setItem(4, axe.clone());
        this.server.getScheduler().performTicks(200L);
        assertEquals(axe, player.getInventory().getItem(4));
    }
}

package FuckFokend.fokendLegendaryWepons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class LegacyItemMigrationTest extends PluginTestBase {

    private static ItemStack withTag(NamespacedKey key, String value) {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private static PersistentDataContainer pdc(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer();
    }

    @Test
    void newItemsCarryOnlyTheCurrentKey() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        this.plugin.stampId(item, FokendLegendaryWepons.ID_RA_BLADE);
        assertEquals(FokendLegendaryWepons.ID_RA_BLADE, pdc(item).get(this.plugin.weaponKey, PersistentDataType.STRING));
        assertFalse(pdc(item).has(this.plugin.legacyIdKey));
    }

    @Test
    void oldItemsAreStillRecognisedBeforeMigration() {
        ItemStack item = withTag(this.plugin.legacyIdKey, FokendLegendaryWepons.ID_MAGNET);
        assertTrue(this.plugin.isLegendary(item, FokendLegendaryWepons.ID_MAGNET));
    }

    @Test
    void joinMigratesInventoryAndEnderChest() {
        PlayerMock player = this.server.addPlayer();
        player.getInventory().setItem(0, withTag(this.plugin.legacyIdKey, FokendLegendaryWepons.ID_MAGNET));
        player.getEnderChest().setItem(0, withTag(new NamespacedKey("legendaryweapons", "weapon"), "chaos"));

        this.server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));

        ItemStack inv = player.getInventory().getItem(0);
        assertEquals(FokendLegendaryWepons.ID_MAGNET, pdc(inv).get(this.plugin.weaponKey, PersistentDataType.STRING));
        assertFalse(pdc(inv).has(this.plugin.legacyIdKey));

        ItemStack ender = player.getEnderChest().getItem(0);
        assertEquals(FokendLegendaryWepons.ID_CHAOS_BOOMERANG, pdc(ender).get(this.plugin.weaponKey, PersistentDataType.STRING));
        assertFalse(pdc(ender).has(new NamespacedKey("legendaryweapons", "weapon")));
    }

    @Test
    void plainItemsAreLeftAlone() {
        assertFalse(this.plugin.migrateLegacyId(new ItemStack(Material.DIAMOND_AXE)));
    }
}

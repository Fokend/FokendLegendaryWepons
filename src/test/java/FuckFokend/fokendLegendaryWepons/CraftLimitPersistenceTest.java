package FuckFokend.fokendLegendaryWepons;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CraftLimitPersistenceTest extends PluginTestBase {

    @Test
    void craftedUniquesAreSavedToSoulsFile() {
        this.plugin.craftedUniques.add("ra_blade");
        assertDoesNotThrow(() -> this.plugin.saveSouls());
        File file = new File(this.plugin.getDataFolder(), "souls.yml");
        assertTrue(file.isFile(), "souls.yml must be written");
        assertTrue(YamlConfiguration.loadConfiguration(file).getStringList("crafted_uniques").contains("ra_blade"));
    }
}

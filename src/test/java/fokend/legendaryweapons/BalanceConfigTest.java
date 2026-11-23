package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class BalanceConfigTest extends PluginTestBase {

    @Test
    void defaultsMatchTheOldHardcodedValues() {
        assertEquals(8000L, this.plugin.balance.cooldownMs("chaos_boomerang"));
        assertEquals(250L, this.plugin.balance.cooldownMs("mask_tompson_shot"));
        assertEquals(0.17D, this.plugin.balance.coreUpgradeChance(FokendLegendaryWepons.ID_CB_GREEN));
        assertEquals(900L, this.plugin.balance.ticks("ra_blade.doll_lifetime_seconds"));
    }

    @Test
    void reloadedCooldownAppliesToARunningCooldown() {
        PlayerMock p = this.server.addPlayer();
        this.plugin.cooldowns.start(p, "magnet", "Магнитник");
        assertTrue(this.plugin.cooldowns.isOnCooldown(p, "magnet"));

        this.plugin.getConfig().set("cooldowns.magnet", 0);
        assertFalse(this.plugin.cooldowns.isOnCooldown(p, "magnet"));
    }

    @Test
    void nonNumberFallsBackToDefault() {
        this.plugin.getConfig().set("ice_spear.radius", "big");
        assertEquals(15.0D, this.plugin.balance.getDouble("ice_spear.radius"));
    }

    @Test
    void unknownCoreHasNoChance() {
        assertEquals(0.0D, this.plugin.balance.coreUpgradeChance("ra_god_blade"));
    }

    /** Every config path written in the code must exist in the bundled config.yml, or the ability throws. */
    @Test
    void everyPathUsedInCodeExistsInConfig() throws IOException {
        Pattern balanceCall = Pattern.compile("balance\\.(?:getDouble|getInt|seconds|ticks)\\(\"([^\"]+)\"\\)");
        Pattern cooldownCall = Pattern.compile("cooldowns\\.(?:start|startSilent|isOnCooldown|remainingSeconds)\\(\\w+, \"([^\"]+)\"");
        List<String> missing = new ArrayList<>();
        int seen = 0;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(file);
                for (Matcher m = balanceCall.matcher(src); m.find(); seen++) {
                    if (!this.plugin.getConfig().getDefaults().contains(m.group(1))) missing.add(m.group(1));
                }
                for (Matcher m = cooldownCall.matcher(src); m.find(); seen++) {
                    if (!this.plugin.getConfig().getDefaults().contains("cooldowns." + m.group(1))) missing.add("cooldowns." + m.group(1));
                }
            }
        }
        assertTrue(seen > 30, "pattern stopped matching the code, saw only " + seen);
        assertEquals(List.of(), missing);
    }
}

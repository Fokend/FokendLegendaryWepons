package fokend.legendaryweapons;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Helpers for chronicle tests: a real SQLite file in the plugin's (temporary) data folder. */
abstract class ChronicleTestBase extends PluginTestBase {
    protected long now = 1_900_000_000_000L;

    /**
     * MockBukkit cannot serialise items with attribute modifiers (every legendary has some), while Paper's
     * {@code serializeAsBytes} can. Tests swap in a codec that keeps exact clones behind an opaque token, so
     * what is checked is the chronicle's own logic: that a copy is stored and comes back intact.
     */
    static final java.util.Map<String, ItemStack> STORE = new java.util.concurrent.ConcurrentHashMap<>();

    static {
        LegendStamp.codec = new LegendStamp.Codec() {
            @Override
            public byte[] encode(ItemStack item) {
                String token = java.util.UUID.randomUUID().toString();
                STORE.put(token, item.clone());
                return token.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }

            @Override
            public ItemStack decode(byte[] bytes) {
                ItemStack it = STORE.get(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                return it == null ? null : it.clone();
            }
        };
    }

    @BeforeEach
    void chronicleUp() {
        assertNotNull(this.plugin.chronicle, "chronicle must start (SQLite on the test classpath)");
        this.plugin.chronicle.registry.clock = () -> this.now;
        this.plugin.chronicle.call.installedAt = this.now - 30L * 24 * 3_600_000L;
    }

    /**
     * MockBukkit has no {@code Item#setUnlimitedLifetime}, which the existing drop protection calls on every
     * item spawn. Tests that drop legendaries switch that listener off; Paper implements the method.
     */
    protected void withoutDropProtection() {
        for (org.bukkit.plugin.RegisteredListener rl : org.bukkit.event.HandlerList.getRegisteredListeners(this.plugin)) {
            if (rl.getListener() instanceof ItemProtectionListener l) {
                org.bukkit.event.HandlerList.unregisterAll(l);
            }
        }
    }

    protected Chronicle chronicle() {
        return this.plugin.chronicle;
    }

    protected LegendRegistry registry() {
        return this.plugin.chronicle.registry;
    }

    /** Waits for every queued database write, then runs the server tasks they scheduled. */
    protected void settle() throws Exception {
        for (int i = 0; i < 3; i++) {
            this.chronicle().db.flush().get(10, TimeUnit.SECONDS);
            this.server.getScheduler().performTicks(2);
        }
        this.chronicle().db.flush().get(10, TimeUnit.SECONDS);
    }

    /** Simulates a restart of the chronicle against the same database file. */
    protected void restartChronicle() throws Exception {
        this.settle();
        this.plugin.chronicle.close();
        this.plugin.chronicle = Chronicle.start(this.plugin);
        assertNotNull(this.plugin.chronicle);
        this.plugin.chronicle.registry.clock = () -> this.now;
        this.plugin.chronicle.call.installedAt = this.now - 30L * 24 * 3_600_000L;
    }

    protected ItemStack blade() {
        return this.plugin.items.raBlade();
    }

    protected PlayerMock player(String name) {
        return this.server.addPlayer(name);
    }

    protected LegendRecord ra() {
        return this.registry().live(FokendLegendaryWepons.ID_RA_BLADE);
    }

    protected List<ChronicleDatabase.HistoryRow> history(String type) throws Exception {
        return this.chronicle().db.history(type, 100, 0).get(10, TimeUnit.SECONDS);
    }

    protected List<ChronicleDatabase.QuarantineRow> quarantine() throws Exception {
        return this.chronicle().db.openQuarantine().get(10, TimeUnit.SECONDS);
    }

    protected int countLegend(PlayerMock p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) {
            if (this.registry().isChronicled(it)) {
                n++;
            }
        }
        return n;
    }

    protected LegendRegistry.Sighting container(Location at) {
        return new LegendRegistry.Sighting(LegendRecord.Holder.CONTAINER, null, null, at);
    }

    protected LegendRegistry.Sighting ground(Location at, java.util.UUID entity) {
        return new LegendRegistry.Sighting(LegendRecord.Holder.GROUND, null, entity, at);
    }
}

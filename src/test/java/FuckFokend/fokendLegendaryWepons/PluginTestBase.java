package FuckFokend.fokendLegendaryWepons;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

abstract class PluginTestBase {
    protected ServerMock server;
    protected FokendLegendaryWepons plugin;

    @BeforeEach
    void startServer() {
        this.server = MockBukkit.mock();
        this.plugin = MockBukkit.load(FokendLegendaryWepons.class);
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    /** Calls only the given listener class for the event, bypassing the server's own sync/async checks. */
    protected static void callListener(HandlerList handlers, Class<?> listenerClass, Event event) throws Exception {
        boolean called = false;
        for (RegisteredListener rl : handlers.getRegisteredListeners()) {
            if (listenerClass.isInstance(rl.getListener())) {
                rl.callEvent(event);
                called = true;
            }
        }
        if (!called) {
            throw new IllegalStateException(listenerClass.getSimpleName() + " is not registered for " + event.getEventName());
        }
    }
}

package FuckFokend.fokendLegendaryWepons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

class DeathNoteChatThreadingTest extends PluginTestBase {

    private Component chatFromAsyncThread(MainThreadOnlyPlayer source, MainThreadOnlyPlayer viewer) throws Exception {
        Component message = Component.text("hello");
        AsyncChatEvent event = new AsyncChatEvent(true, source, Set.of(viewer), ChatRenderer.defaultRenderer(),
                message, message, null);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Component> rendered = new AtomicReference<>();
        Thread chatThread = new Thread(() -> {
            try {
                callListener(AsyncChatEvent.getHandlerList(), DeathNoteListener.class, event);
                rendered.set(event.renderer().render(source, Component.text(source.getName()), message, viewer));
            } catch (Throwable t) {
                failure.set(t);
            }
        }, "Async Chat Thread - #0");
        chatThread.start();
        chatThread.join(10_000L);
        assertNull(failure.get(), () -> "chat handling touched game state off the main thread: " + failure.get());
        return rendered.get();
    }

    @Test
    void invisibleSenderIsCipheredWithoutTouchingEntitiesOffMainThread() throws Exception {
        MainThreadOnlyPlayer source = new MainThreadOnlyPlayer(this.server, "Hidden");
        MainThreadOnlyPlayer viewer = new MainThreadOnlyPlayer(this.server, "Viewer");
        this.server.addPlayer(source);
        this.server.addPlayer(viewer);
        source.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 2000, 0));
        this.server.getScheduler().performTicks(40L);

        Component rendered = this.chatFromAsyncThread(source, viewer);

        TranslatableComponent line = (TranslatableComponent) rendered;
        assertEquals("chat.type.text", line.key());
        Component name = line.arguments().get(0).asComponent();
        assertTrue(name.hasDecoration(TextDecoration.OBFUSCATED), "invisible sender name must be obfuscated: " + name);
    }

    @Test
    void visibleSenderKeepsDefaultRenderer() throws Exception {
        MainThreadOnlyPlayer source = new MainThreadOnlyPlayer(this.server, "Plain");
        MainThreadOnlyPlayer viewer = new MainThreadOnlyPlayer(this.server, "Viewer");
        this.server.addPlayer(source);
        this.server.addPlayer(viewer);
        this.server.getScheduler().performTicks(40L);

        Component rendered = this.chatFromAsyncThread(source, viewer);

        Component name = ((TranslatableComponent) rendered).arguments().get(0).asComponent();
        assertTrue(name.decoration(TextDecoration.OBFUSCATED) != TextDecoration.State.TRUE,
                "visible sender must not be ciphered: " + rendered);
    }
}

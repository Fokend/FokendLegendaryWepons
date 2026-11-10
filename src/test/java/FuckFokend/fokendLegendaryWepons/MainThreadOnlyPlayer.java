package FuckFokend.fokendLegendaryWepons;

import java.util.Collection;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A player whose potion effects and attributes may only be read on the main thread, like on a real
 * Paper server where those collections are mutated by the tick loop without synchronization.
 */
final class MainThreadOnlyPlayer extends PlayerMock {
    private final ServerMock server;

    MainThreadOnlyPlayer(ServerMock server, String name) {
        super(server, name);
        this.server = server;
    }

    private void requireMainThread(String what) {
        // null while the PlayerMock constructor itself initialises attributes
        if (this.server != null && !this.server.isPrimaryThread()) {
            throw new IllegalStateException(what + " read off the main thread (" + Thread.currentThread().getName() + ")");
        }
    }

    @Override
    public boolean hasPotionEffect(PotionEffectType type) {
        this.requireMainThread("hasPotionEffect");
        return super.hasPotionEffect(type);
    }

    @Override
    public PotionEffect getPotionEffect(PotionEffectType type) {
        this.requireMainThread("getPotionEffect");
        return super.getPotionEffect(type);
    }

    @Override
    public Collection<PotionEffect> getActivePotionEffects() {
        this.requireMainThread("getActivePotionEffects");
        return super.getActivePotionEffects();
    }

    @Override
    public AttributeInstance getAttribute(Attribute attribute) {
        this.requireMainThread("getAttribute");
        return super.getAttribute(attribute);
    }
}

package fokend.legendaryweapons;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Instance identity on the item (uid + generation) and item (de)serialisation for snapshots and quarantine.
 * The stamp only says which record an item claims to be; whether that claim is valid is decided by
 * {@link LegendRegistry} against the database, never by the stamp alone.
 */
final class LegendStamp {
    private static final byte[] YAML_MARK = "LCYAML1\n".getBytes(StandardCharsets.UTF_8);

    final NamespacedKey uidKey;
    final NamespacedKey genKey;

    LegendStamp(FokendLegendaryWepons plugin) {
        this.uidKey = new NamespacedKey(plugin, "legend_uid");
        this.genKey = new NamespacedKey(plugin, "legend_gen");
    }

    UUID uid(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(this.uidKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    int gen(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer g = item.getItemMeta().getPersistentDataContainer().get(this.genKey, PersistentDataType.INTEGER);
        return g == null ? 0 : g;
    }

    void stamp(ItemStack item, UUID uid, int gen) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(this.uidKey, PersistentDataType.STRING, uid.toString());
        pdc.set(this.genKey, PersistentDataType.INTEGER, gen);
        item.setItemMeta(meta);
    }

    void strip(ItemMeta meta) {
        if (meta != null) {
            meta.getPersistentDataContainer().remove(this.uidKey);
            meta.getPersistentDataContainer().remove(this.genKey);
        }
    }

    /** How items become bytes. Paper's versioned NBT format by default; replaceable for tests. */
    interface Codec {
        byte[] encode(ItemStack item);

        ItemStack decode(byte[] bytes);
    }

    static final Codec PAPER = new Codec() {
        @Override
        public byte[] encode(ItemStack item) {
            return paperSerialize(item);
        }

        @Override
        public ItemStack decode(byte[] bytes) {
            return paperDeserialize(bytes);
        }
    };

    static volatile Codec codec = PAPER;

    static byte[] serialize(ItemStack item) {
        return codec.encode(item);
    }

    static ItemStack deserialize(byte[] bytes) {
        return bytes == null ? null : codec.decode(bytes);
    }

    /** Full item bytes. Uses Paper's versioned format; falls back to YAML where that is unavailable. */
    private static byte[] paperSerialize(ItemStack item) {
        try {
            return item.serializeAsBytes();
        } catch (Throwable unsupported) {
            YamlConfiguration y = new YamlConfiguration();
            y.set("item", item);
            byte[] body = y.saveToString().getBytes(StandardCharsets.UTF_8);
            byte[] out = Arrays.copyOf(YAML_MARK, YAML_MARK.length + body.length);
            System.arraycopy(body, 0, out, YAML_MARK.length, body.length);
            return out;
        }
    }

    private static ItemStack paperDeserialize(byte[] bytes) {
        if (bytes.length >= YAML_MARK.length && Arrays.equals(Arrays.copyOf(bytes, YAML_MARK.length), YAML_MARK)) {
            YamlConfiguration y = new YamlConfiguration();
            try {
                y.loadFromString(new String(bytes, YAML_MARK.length, bytes.length - YAML_MARK.length, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return null;
            }
            return y.getItemStack("item");
        }
        return ItemStack.deserializeBytes(bytes);
    }
}

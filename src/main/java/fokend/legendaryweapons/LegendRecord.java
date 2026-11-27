package fokend.legendaryweapons;

import java.util.UUID;

/**
 * In-memory state of one legendary instance. Owned by the server thread: it is only read and written there,
 * and {@link #toRow()} hands an immutable copy to the database thread.
 */
final class LegendRecord {
    enum Status { ACTIVE, CALLED, RETIRED }

    /** Where the item was last seen. */
    enum Holder {
        /** In a player's inventory, armour, off hand, cursor or crafting grid. */
        PLAYER,
        /** In a player's ender chest. */
        ENDER_CHEST,
        /** In a block or entity container in the world. */
        CONTAINER,
        /** A dropped item entity. */
        GROUND,
        /** Lost at a death; now in a grave plugin's storage or about to drop. */
        GRAVE,
        /** In an inventory with no world holder (a plugin GUI, e.g. an AxGraves grave). */
        VIRTUAL,
        NONE
    }

    /** What last happened to the item while nobody owned it new; decides the reason of the next transfer. */
    enum Hint { DEATH, DROP, STORED, CALL, REFUSE_NOTE }

    final UUID uid;
    final String type;
    int gen;
    UUID owner;
    long ownerSince;
    Status status = Status.ACTIVE;
    Holder holder = Holder.NONE;
    /** Player for PLAYER/ENDER_CHEST, item entity for GROUND. */
    UUID holderId;
    String world;
    int x;
    int y;
    int z;
    Hint hint;
    UUID hintActor;
    UUID hintFrom;
    long hintAt;
    byte[] snapshot;
    int snapshotHash;
    int callDx;
    int callDz;
    long callStarted;
    long lastAnnounce;
    int announces;
    boolean released;
    boolean spawnPending;
    long lastSeen;
    long createdAt;

    LegendRecord(UUID uid, String type, int gen) {
        this.uid = uid;
        this.type = type;
        this.gen = gen;
    }

    /** Identity of the place the item was last seen in; equal keys mean "the same inventory or entity". */
    String placeKey() {
        return LegendRegistry.placeKey(this.holder, this.holderId, this.world, this.x, this.y, this.z);
    }

    boolean live() {
        return this.status != Status.RETIRED;
    }

    void clearHint() {
        this.hint = null;
        this.hintActor = null;
        this.hintFrom = null;
        this.hintAt = 0;
    }

    void clearCall() {
        this.callDx = 0;
        this.callDz = 0;
        this.callStarted = 0;
        this.lastAnnounce = 0;
        this.announces = 0;
        this.released = false;
        this.spawnPending = false;
    }

    private static String s(UUID u) {
        return u == null ? null : u.toString();
    }

    private static UUID u(String s) {
        if (s == null) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    ChronicleDatabase.LegendRow toRow() {
        return new ChronicleDatabase.LegendRow(this.uid.toString(), this.type, this.gen, s(this.owner), this.ownerSince,
                this.status.name(), this.holder.name(), s(this.holderId), this.world, this.x, this.y, this.z,
                this.hint == null ? null : this.hint.name(), s(this.hintActor), s(this.hintFrom), this.hintAt,
                this.snapshot == null ? null : this.snapshot.clone(), this.callDx, this.callDz, this.callStarted,
                this.lastAnnounce, this.announces, this.released, this.spawnPending, this.lastSeen, this.createdAt);
    }

    static LegendRecord fromRow(ChronicleDatabase.LegendRow r) {
        LegendRecord rec = new LegendRecord(UUID.fromString(r.uid()), r.type(), r.gen());
        rec.owner = u(r.owner());
        rec.ownerSince = r.ownerSince();
        try {
            rec.status = Status.valueOf(r.status());
        } catch (IllegalArgumentException e) {
            rec.status = Status.ACTIVE;
        }
        try {
            rec.holder = Holder.valueOf(r.holder());
        } catch (IllegalArgumentException e) {
            rec.holder = Holder.NONE;
        }
        rec.holderId = u(r.holderId());
        rec.world = r.world();
        rec.x = r.x();
        rec.y = r.y();
        rec.z = r.z();
        if (r.hint() != null) {
            try {
                rec.hint = Hint.valueOf(r.hint());
            } catch (IllegalArgumentException ignored) {
            }
        }
        rec.hintActor = u(r.hintActor());
        rec.hintFrom = u(r.hintFrom());
        rec.hintAt = r.hintAt();
        rec.snapshot = r.snapshot();
        rec.snapshotHash = r.snapshot() == null ? 0 : java.util.Arrays.hashCode(r.snapshot());
        rec.callDx = r.callDx();
        rec.callDz = r.callDz();
        rec.callStarted = r.callStarted();
        rec.lastAnnounce = r.lastAnnounce();
        rec.announces = r.announces();
        rec.released = r.released();
        rec.spawnPending = r.spawnPending();
        rec.lastSeen = r.lastSeen();
        rec.createdAt = r.createdAt();
        return rec;
    }
}

package fokend.legendaryweapons;

/** Why a legendary changed hands (or left them). Stored by name in the history table. */
enum TransferReason {
    CRAFT("скована"),
    ADMIN_GIVE("выдана администрацией"),
    ADOPTED("запись начата"),
    KILL("убийство"),
    LOOT("снята с павшего"),
    GIFT("передана из рук в руки"),
    CONTAINER("взята из хранилища"),
    REFUSE_NOTE("отказ от тетради"),
    LEGEND_CALL("найдена по Зову"),
    RELEASED("ушла на Зов"),
    ADMIN_APPROVE("подтверждена администрацией"),
    RETIRED("погашена"),
    OTHER("иной путь");

    final String label;

    TransferReason(String label) {
        this.label = label;
    }

    static TransferReason parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return OTHER;
        }
    }
}

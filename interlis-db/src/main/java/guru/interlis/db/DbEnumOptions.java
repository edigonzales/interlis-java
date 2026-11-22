package guru.interlis.db;

record DbEnumOptions(boolean itfCodes, boolean textColumns, boolean beautify, Lookup lookup) {
    static final DbEnumOptions DEFAULT = new DbEnumOptions(false, false, false, Lookup.NONE);

    enum Lookup {
        NONE(null), SINGLE("singleTable"), PER_DEFINITION("multiTable");

        final String setting;

        Lookup(String setting) { this.setting = setting; }
    }
}

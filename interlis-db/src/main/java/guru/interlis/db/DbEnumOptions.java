package guru.interlis.db;

record DbEnumOptions(boolean itfCodes, boolean textColumns, boolean beautify, Lookup lookup) {
    static final DbEnumOptions DEFAULT = new DbEnumOptions(false, false, false, Lookup.NONE);

    enum Lookup {
        NONE(null, null), SINGLE("singleTable", "--create-single-enum-tab"),
        PER_DEFINITION("multiTable", "--create-enum-tabs"), WITH_ID("multiTableWithId", "--create-enum-tabs-with-id");

        final String setting;

        final String option;

        Lookup(String setting, String option) {
            this.setting = setting;
            this.option = option;
        }
    }
}

package guru.interlis.db;

record DbEnumOptions(boolean itfCodes, boolean textColumns, boolean beautify) {
    static final DbEnumOptions DEFAULT = new DbEnumOptions(false, false, false);
}

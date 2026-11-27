package guru.interlis.db;

import guru.interlis.model.AlignmentType;

record DbEnumOptions(boolean itfCodes, boolean textColumns, boolean beautify, Lookup lookup) {
    static final DbEnumOptions DEFAULT = new DbEnumOptions(false, false, false, Lookup.NONE);

    boolean numeric() { return itfCodes || lookup == Lookup.WITH_ID; }

    void checkAlignment(DbMapping.Column column) {
        if ((itfCodes || lookup != Lookup.NONE) && column.effectiveType().orElse(null) instanceof AlignmentType) {
            throw DbMapping.unsupported("enumeration lookup or numeric alignment at " + column.slot().name().declared());
        }
    }

    void checkExpandedField(DbMapping.Kind kind, String path) {
        if ((kind == DbMapping.Kind.ENUMERATION && (numeric() || textColumns))
                || (kind == DbMapping.Kind.BOOLEAN && textColumns)) {
            throw DbMapping.unsupported("enumeration column options for expanded field " + path);
        }
    }

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

package guru.interlis.db;

import guru.interlis.model.AlignmentType;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.Origin;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.TransferPlan;

record DbEnumOptions(boolean itfCodes, boolean textColumns, boolean beautify, Lookup lookup) {
    static final DbEnumOptions DEFAULT = new DbEnumOptions(false, false, false, Lookup.NONE);

    boolean numeric() { return itfCodes || lookup == Lookup.WITH_ID; }

    void checkAlignment(TransferPlan plan, DbMapping.Column column) {
        if (!(column.effectiveType().orElse(null) instanceof AlignmentType) || (!itfCodes && lookup == Lookup.NONE)) return;
        var attribute = (AttributeSlot) column.slot();
        var declared = plan.type(attribute.declaredType()).known().orElseThrow();
        if (declared instanceof DomainReferenceType alias
                && !(DbDomains.domain(plan, alias.targetDomain()).header().origin() instanceof Origin.PredefinedOrigin)) {
            throw DbMapping.unsupported("numeric or lookup alignment through a named domain at " + attribute.name().declared());
        }
        if (itfCodes && textColumns && lookup == Lookup.SINGLE) {
            throw DbMapping.unsupported("ITF codes with a central lookup and alignment labels at " + attribute.name().declared());
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

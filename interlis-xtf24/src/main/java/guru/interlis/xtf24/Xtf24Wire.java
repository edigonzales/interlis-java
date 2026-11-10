package guru.interlis.xtf24;

import guru.interlis.model.SymbolKind;
import guru.interlis.transfer.ObjectDescriptor;

import javax.xml.namespace.QName;

final class Xtf24Wire {
    static final String NAMESPACE = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    static final int MAX_MODELS = 4_096;

    private Xtf24Wire() {}

    static QName ili(String name) {
        return new QName(NAMESPACE, name);
    }

    static boolean hasStableOid(ObjectDescriptor object) {
        return object.oidRules().stream().anyMatch(rule -> !rule.basket() && rule.domain().isPresent());
    }

    static boolean hasTid(ObjectDescriptor object) {
        return object.kind() == SymbolKind.CLASS || hasStableOid(object);
    }

    static Xtf24Exception failure(Xtf24Exception.Reason reason, String message) {
        return new Xtf24Exception(reason, message, -1, -1, null);
    }
}

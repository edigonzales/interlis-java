package guru.interlis.xtf24;

import javax.xml.namespace.QName;

final class Xtf24Wire {
    static final String NAMESPACE = "http://www.interlis.ch/xtf/2.4/INTERLIS";
    static final int MAX_MODELS = 4_096;

    private Xtf24Wire() {}

    static QName ili(String name) {
        return new QName(NAMESPACE, name);
    }

    static Xtf24Exception failure(Xtf24Exception.Reason reason, String message) {
        return new Xtf24Exception(reason, message, -1, -1, null);
    }
}

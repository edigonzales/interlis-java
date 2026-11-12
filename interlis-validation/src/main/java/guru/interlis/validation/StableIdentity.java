package guru.interlis.validation;

import guru.interlis.model.QualifiedName;

import java.util.Map;

/** Stable identifier with its transfer alias resolved to a declared namespace. */
record StableIdentity(String namespace, String value) {
    static StableIdentity bind(Map<String, QualifiedName> namespaces, String identifier) {
        int separator = identifier.indexOf(':');
        if (separator >= 0 && !namespaces.isEmpty()) {
            var domain = namespaces.get(identifier.substring(0, separator));
            if (domain == null) {
                throw new ValidationStreamException("Unknown stable identity namespace");
            }
            return new StableIdentity(domain.toString(), identifier.substring(separator + 1));
        }
        return new StableIdentity("", identifier);
    }
}

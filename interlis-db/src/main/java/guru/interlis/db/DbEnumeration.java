package guru.interlis.db;

import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.transfer.TransferValue.Enumeration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Bidirectional mapping between an enumeration path and its numeric database value. */
record DbEnumeration(Map<String, Long> numbers, Map<Long, String> paths) {
    DbEnumeration {
        numbers = Map.copyOf(numbers);
        paths = Map.copyOf(paths);
    }

    static List<EnumerationValue> values(EnumerationType type) {
        if (type.treeValues()) return type.values();
        var parents = new HashSet<String>();
        for (var value : type.values()) {
            int separator = value.path().lastIndexOf('.');
            if (separator >= 0) parents.add(value.path().substring(0, separator));
        }
        return type.values().stream().filter(value -> !parents.contains(value.path())).toList();
    }

    static DbEnumeration itfCodes(EnumerationType type, String path) {
        if (type.treeValues()) throw DbMapping.unsupported("ITF codes for ALL OF at " + path);
        var numbers = new HashMap<String, Long>();
        var paths = new HashMap<Long, String>();
        long code = 0;
        for (var value : values(type)) {
            numbers.put(value.path(), code);
            paths.put(code++, value.path());
        }
        return new DbEnumeration(numbers, paths);
    }

    Enumeration read(String value, String context) {
        if (value == null) return null;
        String mapped;
        try {
            mapped = paths.get(Long.parseLong(value));
        } catch (NumberFormatException failure) {
            throw new DbException("Invalid numeric enumeration value " + value + " at " + context, failure);
        }
        if (mapped == null) throw new DbException("Unknown numeric enumeration value " + value + " at " + context);
        return DbTransfer.readEnumeration(mapped, context);
    }

    long number(Enumeration value, String context) {
        String path = String.join(".", value.path());
        var code = numbers.get(path);
        if (code == null) throw new DbException("Cannot encode enumeration value " + path + " at " + context);
        return code;
    }
}

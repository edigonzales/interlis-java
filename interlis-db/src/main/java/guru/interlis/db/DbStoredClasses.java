package guru.interlis.db;

import guru.interlis.execution.CancellationToken;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Stored inheritance and SQL names used to discover tables shared across model sources. */
final class DbStoredClasses {
    record Entry(String path, String base, String name, String strategy) { }

    private final Map<String, Entry> byPath;

    private DbStoredClasses(Map<String, Entry> byPath) {
        this.byPath = byPath;
    }

    static DbStoredClasses read(Database db, CancellationToken token) throws SQLException {
        String sql = """
                SELECT i.thisclass, i.baseclass, c.sqlname, t.setting
                FROM %s i JOIN %s c ON c.iliname = i.thisclass
                JOIN %s t ON t.iliname = i.thisclass AND t.tag = ?
                """.formatted(db.technicalTable(Ili2dbLayout.INHERITANCE), db.technicalTable(Ili2dbLayout.CLASSNAME),
                db.technicalTable(Ili2dbLayout.TRAFO));
        var entries = new LinkedHashMap<String, Entry>();
        try (var statement = db.connection().prepareStatement(sql)) {
            statement.setString(1, Ili2dbLayout.INHERITANCE_TAG);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    var entry = new Entry(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4));
                    if (entries.putIfAbsent(entry.path(), entry) != null) {
                        throw new DbException("Duplicate stored inheritance for " + entry.path());
                    }
                }
            }
        }
        return new DbStoredClasses(entries);
    }

    Set<String> sharedSubtypes(Set<String> selected) {
        var subtypes = new HashSet<String>();
        for (var entry : byPath.values()) {
            if (!DbInheritance.Strategy.SUPER_CLASS.value.equals(entry.strategy()) || selected.contains(entry.path())) continue;
            var ancestors = new HashSet<String>();
            for (var base = byPath.get(entry.base()); base != null && ancestors.add(base.path()); base = byPath.get(base.base())) {
                if (Ili2dbLayout.NEW_CLASS.equals(base.strategy())) {
                    if (selected.contains(base.path())) subtypes.add(entry.path());
                    break;
                }
            }
        }
        return subtypes;
    }
}

package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

import static guru.interlis.db.DbTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class TransferCatalogAssertions {
    private TransferCatalogAssertions() { }

    static void assertAssociationCatalog(Target db, Connection sql, String model, String name,
                                                 Map<String, String> roles) throws SQLException {
        assertQualifiedAssociationCatalog(db, sql, model + ".Data", name, roles);
    }

    static void assertReferenceMapping(Target db, Connection sql, String model, String attribute,
                                               String owner, String column, String target) throws SQLException {
        assertQualifiedReferenceMapping(db, sql, model + ".Data." + attribute, owner, column, target);
    }

    static void assertTypeKind(Target db, Connection sql, String table, String column, String kind)
            throws SQLException {
        assertEquals(kind, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='" + table + "' and columnname='" + column + "' and tag='ch.ehi.ili2db.typeKind'"));
    }

    static void assertQualifiedAssociationCatalog(Target db, Connection sql, String topic, String name,
                                                          Map<String, String> roles) throws SQLException {
        String association = topic + "." + name;
        String table = name.toLowerCase(Locale.ROOT);
        assertEquals(table, scalar(sql, "select sqlname from " + db.table("t_ili2db_classname")
                + " where iliname='" + association + "'"));
        assertEquals("ASSOCIATION", scalar(sql, "select setting from " + db.table("t_ili2db_table_prop")
                + " where tablename='" + table + "' and tag='ch.ehi.ili2db.tableKind'"));
        assertEquals("newClass", scalar(sql, "select setting from " + db.table("t_ili2db_trafo")
                + " where iliname='" + association + "' and tag='ch.ehi.ili2db.inheritance'"));
        for (var role : roles.entrySet()) {
            String target = role.getValue();
            String column = role.getKey().toLowerCase(Locale.ROOT);
            assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                    + " where iliname='" + association + "." + role.getKey() + "' and sqlname='" + column
                    + "' and colowner='" + table + "' and target='" + target + "'"));
            assertEquals(target, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                    + " where tablename='" + table + "' and subtype is null and columnname='" + column
                    + "' and tag='ch.ehi.ili2db.foreignKey'"));
        }
        String identityColumn = db.postgis
                ? "select column_name from information_schema.columns where table_schema='" + db.schema
                        + "' and table_name='" + table + "' and column_name='t_ili_tid'"
                : "select name from pragma_table_info('" + table + "') where lower(name)='t_ili_tid'";
        assertNull(scalar(sql, identityColumn));
    }

    static void assertQualifiedReferenceMapping(Target db, Connection sql, String attribute,
                                                        String owner, String column, String target) throws SQLException {
        assertEquals("1", scalar(sql, "select count(*) from " + db.table("t_ili2db_attrname")
                + " where iliname='" + attribute + "' and sqlname='" + column
                + "' and colowner='" + owner + "' and target='" + target + "'"));
        assertEquals(target, scalar(sql, "select setting from " + db.table("t_ili2db_column_prop")
                + " where tablename='" + owner + "' and subtype is null and columnname='" + column
                + "' and tag='ch.ehi.ili2db.foreignKey'"));
    }
}

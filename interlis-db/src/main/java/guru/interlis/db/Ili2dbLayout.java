package guru.interlis.db;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Technical identifiers and constants shared by the flat ili2db profile. */
final class Ili2dbLayout {
    static final String PREFIX = "ch.ehi.ili2db.";
    static final String SCHEMA_SENDER = "interlis-db/ili2db-5.5.2";
    static final int MAX_MODEL_CHARACTERS = 1_000_000;
    static final int LV95 = 2056;

    static final String BASKET = "T_ILI2DB_BASKET";
    static final String DATASET = "T_ILI2DB_DATASET";
    static final String INHERITANCE = "T_ILI2DB_INHERITANCE";
    static final String SETTINGS = "T_ILI2DB_SETTINGS";
    static final String TRAFO = "T_ILI2DB_TRAFO";
    static final String MODEL = "T_ILI2DB_MODEL";
    static final String CLASSNAME = "T_ILI2DB_CLASSNAME";
    static final String ATTRNAME = "T_ILI2DB_ATTRNAME";
    static final String COLUMN_PROP = "T_ILI2DB_COLUMN_PROP";
    static final String TABLE_PROP = "T_ILI2DB_TABLE_PROP";
    static final String KEY_OBJECT = "T_KEY_OBJECT";
    static final String SEQUENCE = "t_ili2db_seq";
    static final String DATASET_KEY = DATASET + "_datasetName_key";
    static final String MODEL_KEY = MODEL + "_modelName_iliversion_key";
    static final String ATTRNAME_KEY = ATTRNAME + "_ColOwner_SqlName_key";

    static final String ID_COLUMN = "T_Id";
    static final String BASKET_COLUMN = "T_basket";
    static final String TID_COLUMN = "T_Ili_Tid";
    static final String T_ID = ID_COLUMN.toLowerCase(Locale.ROOT);
    static final String T_BASKET = BASKET_COLUMN.toLowerCase(Locale.ROOT);
    static final String T_ILI_TID = TID_COLUMN.toLowerCase(Locale.ROOT);

    static final List<String> METADATA_TABLES = List.of(BASKET, DATASET, INHERITANCE, SETTINGS, TRAFO, MODEL,
            CLASSNAME, ATTRNAME, COLUMN_PROP, TABLE_PROP);
    static final Set<String> TECHNICAL_COLUMNS = Set.of(T_ID, T_BASKET, T_ILI_TID);
    private static final Set<String> GPKG_TABLES = Set.of(KEY_OBJECT.toLowerCase(Locale.ROOT), "gpkg_contents",
            "gpkg_spatial_ref_sys", "gpkg_geometry_columns", "gpkg_extensions", "gpkg_data_columns",
            "gpkg_data_column_constraints");
    private static final Set<String> PG_RELATIONS = Set.of(SEQUENCE, DATASET_KEY.toLowerCase(Locale.ROOT),
            MODEL_KEY.toLowerCase(Locale.ROOT), ATTRNAME_KEY.toLowerCase(Locale.ROOT));

    private Ili2dbLayout() { }

    static String tableName(String name, boolean gpkg) {
        return gpkg ? name : foldedName(name);
    }

    static String foldedName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** The input name has already been normalized by the name allocator. */
    static boolean reservedTable(String name, boolean gpkg) {
        return METADATA_TABLES.stream().anyMatch(name::equalsIgnoreCase)
                || (gpkg ? GPKG_TABLES.contains(name) || name.startsWith("sqlite_") : PG_RELATIONS.contains(name));
    }
}

# Conformance backlog

Source: pinned suite, 20 FAIL cases.

## Primary feature counts

- `UNCLASSIFIED`: 11
- `MISSING_ATTRIBUTE_TYPE`: 3
- `METAOBJECT`: 2
- `PATH`: 2
- `ASSOCIATION_CARDINALITY`: 1
- `PARAMETER`: 1

## Status

Generic/Deferred/Context is complete: 16/16 cases PASS, 0 false accepts. WP-22, WP-23 and WP-24 Translation are complete.

## Case table

| Case | Primary | Features | Expected | Actual |
|---|---|---|---|---|
| `ili23.Assoc23Test.assocComplete` | `ASSOCIATION_CARDINALITY` | `ASSOCIATION_CARDINALITY` | accept | rejected |
| `ili23.Attribute10Test.attributeComplete` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Attribute23Test.attributeComplete` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.CoordType23Test.coordTypeComplete` | `METAOBJECT` | `METAOBJECT` | accept | rejected |
| `ili23.Enumerations23Test.extendedAttr` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Enumerations23Test.extendedEnum` | `MISSING_ATTRIBUTE_TYPE` | `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili23.IliSymbols23Test.nestedComment` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.InterlisManual23Test.ilimanual_CoordSys` | `PATH` | `PARAMETER`, `STRING_RANGE`, `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.InterlisManual23Test.ilimanual_CoordSysAppendix` | `PATH` | `PARAMETER`, `STRING_RANGE`, `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.LineType23Test.lineTypeLineFormOrdering` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.MainRule23Test.mainRules_SyntaxIli23` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.NumericType23Test.numericTypeComplete` | `METAOBJECT` | `METAOBJECT` | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLForAssocToEmbedTopic` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLForAssocToNotEmbeddTopic` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.enumeration_GenerateXMLSchemaWithEnum` | `MISSING_ATTRIBUTE_TYPE` | `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili23.XMLGenerator23Test.structureAttr_GenerateXMLForEntendedStructAttr` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XSDMetaDataGenerator23Test.xsdmeta_null` | `UNCLASSIFIED` |  | accept | rejected |
| `ili24.Attribute24Test.attributeRef_RefAttrInClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili24.Attribute24Test.enum_List` | `MISSING_ATTRIBUTE_TYPE` | `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili24.Refhb24Test.roadsExgm2ien` | `PARAMETER` | `PARAMETER`, `GRAPHIC` | accept | rejected |

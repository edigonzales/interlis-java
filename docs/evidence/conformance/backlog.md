# Conformance backlog

Source: pinned suite, 31 FAIL cases.

## Primary feature counts

- `UNCLASSIFIED`: 16
- `DEFERRED_GENERICS`: 4
- `MISSING_ATTRIBUTE_TYPE`: 3
- `METAOBJECT`: 2
- `PATH`: 2
- `GENERIC_DOMAIN`: 2
- `ASSOCIATION_CARDINALITY`: 1
- `PARAMETER`: 1

## Status

WP-22, WP-23 and WP-24 Translation are complete; 562/593 selected 2.3/2.4 cases PASS with 0 false accepts.

## Case table

| Case | Primary | Features | Expected | Actual |
|---|---|---|---|---|
| `ili23.Assoc23Test.assocComplete` | `ASSOCIATION_CARDINALITY` | `ASSOCIATION_CARDINALITY` | accept | rejected |
| `ili23.Association23Test.association_AssociationAsTarget` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Association23Test.association_RoleRefToAnyClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Association23Test.association_SameRolenameInDiffExtTopics` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Attribute10Test.attributeComplete` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Attribute23Test.attributeComplete` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Classes23Test.classExtension_AcceptingClassSpecOfExtClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.CoordType23Test.coordTypeComplete` | `METAOBJECT` | `METAOBJECT` | accept | rejected |
| `ili23.Enumerations23Test.extendedAttr` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Enumerations23Test.extendedEnum` | `MISSING_ATTRIBUTE_TYPE` | `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili23.IliSymbols23Test.nestedComment` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.InterlisManual23Test.ilimanual_CoordSys` | `PATH` | `PARAMETER`, `STRING_RANGE`, `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.InterlisManual23Test.ilimanual_CoordSysAppendix` | `PATH` | `PARAMETER`, `STRING_RANGE`, `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.LineType23Test.lineTypeLineFormOrdering` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.MainRule23Test.mainRules_SyntaxIli23` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.NumericType23Test.numericTypeComplete` | `METAOBJECT` | `METAOBJECT` | accept | rejected |
| `ili23.ViewableGetAttributesAndRoles2Test.baseClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLForAssocToEmbedTopic` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLForAssocToNotEmbeddTopic` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.enumeration_GenerateXMLSchemaWithEnum` | `MISSING_ATTRIBUTE_TYPE` | `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili23.XMLGenerator23Test.structureAttr_GenerateXMLForEntendedStructAttr` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XSDMetaDataGenerator23Test.xsdmeta_null` | `UNCLASSIFIED` |  | accept | rejected |
| `ili24.Attribute24Test.attributeRef_RefAttrInClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili24.Attribute24Test.enum_List` | `MISSING_ATTRIBUTE_TYPE` | `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili24.GenericValueRanges24Test.contextInDifferentModel` | `DEFERRED_GENERICS` | `DEFERRED_GENERICS`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.contextInSameModel` | `DEFERRED_GENERICS` | `DEFERRED_GENERICS`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.genericInStructAttr` | `DEFERRED_GENERICS` | `DEFERRED_GENERICS`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.multipleContexts` | `GENERIC_DOMAIN` | `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.multipleContextsB` | `GENERIC_DOMAIN` | `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.multipleContextsExtending` | `DEFERRED_GENERICS` | `DEFERRED_GENERICS`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.Refhb24Test.roadsExgm2ien` | `PARAMETER` | `PARAMETER`, `GRAPHIC` | accept | rejected |

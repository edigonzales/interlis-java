# Conformance backlog

Source: pinned suite, 207 FAIL cases.

## Primary feature counts

- `TRANSLATION`: 30
- `DECLARATION`: 23
- `VIEW`: 22
- `UNCLASSIFIED`: 20
- `CONSTANT_KIND`: 13
- `EXTERNAL_CONSTRAINTS`: 10
- `FUNCTION`: 7
- `GEOMETRY_LINE`: 7
- `FALSE_ACCEPT`: 6
- `CONSTRAINT_UNIQUENESS`: 6
- `EXPRESSION`: 6
- `CONTEXT`: 6
- `ATTRIBUTE_STYLE_ROLE`: 4
- `OID`: 4
- `OID_RULE`: 4
- `PARAMETER`: 4
- `ATTRIBUTE_TYPE`: 3
- `ATTRIBUTE_MODIFIER`: 3
- `FORMAT`: 3
- `UNIT`: 3
- `MISSING_CONSTRAINT`: 2
- `MISSING_ATTRIBUTE_TYPE`: 2
- `PATH`: 2
- `BUILTIN_FUNCTION`: 2
- `METAOBJECT`: 2
- `INTERLIS1_TRANSFER`: 1
- `BLACKBOX`: 1
- `CLASS_VALUE`: 1
- `ALIGNMENT`: 1
- `FUNCTION_CALL`: 1
- `DOMAIN_EXTENDS`: 1
- `REFERENCE_SYSTEM`: 1
- `CONSTRAINT_EXISTENCE`: 1
- `CONSTRAINT_SET`: 1
- `LINE_FORM`: 1
- `BASKET_OID`: 1
- `TEMPORAL`: 1
- `IMPLICATION`: 1

## Dependency-safe single-feature candidates

- `TRANSLATION`: 19
- `VIEW`: 11
- `EXTERNAL_CONSTRAINTS`: 5
- `OID_RULE`: 4
- `DECLARATION`: 4
- `EXPRESSION`: 4
- `ATTRIBUTE_STYLE_ROLE`: 3
- `ATTRIBUTE_MODIFIER`: 3
- `GEOMETRY_LINE`: 3
- `INTERLIS1_TRANSFER`: 1
- `CONSTRAINT_UNIQUENESS`: 1
- `CONSTANT_KIND`: 1
- `BUILTIN_FUNCTION`: 1
- `FORMAT`: 1
- `PARAMETER`: 1
- `METAOBJECT`: 1
- `OID`: 1
- `TEMPORAL`: 1
- `MISSING_ATTRIBUTE_TYPE`: 1
- `IMPLICATION`: 1

## First-family decision

The role/association and declaration-validation family is implemented.
Role defaults, extended-attribute checks, name uniqueness and end-name matching are covered.
Translation, views and generic features remain later P2 families.

## Case table

| Case | Primary | Features | Expected | Actual |
|---|---|---|---|---|
| `ili23.AliasClass23Test.aliasClass_GenerateAliasClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Assoc23Test.assocComplete` | `VIEW` | `ASSOCIATION_CARDINALITY`, `DERIVED_ASSOCIATION`, `OID_RULE`, `ROLE_DERIVATION`, `TRANSLATION`, `VIEW` | accept | rejected |
| `ili23.Association23Test.association_AcceptExtendedRoles` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Association23Test.association_AssoDefExtendsProperty` | `ATTRIBUTE_STYLE_ROLE` | `ATTRIBUTE_STYLE_ROLE` | accept | rejected |
| `ili23.Association23Test.association_AssociationAsTarget` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Association23Test.association_AttrNameInExtClassConflict_Fail` | `FALSE_ACCEPT` |  | reject | accepted |
| `ili23.Association23Test.association_ClassesLevelTopicAndModel` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Association23Test.association_DerivedAssociationDef` | `FUNCTION` | `CONTRACTED`, `DERIVED_ASSOCIATION`, `FUNCTION`, `GEOMETRY_LINE`, `ROLE_DERIVATION`, `VIEW` | accept | rejected |
| `ili23.Association23Test.association_RoleRefToAnyClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Association23Test.association_SameRolenameInDiffExtTopics` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Attribute10Test.attributeComplete` | `INTERLIS1_TRANSFER` | `INTERLIS1_TRANSFER` | accept | rejected |
| `ili23.Attribute23Test.attributeComplete` | `TRANSLATION` | `ATTRIBUTE_DEFAULT`, `ATTRIBUTE_MODIFIER`, `TRANSLATION` | accept | rejected |
| `ili23.AttributePathType23Test.attributePathTypeComplete` | `ATTRIBUTE_TYPE` | `ATTRIBUTE_TYPE`, `CLASS_VALUE`, `CONTRACTED`, `FUNCTION`, `TRANSLATION` | accept | rejected |
| `ili23.Attributes23Test.ExtendedAttrInAnAssociation` | `ATTRIBUTE_STYLE_ROLE` | `ATTRIBUTE_STYLE_ROLE`, `CONTRACTED` | accept | rejected |
| `ili23.Attributes23Test.RefAttrInStructModelLevel` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Attributes23Test.RefAttrToAnyClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Attributes23Test.TransientAttributeWithoutAFactor_Ok` | `ATTRIBUTE_MODIFIER` | `ATTRIBUTE_MODIFIER` | accept | rejected |
| `ili23.Attributes23Test.attributePathType_ArgumentRestriction` | `FUNCTION` | `CONTRACTED`, `FUNCTION` | accept | rejected |
| `ili23.Attributes23Test.attributePathType_ClassTypeRestriction` | `ATTRIBUTE_TYPE` | `ATTRIBUTE_DEFAULT`, `ATTRIBUTE_TYPE`, `CLASS_VALUE` | accept | rejected |
| `ili23.BlackboxType23Test.blackboxTypeComplete` | `BLACKBOX` | `BLACKBOX`, `TRANSLATION` | accept | rejected |
| `ili23.Class23Test.classComplete` | `OID` | `MODIFIER_FINAL`, `OID`, `OID_RULE`, `TRANSLATION` | accept | rejected |
| `ili23.ClassType23Test.classTypeComplete` | `CLASS_VALUE` | `CLASS_VALUE`, `TRANSLATION` | accept | rejected |
| `ili23.Classes23Test.classExtension_DetectClassSpecOfExtBaseClass_Fail` | `FALSE_ACCEPT` |  | reject | accepted |
| `ili23.Classes23Test.classExtension_DetectExtClassOfSpecBaseClass_Fail` | `FALSE_ACCEPT` |  | reject | accepted |
| `ili23.Classes23Test.classExtension_ExtendingClassInExtendingTopic_Fail` | `FALSE_ACCEPT` |  | reject | accepted |
| `ili23.Classes23Test.class_AcceptingNoOidDefinition` | `OID_RULE` | `OID_RULE` | accept | rejected |
| `ili23.Classes23Test.class_AcceptingOidDefinition` | `OID_RULE` | `OID_RULE` | accept | rejected |
| `ili23.Classes23Test.class_DetectNonMatchingNames_Fail` | `FALSE_ACCEPT` |  | reject | accepted |
| `ili23.Constraint23Test.exstCstrt` | `TRANSLATION` | `CONSTRAINT_EXISTENCE`, `TRANSLATION`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtAssocPath` | `EXTERNAL_CONSTRAINTS` | `ATTRIBUTE_STYLE_ROLE`, `EXTERNAL_CONSTRAINTS`, `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtAssocRole` | `TRANSLATION` | `TRANSLATION`, `PATH` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtAttributeRef` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtClassRole` | `EXTERNAL_CONSTRAINTS` | `EXTERNAL_CONSTRAINTS`, `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtConjunction` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtDefined` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtDisjunction` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtEnumTreeConstant` | `CONSTANT_KIND` | `CONSTANT_KIND`, `ENUMTREEVAL`, `TRANSLATION`, `EXPRESSION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtEnumerationConstant` | `CONSTANT_KIND` | `CONSTANT_KIND`, `TRANSLATION`, `EXPRESSION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtEquality` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtFormattedConstant` | `TRANSLATION` | `TRANSLATION`, `EXPRESSION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtFunction` | `FUNCTION` | `CONTRACTED`, `FUNCTION`, `FUNCTION_CALL`, `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtGreaterThan` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtGreaterThanOrEqual` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtInequality` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtLessThan` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtLessThanOrEqual` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtNegation` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtNumericConstant` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtRefAttrRef` | `TRANSLATION` | `TRANSLATION`, `PATH` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtTextConstant` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.mandCstrtUndefinedConstant` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.plausiCstrt` | `TRANSLATION` | `CONSTRAINT_PLAUSIBILITY`, `TRANSLATION` | accept | rejected |
| `ili23.Constraint23Test.setCstrt` | `TRANSLATION` | `CONSTRAINT_SET`, `TRANSLATION`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraint23Test.uniqueCstrt` | `TRANSLATION` | `CONSTRAINT_UNIQUENESS`, `TRANSLATION`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraint23Test.uniqueCstrtLocal` | `TRANSLATION` | `CONSTRAINT_UNIQUENESS`, `TRANSLATION`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraints23Test.constraintDef_Basic` | `EXTERNAL_CONSTRAINTS` | `EXTERNAL_CONSTRAINTS` | accept | rejected |
| `ili23.Constraints23Test.constraintDef_RefViewableInSameTopic` | `EXTERNAL_CONSTRAINTS` | `EXTERNAL_CONSTRAINTS` | accept | rejected |
| `ili23.Constraints23Test.existence_BasicExistenceConstraint` | `DECLARATION` | `CONSTRAINT_EXISTENCE`, `DECLARATION`, `GEOMETRY_LINE`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraints23Test.uniqueGlobal_Basic` | `CONSTRAINT_UNIQUENESS` | `CONSTRAINT_UNIQUENESS`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraints23Test.uniqueGlobal_Path` | `MISSING_CONSTRAINT` | `CONSTRAINT_UNIQUENESS`, `EXTERNAL_CONSTRAINTS`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraints23Test.uniqueGlobal_PreCondition` | `CONSTRAINT_UNIQUENESS` | `CONSTRAINT_UNIQUENESS` | accept | rejected |
| `ili23.Constraints23Test.uniqueLocal_Basic` | `CONSTRAINT_UNIQUENESS` | `CONSTRAINT_UNIQUENESS`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.Constraints23Test.uniqueLocal_DefinedInStructure` | `CONSTRAINT_UNIQUENESS` | `CONSTRAINT_UNIQUENESS`, `MODEL_KIND`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.CoordType23Test.coordTypeComplete` | `DECLARATION` | `ATTRIBUTE_MODIFIER`, `DECLARATION`, `METAOBJECT`, `REFERENCE_SYSTEM`, `UNIT_REFERENCE` | accept | rejected |
| `ili23.Domain23Test.domainComplete` | `TRANSLATION` | `DOMAIN_EXTENDS`, `MODIFIER_FINAL`, `TRANSLATION` | accept | rejected |
| `ili23.Enum23Test.enumComplete` | `ALIGNMENT` | `ALIGNMENT`, `DECLARATION`, `ENUMTREEVAL`, `TRANSLATION` | accept | rejected |
| `ili23.Enumerations23Test.enumeration_BasicEnumType` | `DECLARATION` | `DECLARATION` | accept | rejected |
| `ili23.Enumerations23Test.enumeration_ExtendedEnumerationType` | `DECLARATION` | `DECLARATION`, `DOMAIN_EXTENDS` | accept | rejected |
| `ili23.Enumerations23Test.extendedAttr` | `DECLARATION` | `DECLARATION`, `UNIT` | accept | rejected |
| `ili23.Enumerations23Test.extendedEnum` | `MISSING_ATTRIBUTE_TYPE` | `DOMAIN_EXTENDS`, `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili23.ExpressionTest.simpleOk` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Expressions23Test.equals_attributePathType` | `CONSTANT_KIND` | `ATTRIBUTE_TYPE`, `CONSTANT_KIND`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_classType` | `CONSTANT_KIND` | `CLASS_VALUE`, `CONSTANT_KIND`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_coord` | `DECLARATION` | `DECLARATION`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_enumAllOf_const` | `CONSTANT_KIND` | `CONSTANT_KIND`, `DECLARATION`, `DOMAIN_EXTENDS`, `ENUMTREEVAL`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_enumExtended_const` | `CONSTANT_KIND` | `CONSTANT_KIND`, `DECLARATION`, `DOMAIN_EXTENDS`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_enum_const` | `CONSTANT_KIND` | `CONSTANT_KIND`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_formatted` | `EXPRESSION` | `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_function` | `FUNCTION_CALL` | `CONTRACTED`, `FUNCTION`, `FUNCTION_CALL` | accept | rejected |
| `ili23.Expressions23Test.equals_object` | `PATH` | `PATH`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_oid` | `DECLARATION` | `DECLARATION`, `OID`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_runtimeParam` | `PARAMETER` | `CONTRACTED`, `PARAMETER`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.equals_struct` | `EXPRESSION` | `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.expressions_AcceptBasicObjectWithRefAttrname` | `EXPRESSION` | `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.Expressions23Test.expressions_AcceptingBasicObjectWithRoleOfClass` | `EXTERNAL_CONSTRAINTS` | `EXTERNAL_CONSTRAINTS` | accept | rejected |
| `ili23.Expressions23Test.expressions_AcceptingExtendedObjectWithRoleOfClass` | `EXTERNAL_CONSTRAINTS` | `EXTERNAL_CONSTRAINTS` | accept | rejected |
| `ili23.Expressions23Test.expressions_AcceptingObjectsWithAssocPathEle` | `EXTERNAL_CONSTRAINTS` | `ATTRIBUTE_STYLE_ROLE`, `EXTERNAL_CONSTRAINTS` | accept | rejected |
| `ili23.Expressions23Test.expressions_BasenamePathElement` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.Expressions23Test.expressions_BasenameToUnrenamedViewable` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.Expressions23Test.expressions_ChecksRolenameOfAssociation` | `EXPRESSION` | `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.Expressions23Test.expressions_CompositionRoleWithoutCard` | `EXTERNAL_CONSTRAINTS` | `ATTRIBUTE_STYLE_ROLE`, `EXTERNAL_CONSTRAINTS` | accept | rejected |
| `ili23.Expressions23Test.expressions_ParentPathElement` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.Expressions23Test.expressions_ThisAreaThatArea` | `DECLARATION` | `DECLARATION`, `GEOMETRY_LINE`, `UNIT_REFERENCE`, `VIEW` | accept | rejected |
| `ili23.Expressions23Test.less_enum` | `CONSTANT_KIND` | `CONSTANT_KIND`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.less_enumAllOf_const` | `CONSTANT_KIND` | `CONSTANT_KIND`, `DECLARATION`, `DOMAIN_EXTENDS`, `ENUMTREEVAL`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.less_enum_domain` | `DECLARATION` | `DECLARATION`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.less_formatted` | `EXPRESSION` | `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.logical_const` | `CONSTANT_KIND` | `CONSTANT_KIND` | accept | rejected |
| `ili23.Expressions23Test.logical_function` | `BUILTIN_FUNCTION` | `BUILTIN_FUNCTION` | accept | rejected |
| `ili23.Expressions23Test.notEquals_coord` | `DECLARATION` | `DECLARATION`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.notEquals_enum_const` | `CONSTANT_KIND` | `CONSTANT_KIND`, `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.notEquals_formatted` | `EXPRESSION` | `EXPRESSION` | accept | rejected |
| `ili23.Expressions23Test.notEquals_object` | `PATH` | `PATH`, `EXPRESSION` | accept | rejected |
| `ili23.FormattedType23Test.formattedTypeComplete` | `FORMAT` | `DECLARATION`, `DOMAIN_EXTENDS`, `FORMAT`, `TRANSLATION` | accept | rejected |
| `ili23.FormattedType23Test.simple + baseDomainRestricted` | `FORMAT` | `FORMAT` | accept | rejected |
| `ili23.FunctionDef23Test.functionDefComplete` | `FUNCTION` | `CONTRACTED`, `FUNCTION`, `TRANSLATION` | accept | rejected |
| `ili23.Functions23Test.functions_AcceptFunctionDefInTopic` | `EXTERNAL_CONSTRAINTS` | `CONTRACTED`, `EXTERNAL_CONSTRAINTS`, `FUNCTION` | accept | rejected |
| `ili23.Functions23Test.functions_AcceptsBasicFunctionDef` | `FUNCTION` | `CONTRACTED`, `FUNCTION` | accept | rejected |
| `ili23.Functions23Test.functions_BasicFunctionOfObjectOF` | `FUNCTION` | `CONTRACTED`, `FUNCTION` | accept | rejected |
| `ili23.Functions23Test.functions_ObjectCountCheck` | `EXTERNAL_CONSTRAINTS` | `EXTERNAL_CONSTRAINTS` | accept | rejected |
| `ili23.Functions23Test.functions_textArg` | `BUILTIN_FUNCTION` | `BUILTIN_FUNCTION`, `EXPRESSION` | accept | rejected |
| `ili23.Gml23Test.gml_CheckOutputOfAttrTypes` | `FORMAT` | `ATTRIBUTE_STYLE_ROLE`, `ATTRIBUTE_TYPE`, `BLACKBOX`, `DOMAIN_EXTENDS`, `ENUMTREEVAL`, `FORMAT`, `GEOMETRY_LINE`, `MODIFIER_FINAL`, `OID`, `UNIT_REFERENCE`, `UNSUPPORTED_DOMAIN_TYPE` | accept | rejected |
| `ili23.IliSymbols23Test.decNumber_Extended` | `DOMAIN_EXTENDS` | `DOMAIN_EXTENDS`, `MODEL_KIND` | accept | rejected |
| `ili23.IliSymbols23Test.nestedComment` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.InterlisConversion23Test.iliconversion_CheckConversionOfDomainname` | `REFERENCE_SYSTEM` | `REFERENCE_SYSTEM`, `UNIT_REFERENCE` | accept | rejected |
| `ili23.InterlisConversion23Test.iliconversion_CheckViewDefWithALLOF` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.InterlisConversion23Test.iliconversion_ConversionOfExistenceConstraint` | `CONSTRAINT_EXISTENCE` | `CONSTRAINT_EXISTENCE`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.InterlisConversion23Test.iliconversion_ConversionOfGlobalUniqueConstraint` | `CONSTRAINT_UNIQUENESS` | `CONSTRAINT_UNIQUENESS`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.InterlisConversion23Test.iliconversion_ConversionOfLocalUniqueConstraint` | `CONSTRAINT_UNIQUENESS` | `CONSTRAINT_UNIQUENESS`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.InterlisConversion23Test.iliconversion_ConversionOfSETCONSTRAINT` | `CONSTRAINT_SET` | `CONSTRAINT_SET`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.InterlisConversion23Test.iliconversion_ili2GenerationOfAttrPathType` | `ATTRIBUTE_TYPE` | `ATTRIBUTE_TYPE`, `CLASS_VALUE`, `CONTRACTED`, `FUNCTION` | accept | rejected |
| `ili23.InterlisManual23Test.ilimanual_CoordSys` | `CONSTANT_KIND` | `ATTRIBUTE_STYLE_ROLE`, `CONSTANT_KIND`, `DOMAIN_EXTENDS`, `FORMAT`, `MODEL_KIND`, `PARAMETER`, `STRING_RANGE`, `UNIT`, `UNIT_REFERENCE`, `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.InterlisManual23Test.ilimanual_CoordSysAppendix` | `CONSTANT_KIND` | `ATTRIBUTE_STYLE_ROLE`, `CONSTANT_KIND`, `DOMAIN_EXTENDS`, `FORMAT`, `MODEL_KIND`, `PARAMETER`, `STRING_RANGE`, `UNIT`, `UNIT_REFERENCE`, `EXPRESSION`, `PATH` | accept | rejected |
| `ili23.InterlisManual23Test.ilimanual_Units` | `UNIT` | `CONTRACTED`, `MODEL_KIND`, `UNIT` | accept | rejected |
| `ili23.LineFormType23Test.lineFormTypeComplete` | `LINE_FORM` | `CONTRACTED`, `LINE_FORM`, `TRANSLATION` | accept | rejected |
| `ili23.LineType23Test.lineTypeComplete` | `GEOMETRY_LINE` | `CONTRACTED`, `GEOMETRY_LINE`, `LINE_FORM`, `TRANSLATION` | accept | rejected |
| `ili23.LineType23Test.lineTypeLineFormOrdering` | `GEOMETRY_LINE` | `CONTRACTED`, `GEOMETRY_LINE`, `LINE_FORM`, `TRANSLATION` | accept | rejected |
| `ili23.LineType23Test.simple` | `DECLARATION` | `DECLARATION`, `GEOMETRY_LINE` | accept | rejected |
| `ili23.MainRule23Test.mainRules_SyntaxIli23` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.MetaDataBasket23Test.metaDataBasketComplete` | `METAOBJECT` | `METAOBJECT`, `REFERENCE_SYSTEM`, `TRANSLATION` | accept | rejected |
| `ili23.Model23Test.modelComplete` | `TRANSLATION` | `CONTRACTED`, `MODEL_KIND`, `TRANSLATION` | accept | rejected |
| `ili23.Model23Test.modelImportsOrderingOk` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Models23Test.models_DomainAndRuntimeParam_SameName` | `PARAMETER` | `PARAMETER` | accept | rejected |
| `ili23.Models23Test.models_ImportedModelReferenceElements` | `METAOBJECT` | `METAOBJECT` | accept | rejected |
| `ili23.Models23Test.models_UnqualifiedUnitReferences` | `UNIT` | `UNIT`, `UNIT_REFERENCE` | accept | rejected |
| `ili23.NumericType23Test.numericTypeComplete` | `DECLARATION` | `ATTRIBUTE_MODIFIER`, `DECLARATION`, `METAOBJECT`, `REFERENCE_SYSTEM`, `TRANSLATION`, `UNIT_REFERENCE` | accept | rejected |
| `ili23.ObjectPathTest.simpleOk` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.OidType23Test.oidTypeComplete` | `OID` | `OID`, `TRANSLATION` | accept | rejected |
| `ili23.Role23Test.roleComplete` | `VIEW` | `DERIVED_ASSOCIATION`, `MODIFIER_FINAL`, `ROLE_DERIVATION`, `TRANSLATION`, `VIEW` | accept | rejected |
| `ili23.RuntimeParameter23Test.runtimeParameterComplete` | `PARAMETER` | `PARAMETER`, `TRANSLATION` | accept | rejected |
| `ili23.Struct23Test.structComplete` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.SurfaceType23Test.surface_BasicLineAttr` | `DECLARATION` | `DECLARATION`, `GEOMETRY_LINE` | accept | rejected |
| `ili23.TextType23Test.string_AcceptAbstractText` | `ATTRIBUTE_MODIFIER` | `ATTRIBUTE_MODIFIER` | accept | rejected |
| `ili23.TextType23Test.textTypeComplete` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Topic23Test.topicComplete` | `OID` | `BASKET_OID`, `MODIFIER_FINAL`, `OID`, `OID_RULE`, `TRANSLATION`, `VIEW_TOPIC` | accept | rejected |
| `ili23.Topic23Test.topicDependsOrderingOk` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Topics23Test.ExtendedAbstractTopicAbstractClassConcreteExtension` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Topics23Test.ExtendedAbstractTopicAbstractClass_fail` | `FALSE_ACCEPT` |  | reject | accepted |
| `ili23.Topics23Test.abstractTopicAbstractClassConcreteExtension` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.Topics23Test.topics_ExplicitBasketOidDefinition` | `OID_RULE` | `OID_RULE` | accept | rejected |
| `ili23.Topics23Test.topics_ExplicitTopicOidDefinition` | `BASKET_OID` | `BASKET_OID`, `OID_RULE` | accept | rejected |
| `ili23.Translation23Test.enumOk` | `TRANSLATION` | `TRANSLATION` | accept | rejected |
| `ili23.Translation23Test.simpleOk` | `GEOMETRY_LINE` | `GEOMETRY_LINE`, `TRANSLATION` | accept | rejected |
| `ili23.Unit23Test.unitComplete` | `UNIT` | `TRANSLATION`, `UNIT` | accept | rejected |
| `ili23.View23Test.abstractView_AbstractBaseInConcretViewDef` | `VIEW` | `ATTRIBUTE_MODIFIER`, `VIEW` | accept | rejected |
| `ili23.View23Test.abstractView_AcceptAbstractBaseInAbstractJoinDef` | `VIEW` | `ATTRIBUTE_MODIFIER`, `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptBasenameOfRenamedInspectionDef` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptBasenameOfUnrenamedInspectionDef` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptBasicInspectionDef` | `VIEW` | `CONSTRAINT_UNIQUENESS`, `VIEW`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.View23Test.view_AcceptBasicJoinDef` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptBasicSelectionViewDef` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptBasicUnionDef` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptBasicViewProjectionDef` | `FUNCTION` | `CONSTRAINT_UNIQUENESS`, `CONTRACTED`, `FUNCTION`, `VIEW`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili23.View23Test.view_AcceptJoinDefWithoutBasenames` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptPARENTPathElement` | `GEOMETRY_LINE` | `GEOMETRY_LINE`, `VIEW` | accept | rejected |
| `ili23.View23Test.view_AcceptSimpleBaseExtentionInView` | `VIEW` | `VIEW` | accept | rejected |
| `ili23.View23Test.view_ObjectPathWithRolename` | `VIEW` | `CONTRACTED`, `VIEW` | accept | rejected |
| `ili23.ViewDef23Test.viewAggregationComplete` | `VIEW` | `TRANSLATION`, `VIEW` | accept | rejected |
| `ili23.ViewDef23Test.viewInspectionComplete` | `VIEW` | `TRANSLATION`, `VIEW` | accept | rejected |
| `ili23.ViewDef23Test.viewJoinComplete` | `VIEW` | `TRANSLATION`, `VIEW` | accept | rejected |
| `ili23.ViewDef23Test.viewProjectionComplete` | `VIEW` | `TRANSLATION`, `VIEW` | accept | rejected |
| `ili23.ViewDef23Test.viewUnionComplete` | `VIEW` | `TRANSLATION`, `VIEW` | accept | rejected |
| `ili23.ViewableGetAttributesAndRoles2Test.baseClass` | `OID_RULE` | `OID_RULE` | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLForAssocAttrs` | `ATTRIBUTE_STYLE_ROLE` | `ATTRIBUTE_STYLE_ROLE` | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLForAssocToEmbedTopic` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLForAssocToNotEmbeddTopic` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.association_GenerateXMLWithExtendedRoles` | `DECLARATION` | `DECLARATION` | accept | rejected |
| `ili23.XMLGenerator23Test.attribute_GenerateXMLSchemaWithTransientAttribute` | `DECLARATION` | `ATTRIBUTE_DEFAULT`, `DECLARATION` | accept | rejected |
| `ili23.XMLGenerator23Test.class_GenerateXMLForExtendedClassDef` | `DECLARATION` | `DECLARATION`, `DOMAIN_EXTENDS` | accept | rejected |
| `ili23.XMLGenerator23Test.enumeration_GenerateXMLForEnumType` | `DECLARATION` | `DECLARATION` | accept | rejected |
| `ili23.XMLGenerator23Test.enumeration_GenerateXMLForExtEnumAttr` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.enumeration_GenerateXMLForExtEnumDomain` | `DECLARATION` | `DECLARATION`, `DOMAIN_EXTENDS` | accept | rejected |
| `ili23.XMLGenerator23Test.enumeration_GenerateXMLSchemaWithEnum` | `DECLARATION` | `DECLARATION`, `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili23.XMLGenerator23Test.linetype_GenerateXMLSchemaForPolyline` | `DECLARATION` | `DECLARATION`, `GEOMETRY_LINE`, `UNIT_REFERENCE` | accept | rejected |
| `ili23.XMLGenerator23Test.optionalMandatory_GenerateOptionalAndMandatoryAttrs` | `DECLARATION` | `DECLARATION` | accept | rejected |
| `ili23.XMLGenerator23Test.structureAttr_GenerateXMLForEntendedStructAttr` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XMLGenerator23Test.surface_GenerateXMLSchemaForSurface` | `DECLARATION` | `DECLARATION`, `GEOMETRY_LINE`, `UNIT_REFERENCE` | accept | rejected |
| `ili23.XMLGenerator23Test.text_GenerateXMLForAbstractTextAttr` | `ATTRIBUTE_MODIFIER` | `ATTRIBUTE_MODIFIER` | accept | rejected |
| `ili23.XSDMetaDataGenerator23Test.xsdmeta_assoc` | `ATTRIBUTE_STYLE_ROLE` | `ATTRIBUTE_STYLE_ROLE` | accept | rejected |
| `ili23.XSDMetaDataGenerator23Test.xsdmeta_null` | `UNCLASSIFIED` |  | accept | rejected |
| `ili23.XSDMetaDataGenerator23Test.xsdmeta_oid` | `OID` | `OID` | accept | rejected |
| `ili24.Attribute24Test.attributeRef_RefAttrInClass` | `UNCLASSIFIED` |  | accept | rejected |
| `ili24.Attribute24Test.dateTime` | `TEMPORAL` | `TEMPORAL` | accept | rejected |
| `ili24.Attribute24Test.enum_List` | `MISSING_ATTRIBUTE_TYPE` | `MISSING_ATTRIBUTE_TYPE` | accept | rejected |
| `ili24.Attribute24Test.multiArea` | `GEOMETRY_LINE` | `GEOMETRY_LINE` | accept | rejected |
| `ili24.Attribute24Test.multiPolyline` | `GEOMETRY_LINE` | `GEOMETRY_LINE` | accept | rejected |
| `ili24.Attribute24Test.multiSurface` | `GEOMETRY_LINE` | `GEOMETRY_LINE` | accept | rejected |
| `ili24.Constraints24Test.constraintsCompile + Constraint-Metamodelltests` | `MISSING_CONSTRAINT` | `CONSTRAINT_EXISTENCE`, `CONSTRAINT_PLAUSIBILITY`, `CONSTRAINT_SET`, `CONSTRAINT_UNIQUENESS`, `EXTERNAL_CONSTRAINTS`, `MISSING_CONSTRAINT` | accept | rejected |
| `ili24.Domain24Test.compile_Ok + Metamodellprüfungen` | `DECLARATION` | `CLASS_VALUE`, `DECLARATION`, `DOMAIN_CONSTRAINTS`, `DOMAIN_EXTENDS`, `UNSUPPORTED_DOMAIN_TYPE` | accept | rejected |
| `ili24.Expressions24Test.implication` | `IMPLICATION` | `IMPLICATION` | accept | rejected |
| `ili24.GenericValueRanges24Test.contextInDifferentModel` | `CONTEXT` | `CONTEXT`, `DEFERRED_GENERICS`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.contextInSameModel` | `CONTEXT` | `CONTEXT`, `DEFERRED_GENERICS`, `DOMAIN_EXTENDS`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.genericInStructAttr` | `CONTEXT` | `CONTEXT`, `DEFERRED_GENERICS`, `GENERIC_DOMAIN`, `GEOMETRY_LINE` | accept | rejected |
| `ili24.GenericValueRanges24Test.multipleContexts` | `CONTEXT` | `CONTEXT`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.multipleContextsB` | `CONTEXT` | `CONTEXT`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.GenericValueRanges24Test.multipleContextsExtending` | `CONTEXT` | `CONTEXT`, `DEFERRED_GENERICS`, `DOMAIN_EXTENDS`, `GENERIC_DOMAIN` | accept | rejected |
| `ili24.Refhb24Test.roadsExgm2ien` | `PARAMETER` | `GEOMETRY_LINE`, `MODEL_KIND`, `PARAMETER`, `UNIT`, `UNIT_REFERENCE`, `GRAPHIC`, `METAOBJECT`, `VIEW`, `CONSTANT_KIND` | accept | rejected |

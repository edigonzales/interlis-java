package guru.interlis.cli;

import guru.interlis.compiler.RejectionReason;
import guru.interlis.diagnostics.Diagnostic;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageDecisionTest {
    private static final Path CONFORMANCE = Path.of(System.getProperty("testdata.dir")).resolve("conformance");
    private static final Path FIXTURES = CONFORMANCE.resolve("normative");
    private static final String SET_CONTEXT = "SET condition has no individual object context; object values belong in WHERE";

    @TempDir
    Path home;

    @Test
    void legacyOidDeclarationsWorkWithAndWithoutContracted() {
        accepted(original("classComplete.ili"));
        accepted(control("ili23.Class23Test.classComplete.ili"));
        accepted(original("class_AcceptingOidDefinition.ili"));
        accepted(control("ili23.Classes23Test.class_AcceptingOidDefinition.ili"));
        accepted(original("topicComplete.ili"));
        accepted(control("ili23.Topic23Test.topicComplete.ili"));
        accepted(original("topics_ExplicitBasketOidDefinition.ili"));
        accepted(control("ili23.Topics23Test.topics_ExplicitBasketOidDefinition.ili"));
        accepted(original("topics_ExplicitTopicOidDefinition.ili"));
        accepted(control("ili23.Topics23Test.topics_ExplicitTopicOidDefinition.ili"));
        accepted(original("ViewableGetAttributesAndRoles2.ili"));
        accepted(control("ili23.ViewableGetAttributesAndRoles2Test.baseClass.ili"));

        Path wrongDomain = control("oid23-wrong-domain.ili");
        rejected(check(wrongDomain), rule(wrongDomain, 106, 164, "T", "OID AS must bind an OID domain/OID_DOMAIN"));
    }

    @Test
    void associationTargetsRequireOid() {
        Path source = original("association_AssociationAsTarget.ili");
        rejected(check(source), rule(source, 247, 257, "a2b",
                "association target a2b requires its own OID/REFERENCE_ASSOCIATION_OID"));
        accepted(control("ili23.Association23Test.association_AssociationAsTarget.ili"));
    }

    @Test
    void multipleLinksRequireOidInBothTranslations() {
        Path source = original("assocComplete.ili");
        String rule = "multiple links for the same participants require OID/ASSOCIATION_CARDINALITY_OID";
        rejected(check(source), rule(source, 346, 450, "assocA3", rule),
                rule(source, 1082, 1186, "assocB3", rule));
        accepted(control("ili23.Assoc23Test.assocComplete.ili"));
    }

    @Test
    void siblingRoleHidingMustAgreeInBothTranslations() {
        Path source = original("roleComplete.ili");
        String rule = "HIDING conflicts with a non-hiding sibling role/ROLE_HIDING_CONFLICT";
        rejected(check(source), rule(source, 610, 703, "assocA2q", rule),
                rule(source, 2140, 2233, "assocB2q", rule));
        accepted(control("ili23.Role23Test.roleComplete.ili"));
    }

    @Test
    void parentPathValueMustFitTheDerivedAttribute() {
        Path source = original("expressions_ParentPathElement.ili");
        rejected(check(source), mismatch(source, 297, 309, "TEXT*20", "TEXT*10"));
        accepted(control("ili23.Expressions23Test.expressions_ParentPathElement.ili"));
    }

    @Test
    void functionOutputMustFitTheViewAttribute() {
        Path source = original("view_AcceptBasicViewProjectionDef.ili");
        rejected(check(source), mismatch(source, 471, 498, "TEXT", "TEXT*80"));
        accepted(control("ili23.View23Test.view_AcceptBasicViewProjectionDef.ili"));
    }

    @Test
    void numericPathCannotSupplyBooleanViewAttribute() {
        Path source = original("iliconversion_CheckViewDefWithALLOF.ili");
        rejected(check(source), mismatch(source, 446, 462, "1..9999", "BOOLEAN"));
        accepted(control("ili23.InterlisConversion23Test.iliconversion_CheckViewDefWithALLOF.ili"));
    }

    @Test
    void runtimeParameterRequiresContractedDespiteSeparateNamespace() {
        Path source = original("models_DomainAndRuntimeParam_SameName.ili");
        rejected(check(source), contracted(source, 96, 112, "param"));
        accepted(control("ili23.Models23Test.models_DomainAndRuntimeParam_SameName.ili"));
    }

    @Test
    void runtimeParametersRequireContractedInBothTranslations() {
        Path source = original("runtimeParameterComplete.ili");
        rejected(check(source), contracted(source, 103, 121, "paramA1"), contracted(source, 124, 152, "paramA3"),
                contracted(source, 285, 303, "paramB1"), contracted(source, 306, 334, "paramB3"));
        accepted(control("ili23.RuntimeParameter23Test.runtimeParameterComplete.ili"));
    }

    @Test
    void areaInspectionAllowsAreaFactorsButRejectsAllOf() {
        Path source = original("expressions_ThisAreaThatArea.ili");
        rejected(check(source), rule(source, 527, 823, "BoFlaecheBoundaryView",
                "ALL OF is not allowed in UNION or AREA INSPECTION/VIEW_ALL_OF"));
        accepted(control("ili23.Expressions23Test.expressions_ThisAreaThatArea.ili"));
    }

    @Test
    void overlapIn23MustBePositiveButCanBeBelowCoordinatePrecision() {
        accepted(original("OverlapTooSmallFail.ili"));
        accepted(control("ili23.LineType23Test.overlapTooSmallFail.positive.ili"));
        Path zero = control("ili23.LineType23Test.overlapTooSmallFail.zero.ili");
        rejected(check(zero), rule(zero, 248, 339, "Geometry", "overlap tolerance must be greater than zero/OVERLAP_POSITIVE"));
    }

    @Test
    void continuousSubdivisionRequiresCircularInBothTranslations() {
        Path source = original("attributeComplete.ili");
        String rule = "continuous subdivision must be CIRCULAR/SUBDIVISION_CIRCULAR";
        rejected(check(source), rule(source, 368, 405, "attrA6", rule), rule(source, 1246, 1283, "attrB6", rule));
        accepted(control("ili23.Attribute23Test.attributeComplete.ili"));
    }

    @Test
    void translatedSetConditionsUseObjectValuesOnlyInWhere() {
        Path source = original("setCstrt.ili");
        rejected(check(source), setContext(source, 186, 192, "attrA1"), setContext(source, 442, 448, "attrB1"));
        accepted(control("ili23.Constraint23Test.setCstrt.ili"));
    }

    @Test
    void definedDoesNotSupplyAnObjectContextInSetConditions() {
        Path source = original("iliconversion_ConversionOfSETCONSTRAINT.ili");
        rejected(check(source), setContext(source, 240, 245, "attr2"));
        accepted(control("ili23.InterlisConversion23Test.iliconversion_ConversionOfSETCONSTRAINT.ili"));
    }

    @Test
    void transientBooleanStillRequiresTheCorrectEnumLiteral() {
        Path source = original("attribute_GenerateXMLSchemaWithTransientAttribute.ili");
        rejected(check(source), mismatch(source, 186, 191, "#True", "BOOLEAN"));
        accepted(control("ili23.XMLGenerator23Test.attribute_GenerateXMLSchemaWithTransientAttribute.ili"));
    }

    @Test
    void roadsGraphicNeedsExplicitSignatureUnitConversion() {
        Path roads = CONFORMANCE.resolve("no-oid/roads");
        Path germanModel = roads.resolve("RoadsExdm2ben.ili");
        Path abstractSymbology = roads.resolve("AbstractSymbology.ili");
        Path englishModel = roads.resolve("RoadsExdm2ien.ili");
        Path standardSymbology = roads.resolve("StandardSymbology.ili");
        Path source = roads.resolve("RoadsExgm2ien.ili");
        rejected(check(germanModel, abstractSymbology, englishModel, standardSymbology, source),
                rule(source, 2644, 2855, "StreetName", "incompatible signature parameter assignment/GRAPHIC_PARAMETER_TYPE"));
        accepted(germanModel, abstractSymbology, englishModel, standardSymbology,
                control("ili24.Refhb24Test.roadsExgm2ien.ili"));
    }

    private static Path original(String filename) {
        return FIXTURES.resolve("originals").resolve(filename);
    }

    private static Path control(String filename) {
        return FIXTURES.resolve(filename);
    }

    private CliExecutionResult check(Path... files) {
        var arguments = new ArrayList<>(List.of("check", "--no-cache", "--offline"));
        for (Path file : files) arguments.add(file.toString());
        try (var output = new PrintStream(OutputStream.nullOutputStream())) {
            return new DefaultCompilerCli().run(arguments,
                    new CliEnvironment(home, InputStream.nullInputStream(), output, output, Map.of()),
                    CancellationToken.none());
        }
    }

    private void accepted(Path... files) {
        CliExecutionResult result = check(files);
        assertEquals(CliTermination.SUCCESS, result.termination(), result::toString);
        assertEquals(List.of(), result.diagnostics());
    }

    private static void rejected(CliExecutionResult result, Finding... findings) {
        assertEquals(CliTermination.SOURCE_REJECTED, result.termination(), result::toString);
        var rejection = result.rejectionSummary().orElseThrow();
        assertEquals(Set.of(RejectionReason.SOURCE_ERRORS), rejection.reasons());
        assertEquals(List.of(), rejection.unsupportedFeatureIds());
        assertEquals(List.of(findings), result.diagnostics().stream().map(Finding::from).toList());
    }

    private static Finding contracted(Path source, int start, int end, String parameter) {
        return rule(source, start, end, parameter, "runtime parameters require CONTRACTED in INTERLIS 2.3/PARAMETER_CONTRACTED");
    }

    private static Finding rule(Path source, int start, int end, String element, String description) {
        return new Finding("ILI-SEM-0010", Severity.ERROR, Phase.SEMANTIC_CHECKING, "diagnostic.semantics.inheritance",
                range(source, start, end), List.of(new DiagnosticArgument.IdentifierArgument("element", element),
                        new DiagnosticArgument.TextArgument("rule", description)));
    }

    private static Finding mismatch(Path source, int start, int end, String actual, String expected) {
        return new Finding("ILI-SEM-0005", Severity.ERROR, Phase.TYPE_CHECKING, "diagnostic.semantics.type",
                range(source, start, end), List.of(new DiagnosticArgument.TypeNameArgument("actual", actual),
                        new DiagnosticArgument.TypeNameArgument("expected", expected)));
    }

    private static Finding setContext(Path source, int start, int end, String segment) {
        return new Finding("ILI-SEM-0008", Severity.ERROR, Phase.TYPE_CHECKING, "diagnostic.semantics.path",
                range(source, start, end), List.of(new DiagnosticArgument.IdentifierArgument("segment", segment),
                        new DiagnosticArgument.TextArgument("reason", SET_CONTEXT)));
    }

    private static SourceRange range(Path source, int start, int end) {
        return new SourceRange(new SourceId(source.toUri().toString()), new SourceRevision(1), start, end);
    }

    private record Finding(String code, Severity severity, Phase phase, String messageKey, SourceRange range,
                           List<DiagnosticArgument> arguments) {
        static Finding from(Diagnostic diagnostic) {
            return new Finding(diagnostic.code().value(), diagnostic.severity(), diagnostic.phase(), diagnostic.messageKey(),
                    diagnostic.primaryRange().orElseThrow(), diagnostic.arguments());
        }
    }
}

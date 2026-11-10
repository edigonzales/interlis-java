package guru.interlis.xtf24;

import guru.interlis.compiler.CompilationOutcome;
import guru.interlis.compiler.CompilationRequest;
import guru.interlis.compiler.CompilerEngines;
import guru.interlis.compiler.CompilerOptions;
import guru.interlis.compiler.ImportBinding;
import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.ImportSite;
import guru.interlis.compiler.ModelLocation;
import guru.interlis.compiler.ModelSelector;
import guru.interlis.compiler.ResolutionFingerprint;
import guru.interlis.compiler.SourceSet;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.QualifiedName;
import guru.interlis.model.SnapshotMismatchException;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferPlanner;
import guru.interlis.transfer.TransferPlanningResult;
import guru.interlis.syntax.ParseMode;
import guru.interlis.syntax.ParseOptions;
import guru.interlis.syntax.SyntaxServices;
import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Xtf24NamesTest {
    @Test
    void usesExpandedNamesAndQualifiesCollisionsWithStructures() {
        var plan = plan("""
                INTERLIS 2.4;
                MODEL Main AT "https://example.org" VERSION "1" =
                  XMLNS "urn:main";
                  TOPIC First = CLASS Item = END Item; END First;
                  TOPIC Second = STRUCTURE Item = END Item; END Second;
                END Main.
                MODEL Other AT "https://example.org" VERSION "1" =
                  TOPIC First = CLASS Item = END Item; END First;
                END Other.
                """, "Main", "Other");
        var names = new Xtf24Names(plan);
        var main = object(plan, "Main", "First", "Item");
        var other = object(plan, "Other", "First", "Item");
        assertEquals(new QName("urn:main", "First.Item"), names.objectName(main));
        assertEquals(new QName("urn:main", "Second.Item"),
                names.objectName(object(plan, "Main", "Second", "Item")));
        assertEquals(new QName("http://www.interlis.ch/xtf/2.4/Other", "Item"), names.objectName(other));
        assertSame(main, names.object(new QName("urn:main", "First.Item", "unrelated"), Set.of(main.symbol())));
        assertThrows(Xtf24Exception.class, () -> names.object(new QName("urn:main", "First.Item"), Set.of(other.symbol())));
    }

    @Test
    void usesCanonicalNamesWithDeclaredPlanAndKeepsTranslationContext() {
        var plan = plan("""
                INTERLIS 2.4;
                MODEL Original AT "https://example.org" VERSION "1" =
                  XMLNS "urn:original";
                  TOPIC T = CLASS A = value : TEXT; END A; END T;
                END Original.
                MODEL French (fr) AT "https://example.org" VERSION "1" TRANSLATION OF Original ["1"] =
                  TOPIC U = CLASS B = valeur : TEXT; END B; END U;
                END French.
                MODEL German (de) AT "https://example.org" VERSION "1" TRANSLATION OF Original ["1"] =
                  TOPIC V = CLASS C = wert : TEXT; END C; END V;
                END German.
                """, "French", "German");
        var names = new Xtf24Names(plan);
        var french = object(plan, "French", "U", "B");
        var german = object(plan, "German", "V", "C");
        var name = new QName("urn:original", "A");
        assertEquals(name, names.objectName(french));
        assertEquals(name, names.objectName(german));
        assertEquals(new QName("urn:original", "value"), names.slotName(german.slots().getFirst()));
        assertSame(french, names.object(name, Set.of(french.symbol())));
        assertSame(german, names.object(name, Set.of(german.symbol())));
        assertThrows(Xtf24Exception.class, () -> names.object(name, Set.of(french.symbol(), german.symbol())));
        assertEquals("Original", names.modelName(plan.model(french.model()).orElseThrow()));
        var topic = names.topic(new QName("urn:original", "T", "different"), Set.of(german.model()));
        assertEquals(german.topic().orElseThrow(), topic.symbol());
    }

    @Test
    void rejectsDifferentCanonicalDeclarationsWithSameWireName() {
        var plan = plan("""
                INTERLIS 2.4;
                MODEL One AT "https://example.org" VERSION "1" =
                  XMLNS "urn:shared";
                  TOPIC A = CLASS Same = END Same; END A;
                END One.
                MODEL Two AT "https://example.org" VERSION "1" =
                  XMLNS "urn:shared";
                  TOPIC B = CLASS Same = END Same; END B;
                END Two.
                """, "One", "Two");
        assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                assertThrows(Xtf24Exception.class, () -> new Xtf24Names(plan)).reason());
    }

    @Test
    void keepsInheritedFieldNamespacesAndOrder() {
        String text = """
                INTERLIS 2.4;
                MODEL Base AT "https://example.org" VERSION "1" =
                  TOPIC Common = CLASS Parent = first : TEXT; END Parent; END Common;
                END Base.
                MODEL Main AT "https://example.org" VERSION "1" =
                  IMPORTS Base;
                  TOPIC Data =
                    CLASS Child EXTENDS Base.Common.Parent = second : TEXT; END Child;
                  END Data;
                END Main.
                """;
        var plan = plan(text, "Main");
        var layout = new Xtf24Layout(plan);
        var child = object(plan, "Main", "Data", "Child");
        var fields = layout.fields(child);
        assertEquals(List.of(new QName("http://www.interlis.ch/xtf/2.4/Base", "first"),
                new QName("http://www.interlis.ch/xtf/2.4/Main", "second")),
                fields.stream().map(Xtf24Layout.Field::name).toList());
        assertEquals(List.of(0, 1), fields.stream().map(Xtf24Layout.Field::inheritanceLevel).toList());
        var foreign = object(plan(text.replace("VERSION \"1\"", "VERSION \"2\""), "Main"),
                "Main", "Data", "Child");
        assertThrows(SnapshotMismatchException.class, () -> layout.names().objectName(foreign));
    }

    private static ObjectDescriptor object(TransferPlan plan, String... name) {
        return plan.objects().stream().filter(type -> type.name().declared().equals(QualifiedName.of(name)))
                .findFirst().orElseThrow();
    }

    private static TransferPlan plan(String text, String... roots) {
        var source = new SourceText(new SourceId("memory:/names.ili"), new SourceRevision(1), text);
        List<ModelSelector> selectors = Arrays.stream(roots)
                .map(name -> (ModelSelector) new ModelSelector.ByName(name, Optional.empty(), Optional.empty()))
                .toList();
        var syntax = SyntaxServices.create().parse(source, ParseOptions.defaults(ParseMode.EDITOR),
                CancellationToken.none()).root();
        var bindings = syntax.models().stream().filter(model -> !model.imports().isEmpty()).map(importer -> {
            var imported = syntax.models().getFirst();
            var site = new ImportSite(importer.imports().getFirst().range(), "Main", "Base");
            var location = new ModelLocation(source.id(), source.revision(), "Base",
                    Optional.of(imported.modelVersion()), imported.range().startOffset());
            return new ImportBinding(site, new ImportResolution.Resolved(location));
        }).toList();
        var sources = SourceSet.of(Map.of(source.id(), source), bindings, List.of(), ResolutionFingerprint.none());
        var request = new CompilationRequest(selectors, sources, CompilerOptions.defaults());
        var outcome = CompilerEngines.create().compile(request, CancellationToken.none());
        var schema = assertInstanceOf(CompilationOutcome.Succeeded.class, outcome, outcome::toString).schema();
        return assertInstanceOf(TransferPlanningResult.Ready.class,
                TransferPlanner.plan(schema, TransferPlanner.Names.DECLARED)).plan();
    }
}

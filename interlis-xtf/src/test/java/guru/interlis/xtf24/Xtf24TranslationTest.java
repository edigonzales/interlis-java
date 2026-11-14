package guru.interlis.xtf24;

import guru.interlis.transfer.TransferEvent.ModelDeclaration;
import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferEvent.StartTransfer;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue.Structured;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Xtf24TranslationTest {
    private static final String SOURCE = """
            INTERLIS 2.4;
            MODEL Original AT "https://example.org" VERSION "1" =
              XMLNS "urn:original";
              DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                     C = COORD 0..100,0..100;
              CONTEXT Default = G = C;
              STRUCTURE Label = content : TEXT; END Label;
              STRUCTURE Detail = caption : Label; END Detail;
              TOPIC Left = CLASS Item = p : G; payload : Detail; END Item; END Left;
              TOPIC Right = STRUCTURE Item = END Item; END Right;
            END Original.
            MODEL French (fr) AT "https://example.org" VERSION "1" TRANSLATION OF Original ["1"] =
              DOMAIN Generique (GENERIC) = COORD NUMERIC, NUMERIC;
                     Concret = COORD 0..100,0..100;
              CONTEXT Defaut = Generique = Concret;
              STRUCTURE Etiquette = contenu : TEXT; END Etiquette;
              STRUCTURE Element = legende : Etiquette; END Element;
              TOPIC Gauche = CLASS Objet = position : Generique; charge : Element; END Objet; END Gauche;
              TOPIC Droite = STRUCTURE Objet = END Objet; END Droite;
            END French.
            MODEL German (de) AT "https://example.org" VERSION "1" TRANSLATION OF Original ["1"] =
              DOMAIN Generisch (GENERIC) = COORD NUMERIC, NUMERIC;
                     Konkret = COORD 0..100,0..100;
              CONTEXT Standard = Generisch = Konkret;
              STRUCTURE Schild = inhalt : TEXT; END Schild;
              STRUCTURE Detail = beschriftung : Schild; END Detail;
              TOPIC Links = CLASS Sache = punkt : Generisch; nutzlast : Detail; END Sache; END Links;
              TOPIC Rechts = STRUCTURE Sache = END Sache; END Rechts;
            END German.
            MODEL Consumer AT "https://example.org" VERSION "1" =
              XMLNS "urn:consumer";
              IMPORTS Original, French;
              TOPIC Mixed = CLASS Holder = payload : French.Element; END Holder; END Mixed;
            END Consumer.
            MODEL ImportsGerman AT "https://example.org" VERSION "1" =
              IMPORTS German;
              TOPIC Mixed = END Mixed;
            END ImportsGerman.
            """;
    private static final String PAYLOAD = "<Detail><caption><Label><content>text</content></Label></caption></Detail>";
    private static final String TRANSFER = """
            <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:original" xmlns:c="urn:consumer">
              <i:headersection><i:models><i:model>Original</i:model></i:models></i:headersection>
              <i:datasection><Left i:bid="b" i:domains="Original.G=Original.C">
                <Left.Item i:tid="one"><payload>%s</payload></Left.Item>
              </Left></i:datasection>
            </i:transfer>
            """.formatted(PAYLOAD);
    private static final String MIXED_TRANSFER = TRANSFER.replace("<i:model>Original</i:model>",
            "<i:model>Original</i:model><i:model>French</i:model>");

    @Test
    void readsCanonicalPrefixedElementsWithOriginalAndTranslationInThePlan() {
        var plan = plan(SOURCE, "Original", "French");
        var prefixed = MIXED_TRANSFER.replace("xmlns=", "xmlns:o=")
                .replaceAll("(<\\/?)(Left|payload|Detail|caption|Label|content)", "$1o:$2");
        var events = read(plan, prefixed);
        var header = (StartTransfer) events.getFirst();
        assertEquals("Original", plan.model(header.models().getFirst().model()).orElseThrow().name().declared().last());
        assertEquals("French", plan.model(header.models().getLast().model()).orElseThrow().name().declared().last());
        var basket = (StartBasket) events.get(1);
        assertEquals("Left", plan.topic(basket.topic()).orElseThrow().name().declared().last());
        var payload = firstStructure(((ObjectEvent) events.get(2)).object().data());
        assertEquals("Original.Detail", structureName(plan, payload));
        assertEquals("Original.Label", structureName(plan, firstStructure(payload)));
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void usesOriginalWireNamesAndDomainNamesWithOneTranslatedRoot() {
        var plan = plan(SOURCE, "French");
        var events = read(plan, TRANSFER);
        var start = (StartTransfer) events.getFirst();
        var basket = (StartBasket) events.get(1);
        assertEquals("French", plan.model(start.models().getFirst().model()).orElseThrow().name().declared().last());
        assertEquals("Gauche", plan.topic(basket.topic()).orElseThrow().name().declared().last());
        assertEquals("Generique", plan.symbol(basket.domainBindings().getFirst().generic()).known().orElseThrow().name().value());
        assertEquals("Concret", plan.symbol(basket.domainBindings().getFirst().concrete()).known().orElseThrow().name().value());
        var payload = firstStructure(((ObjectEvent) events.get(2)).object().data());
        assertEquals("French.Element", structureName(plan, payload));
        assertEquals("French.Etiquette", structureName(plan, firstStructure(payload)));
        var wire = write(plan, events);
        assertEquals(true, wire.contains("Original.G=Original.C"));
        assertEquals(true, wire.contains("Left.Item"));
        assertEquals(events, read(plan, wire));
    }

    @Test
    void resolvesImportedStructureFromTheFieldType() {
        var plan = plan(SOURCE, "Consumer");
        var transfer = TRANSFER.replace("<i:model>Original</i:model>", "<i:model>Consumer</i:model>")
                .replace("<Left i:bid=\"b\" i:domains=\"Original.G=Original.C\">", "<c:Mixed i:bid=\"b\">")
                .replace("Left.Item", "c:Holder")
                .replace("<payload>", "<c:payload>").replace("</payload>", "</c:payload>")
                .replace("</Left>", "</c:Mixed>");
        var events = read(plan, transfer);
        var payload = firstStructure(((ObjectEvent) events.get(2)).object().data());
        assertEquals("French.Element", structureName(plan, payload));
        assertEquals("French.Etiquette", structureName(plan, firstStructure(payload)));
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void resolvesDerivedStructureInTheContainingModelsImports() {
        var plan = plan("""
                INTERLIS 2.4;
                MODEL Shared AT "https://example.org" VERSION "1" =
                  STRUCTURE Base = END Base;
                END Shared.
                MODEL Original AT "https://example.org" VERSION "1" =
                  XMLNS "urn:original";
                  IMPORTS Shared;
                  STRUCTURE Detail EXTENDS Shared.Base = value : TEXT; END Detail;
                END Original.
                MODEL French (fr) AT "https://example.org" VERSION "1" TRANSLATION OF Original ["1"] =
                  IMPORTS Shared;
                  STRUCTURE Element EXTENDS Shared.Base = contenu : TEXT; END Element;
                END French.
                MODEL Consumer AT "https://example.org" VERSION "1" =
                  XMLNS "urn:consumer";
                  IMPORTS Shared, French;
                  TOPIC Main = CLASS Holder = payload : Shared.Base; END Holder; END Main;
                END Consumer.
                """, "Consumer", "Original");
        var transfer = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:original" xmlns:c="urn:consumer">
                  <i:headersection><i:models><i:model>Consumer</i:model>%s</i:models></i:headersection>
                  <i:datasection><c:Main i:bid="b"><c:Holder i:tid="id">
                    <c:payload><Detail><value>data</value></Detail></c:payload>
                  </c:Holder></c:Main></i:datasection>
                </i:transfer>
                """;
        for (var extraModel : List.of("", "<i:model>Original</i:model>")) {
            var events = read(plan, transfer.formatted(extraModel));
            var payload = firstStructure(((ObjectEvent) events.get(2)).object().data());
            assertEquals("French.Element", structureName(plan, payload));
            assertEquals(events, read(plan, write(plan, events)));
        }
    }

    @Test
    void retainsDecodableStructureOutsideTheFieldType() {
        var plan = plan(SOURCE, "Original", "French");
        var events = read(plan, MIXED_TRANSFER.replace(PAYLOAD, "<Label><content>text</content></Label>"));
        var payload = firstStructure(((ObjectEvent) events.get(2)).object().data());
        assertEquals("Original.Label", structureName(plan, payload));
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void rejectsTranslatedBasketWhenItsWireNameSelectsTheOriginal() {
        var plan = plan(SOURCE, "Original", "French");
        var original = read(plan, MIXED_TRANSFER);
        var translated = read(plan, TRANSFER.replace("<i:model>Original</i:model>", "<i:model>French</i:model>"));
        var output = new ByteArrayOutputStream();
        try (var writer = new Xtf24Writer(plan, output)) {
            writer.write(original.getFirst());
            var failure = assertThrows(Xtf24Exception.class, () -> writer.write(translated.get(1)));
            assertEquals(Xtf24Exception.Reason.UNSUPPORTED, failure.reason());
        }
        assertFalse(output.toString(StandardCharsets.UTF_8).contains("urn:original"));
    }

    @Test
    void rejectsOriginalHeaderWhenItsWireNameSelectsTheOnlyTranslatedRoot() {
        var plan = plan(SOURCE, "French");
        var original = plan.models().stream().filter(model -> model.name().declared().last().equals("Original"))
                .findFirst().orElseThrow();
        var declaration = new ModelDeclaration(original.symbol(), original.languageVersion(), Optional.empty(), Optional.empty());
        var header = new StartTransfer(Optional.empty(), Optional.empty(), Optional.empty(), List.of(declaration));
        var output = new ByteArrayOutputStream();
        try (var writer = new Xtf24Writer(plan, output)) {
            assertEquals(Xtf24Exception.Reason.UNSUPPORTED,
                    assertThrows(Xtf24Exception.class, () -> writer.write(header)).reason());
        }
        assertEquals(0, output.size());
    }

    @Test
    void preservesExplicitHeaderDeclarationsForTwoTranslationsOfTheSameModel() {
        var plan = plan(SOURCE, "French", "ImportsGerman");
        var transfer = """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS">
                  <i:headersection><i:models><i:model>French</i:model><i:model>German</i:model></i:models></i:headersection>
                  <i:datasection/>
                </i:transfer>
                """;
        var events = read(plan, transfer);
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void choosesCanonicalOriginalForIndistinguishableTranslatedRoots() {
        for (var roots : java.util.List.of(new String[]{"French", "German"}, new String[]{"German", "French"})) {
            var plan = plan(SOURCE, roots);
            var events = read(plan, TRANSFER);
            var start = (StartTransfer) events.getFirst();
            var basket = (StartBasket) events.get(1);
            assertEquals("Original", plan.model(start.models().getFirst().model()).orElseThrow().name().declared().last());
            assertEquals("Left", plan.topic(basket.topic()).orElseThrow().name().declared().last());
            assertEquals(events, read(plan, write(plan, events)));
        }
    }

    private static Structured firstStructure(Structured value) {
        return assertInstanceOf(Structured.class, value.values().values().iterator().next());
    }

    private static String structureName(TransferPlan plan, Structured value) {
        return plan.object(value.type()).orElseThrow().name().declared().toString();
    }
}

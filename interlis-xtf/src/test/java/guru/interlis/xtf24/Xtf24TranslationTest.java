package guru.interlis.xtf24;

import guru.interlis.transfer.TransferEvent.StartBasket;
import guru.interlis.transfer.TransferEvent.StartTransfer;
import org.junit.jupiter.api.Test;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Xtf24TranslationTest {
    private static final String SOURCE = """
            INTERLIS 2.4;
            MODEL Original AT "https://example.org" VERSION "1" =
              XMLNS "urn:original";
              DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                     C = COORD 0..100,0..100;
              CONTEXT Default = G = C;
              TOPIC Left = CLASS Item = p : G; END Item; END Left;
              TOPIC Right = STRUCTURE Item = END Item; END Right;
            END Original.
            MODEL French (fr) AT "https://example.org" VERSION "1" TRANSLATION OF Original ["1"] =
              DOMAIN Generique (GENERIC) = COORD NUMERIC, NUMERIC;
                     Concret = COORD 0..100,0..100;
              CONTEXT Defaut = Generique = Concret;
              TOPIC Gauche = CLASS Objet = position : Generique; END Objet; END Gauche;
              TOPIC Droite = STRUCTURE Objet = END Objet; END Droite;
            END French.
            MODEL German (de) AT "https://example.org" VERSION "1" TRANSLATION OF Original ["1"] =
              DOMAIN Generisch (GENERIC) = COORD NUMERIC, NUMERIC;
                     Konkret = COORD 0..100,0..100;
              CONTEXT Standard = Generisch = Konkret;
              TOPIC Links = CLASS Sache = punkt : Generisch; END Sache; END Links;
              TOPIC Rechts = STRUCTURE Sache = END Sache; END Rechts;
            END German.
            """;
    private static final String TRANSFER = """
            <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns="urn:original">
              <i:headersection><i:models><i:model>Original</i:model></i:models></i:headersection>
              <i:datasection><Left i:bid="b" i:domains="Original.G=Original.C"><Left.Item i:tid="one"/></Left></i:datasection>
            </i:transfer>
            """;

    @Test
    void readsCanonicalPrefixedElementsWithOriginalAndTranslationInThePlan() {
        var plan = plan(SOURCE, "Original", "French");
        var prefixed = TRANSFER.replace("xmlns=", "xmlns:o=")
                .replace("<Left", "<o:Left").replace("</Left", "</o:Left");
        var events = read(plan, prefixed);
        var header = (StartTransfer) events.getFirst();
        assertEquals("Original", plan.model(header.models().getFirst().model()).orElseThrow().name().declared().last());
        var basket = (StartBasket) events.get(1);
        assertEquals("Left", plan.topic(basket.topic()).orElseThrow().name().declared().last());
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
        var wire = write(plan, events);
        assertEquals(true, wire.contains("Original.G=Original.C"));
        assertEquals(true, wire.contains("Left.Item"));
        assertEquals(events, read(plan, wire));
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
}

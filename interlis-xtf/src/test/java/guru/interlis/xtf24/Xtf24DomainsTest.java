package guru.interlis.xtf24;

import guru.interlis.model.DomainSymbol;
import guru.interlis.transfer.TransferEvent.StartBasket;
import org.junit.jupiter.api.Test;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Xtf24DomainsTest {
    private static final String MODEL = """
            INTERLIS 2.4;
            MODEL GenericExample AT "https://example.org" VERSION "1" =
              DOMAIN G (GENERIC) = COORD NUMERIC, NUMERIC;
                     A = COORD 0..100,0..100;
                     B = COORD 200..300,200..300;
              CONTEXT Default = G = %s;
              TOPIC Base =
                %s
                CLASS Item = point : G; END Item;
              END Base;
              TOPIC Data EXTENDS Base = END Data;
            END GenericExample.
            """;

    @Test
    void retainsDifferentDeferredBindingsInConsecutiveBaskets() {
        var plan = plan(MODEL.formatted("A OR B", "DEFERRED GENERICS G;"), "GenericExample");
        var events = read(plan, transfer("""
                <Data ili:bid="b1" ili:domains="GenericExample.G=GenericExample.A"/>
                <Data ili:bid="b2" ili:domains="GenericExample.G=GenericExample.B"/>
                """));
        var first = (StartBasket) events.get(1);
        var second = (StartBasket) events.get(3);
        var a = first.domainBindings().getFirst();
        var b = second.domainBindings().getFirst();
        assertEquals(a.generic(), b.generic());
        assertEquals("A", plan.symbol(a.concrete()).known().orElseThrow().name().value());
        assertEquals("B", plan.symbol(b.concrete()).known().orElseThrow().name().value());
        var topic = plan.topic(first.topic()).orElseThrow();
        var domains = new Xtf24Domains(plan, new Xtf24Names(plan));
        var aType = domains.resolve(topic, first.domainBindings()).get(a.generic());
        var bType = domains.resolve(topic, second.domainBindings()).get(b.generic());
        assertNotEquals(aType, bType);
        assertEquals(((DomainSymbol) plan.symbol(a.concrete()).known().orElseThrow()).typeId(), aType.type());
        assertEquals(((DomainSymbol) plan.symbol(b.concrete()).known().orElseThrow()).typeId(), bType.type());
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void readsGeometryInConsecutiveGenericBasketContexts() {
        var plan = plan(MODEL.formatted("A OR B", "DEFERRED GENERICS G;"), "GenericExample");
        var xml = transfer("""
                <Data ili:bid="b1" ili:domains="GenericExample.G=GenericExample.A">
                  <Item ili:tid="one"><point><g:coord><g:c1>1.001</g:c1><g:c2>2</g:c2></g:coord></point></Item>
                </Data>
                <Data ili:bid="b2" ili:domains="GenericExample.G=GenericExample.B">
                  <Item ili:tid="two"><point><g:coord><g:c1>250</g:c1><g:c2>260</g:c2></g:coord></point></Item>
                </Data>
                """).replace("<ili:transfer ", "<ili:transfer xmlns:g=\"http://www.interlis.ch/geometry/1.0\" ");
        var events = read(plan, xml);
        var first = ((guru.interlis.transfer.TransferEvent.ObjectEvent) events.get(2)).object();
        var second = ((guru.interlis.transfer.TransferEvent.ObjectEvent) events.get(5)).object();
        var firstPoint = (guru.interlis.transfer.TransferGeometry.Coordinate) first.data().values().values().iterator().next();
        var secondPoint = (guru.interlis.transfer.TransferGeometry.Coordinate) second.data().values().values().iterator().next();
        assertEquals("1.001", firstPoint.ordinates().getFirst().lexeme());
        assertEquals("250", secondPoint.ordinates().getFirst().lexeme());
        assertEquals(events, read(plan, write(plan, events)));
    }

    @Test
    void requiresExplicitFixedBindingsOnReadAndWrite() {
        var plan = plan(MODEL.formatted("A", ""), "GenericExample");
        assertEquals(Xtf24Exception.Reason.INVALID_WIRE, assertThrows(Xtf24Exception.class,
                () -> read(plan, transfer("<Data ili:bid=\"b1\"/>"))).reason());
        var events = read(plan, transfer("<Data ili:bid=\"b1\" ili:domains=\"GenericExample.G=GenericExample.A\"/>"));
        var start = (StartBasket) events.get(1);
        var noBindings = new StartBasket(start.id(), start.topic(), start.additionalTopics(),
                start.kind(), start.consistency(), start.startState(), start.endState(), start.stableOid(),
                java.util.List.of());
        try (var writer = new Xtf24Writer(plan, new java.io.ByteArrayOutputStream())) {
            writer.write(events.getFirst());
            assertEquals(Xtf24Exception.Reason.INVALID_WIRE,
                    assertThrows(Xtf24Exception.class, () -> writer.write(noBindings)).reason());
        }
    }

    @Test
    void rejectsConflictingAssignmentsInTheSameBasket() {
        var plan = plan(MODEL.formatted("A OR B", "DEFERRED GENERICS G;"), "GenericExample");
        assertEquals(Xtf24Exception.Reason.INVALID_WIRE, assertThrows(Xtf24Exception.class, () -> read(plan,
                transfer("""
                        <Data ili:bid="b1"
                          ili:domains="GenericExample.G=GenericExample.A GenericExample.G=GenericExample.B"/>
                        """))).reason());
    }

    private static String transfer(String baskets) {
        return """
                <ili:transfer xmlns:ili="http://www.interlis.ch/xtf/2.4/INTERLIS"
                    xmlns="http://www.interlis.ch/xtf/2.4/GenericExample">
                  <ili:headersection><ili:models><ili:model>GenericExample</ili:model></ili:models></ili:headersection>
                  <ili:datasection>%s</ili:datasection>
                </ili:transfer>
                """.formatted(baskets);
    }
}

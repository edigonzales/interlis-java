package guru.interlis.xtf24;

import guru.interlis.transfer.TransferEvent.ObjectEvent;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import org.junit.jupiter.api.Test;

import static guru.interlis.xtf24.Xtf24EnvelopeTest.read;
import static guru.interlis.xtf24.Xtf24EnvelopeTest.write;
import static guru.interlis.xtf24.Xtf24TestSchemas.plan;
import static org.junit.jupiter.api.Assertions.*;

class Xtf24InheritanceTest {
    @Test
    void placesInheritedEmbeddingBeforeDerivedAttributesWithDeclaringNamespaces() {
        var plan = plan("""
                INTERLIS 2.4;
                MODEL Base AT "https://example.org" VERSION "1" =
                  XMLNS "urn:base";
                  TOPIC Common =
                    CLASS Parent = END Parent;
                    CLASS Item = title: TEXT; END Item;
                    ASSOCIATION Note (ABSTRACT) =
                      parent -- {0..1} Parent;
                      item -- {0..*} Item;
                      remark : TEXT;
                    END Note;
                  END Common;
                END Base.
                MODEL Main AT "https://example.org" VERSION "1" =
                  XMLNS "urn:main";
                  IMPORTS Base;
                  TOPIC Data EXTENDS Base.Common =
                    CLASS Child EXTENDS Base.Common.Item = label: TEXT; END Child;
                    ASSOCIATION Concrete EXTENDS Base.Common.Note = extra : TEXT; END Concrete;
                  END Data;
                END Main.
                """, "Main");
        var events = read(plan, """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS" xmlns:b="urn:base" xmlns:m="urn:main">
                  <i:headersection><i:models><i:model>Main</i:model></i:models></i:headersection>
                  <i:datasection><m:Data i:bid="b">
                    <m:Child i:tid="child"><b:title>base</b:title>
                      <b:parent i:ref="missing"><m:Concrete><b:remark>inherited</b:remark><m:extra>own</m:extra></m:Concrete></b:parent>
                      <m:label>child</m:label>
                    </m:Child>
                  </m:Data></i:datasection>
                </i:transfer>
                """);
        var object = ((ObjectEvent) events.get(2)).object();
        assertEquals(new Text("base"), object.data().values().values().iterator().next());
        var payload = object.data().values().values().stream().filter(Structured.class::isInstance)
                .map(Structured.class::cast).findFirst().orElseThrow();
        assertEquals("Concrete", plan.object(payload.type()).orElseThrow().name().declared().last());
        assertEquals(java.util.List.of("inherited", "own"), payload.values().values().stream()
                .filter(Text.class::isInstance).map(Text.class::cast).map(Text::value).toList());
        var wire = write(plan, events);
        assertTrue(wire.indexOf(">base<") < wire.indexOf(">inherited<"));
        assertTrue(wire.indexOf(">own<") < wire.indexOf(">child<"));
        assertTrue(wire.contains("urn:base"));
        assertTrue(wire.contains("urn:main"));
        assertEquals(events, read(plan, wire));
    }

    @Test
    void keepsThePositionOfAnOverriddenAttribute() {
        var plan = plan("""
                INTERLIS 2.4;
                MODEL Main AT "https://example.org" VERSION "1" =
                  TOPIC Data =
                    CLASS Parent = first: TEXT; second: TEXT; END Parent;
                    CLASS Child EXTENDS Parent =
                      first (EXTENDED): TEXT*5;
                      third : TEXT;
                    END Child;
                  END Data;
                END Main.
                """, "Main");
        var child = plan.objects().stream().filter(type -> type.name().declared().last().equals("Child"))
                .findFirst().orElseThrow();
        var fields = new Xtf24Layout(plan).fields(child);
        assertEquals(java.util.List.of("first", "second", "third"),
                fields.stream().map(field -> field.name().getLocalPart()).toList());
        assertEquals(java.util.List.of(0, 0, 1), fields.stream().map(Xtf24Layout.Field::inheritanceLevel).toList());
    }
}

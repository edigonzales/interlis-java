package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class TopicEnumFixtures {
    final String base;
    final String derived;
    final Path baseSource;
    final Path derivedSource;
    private final Path work;
    private final int version;

    TopicEnumFixtures(Path work, int version) throws Exception {
        this.work = work;
        this.version = version;
        base = "EnumTopicBase" + version;
        derived = "EnumTopicDerived" + version;
        baseSource = Files.writeString(work.resolve(base + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s AT "https://example.org/enum" VERSION "1" =
                  TOPIC Core =
                    CLASS Station = State : (open, closed); END Station;
                  END Core;
                END %s.
                """.formatted(version % 10, base, base));
        derivedSource = Files.writeString(work.resolve(derived + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s AT "https://example.org/enum" VERSION "1" =
                  IMPORTS %s;
                  TOPIC Region EXTENDS %s.Core =
                    CLASS LocalStation EXTENDS %s.Core.Station =
                      State (EXTENDED) : (open (daily, weekend));
                    END LocalStation;
                  END Region;
                END %s.
                """.formatted(version % 10, derived, base, base, base, derived));
    }

    Path delivery(String name, String openValue) throws Exception {
        String contents = version == 23 ? """
                <?xml version="1.0" encoding="UTF-8"?>
                <TRANSFER xmlns="http://www.interlis.ch/INTERLIS2.3">
                  <HEADERSECTION VERSION="2.3" SENDER="test"><MODELS>
                    <MODEL NAME="%1$s" VERSION="1" URI="https://example.org/enum"/>
                  </MODELS></HEADERSECTION>
                  <DATASECTION><%1$s.Region BID="region">
                    <%1$s.Region.LocalStation TID="one"><State>%2$s</State></%1$s.Region.LocalStation>
                    <%1$s.Region.LocalStation TID="two"><State>closed</State></%1$s.Region.LocalStation>
                    <%1$s.Region.LocalStation TID="three"/>
                  </%1$s.Region></DATASECTION>
                </TRANSFER>
                """.formatted(derived, openValue) : """
                <i:transfer xmlns:i="http://www.interlis.ch/xtf/2.4/INTERLIS"
                      xmlns:d="http://www.interlis.ch/xtf/2.4/%1$s" xmlns:b="http://www.interlis.ch/xtf/2.4/%2$s">
                  <i:headersection><i:models><i:model>%1$s</i:model></i:models><i:sender>test</i:sender></i:headersection>
                  <i:datasection><d:Region i:bid="region">
                    <d:LocalStation i:tid="one"><b:State>%3$s</b:State></d:LocalStation>
                    <d:LocalStation i:tid="two"><b:State>closed</b:State></d:LocalStation>
                    <d:LocalStation i:tid="three"/>
                  </d:Region></i:datasection>
                </i:transfer>
                """.formatted(derived, base, openValue);
        return Files.writeString(work.resolve(name + ".xtf"), contents);
    }

    void assertValues(Path file, String openValue) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(derived), headerModels(data, version));
        var objects = children(basket(data, version, derived + ".Region", "region"));
        assertEquals(3, objects.size());
        for (var object : objects) {
            assertName(object, version, derived + ".Region.LocalStation");
            var state = child(object, "State");
            String tid = id(object, version, "tid");
            if (tid.equals("three")) {
                assertNull(state);
            } else {
                if (version == 24) assertName(state, version, base + ".State");
                assertEquals(tid.equals("one") ? openValue : "closed", state.getTextContent());
            }
        }
        assertEquals(Set.of("one", "two", "three"), Set.copyOf(objects.stream()
                .map(object -> id(object, version, "tid")).toList()));
    }

    void assertStored(DbTestSupport.Target db, Connection sql, String value) throws Exception {
        assertEquals(Set.of("one:" + value, "two:closed", "three:NULL"), catalogKeys(sql,
                "SELECT s.t_ili_tid || ':' || COALESCE(s.astate, 'NULL') FROM " + db.table("station") + " s JOIN "
                + db.table("localstation") + " l ON l.t_id=s.t_id AND l.t_basket=s.t_basket"));
    }

    void assertMapping(DbTestSupport.Target db, Connection sql) throws Exception {
        assertEquals(Set.of(base + ".Core.Station.State:station:astate"), catalogKeys(sql,
                "SELECT iliname || ':' || colowner || ':' || sqlname FROM " + db.table("t_ili2db_attrname")
                + " WHERE iliname LIKE '%.State'"));
    }
}

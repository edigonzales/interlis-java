package guru.interlis.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Set;
import java.util.stream.Collectors;

import static guru.interlis.db.DbTestSupport.catalogKeys;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.foreignKeyColumns;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

record AlternativeRoleFixture(Path work, int version) {
    String model() { return "AlternativeRole" + version; }

    Path source() throws Exception {
        return Files.writeString(work.resolve(model() + ".ili"), """
                INTERLIS 2.%d;
                MODEL %s (en) AT "https://example.org/interlis/db" VERSION "1" =
                  TOPIC Data =
                    CLASS Registry (ABSTRACT) = END Registry;
                    CLASS Station EXTENDS Registry = END Station;
                    CLASS StationLeaf EXTENDS Station = END StationLeaf;
                    CLASS Hidden EXTENDS Registry = END Hidden;
                    CLASS Landmark = END Landmark;
                    CLASS Ticket = END Ticket;
                    ASSOCIATION TicketRegistry =
                      Entries -- {0..*} Ticket;
                      RegisteredAt -- {0..1} Registry;
                    END TicketRegistry;
                    ASSOCIATION ChoiceLink =
                      Candidate -- {0..*} Station OR Landmark;
                      SourceTicket -- {0..*} Ticket;
                    END ChoiceLink;
                  END Data;
                END %s.
                """.formatted(version - 20, model(), model()));
    }

    Path transfer(String dataset, String stationTarget) throws Exception {
        String ticket = dataset + "-ticket";
        String landmark = dataset + "-landmark";
        String objects = object("Ticket", ticket, "") + object("Landmark", landmark, "")
                + object("ChoiceLink", null, reference("Candidate", landmark) + reference("SourceTicket", ticket))
                + object("ChoiceLink", null, reference("Candidate", dataset + "-" + stationTarget) + reference("SourceTicket", ticket))
                + object("StationLeaf", dataset + "-station", "") + object("Hidden", dataset + "-hidden", "");
        return Files.writeString(work.resolve(dataset + ".xtf"),
                referenceDatasetFixture(model(), version, dataset + "-basket", objects));
    }

    String embeddedTargetModel() {
        return "EmbeddedAlternativeTarget" + version;
    }

    Path embeddedTargetSource() throws Exception {
        return new ProfileFixtures(work, version).source(embeddedTargetModel(), """
                CLASS Station = END Station;
                CLASS Landmark = END Landmark;
                CLASS Ticket = END Ticket;
                CLASS Hidden = END Hidden;
                ASSOCIATION Choice =
                  Owner -- {0..*} Ticket;
                  Candidate -- {0..1} Station OR Landmark;
                END Choice;
                """);
    }

    Path embeddedTargetTransfer(String file, String dataset, String target) throws Exception {
        var fixtures = new ProfileFixtures(work, version);
        String model = embeddedTargetModel();
        String objects = fixtures.object(model, "Ticket", dataset + "-ticket",
                fixtures.reference("Candidate", dataset + "-" + target))
                + fixtures.object(model, "Station", dataset + "-station", "")
                + fixtures.object(model, "Landmark", dataset + "-landmark", "")
                + fixtures.object(model, "Hidden", dataset + "-hidden", "");
        return Files.writeString(work.resolve(file + ".xtf"),
                referenceDatasetFixture(model, version, dataset + "-basket", objects));
    }

    void assertEmbeddedTargetTransfer(Path path, String dataset, String target) throws Exception {
        var rows = new ProfileFixtures(work, version).rows(path, dataset, 4);
        var ticket = rows.stream().filter(row -> id(row, version, "tid").equals(dataset + "-ticket"))
                .findFirst().orElseThrow();
        assertEquals(dataset + "-" + target, id(child(ticket, "Candidate"), version, "ref"));
    }

    void assertEmbeddedTargetMapping(DbTestSupport.Target db, Connection sql) throws Exception {
        String role = embeddedTargetModel() + ".Data.Choice.Candidate";
        assertEquals(Set.of("ticket:candidate_station:station", "ticket:candidate_landmark:landmark"),
                catalogKeys(sql, "SELECT colowner || ':' || sqlname || ':' || target FROM " + db.table("t_ili2db_attrname")
                        + " WHERE iliname='" + role + "'"));
        assertEquals(Set.of("candidate_station:station", "candidate_landmark:landmark"),
                catalogKeys(sql, "SELECT columnname || ':' || setting FROM " + db.table("t_ili2db_column_prop")
                        + " WHERE tablename='ticket' AND tag='ch.ehi.ili2db.foreignKey'"));
        assertEquals(Set.of("ticket:candidate_station:station", "ticket:candidate_landmark:landmark"),
                foreignKeyColumns(db, sql).stream().filter(key -> key.startsWith("ticket:candidate_"))
                        .collect(Collectors.toSet()));
    }

    private String object(String name, String tid, String fields) {
        String tag = version == 23 ? model() + ".Data." + name : name;
        String identity = tid == null ? "" : (version == 23 ? " TID=\"" : " i:tid=\"") + tid + "\"";
        return "<" + tag + identity + ">" + fields + "</" + tag + ">";
    }

    private String reference(String role, String tid) {
        return "<" + role + (version == 23 ? " REF=\"" : " i:ref=\"") + tid + "\"/>";
    }

    void assertTransfer(Path output, String dataset) throws Exception {
        var basket = children(readDataSection(output, version)).getFirst();
        assertEquals(dataset + "-basket", id(basket, version, "bid"));
        var links = children(basket).stream().filter(row -> row.getLocalName().endsWith("ChoiceLink")).toList();
        assertEquals(2, links.size());
        assertEquals(Set.of(dataset + "-landmark", dataset + "-station"), links.stream()
                .map(row -> id(child(row, "Candidate"), version, "ref")).collect(Collectors.toSet()));
        for (var link : links) assertEquals(dataset + "-ticket", id(child(link, "SourceTicket"), version, "ref"));
    }

    void assertTargets(DbTestSupport.Target db, Connection sql, String layout) throws Exception {
        var targets = switch (layout) {
            case "newClass" -> Set.of("candidate_station:station", "candidate_landmark:landmark");
            case "smart1" -> Set.of("candidate_registry:registry", "candidate_landmark:landmark");
            case "smart2" -> Set.of("candidate_station:station", "candidate_stationleaf:stationleaf", "candidate_landmark:landmark");
            default -> throw new AssertionError(layout);
        };
        assertEquals(targets, catalogKeys(sql, "SELECT sqlname || ':' || target FROM " + db.table("t_ili2db_attrname")
                + " WHERE iliname='" + model() + ".Data.ChoiceLink.Candidate'"));
    }
}

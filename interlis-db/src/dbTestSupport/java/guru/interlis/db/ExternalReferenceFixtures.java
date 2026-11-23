package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertExternalReference;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.referenceDatasetFixture;
import static guru.interlis.db.DbTestSupport.scalar;
import static guru.interlis.db.DbTestSupport.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExternalReferenceFixtures {
    private ExternalReferenceFixtures() { }

    static String modelText(String name) throws IOException {
        try (var resource = ExternalReferenceFixtures.class.getResourceAsStream("/" + name)) {
            return new String(java.util.Objects.requireNonNull(resource, name).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    record CatalogInput(Path target, Path source, Path duplicate, Path tail) { }

    static CatalogInput catalogInput(Path work, String model, int version) throws IOException {
        String leaf = version == 23 ? "<%s.Data.TargetLeaf TID=\"catalog-leaf\"/>".formatted(model)
                : "<TargetLeaf i:tid=\"catalog-leaf\"/>";
        Path target = Files.writeString(work.resolve("catalog.xtf"), referenceDatasetFixture(model, version, "catalog-basket", leaf));
        String report = version == 23 ? """
                <%1$s.Data.Report TID="report-id"><DetailValue><%1$s.Data.Detail>
                  <TargetRef REF="catalog-leaf" BID="catalog-basket"/>
                </%1$s.Data.Detail></DetailValue>
                  <BaseSite REF="catalog-leaf" BID="catalog-basket"/>
                  <PairedSite REF="catalog-leaf" BID="catalog-basket"/>
                  <ReviewSite REF="catalog-leaf" BID="catalog-basket"><%1$s.Data.ExternalReview><ReviewMemo>catalogue check</ReviewMemo></%1$s.Data.ExternalReview></ReviewSite>
                </%1$s.Data.Report>
                <%1$s.Data.Report TID="direct-report-id"><ReviewSite REF="catalog-leaf" BID="catalog-basket"/></%1$s.Data.Report>
                <%1$s.Data.Report TID="empty-report-id"/>
                """.formatted(model) : """
                <Report i:tid="report-id"><DetailValue><Detail><TargetRef i:ref="catalog-leaf"/></Detail></DetailValue>
                  <DirectRef i:ref="catalog-leaf"/>
                  <BaseSite i:ref="catalog-leaf"/>
                  <PairedSite i:ref="catalog-leaf"/>
                  <ReviewSite i:ref="catalog-leaf"><ExternalReview><ReviewMemo>catalogue check</ReviewMemo></ExternalReview></ReviewSite>
                </Report>
                <Report i:tid="direct-report-id"><ReviewSite i:ref="catalog-leaf"/></Report>
                <Report i:tid="empty-report-id"/>
                """;
        Path input = Files.writeString(work.resolve("catalog-source.xtf"),
                referenceDatasetFixture(model, version, "catalog-source", report));
        Path duplicate = Files.writeString(work.resolve("other-catalog.xtf"),
                referenceDatasetFixture(model, version, "other-basket", leaf));
        Path tail = Files.writeString(work.resolve("catalog-tail.xtf"),
                referenceDatasetFixture(model, version, "tail-catalog-basket", leaf.replace("catalog-leaf", "tail-catalog-leaf")));
        return new CatalogInput(target, input, duplicate, tail);
    }

    record SplitInput(Path target, Path source, Path ownSource) { }

    static SplitInput splitInput(Path work, String model, int version, String fixture) throws IOException {
        String catalog = version == 23 ? model + ".Catalog" : "Catalog";
        String surveys = version == 23 ? model + ".Surveys" : "Surveys";
        String split = fixture.replace("fixture-", "split-");
        Path target = Files.writeString(work.resolve("topics-split-catalog.xtf"), withoutBasket(split, surveys));
        Path source = Files.writeString(work.resolve("topics-split-surveys.xtf"), withoutBasket(split, catalog));
        Path local = Files.writeString(work.resolve("topics-split-native.xtf"),
                Files.readString(source).replace("split-survey", "split-own-survey").replace("split-permit", "split-own-permit"));
        return new SplitInput(target, source, local);
    }

    static void assertReferenceDatasetRows(Target db, Connection sql, int version, String tid) throws SQLException {
        String query = """
                select r.t_basket<>b.t_id, ds.datasetname, dest.t_ili_tid, d.targetref, r.basesite, r.pairedsite%s
                from %s r join %s d on d.report_detailvalue=r.t_id and d.t_basket=r.t_basket
                join %s dest on dest.t_id=d.targetref
                join %s b on b.t_id=dest.t_basket
                join %s ds on ds.t_id=b.dataset where r.t_ili_tid='%s'
                """.formatted(version == 24 ? ", r.directref" : "", db.table("report"), db.table("detail"), db.table("targetroot"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), tid);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertTrue(rows.getBoolean(1));
            assertEquals("catalog", rows.getString(2));
            assertEquals("catalog-leaf", rows.getString(3));
            assertEquals(rows.getLong(4), rows.getLong(5));
            assertEquals(rows.getLong(4), rows.getLong(6));
            if (version == 24) assertEquals(rows.getLong(4), rows.getLong(7));
            assertFalse(rows.next());
        }
        String target = "(select dest.t_id from " + db.table("targetroot") + " dest join "
                + db.table("t_ili2db_basket") + " b on b.t_id=dest.t_basket where dest.t_ili_tid='catalog-leaf' and b.t_ili_tid='catalog-basket')";
        String reviews = " from " + db.table("externalreview") + " l join " + db.table("report")
                + " r on r.t_id=l.reviewowner and l.t_basket=r.t_basket where l.reviewsite=" + target + " and r.t_ili_tid=";
        assertEquals("catalogue check", scalar(sql, "select l.reviewmemo" + reviews + "'" + tid + "'"));
        assertEquals("1", scalar(sql, "select count(*)" + reviews + "'direct-" + tid + "' and l.reviewmemo is null"));
        assertEquals("0", scalar(sql, "select count(*)" + reviews + "'empty-" + tid + "'"));
    }

    static void assertReferenceDatasetXtf(Path output, int version, boolean nativeExport) throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        var objects = children(baskets.getFirst());
        assertEquals(3, objects.size());
        var report = objects.stream().filter(object -> child(object, "DetailValue") != null).findFirst().orElseThrow();
        var detail = children(child(report, "DetailValue")).getFirst();
        assertExternalReference(child(detail, "TargetRef"), version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertExternalReference(child(report, "BaseSite"), version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertExternalReference(child(report, "PairedSite"), version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        var review = child(report, "ReviewSite");
        assertExternalReference(review, version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertEquals("catalogue check", child(children(review).getFirst(), "ReviewMemo").getTextContent());
        var direct = objects.stream().filter(object -> id(object, version, "tid").startsWith("direct-")).findFirst().orElseThrow();
        var directReview = child(direct, "ReviewSite");
        assertExternalReference(directReview, version, "catalog-leaf", nativeExport ? "catalog-basket" : "");
        assertTrue(children(directReview).isEmpty());
        var empty = objects.stream().filter(object -> id(object, version, "tid").startsWith("empty-")).findFirst().orElseThrow();
        assertNull(child(empty, "ReviewSite"));
        if (version == 24) assertExternalReference(child(report, "DirectRef"), version, "catalog-leaf", "");
    }

    static void assertExternalRows(Target db, Connection sql, int version, String dataset) throws SQLException {
        String query = """
                select d.note, b.t_ili_tid, t.t_ili_tid, tb.t_ili_tid, t.t_type, t.t_id, leaf.t_id, r.basesite, r.pairedsite%s
                from %s d join %s r on r.t_id=d.report_detailvalue and r.t_basket=d.t_basket
                join %s b on b.t_id=d.t_basket join %s ds on ds.t_id=b.dataset
                left join %s t on t.t_id=d.targetref
                left join %s tb on tb.t_id=t.t_basket and tb.dataset=ds.t_id
                left join %s leaf on leaf.t_id=t.t_id and leaf.t_basket=t.t_basket
                where ds.datasetname='%s' order by d.note
                """.formatted(version == 24 ? ", r.directref" : "", db.table("detail"), db.table("report"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), db.table("targetroot"),
                db.table("t_ili2db_basket"), db.table("targetleaf"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals("absent", rows.getString(1));
            assertEquals(dataset + "-first", rows.getString(2));
            assertNull(rows.getString(3));
            assertNull(rows.getString(8));
            assertNull(rows.getString(9));
            if (version == 24) assertNull(rows.getString(10));
            for (String direction : List.of("backward", "forward")) {
                boolean backward = direction.equals("backward");
                assertTrue(rows.next());
                assertEquals(direction, rows.getString(1));
                assertEquals(dataset + (backward ? "-last" : "-first"), rows.getString(2));
                assertEquals(dataset + (backward ? "-early" : "-late"), rows.getString(3));
                assertEquals(dataset + (backward ? "-first" : "-last"), rows.getString(4));
                assertEquals("targetleaf", rows.getString(5));
                assertEquals(rows.getString(6), rows.getString(7));
                assertEquals(rows.getString(6), rows.getString(8));
                assertEquals(rows.getString(6), rows.getString(9));
                if (version == 24) assertEquals(rows.getString(6), rows.getString(10));
            }
            assertFalse(rows.next());
        }
        String links = """
                select r.t_ili_tid, t.t_ili_tid, b.t_ili_tid, tb.t_ili_tid from %s l
                join %s r on r.t_id=l.pairobject and r.t_basket=l.t_basket
                join %s t on t.t_id=l.pairtarget join %s b on b.t_id=l.t_basket
                join %s tb on tb.t_id=t.t_basket and tb.dataset=b.dataset
                join %s ds on ds.t_id=b.dataset where ds.datasetname='%s' order by r.t_ili_tid
                """.formatted(db.table("externalpair"), db.table("report"), db.table("targetroot"),
                db.table("t_ili2db_basket"), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(links)) {
            assertTrue(rows.next());
            assertEquals(List.of(dataset + "-backward", dataset + "-early", dataset + "-last", dataset + "-first"),
                    List.of(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)));
            assertTrue(rows.next());
            assertEquals(List.of(dataset + "-forward", dataset + "-late", dataset + "-first", dataset + "-last"),
                    List.of(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)));
            assertFalse(rows.next());
        }
        String reviews = """
                select r.t_ili_tid, t.t_ili_tid, l.reviewmemo from %s l
                join %s b on b.t_id=l.t_basket join %s ds on ds.t_id=b.dataset
                left join %s r on r.t_id=l.reviewowner and r.t_basket=l.t_basket
                left join %s t on t.t_id=l.reviewsite and t.t_basket<>l.t_basket
                where ds.datasetname='%s' order by r.t_ili_tid
                """.formatted(db.table("externalreview"), db.table("t_ili2db_basket"), db.table("t_ili2db_dataset"),
                db.table("report"), db.table("targetroot"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(reviews)) {
            assertTrue(rows.next());
            assertEquals(dataset + "-backward", rows.getString(1));
            assertEquals(dataset + "-early", rows.getString(2));
            assertNull(rows.getString(3));
            assertTrue(rows.next());
            assertEquals(dataset + "-forward", rows.getString(1));
            assertEquals(dataset + "-late", rows.getString(2));
            assertEquals("observed", rows.getString(3));
            assertFalse(rows.next());
        }
    }

    static void assertExternalXtf(Path output, String model, int version, String dataset, boolean own)
            throws Exception {
        var reports = new HashMap<String, Element>();
        var targets = new HashMap<String, String>();
        var pairs = new HashSet<String>();
        for (Element basket : children(readDataSection(output, version))) {
            String bid = id(basket, version, "bid");
            for (Element object : children(basket)) {
                String name = object.getLocalName();
                String tid = id(object, version, "tid");
                if (name.equals("Report") || name.equals(model + ".Data.Report")) {
                    reports.put(tid, object);
                } else if (name.equals("ExternalPair") || name.equals(model + ".Data.ExternalPair")) {
                    assertEquals("", tid);
                    assertTrue(pairs.add(bid));
                    boolean first = bid.equals(dataset + "-first");
                    assertExternalReference(child(object, "PairObject"), version, dataset + (first ? "-forward" : "-backward"), "");
                    assertExternalReference(child(object, "PairTarget"), version, dataset + (first ? "-late" : "-early"),
                            own ? dataset + (first ? "-last" : "-first") : "");
                } else {
                    targets.put(tid, bid);
                }
            }
        }
        assertEquals(dataset + "-first", targets.get(dataset + "-early"));
        assertEquals(dataset + "-last", targets.get(dataset + "-late"));
        assertEquals(Set.of(dataset + "-first", dataset + "-last"), pairs);
        for (String direction : List.of("absent", "backward", "forward")) {
            var report = reports.get(dataset + "-" + direction);
            assertNotNull(report);
            var detail = children(child(report, "DetailValue")).getFirst();
            assertEquals(direction, text(detail, "Note"));
            var role = child(detail, "TargetRef");
            if (direction.equals("absent")) {
                assertNull(role);
                assertNull(child(report, "DirectRef"));
                assertNull(child(report, "BaseSite"));
                assertNull(child(report, "PairedSite"));
                assertNull(child(report, "ReviewSite"));
                continue;
            }
            String target = dataset + (direction.equals("backward") ? "-early" : "-late");
            String bid = own ? targets.get(target) : "";
            assertExternalReference(role, version, target, bid);
            assertExternalReference(child(report, "BaseSite"), version, target, bid);
            assertExternalReference(child(report, "PairedSite"), version, target, bid);
            var review = child(report, "ReviewSite");
            assertExternalReference(review, version, target, bid);
            var payload = children(review);
            if (direction.equals("forward")) {
                assertEquals(1, payload.size());
                var association = payload.getFirst();
                assertEquals(version == 23 ? model + ".Data.ExternalReview" : "ExternalReview", association.getLocalName());
                assertEquals("observed", text(association, "ReviewMemo"));
            } else {
                assertTrue(payload.isEmpty());
            }
            if (version == 24) assertExternalReference(child(report, "DirectRef"), version, target, "");
        }
    }

    static String withoutBasket(String fixture, String tag) {
        String name = java.util.regex.Pattern.quote(tag);
        return fixture.replaceFirst("(?s)<" + name + " .*?</" + name + ">", "");
    }

    static void assertSplitTopicLinks(Target db, Connection sql, String source) throws SQLException {
        assertExternalTopicLink(db, sql, "surveymatch", "surveyrecord", "matchedrecord", "surveyed",
                source, source + "-survey", null, "split");
        assertExternalTopicLink(db, sql, "sitepermit", "permitrecord", "issuedpermit", "coveredsite",
                source, source + "-permit", "access", "split");
    }

    static void assertSplitTopicLinksXtf(Path output, String model, int version, String source, boolean own)
            throws Exception {
        var baskets = children(readDataSection(output, version));
        assertEquals(1, baskets.size());
        assertEquals(source + "-surveys", id(baskets.getFirst(), version, "bid"));
        String prefix = version == 23 ? model + ".Surveys." : "";
        var match = child(baskets.getFirst(), prefix + "SurveyMatch");
        var permit = child(baskets.getFirst(), prefix + "SitePermit");
        String bid = own ? "split-catalog" : "";
        assertExternalReference(child(match, "Surveyed"), version, "split-leaf", bid);
        assertExternalReference(child(match, "MatchedRecord"), version, source + "-survey", "");
        assertExternalReference(child(permit, "CoveredSite"), version, "split-leaf", bid);
        assertExternalReference(child(permit, "IssuedPermit"), version, source + "-permit", "");
        assertEquals("access", child(permit, "Reason").getTextContent());
    }

    static void assertExternalTopicRows(Target db, Connection sql, String model, int version, String dataset)
            throws SQLException {
        String query = """
                select src.topic, dst.topic, t.t_ili_tid, t.t_type, t.t_id, r.markedsite, v.reviewedat, v.reviewmemo%s
                from %s r join %s a on a.surveyrecord_annotation=r.t_id and a.t_basket=r.t_basket
                join %s v on v.reviews=r.t_id and v.t_basket=r.t_basket
                join %s t on t.t_id=a.annotationsite join %s leaf on leaf.t_id=t.t_id and leaf.t_basket=t.t_basket
                join %s src on src.t_id=r.t_basket join %s dst on dst.t_id=t.t_basket and dst.dataset=src.dataset
                where r.t_ili_tid='%s-survey'
                """.formatted(version == 24 ? ", r.directsite" : "", db.table("surveyrecord"), db.table("siteannotation"),
                db.table("surveyreview"), db.table("registry"), db.table("siteleaf"), db.table("t_ili2db_basket"),
                db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals(model + ".Surveys", rows.getString(1));
            assertEquals(model + ".Catalog", rows.getString(2));
            assertEquals(dataset + "-leaf", rows.getString(3));
            assertEquals("siteleaf", rows.getString(4));
            assertEquals(rows.getString(5), rows.getString(6));
            assertEquals(rows.getString(5), rows.getString(7));
            assertEquals("seen", rows.getString(8));
            if (version == 24) assertEquals(rows.getString(5), rows.getString(9));
            assertFalse(rows.next());
        }
        assertExternalTopicLink(db, sql, "surveymatch", "surveyrecord", "matchedrecord", "surveyed",
                dataset, dataset + "-survey", null, dataset);
        assertExternalTopicLink(db, sql, "sitepermit", "permitrecord", "issuedpermit", "coveredsite",
                dataset, dataset + "-permit", "access", dataset);
    }

    static void assertExternalTopicLink(Target db, Connection sql, String linkTable, String localTable,
                                                String localColumn, String externalColumn, String dataset,
                                                String localTid, String reason, String target) throws SQLException {
        String query = """
                select local.t_ili_tid, foreign_site.t_ili_tid, src.t_ili_tid, dst.t_ili_tid, %s from %s link
                join %s src on src.t_id=link.t_basket
                join %s ds on ds.t_id=src.dataset
                left join %s local on local.t_id=link.%s and local.t_basket=link.t_basket
                left join %s foreign_site on foreign_site.t_id=link.%s
                left join %s dst on dst.t_id=foreign_site.t_basket
                where ds.datasetname='%s'
                """.formatted(reason == null ? "null" : "link.reason", db.table(linkTable), db.table("t_ili2db_basket"),
                db.table("t_ili2db_dataset"), db.table(localTable), localColumn, db.table("registry"), externalColumn,
                db.table("t_ili2db_basket"), dataset);
        try (var statement = sql.createStatement(); var rows = statement.executeQuery(query)) {
            assertTrue(rows.next());
            assertEquals(localTid, rows.getString(1));
            assertEquals(target + "-leaf", rows.getString(2));
            assertEquals(dataset + "-surveys", rows.getString(3));
            assertEquals(target + "-catalog", rows.getString(4));
            assertEquals(reason, rows.getString(5));
            assertFalse(rows.next());
        }
    }

    static void assertExternalTopicXtf(Path output, String model, int version, String dataset, boolean own)
            throws Exception {
        String catalog = version == 23 ? model + ".Catalog" : "Catalog";
        String surveys = version == 23 ? model + ".Surveys" : "Surveys";
        var section = readDataSection(output, version);
        var source = child(section, surveys);
        var target = child(section, catalog);
        assertEquals(dataset + "-surveys", id(source, version, "bid"));
        assertEquals(dataset + "-catalog", id(target, version, "bid"));
        var report = child(source, version == 23 ? surveys + ".SurveyRecord" : "SurveyRecord");
        var leaf = child(target, version == 23 ? catalog + ".SiteLeaf" : "SiteLeaf");
        assertEquals(dataset + "-survey", id(report, version, "tid"));
        assertEquals(dataset + "-leaf", id(leaf, version, "tid"));
        var landmark = child(target, version == 23 ? catalog + ".Landmark" : "Landmark");
        assertEquals(dataset + "-landmark", id(landmark, version, "tid"));
        String bid = own ? dataset + "-catalog" : "";
        var note = children(child(report, "Annotation")).getFirst();
        assertExternalReference(child(note, "AnnotationSite"), version, dataset + "-leaf", bid);
        assertExternalReference(child(note, "FlatSite"), version, dataset + "-landmark", bid);
        if (version == 24) assertExternalReference(child(report, "DirectSite"), version, dataset + "-leaf", "");
        assertExternalReference(child(report, "MarkedSite"), version, dataset + "-leaf", bid);
        var reviewed = child(report, "ReviewedAt");
        assertExternalReference(reviewed, version, dataset + "-leaf", bid);
        var payload = children(reviewed);
        assertEquals(1, payload.size());
        assertEquals(version == 23 ? surveys + ".SurveyReview" : "SurveyReview", payload.getFirst().getLocalName());
        assertEquals("seen", text(payload.getFirst(), "ReviewMemo"));
        var match = child(source, version == 23 ? surveys + ".SurveyMatch" : "SurveyMatch");
        assertEquals("", id(match, version, "tid"));
        assertExternalReference(child(match, "Surveyed"), version, dataset + "-leaf", bid);
        assertExternalReference(child(match, "MatchedRecord"), version, dataset + "-survey", "");
        var permit = child(source, version == 23 ? surveys + ".SitePermit" : "SitePermit");
        assertEquals("", id(permit, version, "tid"));
        assertExternalReference(child(permit, "CoveredSite"), version, dataset + "-leaf", bid);
        assertExternalReference(child(permit, "IssuedPermit"), version, dataset + "-permit", "");
        assertEquals("access", text(permit, "Reason"));
    }
}

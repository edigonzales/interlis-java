package guru.interlis.db;

import guru.interlis.db.BaseDbFixture.CurveComponent;
import guru.interlis.db.BaseDbFixture.DetailValue;
import guru.interlis.db.DbTestSupport.Target;

import org.w3c.dom.Element;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static guru.interlis.db.BaseDbFixture.ARC_ROUTE;
import static guru.interlis.db.BaseDbFixture.CIRCULARSTRING;
import static guru.interlis.db.BaseDbFixture.CURVED_SURFACE;
import static guru.interlis.db.BaseDbFixture.EAST_AREA;
import static guru.interlis.db.BaseDbFixture.ENTRIES;
import static guru.interlis.db.BaseDbFixture.LATER_ENTRY;
import static guru.interlis.db.BaseDbFixture.MARKER_LABELS;
import static guru.interlis.db.BaseDbFixture.MAST_CENTER;
import static guru.interlis.db.BaseDbFixture.MIXED_ROUTE;
import static guru.interlis.db.BaseDbFixture.OBSERVATIONS;
import static guru.interlis.db.BaseDbFixture.SERVICE_POINT;
import static guru.interlis.db.BaseDbFixture.STRAIGHT_ROUTE;
import static guru.interlis.db.BaseDbFixture.SUMMARY;
import static guru.interlis.db.BaseDbFixture.SURFACE_WITH_HOLE;
import static guru.interlis.db.BaseDbFixture.SURVEY_POINTS;
import static guru.interlis.db.BaseDbFixture.WEST_AREA;
import static guru.interlis.db.BaseDbFixture.assertTransferredAmounts;
import static guru.interlis.db.BaseDbFixture.assertTransferredNestedTree;
import static guru.interlis.db.BaseDbFixture.expectedMarkerLinks;
import static guru.interlis.db.DbTestSupport.assertLocalReference;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.DbTestSupport.text;
import static guru.interlis.db.DbTestSupport.transferredStructures;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BaseTransferAssertions {
    private BaseTransferAssertions() { }

    private static void assertTransferredPolymorphicTargets(Element carrier, int version, String dataset) {
        String prefix = version == 23 ? "BaseMapping23.Data." : "";
        assertNotNull(carrier);
        assertEquals(prefix + "RefCarrier", carrier.getLocalName());
        var pairs = children(carrier).stream().filter(value -> value.getLocalName().equals("Pairs"))
                .flatMap(value -> children(value).stream()).toList();
        assertEquals(List.of(prefix + "TargetPair", prefix + "TargetPair"),
                pairs.stream().map(Element::getLocalName).toList());
        assertLocalReference(pairs.getFirst(), "RootTarget", version, dataset + "-serviceunit");
        assertLocalReference(pairs.getFirst(), "BranchTarget", version, dataset + "-relay");
        assertNull(child(pairs.getLast(), "RootTarget"));
        assertNull(child(pairs.getLast(), "BranchTarget"));
        if (version == 24) {
            assertLocalReference(carrier, "DirectRoot", version, dataset + "-serviceunit");
            assertLocalReference(carrier, "DirectBranch", version, dataset + "-tower");
        }
    }

    private static void assertTransferredStructureReferences(Element report, int version, String dataset) {
        var links = children(report).stream().filter(element -> element.getLocalName().equals("Links"))
                .flatMap(element -> children(element).stream()).toList();
        String name = version == 23 ? "BaseMapping23.Data.LinkDetail" : "LinkDetail";
        assertEquals(List.of(name, name), links.stream().map(Element::getLocalName).toList());
        assertEquals("first", text(links.getFirst(), "Label"));
        var destination = child(links.getFirst(), "Destination");
        assertNotNull(destination);
        assertEquals(dataset + "-place-3", id(destination, version, "ref"));
        assertEquals("", id(destination, version, "bid"));
        assertEquals("second", text(links.getLast(), "Label"));
        assertNull(child(links.getLast(), "Destination"));
    }

    static void assertTransfer(Path output, int version, String dataset, boolean hasEmptyText) throws Exception {
        var data = readDataSection(output, version);
        Map<String, Set<String>> baskets = new HashMap<>();
        Map<String, Element> objects = new HashMap<>();
        var markerLinks = new HashSet<List<String>>();
        var annotatedLinks = new HashSet<AnnotatedPair>();
        String markerLinkName = version == 24 ? "MarkerLink" : "BaseMapping23.Data.MarkerLink";
        String hierarchyPrefix = version == 24 ? "" : "BaseMapping23.Data.";
        var hierarchyLinks = new HashSet<String>();
        String annotatedLinkName = version == 24 ? "AnnotatedLink" : "BaseMapping23.Data.AnnotatedLink";
        String aggregationName = version == 24 ? "ServiceAggregation" : "BaseMapping23.Data.ServiceAggregation";
        int aggregations = 0;
        String allocationName = version == 24 ? "TaggedAllocation" : "BaseMapping23.Data.TaggedAllocation";
        int allocations = 0;
        for (Element basket : children(data)) {
            assertEquals(version == 24 ? "Data" : "BaseMapping23.Data", basket.getLocalName());
            String bid = id(basket, version, "bid");
            var tids = new java.util.HashSet<String>();
            for (Element object : children(basket)) {
                String tid = id(object, version, "tid");
                if (object.getLocalName().equals(markerLinkName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-1", bid);
                    assertTrue(markerLinks.add(transferredMarkerLink(object, version)));
                    continue;
                }
                if (object.getLocalName().equals(annotatedLinkName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-1", bid);
                    assertTrue(annotatedLinks.add(transferredAnnotatedLink(object, version)));
                    continue;
                }
                if (object.getLocalName().equals(hierarchyPrefix + "HierarchyLink")) {
                    assertTrue(hierarchyLinks.add("HierarchyLink"));
                    assertEquals(dataset + "-basket-2", bid);
                    assertEquals("", tid);
                    assertEquals(List.of("WorkBranch", "StaffRoot"),
                            children(object).stream().map(Element::getLocalName).toList());
                    assertLocalReference(object, "WorkBranch", version, dataset + "-relay");
                    assertLocalReference(object, "StaffRoot", version, dataset + "-serviceunit");
                    continue;
                }
                if (object.getLocalName().equals(hierarchyPrefix + "HierarchyNote")) {
                    assertTrue(hierarchyLinks.add("HierarchyNote"));
                    assertEquals(dataset + "-basket-2", bid);
                    assertEquals("", tid);
                    assertEquals(List.of("RaisedAt", "SharedAt", "Memo", "Severity"),
                            children(object).stream().map(Element::getLocalName).toList());
                    assertLocalReference(object, "RaisedAt", version, dataset + "-beacon");
                    assertLocalReference(object, "SharedAt", version, dataset + "-serviceunit");
                    assertEquals("reviewed", text(object, "Memo"));
                    assertEquals("major", text(object, "Severity"));
                    continue;
                }
                if (object.getLocalName().equals(aggregationName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-2", bid);
                    assertLocalReference(object, "GroupWhole", version, dataset + "-refcarrier");
                    assertLocalReference(object, "GroupPart", version, dataset + "-serviceunit");
                    aggregations++;
                    continue;
                }
                if (object.getLocalName().equals(hierarchyPrefix + "DispatchRecord")) {
                    assertTrue(hierarchyLinks.add("DispatchRecord"));
                    assertEquals(dataset + "-basket-2", bid);
                    assertEquals("", tid);
                    assertEquals(List.of("DispatchLeft", "DispatchLocation", "DispatchMast", "Memo", "Severity"),
                            children(object).stream().map(Element::getLocalName).toList());
                    assertLocalReference(object, "DispatchLeft", version, dataset + "-pairleft-1");
                    assertLocalReference(object, "DispatchLocation", version, dataset + "-place-3");
                    assertLocalReference(object, "DispatchMast", version, dataset + "-relay");
                    assertEquals("dispatched", text(object, "Memo"));
                    assertEquals("minor", text(object, "Severity"));
                    continue;
                }
                if (object.getLocalName().equals(allocationName)) {
                    assertEquals("", tid);
                    assertEquals(dataset + "-basket-2", bid);
                    assertLocalReference(object, "ListedSite", version, dataset + "-contract-site");
                    assertLocalReference(object, "ListedSigner", version, dataset + "-contract-signer");
                    assertEquals("base allocation", text(object, "BaseNote"));
                    assertEquals("17", text(object, "DetailRank"));
                    allocations++;
                    continue;
                }
                assertNull(objects.put(tid, object));
                tids.add(tid);
            }
            assertNull(baskets.put(bid, tids));
        }
        assertEquals(Set.of("HierarchyLink", "HierarchyNote", "DispatchRecord"), hierarchyLinks);
        assertEquals(expectedMarkerLinks(dataset), markerLinks);
        assertEquals(1, aggregations);
        assertEquals(1, allocations);
        assertEquals(Set.of(new AnnotatedPair(dataset + "-marker-1", dataset + "-report-1", "confirmed", "-3", "approved"),
                new AnnotatedPair(dataset + "-marker-2", dataset + "-report-1", hasEmptyText ? "" : null, null, null)),
                annotatedLinks);
        String first = dataset + "-place-1";
        String second = dataset + "-place-2";
        String third = dataset + "-place-3";
        String link = dataset + "-link";
        String line = dataset + "-route-1";
        String noLine = dataset + "-route-2";
        String arcs = dataset + "-route-3";
        String mixed = dataset + "-route-4";
        String plotWithHole = dataset + "-plot-1";
        String curvedPlot = dataset + "-plot-2";
        String noSurface = dataset + "-plot-3";
        String westArea = dataset + "-parcel-1";
        String eastArea = dataset + "-parcel-2";
        String noArea = dataset + "-parcel-3";
        String firstReport = dataset + "-report-1";
        String secondReport = dataset + "-report-2";
        var firstBasket = new HashSet<>(Set.of(first, second, line, arcs, mixed, plotWithHole, curvedPlot,
                westArea, eastArea, firstReport, dataset + "-marker-1", dataset + "-marker-2", dataset + "-hub-1",
                dataset + "-hub-2"));
        var secondBasket = new HashSet<>(Set.of(third, noLine, noSurface, noArea, secondReport, dataset + "-marker-3",
                dataset + "-nested-1", dataset + "-nested-2", dataset + "-typed-1", dataset + "-typed-2",
                dataset + "-pairleft-1", dataset + "-pairleft-2", dataset + "-pairright-1"));
        secondBasket.addAll(List.of(dataset + "-contract-site", dataset + "-contract-signer"));
        if (version == 24) {
            firstBasket.add(link);
            for (String table : List.of("park", "district", "routegroup", "survey")) {
                firstBasket.add(dataset + "-" + table + "-1");
                secondBasket.add(dataset + "-" + table + "-2");
            }
        }
        for (String name : List.of("mast", "tower", "relay", "beacon", "serviceunit")) {
            secondBasket.add(dataset + "-" + name);
        }
        for (int index = 1; index <= 3; index++) secondBasket.add(dataset + "-ratingform-" + index);
        secondBasket.addAll(List.of(dataset + "-serviceunit-2", dataset + "-serviceunit-3"));
        secondBasket.add(dataset + "-refcarrier");
        assertTransferredClassHierarchy(objects, version, dataset);
        secondBasket.add(dataset + "-ref-notebook");
        if (version == 24) {
            secondBasket.add(dataset + "-ref-asset-1");
            secondBasket.add(dataset + "-ref-asset-2");
        }
        assertTransferredHierarchyReferences(objects, version, dataset);
        assertEquals(Map.of(dataset + "-basket-1", firstBasket, dataset + "-basket-2", secondBasket), baskets);
        assertTransferredReports(objects.get(firstReport), objects.get(secondReport), version);
        assertTransferredNestedTree(objects.get(dataset + "-nested-1"), version, List.of("2", "3", "2"));
        assertTrue(children(objects.get(dataset + "-nested-2")).isEmpty());
        assertTransferredStructureHierarchy(objects.get(dataset + "-typed-1"), version);
        assertTrue(children(objects.get(dataset + "-typed-2")).isEmpty());
        assertTransferredPolymorphicTargets(objects.get(dataset + "-refcarrier"), version, dataset);
        assertTransferredStructureReferences(objects.get(secondReport), version, dataset);
        assertTransferredRoles(objects, version, dataset);
        assertEmbeddedLink(objects.get(dataset + "-ratingform-1"), "Rated", dataset + "-relay", "Rating", version, "checked");
        assertEmbeddedLink(objects.get(dataset + "-ratingform-2"), "Rated", dataset + "-tower", "Rating", version, null);
        assertEmbeddedLink(objects.get(dataset + "-ratingform-3"), "Rated", null, "Rating", version, null);
        assertEmbeddedLink(objects.get(dataset + "-relay"), "Recommended", dataset + "-serviceunit",
                "RelayReview", version, "accepted");
        assertNull(child(objects.get(dataset + "-beacon"), "Recommended"));
        assertTransferredRatingTags(objects, version, dataset);
        assertTransferredReviews(objects, version, dataset);
        assertTransferredPairing(objects, version, dataset);

        String placeName = version == 24 ? "Place" : "BaseMapping23.Data.Place";
        for (String tid : List.of(first, second, third)) {
            assertEquals(placeName, objects.get(tid).getLocalName());
        }
        Element place = objects.get(first);
        assertEquals("Brücke", child(place, "Select").getTextContent());
        assertEquals("true", child(place, "Active").getTextContent());
        assertEquals("-7", child(place, "Count").getTextContent());
        assertEquals("approved", child(place, "Status").getTextContent());
        var coord = children(child(place, "Geometry")).getFirst();
        assertEquals(0, new BigDecimal("2600000.125").compareTo(new BigDecimal(children(coord).getFirst().getTextContent())));
        assertEquals(0, new BigDecimal("1200000.375").compareTo(new BigDecimal(children(coord).get(1).getTextContent())));
        Element empty = child(objects.get(second), "Select");
        if (hasEmptyText) {
            assertNotNull(empty);
            assertEquals("", empty.getTextContent());
        } else {
            assertNull(empty);
        }
        assertEquals("false", child(objects.get(second), "Active").getTextContent());
        assertEquals(List.of(), children(objects.get(third)));
        if (version == 24) {
            assertEquals("Link", objects.get(link).getLocalName());
            assertEquals(first, id(child(objects.get(link), "Target"), version, "ref"));
        }
        String routeName = version == 24 ? "Route" : "BaseMapping23.Data.Route";
        for (String tid : List.of(line, noLine, arcs, mixed)) {
            assertEquals(routeName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noLine), "Trace"));
        assertTransferRoute(child(objects.get(line), "Trace"), version, STRAIGHT_ROUTE);
        assertTransferRoute(child(objects.get(arcs), "Trace"), version, ARC_ROUTE);
        assertTransferRoute(child(objects.get(mixed), "Trace"), version, MIXED_ROUTE);
        String plotName = version == 24 ? "Plot" : "BaseMapping23.Data.Plot";
        for (String tid : List.of(plotWithHole, curvedPlot, noSurface)) {
            assertEquals(plotName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noSurface), "Footprint"));
        assertTransferSurface(child(objects.get(plotWithHole), "Footprint"), version, SURFACE_WITH_HOLE);
        assertTransferSurface(child(objects.get(curvedPlot), "Footprint"), version, CURVED_SURFACE);
        String parcelName = version == 24 ? "Parcel" : "BaseMapping23.Data.Parcel";
        for (String tid : List.of(westArea, eastArea, noArea)) {
            assertEquals(parcelName, objects.get(tid).getLocalName());
        }
        assertNull(child(objects.get(noArea), "Boundary"));
        assertTransferSurface(child(objects.get(westArea), "Boundary"), version, WEST_AREA);
        assertTransferSurface(child(objects.get(eastArea), "Boundary"), version, EAST_AREA);
        if (version == 24) {
            for (String name : List.of("Park", "District")) {
                var surfaces = assertTransferCollection(objects, dataset, name, "Coverage", "multisurface", "surface");
                assertTransferRings(surfaces.getFirst(), 24, SURFACE_WITH_HOLE);
                assertTransferRings(surfaces.getLast(), 24, CURVED_SURFACE);
            }
            var lines = assertTransferCollection(objects, dataset, "RouteGroup", "Traces", "multipolyline", "polyline");
            assertTransferPolyline(lines.getFirst(), 24, STRAIGHT_ROUTE);
            assertTransferPolyline(lines.getLast(), 24, MIXED_ROUTE);
            var points = assertTransferCollection(objects, dataset, "Survey", "Locations", "multicoord", "coord");
            for (int index = 0; index < points.size(); index++) {
                var point = points.get(index);
                assertEquals(2, children(point).size());
                assertTransferPosition(point, "c", SURVEY_POINTS.get(index));
            }
        }
    }

    private static void assertTransferredRoles(Map<String, Element> objects, int version, String dataset) {
        String prefix = version == 23 ? "BaseMapping23.Data." : "";
        String hub = dataset + "-hub-1";
        assertEquals(prefix + "Hub", objects.get(hub).getLocalName());
        assertEquals(prefix + "Hub", objects.get(dataset + "-hub-2").getLocalName());
        for (int index = 0; index < MARKER_LABELS.size(); index++) {
            var marker = objects.get(dataset + "-marker-" + (index + 1));
            assertEquals(prefix + "Marker", marker.getLocalName());
            assertEquals(MARKER_LABELS.get(index), text(marker, "ASelect"));
            var role = child(marker, "Select");
            if (index < 2) {
                assertNotNull(role);
                assertEquals(hub, id(role, version, "ref"));
                assertEquals("", id(role, version, "bid"));
                assertEquals(List.of(), children(role));
            } else {
                assertNull(role);
            }
        }
    }

    private static void assertTransferredRatingTags(Map<String, Element> objects, int version, String dataset) {
        for (int index = 1; index <= 3; index++) {
            String form = dataset + "-ratingform-" + index;
            String unit = dataset + "-serviceunit" + (index == 1 ? "" : "-" + index);
            String owner = version == 23 ? form : unit;
            String target = index == 3 ? null : version == 23 ? unit : form;
            assertEmbeddedLink(objects.get(owner), version == 23 ? "TaggedUnit" : "TaggedForm", target,
                    "RatingTag", version, index == 1 ? "tagged" : null);
            assertNull(child(objects.get(version == 23 ? unit : form), version == 23 ? "TaggedForm" : "TaggedUnit"));
        }
    }

    private static void assertTransferredReviews(Map<String, Element> objects, int version, String dataset) {
        String hub = dataset + "-hub-1";
        var first = child(objects.get(dataset + "-marker-1"), "Witness");
        assertNotNull(first);
        assertEquals(hub, id(first, version, "ref"));
        assertEquals(1, children(first).size());
        var payload = children(first).getFirst();
        assertEquals(version == 24 ? "Review" : "BaseMapping23.Data.Review", payload.getLocalName());
        assertEquals("reviewed", text(payload, "Memo"));
        assertEquals("-3", text(payload, "Grade"));

        var second = child(objects.get(dataset + "-marker-2"), "Witness");
        assertNotNull(second);
        assertEquals(hub, id(second, version, "ref"));
        assertEquals(List.of(), children(second));
        assertNull(child(objects.get(dataset + "-marker-3"), "Witness"));
    }

    private static void assertTransferredClassHierarchy(Map<String, Element> objects, int version, String dataset) {
        String prefix = version == 23 ? "BaseMapping23.Data." : "";
        var mast = objects.get(dataset + "-mast");
        var relay = objects.get(dataset + "-relay");
        assertClassFields(mast, prefix + "Mast", Map.of("Caption", "base"), "Notes");
        assertClassFields(objects.get(dataset + "-tower"), prefix + "Tower",
                Map.of("Caption", "middle", "Height", "75", "SerialNumber", "45000000000"));
        assertClassFields(relay, prefix + "Relay",
                Map.of("Caption", "leaf", "Height", "24", "SerialNumber", "35000000000", "Online", "false"),
                "Notes", "Supplement", "Center", "Alignment", "Sites", "Mentor", "PairedService", "Recommended");
        assertLocalReference(relay, "Mentor", version, dataset + "-relay");
        assertTransferClassPoint(relay, "Center", version, MAST_CENTER);
        assertTransferRoute(child(relay, "Alignment"), version, STRAIGHT_ROUTE);
        if (version == 24) {
            var sites = child(relay, "Sites");
            assertNotNull(sites);
            var multicoord = child(sites, "multicoord");
            assertNotNull(multicoord);
            var points = children(multicoord);
            assertEquals(List.of("coord", "coord"), points.stream().map(Element::getLocalName).toList());
            for (int index = 0; index < points.size(); index++) {
                assertTransferPosition(points.get(index), "c", SURVEY_POINTS.get(index));
            }
        } else {
            assertNull(child(relay, "Sites"));
        }
        assertTransferredAmounts(mast, "Notes", version, prefix, List.of("5", "6"));
        assertTransferredAmounts(relay, "Notes", version, prefix, List.of("7"));
        var supplement = transferredStructures(relay, "Supplement");
        assertEquals(List.of(prefix + "NestedOuter"), supplement.stream().map(Element::getLocalName).toList());
        var items = transferredStructures(supplement.getFirst(), "Items");
        assertEquals(List.of(prefix + "NestedInner"), items.stream().map(Element::getLocalName).toList());
        var inner = items.getFirst();
        assertEquals("inherited", text(inner, "Headline"));
        assertTransferredAmounts(inner, "Main", version, prefix, List.of("8"));
        assertNull(child(inner, "Values"));
        assertNull(child(inner, "Extras"));
        assertClassFields(objects.get(dataset + "-beacon"), prefix + "Beacon", Map.of("Caption", "sibling", "Signal", "green"));
        var serviceUnit = objects.get(dataset + "-serviceunit");
        assertClassFields(serviceUnit, prefix + "ServiceUnit", Map.of("ServiceCode", "unit-1", "Label", "active"),
                "ServicePoint", "Supervisor", "PairedTower", "TaggedForm", "WholeMast");
        assertLocalReference(serviceUnit, "Supervisor", version, dataset + "-relay");
        assertLocalReference(serviceUnit, "WholeMast", version, dataset + "-beacon");
        if (version == 23) {
            assertLocalReference(relay, "PairedService", version, dataset + "-serviceunit");
            assertNull(child(serviceUnit, "PairedTower"));
        } else {
            assertLocalReference(serviceUnit, "PairedTower", version, dataset + "-relay");
            assertNull(child(relay, "PairedService"));
        }
        assertTransferClassPoint(serviceUnit, "ServicePoint", version, SERVICE_POINT);
    }

    private static void assertTransferredHierarchyReferences(Map<String, Element> objects, int version, String dataset) {
        var book = objects.get(dataset + "-ref-notebook");
        assertNotNull(book);
        var entries = transferredStructures(book, "Entries");
        String note = version == 23 ? "BaseMapping23.Data.RefNoteLeaf" : "RefNoteLeaf";
        assertEquals(List.of(note, note), entries.stream().map(Element::getLocalName).toList());
        assertTrue(entries.stream().allMatch(entry -> id(entry, version, "tid").isEmpty()));
        assertTransferredReferenceFields(entries.getFirst(), entries.getLast(), version, dataset);
        if (version == 24) {
            var populated = objects.get(dataset + "-ref-asset-1");
            var empty = objects.get(dataset + "-ref-asset-2");
            assertNotNull(populated);
            assertNotNull(empty);
            assertEquals("RefAssetLeaf", populated.getLocalName());
            assertEquals("RefAssetLeaf", empty.getLocalName());
            assertTransferredReferenceFields(populated, empty, version, dataset);
        }
    }

    private static void assertTransferredReferenceFields(Element populated, Element empty, int version, String dataset) {
        assertEquals("populated", text(populated, "Label"));
        assertEquals("empty", text(empty, "Label"));
        for (var field : Map.of("RootTarget", "beacon", "BranchTarget", "relay", "OwnTarget", "mast").entrySet()) {
            assertLocalReference(populated, field.getKey(), version, dataset + "-" + field.getValue());
            assertNull(child(empty, field.getKey()));
        }
    }

    private static void assertTransferClassPoint(Element object, String attribute, int version, List<String> position) {
        var value = child(object, attribute);
        assertNotNull(value, attribute);
        var point = child(value, version == 24 ? "coord" : "COORD");
        assertNotNull(point, attribute);
        assertTransferPosition(point, version == 24 ? "c" : "C", position);
    }

    private static void assertTransferredPairing(Map<String, Element> objects, int version, String dataset) {
        String prefix = version == 23 ? "BaseMapping23.Data." : "";
        String leftTid = dataset + "-pairleft-1";
        String rightTid = dataset + "-pairright-1";
        var left = objects.get(leftTid);
        var leftWithoutPair = objects.get(dataset + "-pairleft-2");
        var right = objects.get(rightTid);
        assertEquals(prefix + "PairLeft", left.getLocalName());
        assertEquals(prefix + "PairLeft", leftWithoutPair.getLocalName());
        assertEquals(prefix + "PairRight", right.getLocalName());
        assertTrue(children(leftWithoutPair).isEmpty());
        var owner = version == 23 ? right : left;
        var target = version == 23 ? left : right;
        assertTrue(children(target).isEmpty());
        var role = child(owner, version == 23 ? "LeftLink" : "RightLink");
        assertNotNull(role);
        assertEquals(version == 23 ? leftTid : rightTid, id(role, version, "ref"));
        assertEquals("", id(role, version, "bid"));
        assertTrue(children(role).isEmpty());
    }

    private static void assertTransferredStructureHierarchy(Element record, int version) {
        String prefix = version == 23 ? "BaseMapping23.Data." : "";
        assertEquals(prefix + "TypedRecord", record.getLocalName());
        var main = transferredStructures(record, "Main");
        assertEquals(1, main.size());
        assertClassFields(main.getFirst(), prefix + "CountNote", Map.of("Label", "main", "Amount", "5"));

        var values = transferredStructures(record, "Values");
        assertEquals(3, values.size());
        assertClassFields(values.get(0), prefix + "NoteBase", Map.of("Label", "root"));
        assertClassFields(values.get(1), prefix + "CountNote", Map.of("Label", "count", "Amount", "7"));
        assertClassFields(values.get(2), prefix + "FlagNote", Map.of("Label", "flag", "Enabled", "true"));

        var extras = transferredStructures(record, "Extras");
        assertEquals(2, extras.size());
        for (var duplicate : extras) {
            assertClassFields(duplicate, prefix + "NamedNote", Map.of("Label", "duplicate", "Code", "ABC"));
        }
        for (var field : List.of(main, values, extras)) {
            assertTrue(field.stream().allMatch(structure -> id(structure, version, "tid").isEmpty()));
        }
    }

    private static void assertTransferredReports(Element first, Element second, int version) {
        String prefix = version == 23 ? "BaseMapping23.Data." : "";
        assertEquals(prefix + "Report", first.getLocalName());
        assertEquals(prefix + "Report", second.getLocalName());
        var summary = child(first, "Summary");
        assertNotNull(summary);
        assertEquals(1, children(summary).size());
        assertTransferredDetail(children(summary).getFirst(), version, prefix + "Detail", SUMMARY);
        assertNull(child(second, "Summary"));
        assertTransferredStructures(first, "Entries", version, prefix + "Detail", ENTRIES);
        assertTransferredStructures(second, "Entries", version, prefix + "Detail", List.of(LATER_ENTRY));
        assertTransferredStructures(first, "Observations", version, prefix + "Detail", OBSERVATIONS);
        assertNull(child(second, "Observations"));
    }

    private static void assertTransferredStructures(Element report, String attribute, int version, String detailName,
                                                    List<DetailValue> expected) {
        var entries = transferredStructures(report, attribute);
        assertEquals(expected.size(), entries.size());
        for (int index = 0; index < entries.size(); index++) {
            assertTransferredDetail(entries.get(index), version, detailName, expected.get(index));
        }
    }

    private static void assertTransferredDetail(Element detail, int version, String name, DetailValue expected) {
        assertEquals(name, detail.getLocalName());
        assertEquals("", id(detail, version, "tid"));
        assertEquals(expected.label(), text(detail, "Label"));
        assertEquals(expected.enabled() == null ? null : expected.enabled().toString(), text(detail, "Enabled"));
        assertEquals(expected.quantity() == null ? null : expected.quantity().toString(), text(detail, "Quantity"));
        assertEquals(expected.decision(), text(detail, "Decision"));
    }

    private static void assertTransferRoute(Element trace, int version, List<CurveComponent> components) {
        assertNotNull(trace);
        assertTransferPolyline(child(trace, version == 24 ? "polyline" : "POLYLINE"), version, components);
    }

    private static void assertTransferSurface(Element footprint, int version, List<List<CurveComponent>> rings) {
        assertNotNull(footprint);
        var surface = child(footprint, version == 24 ? "surface" : "SURFACE");
        assertNotNull(surface);
        assertTransferRings(surface, version, rings);
    }

    private static List<Element> assertTransferCollection(Map<String, Element> objects, String dataset, String name,
                                                          String attribute, String collectionTag, String memberTag) {
        String tidPrefix = dataset + "-" + name.toLowerCase(Locale.ROOT);
        var populated = objects.get(tidPrefix + "-1");
        var absent = objects.get(tidPrefix + "-2");
        assertEquals(name, populated.getLocalName());
        assertEquals(name, absent.getLocalName());
        assertNull(child(absent, attribute));
        var value = child(populated, attribute);
        assertNotNull(value);
        var multi = child(value, collectionTag);
        assertNotNull(multi);
        assertEquals("http://www.interlis.ch/geometry/1.0", multi.getNamespaceURI());
        var members = children(multi);
        assertEquals(List.of(memberTag, memberTag), members.stream().map(Element::getLocalName).toList());
        return members;
    }

    private static void assertTransferRings(Element surface, int version, List<List<CurveComponent>> rings) {
        var boundaries = children(surface);
        assertEquals(rings.size(), boundaries.size());
        for (int index = 0; index < rings.size(); index++) {
            var boundary = boundaries.get(index);
            if (version == 24) {
                assertEquals(index == 0 ? "exterior" : "interior", boundary.getLocalName());
            } else {
                assertEquals("BOUNDARY", boundary.getLocalName());
            }
            var lines = children(boundary);
            assertEquals(1, lines.size());
            var line = lines.getFirst();
            assertEquals(version == 24 ? "polyline" : "POLYLINE", line.getLocalName());
            assertTransferPolyline(line, version, rings.get(index));
        }
    }

    private static void assertTransferPolyline(Element polyline, int version, List<CurveComponent> components) {
        assertNotNull(polyline);
        var segments = children(polyline);
        int expectedSize = 1 + components.stream().mapToInt(component ->
                (component.vertices().size() - 1) / (component.type() == CIRCULARSTRING ? 2 : 1)).sum();
        assertEquals(expectedSize, segments.size());
        String coord = version == 24 ? "coord" : "COORD";
        String arc = version == 24 ? "arc" : "ARC";
        String c = version == 24 ? "c" : "C";
        String a = version == 24 ? "a" : "A";
        assertEquals(coord, segments.getFirst().getLocalName());
        assertEquals(2, children(segments.getFirst()).size());
        assertTransferPosition(segments.getFirst(), c, components.getFirst().vertices().getFirst());
        int segmentIndex = 1;
        for (var component : components) {
            boolean circular = component.type() == CIRCULARSTRING;
            for (int vertex = 1; vertex < component.vertices().size(); vertex += circular ? 2 : 1) {
                var segment = segments.get(segmentIndex++);
                assertEquals(circular ? arc : coord, segment.getLocalName());
                assertEquals(circular ? 4 : 2, children(segment).size());
                if (circular) {
                    // ili2db's XTF 2.4 exporter writes the intermediate point first.
                    assertTransferPosition(segment, a, component.vertices().get(vertex));
                    assertTransferPosition(segment, c, component.vertices().get(vertex + 1));
                } else {
                    assertTransferPosition(segment, c, component.vertices().get(vertex));
                }
            }
        }
    }

    private static void assertTransferPosition(Element segment, String prefix, List<String> expected) {
        for (int axis = 0; axis < expected.size(); axis++) {
            String name = prefix + (axis + 1);
            var ordinate = child(segment, name);
            assertNotNull(ordinate, name);
            assertEquals(0, new BigDecimal(expected.get(axis)).compareTo(new BigDecimal(ordinate.getTextContent())), name);
        }
    }

    private static List<String> transferredMarkerLink(Element object, int version) {
        var roles = children(object).stream().map(Element::getLocalName).toList();
        assertEquals(version == 24 ? List.of("LinkedHub", "LinkedMarker") : List.of("LinkedMarker", "LinkedHub"), roles);
        var marker = child(object, "LinkedMarker");
        var hub = child(object, "LinkedHub");
        assertEquals("", id(marker, version, "bid"));
        assertEquals("", id(hub, version, "bid"));
        return List.of(id(marker, version, "ref"), id(hub, version, "ref"));
    }

    private static AnnotatedPair transferredAnnotatedLink(Element object, int version) {
        var fields = children(object).stream().map(Element::getLocalName).toList();
        var expectedFields = new ArrayList<>(List.of("ConsultedMarker", "ConsultedReport"));
        for (String field : List.of("Memo", "Grade", "LocalDecision")) {
            if (child(object, field) != null) expectedFields.add(field);
        }
        assertEquals(expectedFields, fields);
        return new AnnotatedPair(id(child(object, "ConsultedMarker"), version, "ref"),
                id(child(object, "ConsultedReport"), version, "ref"), text(object, "Memo"), text(object, "Grade"),
                text(object, "LocalDecision"));
    }

    private static void assertEmbeddedLink(Element owner, String role, String targetTid, String association,
                                           int version, String memo) {
        var element = child(owner, role);
        if (targetTid == null) {
            assertNull(element, role);
            return;
        }
        assertLocalReference(owner, role, version, targetTid);
        if (memo == null) {
            assertTrue(children(element).isEmpty(), role);
        } else {
            var payload = children(element);
            assertEquals(1, payload.size());
            String name = version == 23 ? "BaseMapping23.Data." + association : association;
            assertEquals(name, payload.getFirst().getLocalName());
            assertEquals(memo, text(payload.getFirst(), "Memo"));
        }
    }

    private static void assertClassFields(Element object, String name, Map<String, String> fields, String... nestedFields) {
        assertNotNull(object, name);
        assertEquals(name, object.getLocalName());
        var nestedNames = Set.of(nestedFields);
        var actual = new HashMap<String, String>();
        for (var child : children(object)) {
            if (!nestedNames.contains(child.getLocalName())) {
                assertNull(actual.put(child.getLocalName(), child.getTextContent()));
            }
        }
        assertEquals(fields, actual);
    }

    private record AnnotatedPair(String marker, String report, String memo, String grade, String decision) { }
}

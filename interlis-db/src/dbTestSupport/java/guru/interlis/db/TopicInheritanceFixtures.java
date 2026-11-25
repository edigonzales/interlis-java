package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.headerModels;
import static guru.interlis.db.ModelFixtures.mergeDeliveries;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TopicInheritanceFixtures {
    final String town;
    final String flat;
    final String child;
    final String abstractTown;
    final String refined;
    final String linksBase;
    final String links;
    final List<String> models;
    private final Path work;
    private final int version;

    TopicInheritanceFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
        town = "TownParent" + version;
        flat = "TownFlat" + version;
        child = "TownChild" + version;
        abstractTown = "AbstractTown" + version;
        refined = "RefinedTown" + version;
        linksBase = "LinksBase" + version;
        links = "LinksTown" + version;
        models = List.of(child, flat, refined, links);
    }

    Path source(String model) throws Exception {
        return ModelFixtures.fixture(work, "topics/" + model + ".ili");
    }

    Path input(String name) throws Exception {
        return ModelFixtures.fixture(work, "topics/" + name + version + ".xtf");
    }

    List<Path> copySources() throws Exception {
        return List.of(source(child), source(town), source(flat), source(refined), source(abstractTown),
                source(links), source(linksBase));
    }

    String[] modelsAfterChild() {
        return new String[]{"--model", flat, "--model", refined, "--model", links};
    }

    void mixedSchema(Target db, List<Path> sources) {
        var options = new ArrayList<>(List.of(modelsAfterChild()));
        for (var path : sources.subList(1, sources.size())) options.addAll(List.of("--model-file", path.toString()));
        assertSchemaWithIdentityColumns(db, child, sources.getFirst(), options.toArray(String[]::new));
    }

    Path mixedInput() throws Exception {
        return mergeDeliveries(work.resolve("topic-legacy.xtf"), version, input("flat"),
                input("region"), input("refine"), input("links"));
    }

    Path nativeInput(Path legacy) throws Exception {
        return Files.writeString(work.resolve("topic-native.xtf"), Files.readString(legacy)
                .replace("flat-", "native-flat-").replace("region-", "native-region-")
                .replace("refine-", "native-refine-").replace("links-", "native-links-").replace(">text<", ">updated<"));
    }

    Path updateRegion(Path input) throws Exception {
        return Files.writeString(work.resolve("region-updated.xtf"), Files.readString(input)
                .replace(">inherited<", ">new marker<").replace(">base<", ">new base<").replace(">child<", ">new child<"));
    }

    void assertMixedDelivery(Path file, String prefix, String refinedText, boolean regionUpdated) throws Exception {
        var data = readDataSection(file, version);
        var header = headerModels(data, version);
        assertEquals(models.size(), header.size());
        assertEquals(Set.copyOf(models), Set.copyOf(header));
        assertEquals(5, children(data).size());
        assertFlat(basket(data, version, flat + ".Region", prefix + "flat-b"), prefix + "flat");
        assertFragments(basket(data, version, child + ".Region", prefix + "region-b"), prefix + "region",
                regionUpdated ? "new marker" : "inherited",
                regionUpdated ? "new base" : "base",
                regionUpdated ? "new child" : "child");
        assertRefinement(basket(data, version, refined + ".Region", prefix + "refine-b"), prefix + "refine", refinedText);
        assertReferences(data, prefix + "links");
    }

    void assertFragmentDelivery(Path file, String prefix, String markerMemo, String baseMemo, String localMemo) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(child), headerModels(data, version));
        assertEquals(1, children(data).size());
        assertFragments(basket(data, version, child + ".Region", prefix + "-b"), prefix, markerMemo, baseMemo, localMemo);
    }

    void assertRefinementDelivery(Path file, String prefix, String text) throws Exception {
        var data = readDataSection(file, version);
        assertEquals(List.of(refined), headerModels(data, version));
        assertEquals(1, children(data).size());
        assertRefinement(basket(data, version, refined + ".Region", prefix + "-b"), prefix, text);
    }

    private void assertFlat(Element basket, String prefix) {
        assertEquals(2, children(basket).size());
        var marker = object(basket, town + ".Core.Marker", prefix + "-m");
        var desk = object(basket, flat + ".Region.Desk", prefix + "-d");
        assertField(marker, town, "Memo", "inherited");
        assertField(desk, flat, "LocalMemo", "local");
    }

    private void assertFragments(Element basket, String prefix, String markerMemo, String baseMemo, String localMemo) {
        assertEquals(2, children(basket).size());
        var station = object(basket, child + ".Region.LocalStation", prefix + "-s");
        var marker = object(basket, town + ".Core.Marker", prefix + "-m");
        assertField(station, town, "Memo", baseMemo);
        assertField(station, child, "LocalMemo", localMemo);
        assertField(marker, town, "Memo", markerMemo);
    }

    private void assertRefinement(Element basket, String prefix, String text) {
        assertEquals(2, children(basket).size());
        var item = object(basket, refined + ".Region.Item", prefix + "-i");
        assertField(item, abstractTown, "Label", text);
        assertField(item, abstractTown, "Size", "4");
        assertField(item, refined, "Extra", "added");
        object(basket, abstractTown + ".Core.SurveyMark", prefix + "-m");
    }

    private void assertReferences(Element data, String prefix) {
        var region = basket(data, version, links + ".Region", prefix + "-b");
        var registry = basket(data, version, links + ".Registry", prefix + "-registry");
        assertEquals(3, children(region).size());
        assertEquals(1, children(registry).size());
        var delivery = object(region, links + ".Region.Delivery", prefix + "-delivery");
        var crate = object(region, linksBase + ".Core.Crate", prefix + "-crate");
        var depot = object(region, linksBase + ".Core.Depot", prefix + "-depot");
        var note = children(child(delivery, "Details")).getFirst();
        assertName(note, version, links + ".Region.Note");
        String targetTid = id(depot, version, "tid");
        assertEquals(targetTid, id(child(note, "ReceivingDepot"), version, "ref"));
        assertEquals(targetTid, id(child(delivery, "DeliveredAt"), version, "ref"));
        assertEquals(targetTid, id(child(crate, "StockedAt"), version, "ref"));
        var entry = object(registry, links + ".Registry.RegisterEntry", prefix + "-entry");
        assertEquals(id(entry, version, "tid"), id(child(note, "Entry"), version, "ref"));
    }

    private void assertField(Element object, String model, String field, String expected) {
        var value = child(object, field);
        if (version == 24) assertName(value, version, model + "." + field);
        assertEquals(expected, value.getTextContent());
    }

    private Element object(Element basket, String name, String tid) {
        var object = children(basket).stream().filter(element -> tid.equals(id(element, version, "tid")))
                .findFirst().orElseThrow();
        assertName(object, version, name);
        return object;
    }
}

package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import guru.interlis.execution.CancellationToken;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static guru.interlis.db.DbTestSupport.assertSchemaWithIdentityColumns;
import static guru.interlis.db.DbTestSupport.child;
import static guru.interlis.db.DbTestSupport.children;
import static guru.interlis.db.DbTestSupport.cli;
import static guru.interlis.db.DbTestSupport.id;
import static guru.interlis.db.DbTestSupport.readDataSection;
import static guru.interlis.db.ModelFixtures.assertName;
import static guru.interlis.db.ModelFixtures.assertStructures;
import static guru.interlis.db.ModelFixtures.basket;
import static guru.interlis.db.ModelFixtures.fixture;
import static guru.interlis.db.ModelFixtures.headerModels;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ModelSourceFixtures {
    private final int version;
    final String shared;
    final String stored;
    final String structures;
    final String types;
    final Path input;
    final Path storedFile;
    final Path middleFile;
    private final Path sharedFile;
    private final Path typesFile;
    private final Path structureFile;
    private final Path libraryFile;

    ModelSourceFixtures(Path work, int version) throws Exception {
        this.version = version;
        shared = "DbSharedSource" + version;
        stored = "DbStoredImports" + version;
        structures = "DbImportedStruct" + version;
        types = "DbStoredTypes" + version;
        sharedFile = fixture(work, shared + ".ili");
        storedFile = fixture(work, stored + ".ili");
        middleFile = fixture(work, "DbStoredMiddle" + version + ".ili");
        typesFile = fixture(work, types + ".ili");
        structureFile = fixture(work, structures + ".ili");
        libraryFile = fixture(work, "DbSharedDetail" + version + ".ili");
        input = fixture(work, "DbModelSources" + version + ".xtf");
    }

    String referenceModels() {
        return String.join(";", shared, stored, structures);
    }

    void schema(Target db) {
        assertSchemaWithIdentityColumns(db, shared, sharedFile, "--model", stored, "--model", structures,
                "--model-file", storedFile.toString(), "--model-file", middleFile.toString(), "--model-file", typesFile.toString(),
                "--model-file", structureFile.toString(), "--model-file", libraryFile.toString());
    }

    void removeSources() throws Exception {
        for (Path source : List.of(sharedFile, storedFile, middleFile, typesFile, structureFile, libraryFile)) {
            Files.delete(source);
        }
    }

    String[] extraModels() {
        return new String[]{"--model", stored, "--model", structures};
    }

    void run(Target db, String operation, String dataset, Path path) {
        var result = cli(db, operation, shared, dataset, path, CancellationToken.none(), extraModels());
        assertEquals(0, result.code(), result::diagnostics);
    }

    void assertDelivery(Path output, String prefix) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(Set.of(shared, stored, structures), Set.copyOf(headerModels(data, version)));
        assertEquals(3, children(data).size());
        assertDomainBasket(data, shared, "Station", prefix + "-shared");
        assertDomainBasket(data, stored, "ImportedStation", prefix + "-stored");
        assertStructures(basket(data, version, structures + ".Data", prefix + "-detail-basket"),
                version, structures, "DbSharedDetail" + version, prefix + "-detail");
    }

    private void assertDomainBasket(Element data, String model, String type, String prefix) {
        var basket = basket(data, version, model + ".Data", prefix + "-basket");
        assertEquals(1, children(basket).size());
        var station = children(basket).getFirst();
        assertName(station, version, model + ".Data." + type);
        assertEquals(prefix + "-station", id(station, version, "tid"));
        assertEquals("Imported domains", child(station, "Name").getTextContent());
        assertEquals("active", child(station, "State").getTextContent());
        var position = children(children(child(station, "Location")).getFirst());
        assertEquals(0, new BigDecimal("2600123.125").compareTo(new BigDecimal(position.get(0).getTextContent())));
        assertEquals(0, new BigDecimal("1200456.375").compareTo(new BigDecimal(position.get(1).getTextContent())));
    }
}

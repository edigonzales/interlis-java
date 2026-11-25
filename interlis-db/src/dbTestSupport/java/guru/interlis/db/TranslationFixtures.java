package guru.interlis.db;

import guru.interlis.db.DbTestSupport.Target;
import org.w3c.dom.Element;

import java.math.BigDecimal;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TranslationFixtures {
    final String original;
    final String translated;
    final String nestedOriginal;
    final String nestedTranslated;
    final boolean french;
    final String scalarLabel;
    private final Path work;
    private final int version;

    TranslationFixtures(Path work, int version) {
        this.work = work;
        this.version = version;
        original = "TownBase" + version;
        translated = "TownFrench" + version;
        nestedOriginal = "NestedTown" + version;
        nestedTranslated = "NestedFrench" + version;
        french = version == 23;
        scalarLabel = french ? "translated" : "original";
    }

    Path copy(String name) throws Exception {
        return ModelFixtures.fixture(work, "translation/" + name);
    }

    Path scalarInput() throws Exception {
        return copy(scalarLabel + version + ".xtf");
    }

    void scalarSchema(Target db, Path originalSource, Path translatedSource, String... options) {
        var args = new ArrayList<>(List.of("--model-file", originalSource.toString()));
        args.addAll(List.of(options));
        assertSchemaWithIdentityColumns(db, translated, translatedSource, args.toArray(String[]::new));
    }

    void assertScalarDelivery(Path output, boolean inFrench, String prefix, String memo) throws Exception {
        var data = readDataSection(output, version);
        assertEquals(List.of(inFrench ? translated : original), headerModels(data, version));
        assertEquals(1, children(data).size());
        assertScalar(data, inFrench, prefix, memo);
    }

    void assertMixedDelivery(Path output, String scalarPrefix, String nestedPrefix, String memo) throws Exception {
        var data = readDataSection(output, version);
        var models = headerModels(data, version);
        assertEquals(2, models.size());
        assertEquals(Set.of(french ? translated : original, french ? nestedTranslated : nestedOriginal), Set.copyOf(models));
        assertEquals(2, children(data).size());
        assertScalar(data, french, scalarPrefix, memo);
        assertNested(data, nestedPrefix);
    }

    private void assertScalar(Element data, boolean inFrench, String prefix, String memo) {
        String model = inFrench ? translated : original;
        String topic = model + (inFrench ? ".Releve" : ".Survey");
        var basket = basket(data, version, topic, prefix + "-b");
        assertEquals(1, children(basket).size());
        var station = children(basket).getFirst();
        assertName(station, version, topic + (inFrench ? ".Poste" : ".Station"));
        assertEquals(prefix + "-o", id(station, version, "tid"));
        assertEquals(memo, child(station, inFrench ? "Note" : "Memo").getTextContent());
        assertEquals(inFrench ? "ouvert" : "open", child(station, inFrench ? "Statut" : "State").getTextContent());
    }

    private void assertNested(Element data, String prefix) {
        var topic = french ? nestedTranslated + ".Releve" : nestedOriginal + ".Survey";
        var basket = basket(data, version, topic, prefix + "-b");
        assertEquals(2, children(basket).size());
        var reading = children(basket).stream().filter(record -> id(record, version, "tid").equals(prefix + "-r"))
                .findFirst().orElseThrow();
        var site = children(basket).stream().filter(record -> id(record, version, "tid").equals(prefix + "-s"))
                .findFirst().orElseThrow();
        assertName(reading, version, topic + (french ? ".Mesure" : ".Reading"));
        assertName(site, version, topic + (french ? ".Lieu" : ".Site"));
        var outer = children(child(reading, french ? "Infos" : "Payload")).getFirst();
        assertName(outer, version, topic + (french ? ".Dossier" : ".Details"));
        var inner = children(child(outer, french ? "Entree" : "Entry")).getFirst();
        assertName(inner, version, topic + (french ? ".Note" : ".Detail"));
        assertEquals("inside", child(inner, french ? "Texte" : "Memo").getTextContent());
        assertEquals(prefix + "-s", id(child(inner, french ? "Cible" : "Target"), version, "ref"));
        assertEquals(prefix + "-s", id(child(reading, french ? "AuLieu" : "AtSite"), version, "ref"));
        var coordinate = children(child(reading, french ? "Position" : "Location")).getFirst();
        var axes = children(coordinate);
        assertEquals(0, new BigDecimal("2600215.125").compareTo(new BigDecimal(axes.get(0).getTextContent())));
        assertEquals(0, new BigDecimal("1200224.500").compareTo(new BigDecimal(axes.get(1).getTextContent())));
    }
}

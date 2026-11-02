package guru.interlis.documentation;

import java.util.*;

/** Small declarative presentation catalogue, independent from model identifiers. */
final class DocumentationLabels {
    private static final List<String> KEYS=List.of("catalog","name","kind","type","cardinality","origin","description","constraints","inherited","base");
    private static final Map<DocumentationOptions.Language,List<String>> VALUES=Map.of(
        DocumentationOptions.Language.EN,List.of("Object catalogue","Name","Kind","Type","Cardinality","Origin","Description","Constraints","Inherited members","Base"),
        DocumentationOptions.Language.DE,List.of("Objektkatalog","Name","Art","Typ","Kardinalität","Herkunft","Beschreibung","Bedingungen","Geerbte Mitglieder","Basistyp"),
        DocumentationOptions.Language.FR,List.of("Catalogue des objets","Nom","Genre","Type","Cardinalité","Origine","Description","Contraintes","Membres hérités","Type de base"),
        DocumentationOptions.Language.IT,List.of("Catalogo degli oggetti","Nome","Genere","Tipo","Cardinalità","Origine","Descrizione","Vincoli","Membri ereditati","Tipo di base"));
    static String get(DocumentationOptions o,String key) {int i=KEYS.indexOf(key);if(i<0)throw new IllegalArgumentException(key);return o.headers().getOrDefault(key,VALUES.get(o.language()).get(i));}
}

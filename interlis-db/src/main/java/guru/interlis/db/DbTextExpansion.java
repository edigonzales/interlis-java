package guru.interlis.db;

import guru.interlis.db.Ili2dbLayout.AttributeTransformation;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.CollectionKind;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.Modifier;
import guru.interlis.model.SymbolRef;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.transfer.AttributeSlot;
import guru.interlis.transfer.ObjectDescriptor;
import guru.interlis.transfer.TransferPlan;
import guru.interlis.transfer.TransferValue;
import guru.interlis.transfer.TransferValue.Enumeration;
import guru.interlis.transfer.TransferValue.Repeated;
import guru.interlis.transfer.TransferValue.Structured;
import guru.interlis.transfer.TransferValue.Text;
import guru.interlis.transfer.TransferValue.Undefined;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.db.DbStructureShape.bagElement;
import static guru.interlis.db.DbStructureShape.field;
import static guru.interlis.db.DbStructureShape.target;
import static guru.interlis.db.DbStructureShape.type;

/** The checked XTF structure and the ordered SQL fields of an ili2db text expansion. */
record DbTextExpansion(SymbolRef structure, Optional<AttributeSlot> translations, SymbolRef entry,
                       AttributeSlot language, AttributeSlot text, TextType textType) {
    private static final List<String> LANGUAGES = List.of("", "de", "fr", "rm", "it", "en");

    static Optional<DbTextExpansion> plan(TransferPlan plan, AttributeSlot outer, Set<AttributeTransformation> transformations) {
        for (var transformation : List.of(AttributeTransformation.MULTILINGUAL, AttributeTransformation.LOCALISED)) {
            if (!transformations.contains(transformation)) continue;
            var binding = describe(plan, outer, transformation);
            if (binding.isPresent()) return binding;
        }
        return Optional.empty();
    }

    AttributeTransformation transformation() {
        return translations.isPresent() ? AttributeTransformation.MULTILINGUAL : AttributeTransformation.LOCALISED;
    }

    static Optional<DbTextExpansion> describe(TransferPlan plan, AttributeSlot outer,
                                               AttributeTransformation transformation) {
        if (!CardinalityUpper.finite(1).equals(outer.cardinality().upper())) return Optional.empty();
        var structure = target(plan, type(plan, outer)).orElse(null);
        if (structure == null || structure.modifiers().contains(Modifier.ABSTRACT)) return Optional.empty();
        if (transformation == AttributeTransformation.LOCALISED) {
            if (!(chbaseLocalised(structure) || "Localised".equals(mapping(plan, structure)))) return Optional.empty();
            return describeEntry(plan, structure, Optional.empty(), structure);
        }
        if (structure.slots().size() != 1
                || !(chbaseMultilingual(structure, true) || "Multilingual".equals(mapping(plan, structure)))) return Optional.empty();
        var translations = field(structure, "LocalisedText").orElse(null);
        var entry = translations == null ? null : bagElement(plan, translations).orElse(null);
        if (entry == null || entry.modifiers().contains(Modifier.ABSTRACT)) return Optional.empty();
        return describeEntry(plan, structure, Optional.of(translations), entry);
    }

    private static Optional<DbTextExpansion> describeEntry(TransferPlan plan, ObjectDescriptor structure,
                                                           Optional<AttributeSlot> translations, ObjectDescriptor entry) {
        if (entry.slots().size() != 2) return Optional.empty();
        var language = field(entry, "Language").orElse(null);
        var text = field(entry, "Text").orElse(null);
        if (language == null || text == null || !(type(plan, language) instanceof EnumerationType)
                || !(type(plan, text) instanceof TextType value)
                || (value.textKind() != TextKind.TEXT && value.textKind() != TextKind.MTEXT)) return Optional.empty();
        return Optional.of(new DbTextExpansion(structure.symbol(), translations, entry.symbol(), language, text, value));
    }

    private static boolean chbaseLocalised(ObjectDescriptor structure) {
        return switch (structure.name().declared().toString()) {
            case "LocalisationCH_V1.LocalisedText", "LocalisationCH_V1.LocalisedMText",
                 "LocalisationCH_V2.LocalisedText", "LocalisationCH_V2.LocalisedMText" -> true;
            default -> false;
        };
    }

    static boolean isLocalisationBase(ObjectDescriptor structure) {
        return switch (structure.name().declared().toString()) {
            case "Localisation_V1.LocalisedText", "Localisation_V1.LocalisedMText",
                 "Localisation_V2.LocalisedText", "Localisation_V2.LocalisedMText" -> true;
            default -> false;
        };
    }

    static boolean isInheritedCollection(TransferPlan plan, ObjectDescriptor structure, AttributeSlot field) {
        return chbaseMultilingual(structure, false) && field.name().declared().last().equals("LocalisedText")
                && bagElement(plan, field).isPresent();
    }

    private static boolean chbaseMultilingual(ObjectDescriptor structure, boolean swissOnly) {
        return switch (structure.name().declared().toString()) {
            case "LocalisationCH_V1.MultilingualText", "LocalisationCH_V1.MultilingualMText",
                 "LocalisationCH_V2.MultilingualText", "LocalisationCH_V2.MultilingualMText" -> true;
            case "Localisation_V1.MultilingualText", "Localisation_V1.MultilingualMText",
                 "Localisation_V2.MultilingualText", "Localisation_V2.MultilingualMText" -> !swissOnly;
            default -> false;
        };
    }

    private static String mapping(TransferPlan plan, ObjectDescriptor structure) {
        return DbMapping.metaText(plan.symbol(structure.symbol()).known().orElseThrow(), "ili2db.mapping");
    }

    List<String> columns(String stem) {
        return translations.isPresent() ? LANGUAGES.stream().map(lang -> lang.isEmpty() ? stem : stem + "_" + lang).toList()
                : List.of(stem, stem + "_lang");
    }

    int width() { return translations.isPresent() ? LANGUAGES.size() : 2; }

    TransferValue assemble(String[] values) {
        if (translations.isEmpty()) return values[0] == null ? null : entry(values[0], values[1]);
        var entries = new ArrayList<TransferValue>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) continue;
            String lang = LANGUAGES.get(i);
            entries.add(entry(values[i], lang.isEmpty() ? null : lang));
        }
        return entries.isEmpty() ? null : new Structured(structure,
                Map.of(translations.orElseThrow().symbol(), new Repeated(CollectionKind.BAG, entries)));
    }

    private Structured entry(String value, String lang) {
        return new Structured(entry, lang == null ? Map.of(text.symbol(), new Text(value))
                : Map.of(text.symbol(), new Text(value), language.symbol(), new Enumeration(List.of(lang))));
    }

    String[] disassemble(TransferValue value, String context) {
        var values = new String[width()];
        if (value == null || value == Undefined.INSTANCE) return values;
        var wrapper = checked(value, structure, context);
        if (translations.isEmpty()) {
            var fields = entryValues(wrapper, context);
            if (fields.text() == null) return values;
            if (fields.language() != null && fields.language().length() != 2) {
                throw new DbException("Cannot encode language code " + fields.language() + " at " + context);
            }
            return new String[]{fields.text(), fields.language()};
        }
        var collection = translations.orElseThrow().symbol();
        if (wrapper.values().keySet().stream().anyMatch(field -> !collection.equals(field))) {
            throw invalid(context);
        }
        var contents = wrapper.values().get(collection);
        if (contents == null || contents == Undefined.INSTANCE) return values;
        if (!(contents instanceof Repeated bag) || bag.kind() != CollectionKind.BAG) throw invalid(context);
        var seen = new HashSet<String>();
        for (var item : bag.values()) {
            var fields = entryValues(checked(item, entry, context), context);
            String code = fields.language() == null ? "" : fields.language();
            int index = LANGUAGES.indexOf(code);
            if (index < 0) throw new DbException("No stored language column for " + code + " at " + context);
            if (!seen.add(code)) throw new DbException("Multiple texts for language " + (code.isEmpty() ? "(undefined)" : code)
                    + " at " + context);
            values[index] = fields.text();
        }
        return values;
    }

    private record EntryValues(String text, String language) { }

    private EntryValues entryValues(Structured entry, String context) {
        if (entry.values().keySet().stream().anyMatch(field -> !language.symbol().equals(field) && !text.symbol().equals(field))) {
            throw invalid(context);
        }
        var lang = entry.values().get(language.symbol());
        String code = lang == null || lang == Undefined.INSTANCE ? null : languageCode(lang, context);
        var value = entry.values().get(text.symbol());
        if (value == null || value == Undefined.INSTANCE) return new EntryValues(null, code);
        if (value instanceof Text text) return new EntryValues(text.value(), code);
        throw invalid(context);
    }

    private static Structured checked(TransferValue value, SymbolRef type, String context) {
        if (value instanceof Structured structure && structure.type().equals(type)) return structure;
        throw invalid(context);
    }

    private static String languageCode(TransferValue value, String context) {
        if (value instanceof Enumeration enumeration && enumeration.path().size() == 1) return enumeration.path().getFirst();
        throw invalid(context);
    }

    private static DbException invalid(String context) {
        return new DbException("Cannot encode expanded text at " + context);
    }
}

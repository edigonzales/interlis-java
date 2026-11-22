package guru.interlis.db;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static guru.interlis.db.DbStructureShape.bagElement;
import static guru.interlis.db.DbStructureShape.field;
import static guru.interlis.db.DbStructureShape.target;
import static guru.interlis.db.DbStructureShape.type;

/** The checked XTF structure and the ordered SQL fields of an ili2db text expansion. */
record DbTextExpansion(SymbolRef structure, AttributeSlot translations, SymbolRef entry,
                       AttributeSlot language, AttributeSlot text, TextType textType) {
    private static final List<String> LANGUAGES = List.of("", "de", "fr", "rm", "it", "en");

    static Optional<DbTextExpansion> describe(TransferPlan plan, AttributeSlot outer) {
        if (!CardinalityUpper.finite(1).equals(outer.cardinality().upper())) return Optional.empty();
        var structure = target(plan, type(plan, outer)).orElse(null);
        if (structure == null || structure.modifiers().contains(Modifier.ABSTRACT) || structure.slots().size() != 1
                || !(chbaseMultilingual(structure, true) || "Multilingual".equals(mapping(plan, structure)))) {
            return Optional.empty();
        }
        var translations = field(structure, "LocalisedText").orElse(null);
        var entry = translations == null ? null : bagElement(plan, translations).orElse(null);
        if (entry == null || entry.modifiers().contains(Modifier.ABSTRACT) || entry.slots().size() != 2) return Optional.empty();
        var language = field(entry, "Language").orElse(null);
        var text = field(entry, "Text").orElse(null);
        if (language == null || text == null || !(type(plan, language) instanceof EnumerationType)
                || !(type(plan, text) instanceof TextType value)
                || (value.textKind() != TextKind.TEXT && value.textKind() != TextKind.MTEXT)) return Optional.empty();
        return Optional.of(new DbTextExpansion(structure.symbol(), translations, entry.symbol(), language, text, value));
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
        return LANGUAGES.stream().map(lang -> lang.isEmpty() ? stem : stem + "_" + lang).toList();
    }

    int width() { return LANGUAGES.size(); }

    TransferValue assemble(String[] values) {
        var entries = new ArrayList<TransferValue>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) continue;
            String lang = LANGUAGES.get(i);
            Map<SymbolRef, TransferValue> fields = new HashMap<>();
            fields.put(text.symbol(), new Text(values[i]));
            if (!lang.isEmpty()) fields.put(language.symbol(), new Enumeration(List.of(lang)));
            entries.add(new Structured(entry, fields));
        }
        return entries.isEmpty() ? null : new Structured(structure,
                Map.of(translations.symbol(), new Repeated(CollectionKind.BAG, entries)));
    }

    String[] disassemble(TransferValue value, String context) {
        var values = new String[width()];
        if (value == null || value == Undefined.INSTANCE) return values;
        var wrapper = checked(value, structure, context);
        if (wrapper.values().keySet().stream().anyMatch(field -> !translations.symbol().equals(field))) {
            throw invalid(context);
        }
        var contents = wrapper.values().get(translations.symbol());
        if (contents == null || contents == Undefined.INSTANCE) return values;
        if (!(contents instanceof Repeated bag) || bag.kind() != CollectionKind.BAG) throw invalid(context);
        var seen = new HashSet<String>();
        for (var item : bag.values()) {
            var entry = checked(item, this.entry, context);
            if (entry.values().keySet().stream().anyMatch(field -> !language.symbol().equals(field) && !text.symbol().equals(field))) {
                throw invalid(context);
            }
            var lang = entry.values().get(language.symbol());
            String code = lang == null || lang == Undefined.INSTANCE ? "" : languageCode(lang, context);
            int index = LANGUAGES.indexOf(code);
            if (index < 0) throw new DbException("No stored language column for " + code + " at " + context);
            if (!seen.add(code)) throw new DbException("Multiple texts for language " + (code.isEmpty() ? "(undefined)" : code)
                    + " at " + context);
            var content = entry.values().get(text.symbol());
            if (content instanceof Text textValue) values[index] = textValue.value();
            else if (content != null && content != Undefined.INSTANCE) throw invalid(context);
        }
        return values;
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

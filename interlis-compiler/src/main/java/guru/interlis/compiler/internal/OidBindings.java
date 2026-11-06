package guru.interlis.compiler.internal;

import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeId;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.TopicSyntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Binds object identification rules and their predefined domains. */
final class OidBindings {
    private final SemanticGraphRead state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final NameBindings names;
    private final Supplier<TypeBindings> typeBindings;
    private final Supplier<InheritanceBindings> inheritance;
    private final Consumer<DomainSymbol> defineOidDomain;
    private final Consumer<Symbol> replaceOidOwner;
    private final Function<SymbolId, SymbolId> scopeRoot;

    OidBindings(SemanticGraphRead state,
                SemanticWork work,
                IndexedDeclarations index,
                SemanticDiagnostics diagnostics,
                NameBindings names,
                Supplier<TypeBindings> typeBindings,
                Supplier<InheritanceBindings> inheritance,
                Consumer<DomainSymbol> defineOidDomain,
                Consumer<Symbol> replaceOidOwner,
                Function<SymbolId, SymbolId> scopeRoot) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.names = names;
        this.typeBindings = typeBindings;
        this.inheritance = inheritance;
        this.defineOidDomain = defineOidDomain;
        this.replaceOidOwner = replaceOidOwner;
        this.scopeRoot = scopeRoot;
    }

    void bindOidRules() {
        Map<SymbolId, List<guru.interlis.model.OidRule>> bound = new LinkedHashMap<>();
        for (Symbol symbol : List.copyOf(state.symbolsInBuildOrder())) {
            if (symbol instanceof ClassSymbol || symbol instanceof TopicSymbol || symbol instanceof AssociationSymbol)
                bindOidRules(symbol.id(), bound, new HashSet<>());
        }
    }

    List<guru.interlis.model.OidRule> bindOidRules(SymbolId id,
            Map<SymbolId, List<guru.interlis.model.OidRule>> bound, Set<SymbolId> active) {
        work.checkpoint();
        if (bound.containsKey(id)) return bound.get(id);
        if (!active.add(id)) return List.of(); // Existing inheritance checks diagnose the cycle.
        Symbol symbol = state.symbol(id);
        DeclInfo decl = index.byId(id).orElse(null);
        if (decl == null || !(symbol instanceof ClassSymbol || symbol instanceof TopicSymbol || symbol instanceof AssociationSymbol))
            return List.of();
        List<guru.interlis.syntax.OidRuleSyntax> declared = switch (decl.syntax()) {
            case ClassDeclarationSyntax c -> c.oidRules();
            case TopicSyntax t -> t.oidRules();
            case AssociationDeclarationSyntax a -> a.oidRules();
            default -> List.of();
        };
        List<guru.interlis.model.OidRule> inherited = state.hasBase(id)
                ? bindOidRules(state.baseOf(id), bound, active) : List.of();
        List<guru.interlis.model.OidRule> rules = new ArrayList<>(inherited);
        if (!(symbol instanceof TopicSymbol) && rules.isEmpty()) {
            SymbolId topic = scopeRoot.apply(id);
            if (topic != null && !topic.equals(id) && state.symbol(topic) instanceof TopicSymbol)
                bindOidRules(topic, bound, active).stream().filter(r -> !r.basket()).forEach(rules::add);
        }
        for (var rule : declared) {
            work.checkpoint();
            if (symbol instanceof TopicSymbol && rule.domain().isEmpty()) {
                diagnostics.emitRule(decl, "NO OID is only allowed on classes and associations", "OID_TOPIC");
                continue;
            }
            Optional<SymbolId> domain = Optional.empty();
            if (rule.domain().isPresent()) {
                var name = rule.domain().orElseThrow();
                domain=names.bindTarget(decl,name,Set.of(SymbolKind.DOMAIN),ReferenceUsage.TYPE_USE,new ArrayList<>());
                if (domain.isEmpty()) continue;
                if (!(typeBindings.get().effectiveDescriptor(state.declaredType(domain.orElseThrow())) instanceof guru.interlis.model.OidType)) {
                    diagnostics.emitRule(decl, "OID AS must bind an OID domain", "OID_DOMAIN"); continue;
                }
            }
            var own = new guru.interlis.model.OidRule(rule.basket(), domain, new Origin.DeclaredOrigin(rule.range()));
            var previous = inherited.stream().filter(r -> r.basket() == own.basket()).findFirst();
            if (previous.isPresent() && !oidOverrideAllowed(previous.orElseThrow(), own, decl.language(), symbol instanceof TopicSymbol))
                diagnostics.emitRule(decl, "OID rule cannot change its inherited domain", "OID_OVERRIDE");
            rules.removeIf(r -> r.basket() == own.basket()); rules.add(own);
        }
        // Only 2.4 defines an omitted class rule as inherited NO OID.
        if (decl.language() == guru.interlis.model.LanguageVersion.ILI24
                && state.symbol(state.baseOf(id)) instanceof ClassSymbol && inherited.isEmpty()
                && rules.stream().anyMatch(r -> !r.basket() && oidState(r) == 2))
            diagnostics.emitRule(decl, "inherited NO OID must first be concretized as ANYOID", "OID_OVERRIDE");
        rules.sort(Comparator.comparing(r -> !r.basket()));
        for (var rule : rules) {
            if (oidState(rule) == 1 && !decl.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                diagnostics.emitRule(decl, "ANYOID requires an abstract topic, class or association", "OID_ABSTRACT");
        }
        if (symbol instanceof AssociationSymbol association) {
            Cardinality cardinality = association.cardinality();
            boolean multiple = !(cardinality.upper() instanceof guru.interlis.model.CardinalityUpper.Finite finite)
                    || finite.value().compareTo(java.math.BigInteger.ONE) > 0;
            boolean identified = rules.isEmpty()
                    ? inheritance.get().baseChain(id).stream().map(index::byId).flatMap(Optional::stream)
                        .anyMatch(d -> d.modifiers().contains(guru.interlis.model.Modifier.OID))
                    : rules.stream().anyMatch(r -> !r.basket() && oidState(r) != 0);
            if (multiple && !identified)
                diagnostics.emitRule(decl, "multiple links for the same participants require OID", "ASSOCIATION_CARDINALITY_OID");
        }
        var h = symbol.header();
        var header = new SymbolHeader(h.id(), h.kind(), h.name(), h.owner(), h.declarationOrder(), h.origin(),
                h.nameRange(), h.languageVersion(), h.documentation(), h.metaAttributes(), h.modifiers(), rules);
        Symbol replacement = switch (symbol) {
            case ClassSymbol c -> new ClassSymbol(header, c.baseType(), c.members(), c.constraints());
            case TopicSymbol t -> new TopicSymbol(header, t.baseTopic(), t.members(), t.dependencies(), t.deferredGenerics(), t.genericBindings());
            case AssociationSymbol a -> new AssociationSymbol(header, a.baseAssociation(), a.cardinality(), a.roles(), a.attributes(), a.constraints(), a.derivedFrom());
            default -> symbol;
        };
        replaceOidOwner.accept(replacement); active.remove(id);
        var result = List.copyOf(rules); bound.put(id, result); return result;
    }

    int oidState(guru.interlis.model.OidRule rule) {
        if (rule.domain().isEmpty()) return 0;
        Symbol domain = state.symbol(rule.domain().orElseThrow());
        if (domain != null && domain.name().value().equals("INTERLIS.NOOID")) return 0;
        return typeBindings.get().effectiveDescriptor(state.declaredType(rule.domain().orElseThrow())) instanceof guru.interlis.model.OidType oid
                && oid.oidKind() == guru.interlis.model.OidKind.ANY ? 1 : 2;
    }

    private boolean oidOverrideAllowed(guru.interlis.model.OidRule base, guru.interlis.model.OidRule derived,
            guru.interlis.model.LanguageVersion language, boolean topic) {
        if (base.domain().equals(derived.domain())) return true;
        if (base.domain().isPresent() && derived.domain().isPresent()) {
            Symbol a = state.symbol(base.domain().orElseThrow()), b = state.symbol(derived.domain().orElseThrow());
            if (a != null && b != null && a.header().origin() instanceof Origin.SyntheticOrigin
                    && b.header().origin() instanceof Origin.SyntheticOrigin && a.name().equals(b.name())) return true;
        }
        int from = oidState(base), to = oidState(derived);
        if (topic) return false; // A topic assignment is fixed, including ANYOID.
        return from == 0 && to == 0 || from == 1 && to == 2
                || language == guru.interlis.model.LanguageVersion.ILI24 && from == 0 && to == 1;
    }

    Optional<SymbolId> builtinOidDomain(SymbolId context, String name) {
        if (!Set.of("INTERLIS.NOOID", "INTERLIS.ANYOID", "INTERLIS.I32OID", "INTERLIS.STANDARDOID", "INTERLIS.UUIDOID").contains(name))
            return Optional.empty();
        var language = index.byId(context).orElseThrow().language();
        if (name.equals("INTERLIS.NOOID") && language == guru.interlis.model.LanguageVersion.ILI23)
            return Optional.empty();
        SymbolId symbol = new SymbolId("predefined:" + language + ":domain:" + name);
        if (state.hasSymbol(symbol)) return Optional.of(symbol);
        Optional<SymbolId> base = language == guru.interlis.model.LanguageVersion.ILI23 || name.equals("INTERLIS.NOOID")
                ? Optional.empty()
                : builtinOidDomain(context, name.equals("INTERLIS.ANYOID") ? "INTERLIS.NOOID" : "INTERLIS.ANYOID");
        Origin origin = new Origin.PredefinedOrigin(language, name);
        var kind = name.equals("INTERLIS.I32OID") ? guru.interlis.model.OidKind.NUMERIC
                : name.equals("INTERLIS.UUIDOID") ? guru.interlis.model.OidKind.UUID
                : name.equals("INTERLIS.STANDARDOID") ? guru.interlis.model.OidKind.TEXT : guru.interlis.model.OidKind.ANY;
        Optional<TypeId> carrier = Optional.empty();
        if (kind != guru.interlis.model.OidKind.ANY) {
            TypeId value = typeBindings.get().nextTypeId();
            typeBindings.get().storeType(kind == guru.interlis.model.OidKind.NUMERIC
                    ? new NumericType(value, origin, NumericRange.of(guru.interlis.model.DecimalLiteral.parse("0"), guru.interlis.model.DecimalLiteral.parse("2147483647")))
                    : new TextType(value, origin, TextKind.TEXT, java.util.OptionalInt.of(kind == guru.interlis.model.OidKind.UUID ? 36 : 16)));
            carrier = Optional.of(value);
        }
        TypeId type = typeBindings.get().nextTypeId(); typeBindings.get().storeType(new guru.interlis.model.OidType(type, origin, kind, carrier));
        typeBindings.get().bindDeclaredType(symbol, type); base.ifPresent(b -> inheritance.get().bindBase(symbol, b));
        defineOidDomain.accept(new DomainSymbol(new SymbolHeader(symbol, SymbolKind.DOMAIN, DeclaredName.synthetic(name),
                Optional.empty(), 0, origin, Optional.empty(), language,
                Documentation.empty(), MetaAttributeSet.empty(), language == guru.interlis.model.LanguageVersion.ILI24 && name.equals("INTERLIS.ANYOID")
                        ? Set.of(guru.interlis.model.Modifier.ABSTRACT) : Set.of(), List.of()), type, base, false));
        return Optional.of(symbol);
    }
}

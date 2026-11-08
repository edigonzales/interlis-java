package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.Cardinality;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
import guru.interlis.model.CollectionType;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EnumElementSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.ModelImport;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.FunctionParameter;
import guru.interlis.model.UnsupportedSymbol;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.FunctionDeclarationSyntax;
import guru.interlis.syntax.RoleDeclarationSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;
import guru.interlis.syntax.TopicSyntax;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Builds indexed declarations on demand and completes model metadata. */
final class DeclarationBindings {
    private final SemanticGraphRead.Symbols state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final NameResolver resolver;
    private final Closure closure;
    private final OverlayBindings overlays;
    private final UnitBindings units;
    private final LineForms lineForms;
    private final MetadataBindings metadata;
    private final GenericBindings generics;
    private final ConstraintBindings constraints;
    private final GraphicBuilder graphics;
    private final ViewBuilder views;
    private final NameBindings names;
    private final InheritanceBindings inheritance;
    private final ReferenceBindings references;
    private final TypeBindings typeBindings;
    private final ExpressionBindings expressions;
    private final Map<SymbolId, Optional<Symbol>> provisionalSymbols = new HashMap<>();
    private final Set<SymbolId> buildingAssociations = new HashSet<>();
    private final Map<String, DeclInfo> modelNames = new LinkedHashMap<>();

    DeclarationBindings(SemanticGraphRead.Symbols state,
                        SemanticWork work,
                        IndexedDeclarations index,
                        SemanticDiagnostics diagnostics,
                        NameResolver resolver,
                        Closure closure,
                        OverlayBindings overlays,
                        UnitBindings units,
                        LineForms lineForms,
                        MetadataBindings metadata,
                        GenericBindings generics,
                        ConstraintBindings constraints,
                        GraphicBuilder graphics,
                        ViewBuilder views,
                        NameBindings names,
                        InheritanceBindings inheritance,
                        ReferenceBindings references,
                        TypeBindings typeBindings,
                        ExpressionBindings expressions) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.resolver = resolver;
        this.closure = closure;
        this.overlays = overlays;
        this.units = units;
        this.lineForms = lineForms;
        this.metadata = metadata;
        this.generics = generics;
        this.constraints = constraints;
        this.graphics = graphics;
        this.views = views;
        this.names = names;
        this.inheritance = inheritance;
        this.references = references;
        this.typeBindings = typeBindings;
        this.expressions = expressions;
    }

    void validateTypeNamespaces() {
        var typeKinds = DeclarationNamespaces.TYPE_KINDS;
        var names = new LinkedHashMap<SymbolId, Map<String, DeclInfo>>();
        var declarations = index.all().stream().sorted(Comparator.comparingInt(DeclInfo::order)).toList();
        for (var declaration : declarations) {
            work.checkpoint();
            if (declaration.owner().isPresent() && declaration.nameRange().isPresent()
                    && typeKinds.contains(declaration.kind())) {
                var scope = names.computeIfAbsent(declaration.owner().orElseThrow(), ignored -> new LinkedHashMap<>());
                var prior = scope.putIfAbsent(declaration.name(), declaration);
                if (prior != null) emitNamespaceRule(declaration, prior,
                        "type names must be unique in their declaring scope", "SCOPE_TYPE_NAME_UNIQUE");
            }
        }
        for (var declaration : declarations) {
            work.checkpoint();
            if (declaration.owner().isEmpty() || declaration.nameRange().isEmpty()
                    || !typeKinds.contains(declaration.kind())) continue;
            var owner = declaration.owner().orElseThrow();
            var outer = index.byId(owner).flatMap(DeclInfo::owner);
            while (outer.isPresent()) {
                work.checkpoint();
                var prior = names.getOrDefault(outer.orElseThrow(), Map.of()).get(declaration.name());
                if (prior != null) {
                    emitNamespaceRule(declaration, prior,
                            "local type names must not shadow an enclosing type name", "SCOPE_OUTER_TYPE_NAME");
                    break;
                }
                outer = index.byId(outer.orElseThrow()).flatMap(DeclInfo::owner);
            }
            if (declaration.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) continue;
            var seen = new HashSet<SymbolId>();
            for (var inherited = state.baseOf(owner); inherited != null && seen.add(inherited); inherited = state.baseOf(inherited)) {
                work.checkpoint();
                var prior = names.getOrDefault(inherited, Map.of()).get(declaration.name());
                if (prior != null) {
                    emitNamespaceRule(declaration, prior,
                            "inherited type names require an explicit EXTENDED declaration", "SCOPE_INHERITED_TYPE_NAME");
                    break;
                }
            }
        }
    }

    private void emitNamespaceRule(DeclInfo declaration, DeclInfo prior, String rule, String reason) {
        var related = new guru.interlis.diagnostics.RelatedLocation(prior.nameRange().orElseThrow(),
                "diagnostic.related.conflictingDeclaration",
                List.of(new DiagnosticArgument.IdentifierArgument("name", prior.name())));
        diagnostics.emitRule(declaration, rule, reason, List.of(related));
    }

    void buildModel(IndexedDeclarations.ModelEntry entry) {
        DeclInfo decl = entry.declaration();
        guru.interlis.syntax.ModelSyntax modelSyntax = entry.model().syntax();
        try {
            String issuer = modelSyntax.issuer();
            if (issuer.length() > 1023 || issuer.chars().anyMatch(c -> c > 127))
                throw new java.net.URISyntaxException(issuer, "issuer outside URI character/length profile");
            new java.net.URI(issuer);
        } catch (java.net.URISyntaxException invalid) {
            diagnostics.emitRule(decl, "model issuer must be an ASCII URI reference of at most 1023 characters", "MODEL_ISSUER");
        }
        if (modelSyntax.endName().isPresent()
                && !modelSyntax.endName().orElseThrow().equals(modelSyntax.name())) {
            diagnostics.emitRule(decl, "MODEL end name does not match declaration", "MODEL_END_NAME");
        }
        if (modelSyntax.modelKind() == guru.interlis.model.ModelKind.SYMBOLOGY
                && modelSyntax.languageVersion() == guru.interlis.model.LanguageVersion.ILI23 && !modelSyntax.contracted())
            diagnostics.emitRule(decl, "SYMBOLOGY MODEL requires CONTRACTED in INTERLIS 2.3", "SYMBOLOGY_CONTRACTED");
        if (modelSyntax.modelKind() == guru.interlis.model.ModelKind.TYPE) {
            for (var child : index.children(decl.id())) {
                work.checkpoint();
                if (Set.of(SymbolKind.TOPIC, SymbolKind.PARAMETER).contains(child.kind())
                        || child.kind() == SymbolKind.METADATA_BASKET
                        && (!(child.syntax() instanceof guru.interlis.syntax.MetadataBasketDeclarationSyntax basket)
                            || basket.basketKind()!=guru.interlis.model.MetadataBasketSymbol.Kind.REFSYSTEM)
                        || child.kind() == SymbolKind.CLASS && !child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT))
                    diagnostics.emitRule(child, "TYPE MODEL may only declare type definitions", "TYPE_MODEL_CONTENT");
            }
        }
        // The inventory may retain several versions. Uniqueness belongs to the
        // selected closure, not to every available source in the workspace.
        if (closure.models().contains(entry.model())) {
            DeclInfo previousModel = modelNames.putIfAbsent(modelSyntax.name(), decl);
            if (previousModel != null && !previousModel.id().equals(decl.id())) {
                diagnostics.emitRule(decl, "model name must be unique in the selected closure", "MODEL_NAME_UNIQUE");
            }
        }
        Set<String> parameterNames = new LinkedHashSet<>();
        for (var child : index.children(decl.id())) {
            if (child.kind() == SymbolKind.PARAMETER && !parameterNames.add(child.name()))
                diagnostics.emitRule(child, "runtime parameter name must be unique in its model", "PARAMETER_NAME_UNIQUE");
        }
        Set<String> modelLevelNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            if (child.kind() == SymbolKind.DOMAIN || child.kind() == SymbolKind.TOPIC) {
                if (!modelLevelNames.add(child.name())) {
                    diagnostics.emitRule(child, "topic/domain name must be unique in a model",
                            "MODEL_MEMBER_NAME_UNIQUE");
                }
            }
            if (child.kind() == SymbolKind.TOPIC) {
                Set<String> classNames = new LinkedHashSet<>();
                for (DeclInfo topicChild : index.children(child.id())) {
                    work.checkpoint();
                    if (topicChild.kind() == SymbolKind.CLASS
                            && !classNames.add(topicChild.name())) {
                        diagnostics.emitRule(topicChild, "class name must be unique in a topic",
                                "TOPIC_CLASS_NAME_UNIQUE");
                    }
                }
            }
        }
        List<SymbolId> members = index.children(decl.id()).stream().map(DeclInfo::id).toList();
        List<ModelImport> imports = new ArrayList<>();
        for (guru.interlis.syntax.ImportSyntax site : entry.model().syntax().imports()) {
            work.checkpoint();
            SymbolId target = null;
            for (Closure.ImportEdge edge : closure.importEdges()) {
                work.checkpoint();
                if (edge.importer().sourceId().equals(entry.model().sourceId())
                        && edge.importer().declarationOffset() == entry.model().declarationOffset()
                        && edge.site().range().equals(site.range())) {
                    target = index.findModelId(edge.target().name(), edge.target().declarationOffset(),
                            edge.target().sourceId()).orElse(null);
                    break;
                }
            }
            if (site.modelName().equals("INTERLIS")) target = PredefinedModels.ensure(state, entry.declaration().language());
            imports.add(new ModelImport(site.modelName(), site.unqualified(), site.range(),
                    Optional.ofNullable(target)));
        }
        var modelOptions = entry.model().syntax().options();
        modelOptions.characterSet().ifPresent(option -> {
            if (CharacterSets.canonical(option.value()).isEmpty()) diagnostics.emitRule(decl, "CHARSET name is not registered by IANA", "MODEL_CHARSET", ((Origin.DeclaredOrigin)option.origin()).range());
        });
        modelOptions.xmlNamespace().ifPresent(option -> {
            try {
                var uri = new java.net.URI(option.value());
                if (option.value().isEmpty() || option.value().equals("http://www.w3.org/2000/xmlns/")
                        || option.value().equals("http://www.w3.org/XML/1998/namespace"))
                    throw new java.net.URISyntaxException(option.value(), "reserved or empty namespace");
            } catch (java.net.URISyntaxException invalid) {
                diagnostics.emitRule(decl, "XMLNS must be a nonempty, unreserved namespace URI reference", "MODEL_XMLNS", ((Origin.DeclaredOrigin)option.origin()).range());
            }
        });
        guru.interlis.syntax.ModelSyntax syntax = entry.model().syntax();
        Optional<guru.interlis.model.ModelTranslation> translation = syntax.translationSource()
                .map(source -> new guru.interlis.model.ModelTranslation(source.text(),
                        syntax.translationVersion(), syntax.language(), Optional.empty(), List.of()));
        translation=overlays.translation(decl.id(),translation);
        state.defineSymbol(new ModelSymbol(header(decl), syntax.modelKind(),
                syntax.contracted(), entry.model().version(), syntax.issuer(), imports, members,
                translation, syntax.options(), modelNamespace(syntax)));
    }

    private String modelNamespace(guru.interlis.syntax.ModelSyntax syntax) {
        return syntax.languageVersion() == guru.interlis.model.LanguageVersion.ILI23 ? "http://www.interlis.ch/INTERLIS2.3"
                : syntax.options().xmlNamespace().map(guru.interlis.model.ModelOptions.TextValue::value)
                        .orElse("http://www.interlis.ch/xtf/2.4/" + syntax.name());
    }

    /** Translations share the original transfer vocabulary, independent of file/declaration order. */
    void bindTransferNamespaces() {
        for (var symbol : List.copyOf(state.symbolsInBuildOrder())) {
            if (!(symbol instanceof ModelSymbol model) || model.translation().isEmpty()) continue;
            Set<SymbolId> seen = new HashSet<>();
            ModelSymbol original = model;
            while (original.translation().flatMap(guru.interlis.model.ModelTranslation::sourceTarget).isPresent()
                    && seen.add(original.id())) {
                work.checkpoint();
                var next = state.symbol(original.translation().orElseThrow().sourceTarget().orElseThrow());
                if (!(next instanceof ModelSymbol base)) break;
                original = base;
            }
            String namespace = original.transferNamespace();
            if (model.options().xmlNamespace().filter(value -> !value.value().equals(namespace)).isPresent())
                diagnostics.emitRule(index.byId(model.id()).orElseThrow(), "translated XMLNS conflicts with the original transfer namespace", "TRANSLATION_XMLNS");
            state.replaceSymbol(new ModelSymbol(model.header(), model.modelKind(), model.contracted(), model.modelVersion(),
                    model.issuer(), model.imports(), model.members(), model.translation(), model.options(), namespace));
        }
    }

    Symbol buildSymbol(DeclInfo decl) {
        work.checkpoint();
        SymbolId id = decl.id();
        Symbol existing = state.symbol(id);
        if (existing != null) {
            return existing;
        }
        boolean outermost = provisionalSymbols.putIfAbsent(id, Optional.empty()) == null;
        try {
            Symbol symbol = bindDeclaration(decl);
            if (!id.equals(symbol.id()))
                throw new IllegalStateException("Binder returned a different declaration: " + id);
            Symbol published = state.symbol(id);
            if (published != symbol) {
                // Complete only the exact value published by a recursive dispatch of this declaration.
                if (published != null && provisionalSymbols.get(id).orElse(null) == published)
                    state.replaceSymbol(symbol);
                else
                    state.defineSymbol(symbol);
            }
            if (!outermost) provisionalSymbols.put(id, Optional.of(symbol));
            return symbol;
        } finally {
            if (outermost) provisionalSymbols.remove(id);
        }
    }

    private Symbol bindDeclaration(DeclInfo decl) {
        return switch (decl.kind()) {
            case TOPIC -> buildTopic(decl);
            case CLASS -> buildClass(decl);
            case STRUCTURE -> buildStructure(decl);
            case ASSOCIATION -> buildAssociation(decl);
            case DOMAIN -> buildDomain(decl);
            case UNIT -> units.buildUnit(decl);
            case LINE_FORM -> lineForms.buildForm(decl);
            case METADATA_BASKET -> metadata.basket(decl);
            case METAOBJECT -> metadata.object(decl);
            case FUNCTION -> buildFunction(decl);
            case PARAMETER -> buildParameter(decl);
            case CONTEXT -> generics.buildContext(decl);
            case ATTRIBUTE -> buildAttribute(decl);
            case ROLE -> buildRole(decl);
            case CONSTRAINT -> constraints.buildConstraint(decl);
            case ENUM_ELEMENT -> buildEnumElement(decl);
            case UNSUPPORTED -> buildUnsupported(decl);
            case GRAPHIC -> graphics.build(decl);
            case DRAWING_RULE -> {
                buildSymbol(index.byId(decl.owner().orElseThrow()).orElseThrow());
                yield state.symbol(decl.id());
            }
            case VIEW -> views.build(decl);
            case VIEW_BASE -> views.buildBase(decl);
            case MODEL -> throw new IllegalStateException("model symbols are built separately");
        };
    }

    private TopicSymbol buildTopic(DeclInfo decl) {
        TopicSyntax syntax = (TopicSyntax) decl.syntax();
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            buildSymbol(child);
        }
        if (syntax.endName().isPresent() && !syntax.endName().orElseThrow().equals(syntax.name())) {
            diagnostics.emitRule(decl, "TOPIC end name does not match declaration", "TOPIC_END_NAME");
        }
        Optional<SymbolId> base = syntax.baseTopic().flatMap(name -> {
            var resolution = resolver.resolve(decl.id(), name, Set.of(SymbolKind.TOPIC));
            references.deferBaseAmbiguity(decl, name, resolution);
            if (resolution.status() == NameResolver.Status.AMBIGUOUS)
                names.reportAmbiguity(name, resolution);
            else if (resolution.status() == NameResolver.Status.UNRESOLVED)
                diagnostics.unresolved(name);
            return resolution.target();
        });
        base.ifPresent(id -> inheritance.bindBase(decl.id(), id));
        if (base.filter(inheritance::isFinalDeclaration).isPresent())
            diagnostics.emitRule(decl, "cannot extend FINAL topic", "TOPIC_FINAL");
        if (base.isPresent() && state.symbol(base.orElseThrow()) instanceof TopicSymbol baseTopic) {
            boolean abstractBase = baseTopic.header().modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT);
            for (SymbolId member : baseTopic.members()) {
                work.checkpoint();
                if (state.symbol(member) instanceof ClassSymbol baseClass
                        && baseClass.header().modifiers()
                        .contains(guru.interlis.model.Modifier.ABSTRACT)) {
                    abstractBase = true;
                }
            }
            Set<String> baseDomains = new LinkedHashSet<>();
            for (SymbolId member : baseTopic.members()) {
                work.checkpoint();
                if (state.symbol(member) instanceof DomainSymbol domain) {
                    baseDomains.add(domain.name().value());
                }
            }
            for (DeclInfo child : index.children(decl.id())) {
                work.checkpoint();
                if (child.kind() == SymbolKind.DOMAIN && baseDomains.contains(child.name())) {
                    diagnostics.emitRule(child, "domain name conflicts with an inherited topic member",
                            "INHERITED_NAME_CONFLICT");
                }
            }
            boolean baseHasConcrete = base.flatMap(index::byId).map(baseDecl -> {
                for (DeclInfo child : index.children(baseDecl.id())) {
                    work.checkpoint();
                    if (child.kind() == SymbolKind.STRUCTURE) {
                        return true;
                    }
                    if (child.kind() == SymbolKind.CLASS
                            && !child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
                        return true;
                    }
                }
                return false;
            }).orElse(false);
            boolean derivedHasContent = !index.children(decl.id()).isEmpty()
                    || (decl.syntax() instanceof guru.interlis.syntax.TopicSyntax topicSyntax
                    && !topicSyntax.oidRules().isEmpty())
                    || !syntax.deferredGenerics().isEmpty();
            if (abstractBase && !derivedHasContent && !baseHasConcrete && !syntax.modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT)) {
                diagnostics.emitRule(decl, "empty topic extending an abstract topic must be abstract",
                        "ABSTRACT_TOPIC_EXTENSION");
            }
            for (DeclInfo child : index.children(decl.id())) {
                work.checkpoint();
                if (child.kind() != SymbolKind.CLASS
                        || !(state.symbol(child.id()) instanceof ClassSymbol derivedClass)
                        || derivedClass.baseType().isEmpty()) {
                    continue;
                }
                for (SymbolId member : baseTopic.members()) {
                    work.checkpoint();
                    if (!(state.symbol(member) instanceof ClassSymbol baseClass)
                            || !baseClass.name().equals(derivedClass.name())) {
                        continue;
                    }
                    if (!child.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                        diagnostics.emitRule(child, "class in an extending topic may not extend the "
                                + "same-named base class without EXTENDED", "TOPIC_CLASS_EXTENSION");
                    }
                    if (baseClass.header().modifiers()
                            .contains(guru.interlis.model.Modifier.EXTENDED)) {
                        diagnostics.emitRule(child, "cannot extend a class marked EXTENDED",
                                "EXTENDED_BASE_CLASS");
                    }
                }
            }
        }
        List<SymbolId> dependencies = new ArrayList<>();
        for (guru.interlis.syntax.NameSyntax dependency : syntax.dependencies()) {
            work.checkpoint();
            var resolution = resolver.resolve(decl.id(), dependency, Set.of(SymbolKind.TOPIC));
            Optional<SymbolId> resolved = resolution.target();
            if (resolved.isEmpty()) diagnostics.unresolved(dependency);
            resolved.ifPresent(dependencies::add);
            references.recordResolution(decl, dependency, resolution, ReferenceUsage.TYPE_USE);
        }
        Set<String> memberNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            if (child.kind() == SymbolKind.CLASS || child.kind() == SymbolKind.DOMAIN) {
                if (!memberNames.add(child.name())) {
                    diagnostics.emitRule(child, "name conflict in topic", "TOPIC_MEMBER_NAME");
                }
            }
        }
        if (!syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            List<DeclInfo> classes = index.children(decl.id()).stream()
                    .filter(child -> child.kind() == SymbolKind.CLASS).toList();
            for (DeclInfo child : classes) {
                work.checkpoint();
                if (child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                        && classes.stream().noneMatch(candidate ->
                            !candidate.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                            && inheritance.baseChain(candidate.id()).contains(child.id())))
                    diagnostics.emitRule(decl, "abstract class requires an abstract topic or a concrete specialization",
                            "ABSTRACT_TOPIC");
            }
        }
        List<SymbolId> members = index.children(decl.id()).stream().map(DeclInfo::id).toList();
        List<SymbolId> deferred = new ArrayList<>();
        for (var name : syntax.deferredGenerics()) names.bindTarget(decl, name, Set.of(SymbolKind.DOMAIN),
                ReferenceUsage.TYPE_USE, new ArrayList<>()).ifPresent(deferred::add);
        return new TopicSymbol(header(decl), base, members, dependencies, deferred, List.of());
    }

    private ClassSymbol buildClass(DeclInfo decl) {
        ClassDeclarationSyntax syntax = (ClassDeclarationSyntax) decl.syntax();
        if (syntax.endName().isPresent() && !syntax.endName().orElseThrow().equals(syntax.name())) {
            diagnostics.emitRule(decl, "CLASS end name does not match declaration", "CLASS_END_NAME");
        }
        Optional<SymbolId> base = inheritance.objectInheritanceBase(decl.id());
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            buildSymbol(child);
        }
        List<SymbolId> members = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE || child.kind() == SymbolKind.PARAMETER)
                .map(DeclInfo::id).toList();
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        for (SymbolId member : members) {
            work.checkpoint();
            DeclInfo memberDecl = index.byId(member).orElse(null);
            if (memberDecl != null && memberDecl.kind() == SymbolKind.ATTRIBUTE) {
                TypeId typeId = state.declaredType(member);
                if (decl.language() != guru.interlis.model.LanguageVersion.ILI24
                        && typeId != null && state.type(typeId) instanceof ReferenceType) {
                    diagnostics.emitRule(memberDecl, "reference attribute is only allowed in a structure",
                            "REFERENCE_ATTRIBUTE_CLASS");
                }
            }
        }
        return new ClassSymbol(header(decl), base, members, constraints);
    }

    private StructureSymbol buildStructure(DeclInfo decl) {
        StructureDeclarationSyntax syntax = (StructureDeclarationSyntax) decl.syntax();
        Optional<SymbolId> base = inheritance.objectInheritanceBase(decl.id());
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            buildSymbol(child);
        }
        List<SymbolId> members = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE || child.kind() == SymbolKind.PARAMETER)
                .map(DeclInfo::id).toList();
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        return new StructureSymbol(header(decl), base, members, constraints);
    }

    private AssociationSymbol buildAssociation(DeclInfo decl) {
        AssociationDeclarationSyntax syntax = (AssociationDeclarationSyntax) decl.syntax();
        buildingAssociations.add(decl.id());
        var explicitBase = syntax.baseAssociation()
                .map(name -> resolver.resolve(decl.id(), name, Set.of(SymbolKind.ASSOCIATION)));
        Optional<SymbolId> base = explicitBase.flatMap(NameResolver.Resolution::target);
        if (explicitBase.isPresent()) {
            var name = syntax.baseAssociation().orElseThrow();
            references.deferBaseAmbiguity(decl, name, explicitBase.orElseThrow());
            if (base.isEmpty()) diagnostics.unresolved(name);
        }
        if (base.isEmpty() && explicitBase.stream().noneMatch(r -> r.status() == NameResolver.Status.AMBIGUOUS)
                && syntax.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
            base = inheritance.baseTopicMember(decl.id(), decl.name(), Set.of(SymbolKind.ASSOCIATION));
            if (base.isEmpty()) diagnostics.emitRule(decl, "EXTENDED association needs an inherited base", "ASSOCIATION_BASE");
        }
        if (base.isPresent()) {
            SymbolId parent = base.orElseThrow();
            if (buildingAssociations.contains(parent)) {
                diagnostics.emitRule(decl, "cyclic association inheritance", "INHERITANCE_CYCLE");
                base = Optional.empty();
            } else {
                buildSymbol(index.byId(parent).orElseThrow());
                inheritance.bindBase(decl.id(), parent);
                if (state.symbol(parent).header().modifiers().contains(guru.interlis.model.Modifier.FINAL)) {
                    diagnostics.emitRule(decl, "cannot extend FINAL association", "ASSOCIATION_FINAL");
                }
            }
        }
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            buildSymbol(child);
        }
        buildingAssociations.remove(decl.id());
        if (syntax.nameRange().isPresent() != syntax.endName().isPresent()) {
            diagnostics.emitRule(decl, "association name must be present at both begin and END", "ASSOCIATION_NAME");
        }
        Set<String> roleNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            if (child.kind() == SymbolKind.ROLE && !roleNames.add(child.name())) {
                diagnostics.emitRule(child, "duplicate role name", "DUPLICATE_ROLE");
            }
        }
        List<SymbolId> roles = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ROLE)
                .map(DeclInfo::id).toList();
        List<SymbolId> attributes = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE)
                .map(DeclInfo::id).toList();
        if (roles.isEmpty() && attributes.isEmpty() && base.isEmpty()) {
            diagnostics.emitRule(decl, "association requires at least two roles", "ASSOCIATION_ROLES");
        }
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        Cardinality inheritedCardinality = base.map(state::symbol).map(AssociationSymbol.class::cast)
                .map(AssociationSymbol::cardinality).orElse(Cardinality.exactly(1));
        Cardinality effectiveCardinality = syntax.cardinality().orElse(inheritedCardinality);
        if (base.isPresent() && (effectiveCardinality.lower().compareTo(inheritedCardinality.lower()) < 0
                || widerUpper(effectiveCardinality.upper(), inheritedCardinality.upper()))) {
            diagnostics.emitRule(decl, "association extension widens link cardinality", "ASSOCIATION_CARDINALITY_EXTENSION");
        }
        return new AssociationSymbol(header(decl), base, effectiveCardinality, roles, attributes, constraints,
                index.children(decl.id()).stream().filter(d -> d.kind() == SymbolKind.VIEW_BASE).map(DeclInfo::id).findFirst());
    }

    private guru.interlis.model.ParameterSymbol buildParameter(DeclInfo decl) {
        var syntax = (guru.interlis.syntax.ParameterDeclarationSyntax) decl.syntax();
        var model = index.modelOfSymbol(decl.id()).flatMap(index::byId)
                .map(m -> (guru.interlis.syntax.ModelSyntax) m.syntax()).orElseThrow();
        if (index.byId(decl.owner().orElseThrow()).orElseThrow().kind() == SymbolKind.MODEL
                && decl.language() == guru.interlis.model.LanguageVersion.ILI23 && !model.contracted())
            diagnostics.emitRule(decl, "runtime parameters require CONTRACTED in INTERLIS 2.3", "PARAMETER_CONTRACTED");
        TypeId type = typeBindings.buildType(syntax.type(), decl.id(), decl.sourceId());
        if (index.byId(decl.owner().orElseThrow()).orElseThrow().kind() == SymbolKind.MODEL && containsArea(type, new HashSet<>()))
            diagnostics.emitRule(decl, "runtime parameters cannot contain AREA values", "PARAMETER_AREA");
        typeBindings.bindDeclaredType(decl.id(), type);
        return new guru.interlis.model.ParameterSymbol(header(decl), type, syntax.mandatory() || typeBindings.mandatoryType(type), Optional.empty());
    }

    private boolean containsArea(TypeId type, Set<TypeId> seen) {
        if (!seen.add(type)) return false;
        TypeDescriptor descriptor = typeBindings.effectiveDescriptor(type);
        if (descriptor instanceof GeometryType geometry)
            return geometry.geometryKind() == guru.interlis.model.GeometryKind.AREA
                    || geometry.geometryKind() == guru.interlis.model.GeometryKind.MULTIAREA;
        if (descriptor instanceof CollectionType collection) return containsArea(collection.elementType(), seen);
        if (descriptor instanceof StructureValueType structure) {
            for(SymbolId root:structure.targetRoots()) {
                Set<SymbolId> visited=new HashSet<>();
                for(SymbolId current=root;current!=null && visited.add(current);current=inheritance.objectInheritanceBase(current).orElse(null)) {
                    work.checkpoint();
                    if(state.symbol(current) instanceof StructureSymbol known) {
                        for(SymbolId member:known.members())
                            if(state.symbol(member) instanceof AttributeSymbol attribute && containsArea(attribute.declaredType(),seen))return true;
                    } else for(var child:index.children(current))
                        if(child.kind()==SymbolKind.ATTRIBUTE && containsArea(typeBindings.attributeType(child),seen))return true;
                }
            }
        }
        return false;
    }

    private FunctionSymbol buildFunction(DeclInfo decl) {
        FunctionDeclarationSyntax syntax = (FunctionDeclarationSyntax) decl.syntax();
        var model = index.modelOfSymbol(decl.id()).flatMap(index::byId)
                .map(m -> (guru.interlis.syntax.ModelSyntax) m.syntax());
        if (decl.language() == guru.interlis.model.LanguageVersion.ILI23
                && model.isPresent() && !model.orElseThrow().contracted()) {
            diagnostics.emitRule(decl, "functions require CONTRACTED in INTERLIS 2.3", "FUNCTION_CONTRACTED");
        }
        Set<String> names = new LinkedHashSet<>();
        for (var parameter : syntax.parameters()) {
            work.checkpoint();
            if (!names.add(parameter.name())) {
                diagnostics.emitRule(decl, "duplicate function parameter", "FUNCTION_PARAMETER_DUPLICATE");
            }
        }
        List<FunctionParameter> parameters = java.util.stream.IntStream.range(0, syntax.parameters().size())
                .mapToObj(i -> new FunctionParameter(syntax.parameters().get(i).name(), typeBindings.functionArgumentType(decl, i), syntax.parameters().get(i).mandatory() || typeBindings.mandatoryType(typeBindings.functionArgumentType(decl,i)))).toList();
        TypeId result=typeBindings.buildType(syntax.returnType(), decl.id(), decl.sourceId());
        return new FunctionSymbol(header(decl), parameters,result,syntax.returnMandatory() || typeBindings.mandatoryType(result),syntax.explanation().map(e->new Documentation(e.text(),List.of(e.range()))).orElseGet(Documentation::empty));
    }

    private DomainSymbol buildDomain(DeclInfo decl) {
        TypeId type = typeBindings.domainType(decl);
        Set<String> constraintNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            if (child.kind() == SymbolKind.CONSTRAINT && !constraintNames.add(child.name())) {
                diagnostics.emitRule(child, "domain constraint names must be unique", "DOMAIN_CONSTRAINT_NAME");
            }
            buildSymbol(child);
        }
        return new DomainSymbol(header(decl), type, Optional.ofNullable(state.baseOf(decl.id())),
                decl.modifiers().contains(guru.interlis.model.Modifier.GENERIC));
    }

    private AttributeSymbol buildAttribute(DeclInfo decl) {
        AttributeDeclarationSyntax syntax = (AttributeDeclarationSyntax) decl.syntax();
        TypeId type = typeBindings.attributeType(decl);
        for(var child:index.children(decl.id()))if(child.kind()==SymbolKind.ENUM_ELEMENT)buildSymbol(child);
        if (syntax.transientAttribute() && !syntax.assigned()
                && !syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            diagnostics.emitRule(decl, "transient attribute requires an assignment", "TRANSIENT_ASSIGNMENT");
        }
        return new AttributeSymbol(header(decl), type, syntax.mandatory() || typeBindings.mandatoryType(type),
                syntax.transientAttribute(), syntax.continuous(), syntax.subdivision(),
                Optional.empty(), bindAttributeDerivations(decl, syntax, type));
    }

    private List<ExpressionId> bindAttributeDerivations(DeclInfo decl, AttributeDeclarationSyntax syntax, TypeId type) {
        List<ExpressionId> inferred = typeBindings.inferredDerivations(decl.id());
        if (inferred != null) return inferred;
        List<ExpressionId> result = new ArrayList<>();
        for (var expression : syntax.derivations()) {
            var bound = expressions.contextualValue(expressions.buildExpression(expression, decl.owner().orElseThrow()),type,decl.id());
            result.add(bound.id());
            if(!bound.error() && (syntax.mandatory() || typeBindings.mandatoryType(type))
                    && guru.interlis.model.internal.TypeCompatibility.isUndefined(typeBindings.effectiveDescriptor(bound.typeId())))
                diagnostics.emitRule(decl,"MANDATORY attribute excludes an explicitly undefined derived value","MANDATORY_VALUE",expression.range());
            if (!bound.error() && !typeBindings.assignable(bound.typeId(), type))
                diagnostics.emitTypeMismatch(decl.sourceId(), expression.range(), typeBindings.assignmentTypeName(bound.typeId()),
                        typeBindings.assignmentTypeName(type));
        }
        return List.copyOf(result);
    }

    private RoleSymbol buildRole(DeclInfo decl) {
        RoleDeclarationSyntax syntax = (RoleDeclarationSyntax) decl.syntax();
        List<guru.interlis.model.RoleTarget> targets = new ArrayList<>();
        for (var target : syntax.targets()) {
            work.checkpoint();
            SymbolId base = bindRoleTarget(decl, target.base());
            List<SymbolId> restrictions = target.restrictions().stream()
                    .map(name -> bindRoleTarget(decl, name)).toList();
            for (SymbolId restriction : restrictions) {
                if (!inheritance.targetSpecializes(restriction, base)) {
                    diagnostics.emitRule(decl, "restriction must specialize its role target", "ROLE_TARGET_RESTRICTION");
                }
            }
            for (var previous : targets) {
                if (inheritance.targetSpecializes(base, previous.base()) || inheritance.targetSpecializes(previous.base(), base)) {
                    diagnostics.emitRule(decl, "role alternatives must not specialize each other", "ROLE_TARGET_OVERLAP");
                }
            }
            targets.add(new guru.interlis.model.RoleTarget(base, restrictions));
        }
        Cardinality cardinality = syntax.cardinality();
        SymbolId associationBase = state.baseOf(decl.owner().orElseThrow());
        RoleSymbol inherited = associationBase == null ? null : inheritance.associationRoles(associationBase).stream()
                .filter(r -> r.name().value().equals(decl.name())).findFirst().orElse(null);
        if (inherited == null) {
            if (associationBase != null) diagnostics.emitRule(decl, "extended association must not add roles", "ASSOCIATION_EXTENDED_ROLE");
            if (decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)
                    || decl.modifiers().contains(guru.interlis.model.Modifier.HIDING)) {
                diagnostics.emitRule(decl, "role extension requires an inherited role", "ROLE_EXTENSION_BASE");
            }
        } else {
            if (!decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                diagnostics.emitRule(decl, "role override requires EXTENDED", "ROLE_EXTENSION");
            }
            if (inherited.header().modifiers().contains(guru.interlis.model.Modifier.FINAL)) {
                diagnostics.emitRule(decl, "cannot override FINAL role", "ROLE_FINAL");
            }
            if (!syntax.explicitCardinality()) cardinality = inherited.cardinality();
            if (cardinality.lower().compareTo(inherited.cardinality().lower()) < 0
                    || widerUpper(cardinality.upper(), inherited.cardinality().upper())
                    || syntax.aggregation().ordinal() < inherited.aggregation().ordinal()) {
                diagnostics.emitRule(decl, "extended role widens cardinality or weakens aggregation", "ROLE_EXTENSION");
            }
            if (inherited.external() != decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL)) {
                diagnostics.emitRule(decl, "EXTERNAL must remain unchanged in an extended role", "ROLE_EXTERNAL_EXTENSION");
            }
            if (inherited.ordered() && !decl.modifiers().contains(guru.interlis.model.Modifier.ORDERED)) {
                diagnostics.emitRule(decl, "an ordered role cannot become unordered in an extension", "ROLE_ORDER_EXTENSION");
            }
            if (targets.stream().flatMap(t -> t.allowedRoots().stream()).anyMatch(t -> !inheritance.roleAccepts(inherited, t))) {
                diagnostics.emitRule(decl, "extended role widens its target set", "ROLE_TARGET_EXTENSION");
            }
        }
        return new RoleSymbol(header(decl), targets, cardinality, syntax.aggregation(),
                decl.modifiers().contains(guru.interlis.model.Modifier.ORDERED),
                decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL),
                decl.modifiers().contains(guru.interlis.model.Modifier.HIDING), bindRoleDerivation(decl, syntax, targets));
    }

    private Optional<ExpressionId> bindRoleDerivation(DeclInfo decl, RoleDeclarationSyntax syntax, List<guru.interlis.model.RoleTarget> targets) {
        return syntax.derivation().map(expression -> {
            var bound = expressions.buildExpression(expression, decl.owner().orElseThrow());
            var actual = typeBindings.effectiveDescriptor(bound.typeId());
            if (!bound.error()) {
                var roots = targets.stream().flatMap(t -> t.allowedRoots().stream()).toList();
                var expected = new guru.interlis.model.ObjectArgumentType(typeBindings.nextTypeId(), decl.origin(), Optional.empty(), roots, false);
                typeBindings.defineType(expected);
                if (!typeBindings.assignable(bound.typeId(), expected.id())) diagnostics.emitTypeMismatch(decl.sourceId(), expression.range(), typeBindings.typeName(bound.typeId()), "single role target");
            }
            return bound.id();
        });
    }

    SymbolId bindObjectTarget(DeclInfo decl, guru.interlis.syntax.NameSyntax name, ReferenceUsage usage) {
        if (name.text().equals("ANYCLASS")) {
            SymbolId target = anyClass(decl);
            references.recordReference(decl, name, target, usage);
            return target;
        }
        var kinds = Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION);
        var resolution = resolver.resolve(decl.id(), name, kinds);
        if (resolution.status() == NameResolver.Status.UNRESOLVED && name.segments().size() == 1) {
            var inherited = inheritance.baseTopicMember(decl.id(), name.text(), kinds);
            if (inherited.isPresent()) {
                references.recordReference(decl, name, inherited.orElseThrow(), usage);
                return inherited.orElseThrow();
            }
        }
        if (resolution.target().isEmpty()) diagnostics.unresolved(name);
        references.recordResolution(decl, name, resolution, usage);
        return resolution.target().orElseGet(() -> syntheticClass(decl, name));
    }

    private SymbolId bindRoleTarget(DeclInfo decl, guru.interlis.syntax.NameSyntax name) {
        var target=bindObjectTarget(decl, name, ReferenceUsage.ROLE_TARGET);
        return target;
    }

    private SymbolId anyClass(DeclInfo owner) {
        SymbolId model = index.modelOfSymbol(owner.id()).orElseThrow();
        SymbolId id = new SymbolId(model.value() + ":predefined:ANYCLASS");
        if (!state.hasSymbol(id)) {
            state.defineSymbol(new ClassSymbol(new SymbolHeader(id, SymbolKind.CLASS,
                    DeclaredName.declared("ANYCLASS"), Optional.of(model), 0,
                    new Origin.PredefinedOrigin(owner.language(), "INTERLIS.ANYCLASS"), Optional.empty(),
                    owner.language(), Documentation.empty(), MetaAttributeSet.empty(), Set.of(), List.of()),
                    Optional.empty(), List.of(), List.of()));
        }
        return id;
    }

    private SymbolId syntheticClass(DeclInfo owner, guru.interlis.syntax.NameSyntax name) {
        SymbolId id = new SymbolId("sym-unresolved-" + typeBindings.nextTypeSequence());
        SymbolId model = index.modelOfSymbol(owner.id()).orElse(owner.id());
        SymbolHeader header = new SymbolHeader(id, SymbolKind.CLASS,
                DeclaredName.synthetic("<unresolved-role-target>"),
                Optional.of(model), 0,
                new Origin.SyntheticOrigin(guru.interlis.model.SyntheticReason.MISSING_REFERENCE,
                        Optional.of(name.range())),
                Optional.empty(), owner.language(), Documentation.empty(),
                MetaAttributeSet.empty(), Set.of(), List.of());
        ClassSymbol synthetic = new ClassSymbol(header, Optional.empty(),
                List.of(), List.of());
        state.defineSymbol(synthetic);
        return id;
    }

    private boolean widerUpper(guru.interlis.model.CardinalityUpper left,
                               guru.interlis.model.CardinalityUpper right) {
        if (left instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return !(right instanceof guru.interlis.model.CardinalityUpper.Unbounded);
        }
        if (right instanceof guru.interlis.model.CardinalityUpper.Unbounded) {
            return false;
        }
        if (left instanceof guru.interlis.model.CardinalityUpper.Finite leftFinite
                && right instanceof guru.interlis.model.CardinalityUpper.Finite rightFinite) {
            return leftFinite.value().compareTo(rightFinite.value()) > 0;
        }
        return false;
    }

    private EnumElementSymbol buildEnumElement(DeclInfo decl) {
        String path = decl.name();
        List<String> segments = List.of(path.split("\\."));
        TypeDescriptor type = decl.owner().map(state::declaredType).map(typeBindings::effectiveDescriptor).orElse(null);
        int ordinal = type instanceof EnumerationType enumeration ? enumeration.values().stream()
                .filter(value -> value.path().equals(path)).mapToInt(EnumerationValue::order).findFirst().orElse(-1) : -1;
        if (ordinal < 0) {
            // Erroneous partial types still retain local declaration order, never
            // the unrelated global declaration counter.
            ordinal = index.children(decl.owner().orElseThrow()).stream().filter(d -> d.kind() == SymbolKind.ENUM_ELEMENT)
                    .map(DeclInfo::id).toList().indexOf(decl.id());
        }
        return new EnumElementSymbol(header(decl), segments, ordinal);
    }

    private UnsupportedSymbol buildUnsupported(DeclInfo decl) {
        String feature = decl.featureId().orElse("UNKNOWN_SYNTAX");
        diagnostics.emitUnsupported(decl.sourceId(), decl.origin() instanceof Origin.DeclaredOrigin d
                ? d.range() : null, feature, decl.name());
        return new UnsupportedSymbol(header(decl), feature, decl.name());
    }

    SymbolHeader header(DeclInfo decl) {
        var values=new LinkedHashMap<String,MetaAttributeSet.Value>();
        var entries=new ArrayList<MetaAttributeSet.Entry>();
        for(var attribute:decl.metadata().attributes()) {
            var value=new MetaAttributeSet.Value(attribute.text(),new Origin.DeclaredOrigin(attribute.range()));
            entries.add(new MetaAttributeSet.Entry(attribute.key(),value));
            var previous=values.putIfAbsent(attribute.key(),value);
            boolean repeatable=attribute.key().equals("fn.param") && decl.syntax() instanceof FunctionDeclarationSyntax;
            if(previous!=null && !previous.text().equals(attribute.text()) && !repeatable)diagnostics.emitRule(decl,"conflicting source metaattribute: "+attribute.key(),"METAATTRIBUTE_CONFLICT",attribute.range());
        }
        var configured=overlays.metadata(decl.id());
        if(!configured.isEmpty()) {
            var combined=new ArrayList<MetaAttributeSet.Entry>(configured);combined.addAll(entries);entries=combined;values.clear();
            for(var entry:entries)values.putIfAbsent(entry.key(),entry.value());
        }
        var documentation=new Documentation(decl.metadata().documentation().stream().map(guru.interlis.syntax.SourceMetadata.Text::text).collect(java.util.stream.Collectors.joining("\n\n")),
                decl.metadata().documentation().stream().map(guru.interlis.syntax.SourceMetadata.Text::range).toList());
        DeclaredName declaredName = isSyntheticName(decl.name())
                ? DeclaredName.synthetic(decl.name()) : DeclaredName.declared(decl.name());
        Set<guru.interlis.model.Modifier> modifiers = new LinkedHashSet<>(decl.modifiers());
        if (decl.syntax() instanceof AttributeDeclarationSyntax attribute
                && attribute.transientAttribute()) {
            modifiers.add(guru.interlis.model.Modifier.TRANSIENT);
        }
        if (decl.kind() == SymbolKind.DOMAIN && typeBindings.mandatoryDomain(decl.id())) modifiers.add(guru.interlis.model.Modifier.MANDATORY);
        return new SymbolHeader(decl.id(), decl.kind(), declaredName, decl.owner(),
                decl.order(), decl.origin(), decl.nameRange(), decl.language(),
                documentation, new MetaAttributeSet(values,entries), modifiers, List.of());
    }

    private static boolean isSyntheticName(String name) {
        return name.startsWith("<") || name.startsWith("constraint@")
                || name.startsWith("unsupported@") || name.startsWith("Association@");
    }

    Optional<SymbolId> bindObjectTarget(DeclInfo owner,
            guru.interlis.syntax.NameSyntax name, List<DiagnosticId> errors) {
        return names.bindTarget(owner, name, Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION, SymbolKind.VIEW), ReferenceUsage.TYPE_USE, errors);
    }
}

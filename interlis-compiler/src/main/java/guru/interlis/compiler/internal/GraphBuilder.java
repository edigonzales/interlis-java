package guru.interlis.compiler.internal;

import guru.interlis.compiler.ImportResolution;
import guru.interlis.compiler.SourceSet;
import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.DiagnosticId;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.AnalysisCoverage;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.BindingResolution;
import guru.interlis.model.Cardinality;
import guru.interlis.model.CardinalityUpper;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.CollectionType;
import guru.interlis.model.ConstraintDefinition;
import guru.interlis.model.ConstraintSymbol;
import guru.interlis.model.CoverageStatus;
import guru.interlis.model.DecimalLiteral;
import guru.interlis.model.DeclaredName;
import guru.interlis.model.Documentation;
import guru.interlis.model.DomainConstraint;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.DomainSymbol;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveMemberOrigin;
import guru.interlis.model.EffectiveType;
import guru.interlis.model.EnumElementSymbol;
import guru.interlis.model.EnumerationType;
import guru.interlis.model.EnumerationValue;
import guru.interlis.model.ExpressionId;
import guru.interlis.model.ErrorExpression;
import guru.interlis.model.ErrorType;
import guru.interlis.model.MandatoryConstraint;
import guru.interlis.model.MetaAttributeSet;
import guru.interlis.model.ModelImport;
import guru.interlis.model.ModelSymbol;
import guru.interlis.model.NameReference;
import guru.interlis.model.NumericRange;
import guru.interlis.model.NumericType;
import guru.interlis.model.Origin;
import guru.interlis.model.PendingReason;
import guru.interlis.model.PendingType;
import guru.interlis.model.PlausibilityConstraint;
import guru.interlis.model.ReferenceId;
import guru.interlis.model.ReferenceType;
import guru.interlis.model.ReferenceSegment;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.ScopeId;
import guru.interlis.model.SemanticGraph;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolHeader;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TextKind;
import guru.interlis.model.TextType;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.model.TypeId;
import guru.interlis.model.TypedExpression;
import guru.interlis.model.UniquenessConstraint;
import guru.interlis.model.UnsupportedSymbol;
import guru.interlis.model.ViewSymbol;
import guru.interlis.source.SourceId;
import guru.interlis.source.SourceRange;
import guru.interlis.source.SourceRevision;
import guru.interlis.source.SourceText;
import guru.interlis.syntax.AssociationDeclarationSyntax;
import guru.interlis.syntax.AttributeDeclarationSyntax;
import guru.interlis.syntax.BinaryExpressionSyntax;
import guru.interlis.syntax.BooleanTypeSyntax;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.CollectionTypeSyntax;
import guru.interlis.syntax.ConstantExpressionSyntax;
import guru.interlis.syntax.ConstraintDeclarationSyntax;
import guru.interlis.syntax.CoordinateTypeSyntax;
import guru.interlis.syntax.DefinedExpressionSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.DomainReferenceSyntax;
import guru.interlis.syntax.EnumerationTypeSyntax;
import guru.interlis.syntax.ExpressionSyntax;
import guru.interlis.syntax.LiteralExpressionSyntax;
import guru.interlis.syntax.ParenthesizedExpressionSyntax;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.ReferenceTypeSyntax;
import guru.interlis.syntax.RoleDeclarationSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;
import guru.interlis.syntax.TextTypeSyntax;
import guru.interlis.syntax.TopicSyntax;
import guru.interlis.syntax.TypeSyntax;
import guru.interlis.syntax.UnaryExpressionSyntax;
import guru.interlis.syntax.UndefinedExpressionSyntax;
import guru.interlis.syntax.UnsupportedExpressionSyntax;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Builds the immutable semantic graph for the parsed declarations. */
final class GraphBuilder {
    private final IndexedDeclarations index;
    private final SourceSet sourceSet;
    private final Closure closure;
    private final guru.interlis.model.SnapshotId snapshotId;
    private final NameResolver resolver;
    private final DiagnosticCollector collector;
    private final Map<SourceId, Set<String>> syntaxUnsupportedBySource;
    private final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
    private final Map<TypeId, TypeDescriptor> types = new LinkedHashMap<>();
    private final Map<ExpressionId, TypedExpression> expressions = new LinkedHashMap<>();
    private final Map<ReferenceId, NameReference> references = new LinkedHashMap<>();
    private final Map<SymbolId, TypeId> declaredTypes = new LinkedHashMap<>();
    private final Map<SymbolId, EffectiveType> effectiveTypes = new LinkedHashMap<>();
    private final Map<SymbolId, List<EffectiveMember>> effectiveMembers = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> baseOf = new LinkedHashMap<>();
    private final Map<SymbolId, SymbolId> modelIdByDecl = new LinkedHashMap<>();
    private final Map<SourceId, Set<String>> unsupportedBySource = new LinkedHashMap<>();
    private final Map<String, DeclInfo> modelNames = new LinkedHashMap<>();
    private int typeCounter;
    private int expressionCounter;
    private int referenceCounter;
    private TypeId booleanType;
    private TypeId textType;

    GraphBuilder(IndexedDeclarations index, SourceSet sourceSet, Closure closure,
                 DiagnosticCollector collector, guru.interlis.model.SnapshotId snapshotId,
                 Map<SourceId, Set<String>> syntaxUnsupportedBySource) {
        this.index = Objects.requireNonNull(index, "index");
        this.sourceSet = Objects.requireNonNull(sourceSet, "sourceSet");
        this.closure = Objects.requireNonNull(closure, "closure");
        this.collector = Objects.requireNonNull(collector, "collector");
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        this.syntaxUnsupportedBySource = Map.copyOf(
                Objects.requireNonNull(syntaxUnsupportedBySource, "syntaxUnsupportedBySource"));
        this.resolver = new NameResolver(index, closure);
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            modelIdByDecl.put(entry.declaration().id(), entry.id());
        }
    }

    GraphResult build() {
        syntaxUnsupportedBySource.forEach((source, features) -> {
            Set<String> known = unsupportedBySource.computeIfAbsent(source,
                    ignored -> new LinkedHashSet<>());
            for (String feature : features) {
                if (known.add(feature)) {
                    SourceText text = sourceSet.sources().get(source);
                    SourceRange range = text == null ? null : text.range(0, 0);
                    emitUnsupported(source, range, feature, feature);
                }
            }
        });
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            for (DeclInfo child : index.children(entry.id())) {
                buildSymbol(child);
            }
        }
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            buildModel(entry);
        }
        resolveOverridesAndEffectiveMembers();
        buildEffectiveTypes();
        buildReferences();
        validateAssociationRules();
        validateAttributeOverrides();
        Set<SymbolId> modelIds = closure.models().stream()
                .map(m -> findModelId(m.name(), m.declarationOffset(), m.sourceId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SymbolId> rootIds = closure.roots().stream()
                .map(m -> findModelId(m.name(), m.declarationOffset(), m.sourceId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
        Set<guru.interlis.model.LanguageVersion> languages = new LinkedHashSet<>();
        for (ModelSymbol model : symbols.values().stream()
                .filter(s -> s instanceof ModelSymbol)
                .map(s -> (ModelSymbol) s)
                .filter(s -> modelIds.contains(s.id()))
                .toList()) {
            languages.add(model.header().languageVersion());
        }
        SemanticGraph graph = new SemanticGraph(
                snapshotId,
                symbols.values(), types.values(), expressions.values().stream().toList(),
                references.values(), declaredTypes, effectiveTypes, effectiveMembers,
                rootIds, new ArrayList<>(modelIds), languages,
                sourceSet.sources().entrySet().stream().collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, e -> e.getValue().revision())));
        AnalysisCoverage coverage = buildCoverage();
        return new GraphResult(graph, coverage, rootIds, new ArrayList<>(modelIds),
                unsupportedBySource);
    }

    private void validateAttributeOverrides() {
        for (Symbol symbol : symbols.values()) {
            if (!(symbol instanceof ClassSymbol) && !(symbol instanceof StructureSymbol)) {
                continue;
            }
            SymbolId base = baseOf.get(symbol.id());
            if (base == null) {
                continue;
            }
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members() : ((StructureSymbol) symbol).members();
            for (SymbolId member : members) {
                Symbol memberSymbol = symbols.get(member);
                if (!(memberSymbol instanceof AttributeSymbol attribute)) {
                    continue;
                }
                DeclInfo attributeDecl = index.byId(member).orElse(null);
                if (attributeDecl == null
                        || !attributeDecl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                    continue;
                }
                AttributeSymbol baseAttribute = findBaseAttribute(base, attributeDecl.name());
                if (baseAttribute == null) {
                    continue;
                }
                if (baseAttribute.mandatory() && !attribute.mandatory()) {
                    emitRule(attributeDecl, "extended attribute may not remove MANDATORY",
                            "ATTRIBUTE_OVERRIDE_MANDATORY");
                }
                TypeDescriptor baseType = types.get(baseAttribute.declaredType());
                TypeDescriptor derivedType = types.get(attribute.declaredType());
                if (baseType != null && derivedType != null) {
                    String problem = overrideProblem(baseType, derivedType);
                    if (problem != null) {
                        emitRule(attributeDecl, problem, "ATTRIBUTE_OVERRIDE_TYPE");
                    }
                }
            }
        }
    }

    private AttributeSymbol findBaseAttribute(SymbolId base, String name) {
        SymbolId current = base;
        int guard = 0;
        while (current != null && guard++ < 64) {
            Symbol symbol = symbols.get(current);
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members()
                    : symbol instanceof StructureSymbol structure ? structure.members() : List.of();
            for (SymbolId member : members) {
                Symbol memberSymbol = symbols.get(member);
                DeclInfo memberDecl = index.byId(member).orElse(null);
                if (memberSymbol instanceof AttributeSymbol attribute && memberDecl != null
                        && memberDecl.name().equals(name)) {
                    return attribute;
                }
            }
            current = baseOf.get(current);
        }
        return null;
    }

    private String overrideProblem(TypeDescriptor base, TypeDescriptor derived) {
        if (base instanceof TextType baseText && derived instanceof TextType derivedText) {
            if (baseText.textKind() != derivedText.textKind()) {
                return "extended text attribute changes text kind";
            }
            if (baseText.maxLength().isPresent()
                    && (derivedText.maxLength().isEmpty()
                    || derivedText.maxLength().getAsInt() > baseText.maxLength().getAsInt())) {
                return "extended text attribute widens maximum length";
            }
            return null;
        }
        if (base instanceof DomainReferenceType baseDomain
                && derived instanceof DomainReferenceType derivedDomain) {
            return baseDomain.targetDomain().equals(derivedDomain.targetDomain()) ? null
                    : "extended attribute changes domain";
        }
        if (base instanceof ReferenceType baseReference
                && derived instanceof ReferenceType derivedReference) {
            return baseReference.target().equals(derivedReference.target())
                    && baseReference.external() == derivedReference.external() ? null
                    : "extended attribute changes reference target";
        }
        return base.equals(derived) ? null : "extended attribute changes type";
    }

    private void validateAssociationRules() {
        Map<SymbolId, Map<String, DeclInfo>> roleNamesByTarget = new LinkedHashMap<>();
        for (AssociationSymbol association : symbols.values().stream()
                .filter(AssociationSymbol.class::isInstance)
                .map(AssociationSymbol.class::cast).toList()) {
            DeclInfo decl = index.byId(association.id()).orElse(null);
            if (decl == null) {
                continue;
            }
            guru.interlis.syntax.AssociationDeclarationSyntax syntax =
                    (guru.interlis.syntax.AssociationDeclarationSyntax) decl.syntax();
            if (syntax.baseAssociation().isPresent()) {
                Optional<SymbolId> base = resolve(decl, syntax.baseAssociation().orElseThrow(),
                        Set.of(SymbolKind.ASSOCIATION));
                if (base.isEmpty() || !(symbols.get(base.orElseThrow()) instanceof AssociationSymbol)) {
                    emitRule(decl, "base of association is not an association", "ASSOCIATION_BASE");
                }
                if (!syntax.roles().isEmpty()) {
                    emitRule(decl, "extended association must not add roles", "ASSOCIATION_EXTENDED_ROLE");
                }
            }
            long aggregations = syntax.roles().stream()
                    .filter(role -> role.aggregation() != RoleAggregation.NONE).count();
            if (aggregations > 1) {
                emitRule(decl, "only one aggregation role is allowed", "MULTIPLE_AGGREGATION");
            }
            boolean associationAbstract = syntax.modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT);
            for (guru.interlis.syntax.RoleDeclarationSyntax roleSyntax : syntax.roles()) {
                if (roleSyntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                        && !associationAbstract) {
                    DeclInfo roleDecl = index.children(decl.id()).stream()
                            .filter(child -> child.kind() == SymbolKind.ROLE
                                    && child.name().equals(roleSyntax.name()))
                            .findFirst().orElse(decl);
                    emitRule(roleDecl, "abstract role requires an abstract association",
                            "ABSTRACT_ROLE");
                }
                if (roleSyntax.aggregation() != RoleAggregation.COMPOSITION
                        || !roleSyntax.explicitCardinality()) {
                    continue;
                }
                boolean one = roleSyntax.cardinality().lower().equals(java.math.BigInteger.ONE)
                        && roleSyntax.cardinality().upper()
                        instanceof guru.interlis.model.CardinalityUpper.Finite finite
                        && finite.value().equals(java.math.BigInteger.ONE);
                if (!one) {
                    DeclInfo roleDecl = index.children(decl.id()).stream()
                            .filter(child -> child.kind() == SymbolKind.ROLE
                                    && child.name().equals(roleSyntax.name()))
                            .findFirst().orElse(decl);
                    emitRule(roleDecl, "composition role cardinality must be 1", "COMPOSITION_CARDINALITY");
                }
            }
            List<RoleSymbol> roles = association.roles().stream()
                    .map(symbols::get).filter(RoleSymbol.class::isInstance)
                    .map(RoleSymbol.class::cast).toList();
            if (roles.size() == 2) {
                addRoleNamespace(roleNamesByTarget, roles.get(0).target(), roles.get(1));
                addRoleNamespace(roleNamesByTarget, roles.get(1).target(), roles.get(0));
            }
        }
        for (ClassSymbol classSymbol : symbols.values().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast).toList()) {
            Set<SymbolId> chain = new LinkedHashSet<>();
            SymbolId current = classSymbol.id();
            int guard = 0;
            while (current != null && chain.add(current) && guard++ < 64) {
                current = baseOf.get(current);
            }
            for (SymbolId classId : chain) {
                Map<String, DeclInfo> names = roleNamesByTarget.get(classId);
                if (names == null) {
                    continue;
                }
                ClassSymbol currentClass = (ClassSymbol) symbols.get(classId);
                if (currentClass == null) {
                    continue;
                }
                for (SymbolId member : currentClass.members()) {
                    DeclInfo memberDecl = index.byId(member).orElse(null);
                    if (memberDecl != null && memberDecl.kind() == SymbolKind.ATTRIBUTE
                            && names.containsKey(memberDecl.name())) {
                        emitRule(memberDecl, "attribute name conflicts with role name",
                                "ATTRIBUTE_ROLE_CONFLICT");
                    }
                }
            }
        }
    }

    private void addRoleNamespace(Map<SymbolId, Map<String, DeclInfo>> namesByTarget,
                                  SymbolId target, RoleSymbol role) {
        DeclInfo roleDecl = index.byId(role.id()).orElse(null);
        if (roleDecl == null) {
            return;
        }
        Map<String, DeclInfo> names = namesByTarget.computeIfAbsent(target,
                ignored -> new LinkedHashMap<>());
        DeclInfo previous = names.putIfAbsent(roleDecl.name(), roleDecl);
        if (previous != null && !previous.id().equals(roleDecl.id())) {
            emitRule(roleDecl, "role name conflicts with another role in the same namespace",
                    "ROLE_NAMESPACE_CONFLICT");
        }
    }

    private void buildModel(IndexedDeclarations.ModelEntry entry) {
        DeclInfo decl = entry.declaration();
        guru.interlis.syntax.ModelSyntax modelSyntax = entry.model().syntax();
        if (modelSyntax.endName().isPresent()
                && !modelSyntax.endName().orElseThrow().equals(modelSyntax.name())) {
            emitRule(decl, "MODEL end name does not match declaration", "MODEL_END_NAME");
        }
        DeclInfo previousModel = modelNames.putIfAbsent(modelSyntax.name(), decl);
        if (previousModel != null && !previousModel.id().equals(decl.id())) {
            emitRule(decl, "model name must be unique", "MODEL_NAME_UNIQUE");
        }
        Set<String> modelLevelNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            if (child.kind() == SymbolKind.DOMAIN || child.kind() == SymbolKind.TOPIC) {
                if (!modelLevelNames.add(child.name())) {
                    emitRule(child, "topic/domain name must be unique in a model",
                            "MODEL_MEMBER_NAME_UNIQUE");
                }
            }
            if (child.kind() == SymbolKind.TOPIC) {
                Set<String> classNames = new LinkedHashSet<>();
                for (DeclInfo topicChild : index.children(child.id())) {
                    if (topicChild.kind() == SymbolKind.CLASS
                            && !classNames.add(topicChild.name())) {
                        emitRule(topicChild, "class name must be unique in a topic",
                                "TOPIC_CLASS_NAME_UNIQUE");
                    }
                }
            }
        }
        List<SymbolId> members = index.children(decl.id()).stream().map(DeclInfo::id).toList();
        List<ModelImport> imports = new ArrayList<>();
        for (guru.interlis.syntax.ImportSyntax site : entry.model().syntax().imports()) {
            SymbolId target = null;
            for (Closure.ImportEdge edge : closure.importEdges()) {
                if (edge.importer().sourceId().equals(entry.model().sourceId())
                        && edge.importer().declarationOffset() == entry.model().declarationOffset()
                        && edge.site().range().equals(site.range())) {
                    target = findModelId(edge.target().name(), edge.target().declarationOffset(),
                            edge.target().sourceId()).orElse(null);
                    break;
                }
            }
            imports.add(new ModelImport(site.modelName(), site.unqualified(), site.range(),
                    Optional.ofNullable(target)));
        }
        symbols.put(decl.id(), new ModelSymbol(header(decl), entry.model().version(),
                entry.model().syntax().issuer(), imports, members));
    }

    private Symbol buildSymbol(DeclInfo decl) {
        Symbol existing = symbols.get(decl.id());
        if (existing != null) {
            return existing;
        }
        Symbol symbol = switch (decl.kind()) {
            case TOPIC -> buildTopic(decl);
            case CLASS -> buildClass(decl);
            case STRUCTURE -> buildStructure(decl);
            case ASSOCIATION -> buildAssociation(decl);
            case DOMAIN -> buildDomain(decl);
            case ATTRIBUTE -> buildAttribute(decl);
            case ROLE -> buildRole(decl);
            case CONSTRAINT -> buildConstraint(decl);
            case ENUM_ELEMENT -> buildEnumElement(decl);
            case UNSUPPORTED -> buildUnsupported(decl);
            case VIEW -> buildView(decl);
            case MODEL -> throw new IllegalStateException("model symbols are built separately");
        };
        symbols.put(decl.id(), symbol);
        return symbol;
    }

    private TopicSymbol buildTopic(DeclInfo decl) {
        TopicSyntax syntax = (TopicSyntax) decl.syntax();
        for (DeclInfo child : index.children(decl.id())) {
            buildSymbol(child);
        }
        if (syntax.endName().isPresent() && !syntax.endName().orElseThrow().equals(syntax.name())) {
            emitRule(decl, "TOPIC end name does not match declaration", "TOPIC_END_NAME");
        }
        Optional<SymbolId> base = syntax.baseTopic().flatMap(name ->
                resolve(decl, name, Set.of(SymbolKind.TOPIC)));
        base.ifPresent(id -> baseOf.put(decl.id(), id));
        List<SymbolId> dependencies = new ArrayList<>();
        for (guru.interlis.syntax.NameSyntax dependency : syntax.dependencies()) {
            resolve(decl, dependency, Set.of(SymbolKind.TOPIC)).ifPresent(dependencies::add);
        }
        if (!syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            for (DeclInfo child : index.children(decl.id())) {
                if (child.kind() == SymbolKind.CLASS
                        && child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
                    emitRule(decl, "abstract class requires an abstract topic", "ABSTRACT_TOPIC");
                    break;
                }
            }
        }
        List<SymbolId> members = index.children(decl.id()).stream().map(DeclInfo::id).toList();
        return new TopicSymbol(header(decl), base, members, dependencies);
    }

    private ClassSymbol buildClass(DeclInfo decl) {
        ClassDeclarationSyntax syntax = (ClassDeclarationSyntax) decl.syntax();
        for (DeclInfo child : index.children(decl.id())) {
            buildSymbol(child);
        }
        Optional<SymbolId> base = syntax.baseType().flatMap(name ->
                resolve(decl, name, Set.of(SymbolKind.CLASS)));
        base.ifPresent(id -> baseOf.put(decl.id(), id));
        List<SymbolId> members = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE)
                .map(DeclInfo::id).toList();
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        for (SymbolId member : members) {
            DeclInfo memberDecl = index.byId(member).orElse(null);
            if (memberDecl != null && memberDecl.kind() == SymbolKind.ATTRIBUTE) {
                TypeId typeId = declaredTypes.get(member);
                if (typeId != null && types.get(typeId) instanceof ReferenceType) {
                    emitRule(memberDecl, "reference attribute is only allowed in a structure",
                            "REFERENCE_ATTRIBUTE_CLASS");
                }
            }
        }
        return new ClassSymbol(header(decl), base, members, constraints);
    }

    private StructureSymbol buildStructure(DeclInfo decl) {
        StructureDeclarationSyntax syntax = (StructureDeclarationSyntax) decl.syntax();
        for (DeclInfo child : index.children(decl.id())) {
            buildSymbol(child);
        }
        Optional<SymbolId> base = syntax.baseType().flatMap(name ->
                resolve(decl, name, Set.of(SymbolKind.STRUCTURE)));
        base.ifPresent(id -> baseOf.put(decl.id(), id));
        List<SymbolId> members = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE)
                .map(DeclInfo::id).toList();
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        return new StructureSymbol(header(decl), base, members, constraints);
    }

    private AssociationSymbol buildAssociation(DeclInfo decl) {
        for (DeclInfo child : index.children(decl.id())) {
            buildSymbol(child);
        }
        AssociationDeclarationSyntax syntax = (AssociationDeclarationSyntax) decl.syntax();
        if (syntax.nameRange().isPresent() != syntax.endName().isPresent()) {
            emitRule(decl, "association name must be present at both begin and END", "ASSOCIATION_NAME");
        }
        Set<String> roleNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            if (child.kind() == SymbolKind.ROLE && !roleNames.add(child.name())) {
                emitRule(child, "duplicate role name", "DUPLICATE_ROLE");
            }
        }
        List<SymbolId> roles = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ROLE)
                .map(DeclInfo::id).toList();
        if (roles.isEmpty()) {
            emitRule(decl, "association requires at least two roles", "ASSOCIATION_ROLES");
        }
        List<SymbolId> attributes = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE)
                .map(DeclInfo::id).toList();
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        return new AssociationSymbol(header(decl), roles, attributes, constraints);
    }

    private DomainSymbol buildDomain(DeclInfo decl) {
        DomainDeclarationSyntax syntax = (DomainDeclarationSyntax) decl.syntax();
        for (DeclInfo child : index.children(decl.id())) {
            buildSymbol(child);
        }
        TypeId type = buildType(syntax.type(), decl.id(), decl.sourceId());
        declaredTypes.put(decl.id(), type);
        Optional<SymbolId> base = syntax.baseDomain().flatMap(name ->
                resolve(decl, name, Set.of(SymbolKind.DOMAIN)));
        base.ifPresent(id -> baseOf.put(decl.id(), id));
        return new DomainSymbol(header(decl), type, base,
                decl.modifiers().contains(guru.interlis.model.Modifier.GENERIC));
    }

    private AttributeSymbol buildAttribute(DeclInfo decl) {
        AttributeDeclarationSyntax syntax = (AttributeDeclarationSyntax) decl.syntax();
        TypeId type = buildType(syntax.type(), decl.id(), decl.sourceId());
        declaredTypes.put(decl.id(), type);
        if (syntax.transientAttribute() && !syntax.assigned()) {
            emitRule(decl, "transient attribute requires an assignment", "TRANSIENT_ASSIGNMENT");
        }
        return new AttributeSymbol(header(decl), type, syntax.mandatory(),
                syntax.transientAttribute(), Optional.empty());
    }

    private RoleSymbol buildRole(DeclInfo decl) {
        RoleDeclarationSyntax syntax = (RoleDeclarationSyntax) decl.syntax();
        Optional<SymbolId> target = resolve(decl, syntax.target(), Set.of(SymbolKind.CLASS));
        SymbolId resolvedTarget = target.orElseGet(() -> syntheticClass(decl, syntax.target()));
        if (!decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL)) {
            SymbolId roleScope = scopeRoot(decl.owner().orElse(decl.id()));
            SymbolId targetScope = scopeRoot(resolvedTarget);
            if (roleScope != null && targetScope != null && !roleScope.equals(targetScope)) {
                emitRule(decl, "cross-topic role requires EXTERNAL", "ROLE_EXTERNAL");
            }
        }
        return new RoleSymbol(header(decl), resolvedTarget, syntax.cardinality(),
                syntax.aggregation(),
                decl.modifiers().contains(guru.interlis.model.Modifier.ORDERED),
                decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL),
                decl.modifiers().contains(guru.interlis.model.Modifier.HIDING));
    }

    private SymbolId syntheticClass(DeclInfo owner, guru.interlis.syntax.NameSyntax name) {
        SymbolId id = new SymbolId("sym-unresolved-" + (++typeCounter));
        SymbolId model = index.modelOfSymbol(owner.id()).orElse(owner.id());
        SymbolHeader header = new SymbolHeader(id, SymbolKind.CLASS,
                DeclaredName.synthetic("<unresolved-role-target>"),
                Optional.of(model), 0,
                new Origin.SyntheticOrigin(guru.interlis.model.SyntheticReason.MISSING_REFERENCE,
                        Optional.of(name.range())),
                Optional.of(name.range()), owner.language(), Documentation.empty(),
                MetaAttributeSet.empty(), Set.of());
        ClassSymbol synthetic = new ClassSymbol(header, Optional.empty(),
                List.of(), List.of());
        symbols.put(id, synthetic);
        return id;
    }

    private ConstraintSymbol buildConstraint(DeclInfo decl) {
        ConstraintDeclarationSyntax syntax = (ConstraintDeclarationSyntax) decl.syntax();
        ExpressionResult expression = syntax.expression()
                .map(e -> buildExpression(e, decl.owner().orElse(decl.id())))
                .orElseGet(() -> errorExpression(decl.sourceId(), syntax.range(), "MISSING_CONSTRAINT"));
        ConstraintDefinition definition = switch (syntax.constraintKind()) {
            case MANDATORY -> {
                if (!isBoolean(expression.typeId())) {
                    emitTypeMismatch(decl.sourceId(), syntax.range(), typeName(expression.typeId()),
                            "BOOLEAN");
                }
                yield new MandatoryConstraint(syntax.name(), expression.id());
            }
            case UNIQUENESS -> new UniquenessConstraint(syntax.name(), false,
                    Optional.of(expression.id()), List.of(), List.of());
            case PLAUSIBILITY -> new PlausibilityConstraint(syntax.name(),
                    java.math.BigDecimal.ZERO, guru.interlis.model.BinaryOperator.LTE,
                    expression.id());
            case DOMAIN -> new DomainConstraint(expression.id());
            default -> new MandatoryConstraint(syntax.name(), expression.id());
        };
        return new ConstraintSymbol(header(decl), definition);
    }

    private EnumElementSymbol buildEnumElement(DeclInfo decl) {
        String path = decl.name();
        List<String> segments = List.of(path.split("\\."));
        return new EnumElementSymbol(header(decl), segments, decl.order());
    }

    private UnsupportedSymbol buildUnsupported(DeclInfo decl) {
        String feature = decl.featureId().orElse("UNKNOWN_SYNTAX");
        unsupportedBySource.computeIfAbsent(decl.sourceId(), ignored -> new LinkedHashSet<>())
                .add(feature);
        emitUnsupported(decl.sourceId(), decl.origin() instanceof Origin.DeclaredOrigin d
                ? d.range() : null, feature, decl.name());
        return new UnsupportedSymbol(header(decl), feature, decl.name());
    }

    private ViewSymbol buildView(DeclInfo decl) {
        unsupportedBySource.computeIfAbsent(decl.sourceId(), ignored -> new LinkedHashSet<>())
                .add("VIEW");
        return new ViewSymbol(header(decl), List.of(), List.of());
    }

    private SymbolHeader header(DeclInfo decl) {
        DeclaredName declaredName = isSyntheticName(decl.name())
                ? DeclaredName.synthetic(decl.name()) : DeclaredName.declared(decl.name());
        Set<guru.interlis.model.Modifier> modifiers = new LinkedHashSet<>(decl.modifiers());
        if (decl.syntax() instanceof AttributeDeclarationSyntax attribute
                && attribute.transientAttribute()) {
            modifiers.add(guru.interlis.model.Modifier.TRANSIENT);
        }
        return new SymbolHeader(decl.id(), decl.kind(), declaredName, decl.owner(),
                decl.order(), decl.origin(), decl.nameRange(), decl.language(),
                Documentation.empty(), MetaAttributeSet.empty(), modifiers);
    }

    private static boolean isSyntheticName(String name) {
        return name.startsWith("<") || name.startsWith("constraint@")
                || name.startsWith("unsupported@") || name.startsWith("Association@");
    }

    // ---- types -----------------------------------------------------------------

    private TypeId buildType(TypeSyntax syntax, SymbolId context, SourceId source) {
        Objects.requireNonNull(syntax, "syntax");
        TypeId id = nextTypeId();
        TypeDescriptor descriptor = switch (syntax) {
            case TextTypeSyntax text -> new TextType(id, new Origin.DeclaredOrigin(text.range()),
                    text.textKind(), text.maxLength());
            case BooleanTypeSyntax bool -> new guru.interlis.model.BooleanType(id,
                    new Origin.DeclaredOrigin(bool.range()));
            case guru.interlis.syntax.NumericTypeSyntax numeric -> new NumericType(id,
                    new Origin.DeclaredOrigin(numeric.range()),
                    new NumericRange(numeric.minimum(), numeric.maximum(), numeric.circular(),
                            numeric.direction(), Optional.empty(), Optional.empty()));
            case EnumerationTypeSyntax enumeration -> new EnumerationType(id,
                    new Origin.DeclaredOrigin(enumeration.range()),
                    enumeration.values().stream()
                            .map(v -> new EnumerationValue(v.path(), 0, Optional.of(v.range())))
                            .toList(),
                    enumeration.ordered(), enumeration.circular(), false);
            case DomainReferenceSyntax reference -> {
                Optional<SymbolId> target = resolveName(context, reference.name(),
                        Set.of(SymbolKind.DOMAIN), source);
                if (target.isPresent()) {
                    yield new DomainReferenceType(id, new Origin.DeclaredOrigin(reference.range()),
                            target.get());
                }
                yield new ErrorType(id, new Origin.DeclaredOrigin(reference.range()),
                        List.of(unresolved(reference.name())));
            }
            case ReferenceTypeSyntax reference -> {
                Optional<SymbolId> target = resolveName(context, reference.target(),
                        Set.of(SymbolKind.CLASS), source);
                if (target.isPresent()) {
                    yield new guru.interlis.model.ReferenceType(id,
                            new Origin.DeclaredOrigin(reference.range()), target.get(),
                            reference.external());
                }
                yield new ErrorType(id, new Origin.DeclaredOrigin(reference.range()),
                        List.of(unresolved(reference.target())));
            }
            case CollectionTypeSyntax collection -> {
                TypeId element = buildType(collection.elementType(), context, source);
                yield new CollectionType(id, new Origin.DeclaredOrigin(collection.range()),
                        collection.collectionKind(), collection.cardinality(), element);
            }
            case CoordinateTypeSyntax coordinate -> new guru.interlis.model.CoordinateType(id,
                    new Origin.DeclaredOrigin(coordinate.range()), coordinate.dimensions(),
                    coordinate.axes(), coordinate.multiCoordinate());
            case guru.interlis.syntax.UnsupportedTypeSyntax unsupported -> {
                DiagnosticId diagnostic = emitUnsupported(source, unsupported.range(),
                        unsupported.featureId(), unsupported.construct());
                yield new ErrorType(id, new Origin.DeclaredOrigin(unsupported.range()),
                        List.of(diagnostic));
            }
        };
        types.put(id, descriptor);
        return id;
    }

    private Optional<SymbolId> resolve(DeclInfo context, guru.interlis.syntax.NameSyntax name,
                                       Set<SymbolKind> expected) {
        return resolveName(context.id(), name, expected, context.sourceId());
    }

    private Optional<SymbolId> resolveName(SymbolId context, guru.interlis.syntax.NameSyntax name,
                                           Set<SymbolKind> expected, SourceId source) {
        NameResolver.Resolution resolution = resolver.resolve(context, name, expected);
        return switch (resolution.status()) {
            case BOUND -> resolution.target();
            case UNRESOLVED -> {
                unresolved(name);
                yield Optional.empty();
            }
            case AMBIGUOUS -> {
                collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0002"),
                                Severity.ERROR, Phase.BINDING, "diagnostic.semantics.ambiguous")
                        .at(name.range())
                        .argument(new DiagnosticArgument.IdentifierArgument("name", name.text()))
                        .argument(new DiagnosticArgument.IdentifierListArgument("candidates",
                                resolution.candidates().stream().map(SymbolId::value).toList())));
                yield Optional.empty();
            }
        };
    }

    private DiagnosticId unresolved(guru.interlis.syntax.NameSyntax name) {
        return collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0001"),
                        Severity.ERROR, Phase.BINDING, "diagnostic.semantics.unresolved")
                .at(name.range())
                .argument(new DiagnosticArgument.IdentifierArgument("name", name.text())));
    }

    private TypeId errorType(SourceId source, SourceRange range, String feature) {
        TypeId id = nextTypeId();
        DiagnosticId diagnostic = emitUnsupported(source, range, feature, feature);
        types.put(id, new ErrorType(id, new Origin.SyntheticOrigin(
                guru.interlis.model.SyntheticReason.MISSING_TYPE, Optional.of(range)),
                List.of(diagnostic)));
        return id;
    }

    private DiagnosticId emitUnsupported(SourceId source, SourceRange range, String feature,
                                         String construct) {
        DiagnosticDraft draft = DiagnosticDraft.of(new DiagnosticCode("ILI-IMPL-0001"),
                        Severity.ERROR, Phase.SEMANTIC_CHECKING,
                        "diagnostic.implementation.unsupported")
                .argument(new DiagnosticArgument.TextArgument("feature", feature))
                .argument(new DiagnosticArgument.TextArgument("construct",
                        construct == null ? feature : construct));
        if (range != null) {
            draft.at(range);
        }
        unsupportedBySource.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(feature);
        return collector.emit(draft);
    }

    private TypeId builtinBoolean() {
        if (booleanType == null) {
            booleanType = nextTypeId();
            types.put(booleanType, new guru.interlis.model.BooleanType(booleanType,
                    new Origin.PredefinedOrigin(guru.interlis.model.LanguageVersion.ILI23,
                            "BOOLEAN")));
        }
        return booleanType;
    }

    private TypeId builtinText() {
        if (textType == null) {
            textType = nextTypeId();
            types.put(textType, new TextType(textType,
                    new Origin.PredefinedOrigin(guru.interlis.model.LanguageVersion.ILI23, "TEXT"),
                    TextKind.TEXT, java.util.OptionalInt.empty()));
        }
        return textType;
    }

    private TypeId nextTypeId() {
        return new TypeId("type-" + (++typeCounter));
    }

    // ---- expressions -----------------------------------------------------------

    private ExpressionResult buildExpression(ExpressionSyntax syntax, SymbolId owner) {
        ExpressionId id = new ExpressionId("expr-" + (++expressionCounter));
        return switch (syntax) {
            case LiteralExpressionSyntax literal -> literalExpression(id, literal);
            case PathExpressionSyntax path -> pathExpression(id, path, owner);
            case BinaryExpressionSyntax binary -> binaryExpression(id, binary, owner);
            case UnaryExpressionSyntax unary -> unaryExpression(id, unary, owner);
            case DefinedExpressionSyntax defined -> definedExpression(id, defined, owner);
            case ConstantExpressionSyntax constant -> constantExpression(id, constant);
            case UndefinedExpressionSyntax undefined -> undefinedExpression(id, undefined);
            case ParenthesizedExpressionSyntax parenthesized ->
                    buildExpression(parenthesized.inner(), owner);
            case UnsupportedExpressionSyntax unsupported -> {
                DiagnosticId diagnostic = emitUnsupported(ownerSource(owner), unsupported.range(),
                        unsupported.featureId(), unsupported.construct());
                TypeId error = errorType(ownerSource(owner), unsupported.range(),
                        unsupported.featureId());
                ErrorExpression expression = new ErrorExpression(id, error,
                        guru.interlis.model.ExpressionTypeState.ERROR, unsupported.range(),
                        List.of(), List.of(diagnostic));
                expressions.put(id, expression);
                yield new ExpressionResult(id, error, true);
            }
        };
    }

    private ExpressionResult literalExpression(ExpressionId id, LiteralExpressionSyntax syntax) {
        TypeId type;
        TypedExpression expression;
        if (syntax.literalKind() == guru.interlis.model.LiteralKind.TEXT) {
            type = builtinText();
            expression = new guru.interlis.model.LiteralExpression(id, type,
                    guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    syntax.literalKind(), syntax.lexeme(), Optional.empty(),
                    Optional.of(unquote(syntax.lexeme())));
        } else {
            type = numericLiteralType(syntax.lexeme());
            expression = new guru.interlis.model.LiteralExpression(id, type,
                    guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    syntax.literalKind(), syntax.lexeme(), syntax.decimal(), Optional.empty());
        }
        expressions.put(id, expression);
        return new ExpressionResult(id, type, false);
    }

    private TypeId numericLiteralType(String lexeme) {
        TypeId type = nextTypeId();
        Optional<DecimalLiteral> decimal = decimal(lexeme);
        NumericRange range = decimal
                .map(d -> new NumericRange(Optional.of(d), Optional.of(d), false,
                        guru.interlis.model.NumericDirection.NONE, Optional.empty(), Optional.empty()))
                .orElseGet(NumericRange::unbounded);
        types.put(type, new NumericType(type, new Origin.SyntheticOrigin(
                guru.interlis.model.SyntheticReason.TEST_FIXTURE, Optional.empty()), range));
        return type;
    }

    private ExpressionResult pathExpression(ExpressionId id, PathExpressionSyntax syntax,
                                            SymbolId owner) {
        List<guru.interlis.model.PathStep> steps = new ArrayList<>();
        SymbolId currentScope = owner;
        TypeId currentType = null;
        boolean error = false;
        for (int i = 0; i < syntax.segments().size(); i++) {
            guru.interlis.syntax.NameSyntax name = syntax.segments().get(i);
            String text = name.text();
            if ("THIS".equals(text)) {
                steps.add(new guru.interlis.model.PathStep.ThisStep(name.range()));
                continue;
            }
            if ("PARENT".equals(text)) {
                SymbolId base = baseOf.getOrDefault(owner, owner);
                steps.add(new guru.interlis.model.PathStep.ParentStep(name.range()));
                currentScope = base;
                continue;
            }
            Optional<SymbolId> member = findMember(currentScope, text);
            if (member.isEmpty()) {
                unresolved(name);
                TypeId errorType = errorType(ownerSource(owner), name.range(), "PATH");
                currentType = errorType;
                error = true;
                break;
            }
            SymbolKind kind = index.byId(member.get()).map(DeclInfo::kind).orElse(SymbolKind.ATTRIBUTE);
            if (kind == SymbolKind.ROLE) {
                steps.add(new guru.interlis.model.PathStep.RoleStep(text, member.get(),
                        name.range()));
            } else {
                steps.add(new guru.interlis.model.PathStep.AttributeStep(text, member.get(),
                        name.range()));
            }
            currentType = declaredTypes.get(member.get());
            currentScope = member.get();
        }
        if (currentType == null) {
            currentType = errorType(ownerSource(owner), syntax.range(), "PATH");
            error = true;
        }
        guru.interlis.model.ObjectPath path = new guru.interlis.model.ObjectPath(steps.isEmpty()
                ? List.of(new guru.interlis.model.PathStep.ThisStep(syntax.range())) : steps);
        TypedExpression expression = new guru.interlis.model.PathExpression(id, currentType,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(), path);
        expressions.put(id, expression);
        return new ExpressionResult(id, currentType, error);
    }

    private Optional<SymbolId> findMember(SymbolId owner, String name) {
        SymbolId current = owner;
        int guard = 0;
        while (current != null && guard++ < 32) {
            for (DeclInfo child : index.children(current)) {
                if ((child.kind() == SymbolKind.ATTRIBUTE || child.kind() == SymbolKind.ROLE)
                        && child.name().equals(name)) {
                    return Optional.of(child.id());
                }
            }
            current = baseOf.get(current);
        }
        return Optional.empty();
    }

    private ExpressionResult binaryExpression(ExpressionId id, BinaryExpressionSyntax syntax,
                                              SymbolId owner) {
        ExpressionResult left = buildExpression(syntax.left(), owner);
        ExpressionResult right = buildExpression(syntax.right(), owner);
        TypeId result = builtinBoolean();
        boolean error = left.error() || right.error();
        switch (syntax.operator()) {
            case AND, OR -> {
                if (!isBoolean(left.typeId()) || !isBoolean(right.typeId())) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()), "BOOLEAN");
                    error = true;
                }
            }
            case EQ, NEQ, LT, LTE, GT, GTE -> {
                if (!comparable(left.typeId(), right.typeId())) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()),
                            "COMPARABLE");
                    error = true;
                }
            }
            case PLUS, MINUS, MUL, DIV, POW -> {
                result = left.typeId();
                if (!isNumeric(left.typeId()) || !isNumeric(right.typeId())) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()), "NUMERIC");
                    error = true;
                }
            }
        }
        if (error) {
            result = errorType(ownerSource(owner), syntax.range(), "EXPRESSION");
        }
        TypedExpression expression = new guru.interlis.model.BinaryExpression(id, result,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(left.id(), right.id()), syntax.operator(),
                left.id(), right.id());
        expressions.put(id, expression);
        return new ExpressionResult(id, result, error);
    }

    private ExpressionResult unaryExpression(ExpressionId id, UnaryExpressionSyntax syntax,
                                             SymbolId owner) {
        ExpressionResult operand = buildExpression(syntax.operand(), owner);
        TypeId type = builtinBoolean();
        boolean error = operand.error();
        if (syntax.operator() == guru.interlis.model.UnaryOperator.NOT && !isBoolean(operand.typeId())) {
            emitTypeMismatch(ownerSource(owner), syntax.range(), typeName(operand.typeId()),
                    "BOOLEAN");
            error = true;
        }
        if (error) {
            type = errorType(ownerSource(owner), syntax.range(), "EXPRESSION");
        }
        TypedExpression expression = new guru.interlis.model.UnaryExpression(id, type,
                error ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(operand.id()), syntax.operator(), operand.id());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, error);
    }

    private ExpressionResult definedExpression(ExpressionId id, DefinedExpressionSyntax syntax,
                                               SymbolId owner) {
        ExpressionResult operand = buildExpression(syntax.operand(), owner);
        TypeId type = builtinBoolean();
        TypedExpression expression = new guru.interlis.model.DefinedExpression(id, type,
                operand.error() ? guru.interlis.model.ExpressionTypeState.ERROR
                        : guru.interlis.model.ExpressionTypeState.RESOLVED,
                syntax.range(), List.of(operand.id()), operand.id());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, operand.error());
    }

    private ExpressionResult constantExpression(ExpressionId id, ConstantExpressionSyntax syntax) {
        TypeId type = numericLiteralType("PI".equals(syntax.constantName()) ? "3.141592653589793"
                : "2.718281828459045");
        TypedExpression expression = new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.constantName());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult undefinedExpression(ExpressionId id, UndefinedExpressionSyntax syntax) {
        TypeId type = nextTypeId();
        types.put(type, new guru.interlis.model.GenericType(type,
                new Origin.PredefinedOrigin(guru.interlis.model.LanguageVersion.ILI23, "UNDEFINED"),
                "UNDEFINED"));
        TypedExpression expression = new guru.interlis.model.UndefinedExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of());
        expressions.put(id, expression);
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult errorExpression(SourceId source, SourceRange range, String feature) {
        ExpressionId id = new ExpressionId("expr-" + (++expressionCounter));
        TypeId type = errorType(source, range, feature);
        DiagnosticId diagnostic = emitUnsupported(source, range, feature, feature);
        TypedExpression expression = new ErrorExpression(id, type,
                guru.interlis.model.ExpressionTypeState.ERROR, range, List.of(),
                List.of(diagnostic));
        expressions.put(id, expression);
        return new ExpressionResult(id, type, true);
    }

    private boolean isBoolean(TypeId typeId) {
        return effectiveDescriptor(typeId) instanceof guru.interlis.model.BooleanType;
    }

    private boolean isNumeric(TypeId typeId) {
        return effectiveDescriptor(typeId) instanceof NumericType;
    }

    private TypeDescriptor effectiveDescriptor(TypeId typeId) {
        TypeDescriptor descriptor = types.get(typeId);
        int guard = 0;
        while (descriptor instanceof DomainReferenceType reference && guard++ < 64) {
            Symbol symbol = symbols.get(reference.targetDomain());
            if (!(symbol instanceof DomainSymbol domain)) {
                break;
            }
            descriptor = types.get(domain.typeId());
        }
        return descriptor;
    }

    private boolean comparable(TypeId left, TypeId right) {
        TypeDescriptor l = effectiveDescriptor(left);
        TypeDescriptor r = effectiveDescriptor(right);
        if (l instanceof NumericType && r instanceof NumericType) {
            return true;
        }
        if (l instanceof TextType && r instanceof TextType) {
            return true;
        }
        if (l instanceof guru.interlis.model.BooleanType && r instanceof guru.interlis.model.BooleanType) {
            return true;
        }
        boolean leftUndefined = l instanceof guru.interlis.model.GenericType leftGeneric
                && "UNDEFINED".equals(leftGeneric.name());
        boolean rightUndefined = r instanceof guru.interlis.model.GenericType rightGeneric
                && "UNDEFINED".equals(rightGeneric.name());
        return leftUndefined || rightUndefined;
    }

    private String typeName(TypeId id) {
        TypeDescriptor descriptor = effectiveDescriptor(id);
        return descriptor == null ? "UNKNOWN" : descriptor.kind().name();
    }

    private void emitTypeMismatch(SourceId source, SourceRange range, String actual, String expected) {
        collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0005"), Severity.ERROR,
                        Phase.TYPE_CHECKING, "diagnostic.semantics.type")
                .at(range)
                .argument(new DiagnosticArgument.TypeNameArgument("actual", actual))
                .argument(new DiagnosticArgument.TypeNameArgument("expected", expected)));
    }

    private Optional<DecimalLiteral> decimal(String lexeme) {
        try {
            return Optional.of(DecimalLiteral.parse(lexeme));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private SourceId ownerSource(SymbolId owner) {
        return index.modelOfSymbol(owner).flatMap(index::byId)
                .map(DeclInfo::sourceId).orElseGet(() -> sourceSet.sources().keySet().iterator().next());
    }

    // ---- overrides, effective members and types --------------------------------

    private final Set<SymbolId> effectiveProcessed = new HashSet<>();

    private void resolveOverridesAndEffectiveMembers() {
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            for (DeclInfo child : index.children(entry.id())) {
                collectEffective(child);
            }
        }
    }

    private void collectEffective(DeclInfo decl) {
        if (!effectiveProcessed.add(decl.id())) {
            return;
        }
        if (decl.kind() == SymbolKind.CLASS || decl.kind() == SymbolKind.STRUCTURE) {
            List<SymbolId> chain = baseChain(decl.id());
            if (hasCycle(chain)) {
                emitCycle(decl);
                effectiveMembers.put(decl.id(), localMembers(decl));
                return;
            }
            List<EffectiveMember> result = new ArrayList<>();
            Map<String, Integer> positions = new HashMap<>();
            for (int depth = chain.size() - 1; depth >= 0; depth--) {
                SymbolId current = chain.get(depth);
                for (DeclInfo attribute : index.children(current)) {
                    if (attribute.kind() != SymbolKind.ATTRIBUTE) {
                        continue;
                    }
                    Integer existing = positions.get(attribute.name());
                    if (existing != null) {
                        if (!attribute.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                            emitRule(attribute, "override requires EXTENDED", "OVERRIDE");
                        }
                        EffectiveMember old = result.get(existing);
                        result.set(existing, new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth, decl.id()), existing,
                                EffectiveMemberOrigin.OVERRIDDEN, true));
                        AttributeSymbol override = overrideAttribute(attribute.id(),
                                old.declaredSymbol());
                        if (override != null) {
                            symbols.put(attribute.id(), override);
                        }
                    } else {
                        int position = result.size();
                        positions.put(attribute.name(), position);
                        result.add(new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth, decl.id()), position,
                                depth == 0 ? EffectiveMemberOrigin.LOCAL
                                        : EffectiveMemberOrigin.INHERITED, false));
                    }
                }
            }
            effectiveMembers.put(decl.id(), List.copyOf(result));
        }
        for (DeclInfo child : index.children(decl.id())) {
            collectEffective(child);
        }
    }

    private void emitRule(DeclInfo decl, String rule, String reason) {
        collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0010"),
                        Severity.ERROR, Phase.SEMANTIC_CHECKING,
                        "diagnostic.semantics.inheritance")
                .at(decl.origin() instanceof Origin.DeclaredOrigin declared
                        ? declared.range() : decl.nameRange().orElse(null))
                .argument(new DiagnosticArgument.IdentifierArgument("element", decl.name()))
                .argument(new DiagnosticArgument.TextArgument("rule", rule + "/" + reason)));
    }

    private SymbolId scopeRoot(SymbolId symbol) {
        SymbolId current = symbol;
        int guard = 0;
        while (current != null && guard++ < 64) {
            DeclInfo info = index.byId(current).orElse(null);
            if (info == null) {
                return null;
            }
            if (info.kind() == SymbolKind.TOPIC || info.kind() == SymbolKind.MODEL) {
                return current;
            }
            current = info.owner().orElse(null);
        }
        return null;
    }

    private AttributeSymbol overrideAttribute(SymbolId id, SymbolId baseId) {
        if (symbols.get(id) instanceof AttributeSymbol attribute) {
            return new AttributeSymbol(attribute.header(), attribute.declaredType(),
                    attribute.mandatory(), attribute.transientAttribute(), Optional.of(baseId));
        }
        return null;
    }

    private List<SymbolId> baseChain(SymbolId id) {
        List<SymbolId> chain = new ArrayList<>();
        SymbolId current = id;
        int guard = 0;
        while (current != null && guard++ < 64) {
            chain.add(current);
            current = baseOf.get(current);
        }
        return chain;
    }

    private boolean hasCycle(List<SymbolId> chain) {
        return chain.size() != new HashSet<>(chain).size();
    }

    private void emitCycle(DeclInfo decl) {
        collector.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0006"), Severity.ERROR,
                        Phase.SEMANTIC_CHECKING, "diagnostic.semantics.cycle")
                .at(decl.origin() instanceof Origin.DeclaredOrigin d ? d.range() : decl.nameRange().orElse(null))
                .argument(new DiagnosticArgument.TextArgument("relationship", "INHERITANCE"))
                .argument(new DiagnosticArgument.IdentifierListArgument("cycle",
                        baseChain(decl.id()).stream().map(SymbolId::value).toList())));
    }

    private List<EffectiveMember> localMembers(DeclInfo decl) {
        List<EffectiveMember> result = new ArrayList<>();
        int position = 0;
        for (DeclInfo child : index.children(decl.id())) {
            if (child.kind() == SymbolKind.ATTRIBUTE) {
                result.add(new EffectiveMember(child.id(), decl.id(), List.of(decl.id()), position++,
                        EffectiveMemberOrigin.LOCAL, false));
            }
        }
        return List.copyOf(result);
    }

    private List<SymbolId> pathTo(List<SymbolId> chain, int depth, SymbolId derived) {
        List<SymbolId> result = new ArrayList<>();
        for (int i = depth; i < chain.size(); i++) {
            result.add(chain.get(i));
        }
        result.add(derived);
        return List.copyOf(result);
    }

    private void buildEffectiveTypes() {
        for (Map.Entry<SymbolId, TypeId> entry : declaredTypes.entrySet()) {
            effectiveTypes.put(entry.getKey(), effectiveType(entry.getValue()));
        }
    }

    private EffectiveType effectiveType(TypeId declared) {
        List<SymbolId> aliasPath = new ArrayList<>();
        TypeId current = declared;
        Set<TypeId> seen = new HashSet<>();
        int guard = 0;
        while (guard++ < 64) {
            TypeDescriptor descriptor = types.get(current);
            if (!(descriptor instanceof DomainReferenceType reference)) {
                break;
            }
            if (!seen.add(current)) {
                break;
            }
            aliasPath.add(reference.targetDomain());
            Symbol symbol = symbols.get(reference.targetDomain());
            if (symbol instanceof DomainSymbol domain) {
                current = domain.typeId();
            } else {
                break;
            }
        }
        return new EffectiveType(declared, current, aliasPath);
    }

    // ---- references and coverage ----------------------------------------------

    private void buildReferences() {
        // References are recorded during resolution only for declarations that
        // need navigation. The graph is still fully usable without synthetic
        // references; base/type/role references are added here.
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            for (DeclInfo child : index.children(entry.id())) {
                referenceFor(child);
            }
        }
    }

    private void referenceFor(DeclInfo decl) {
        if (decl.syntax() instanceof ClassDeclarationSyntax clazz && clazz.baseType().isPresent()) {
            recordReference(decl, clazz.baseType().get(), baseOf.get(decl.id()), ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof StructureDeclarationSyntax structure
                && structure.baseType().isPresent()) {
            recordReference(decl, structure.baseType().get(), baseOf.get(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        } else if (decl.syntax() instanceof TopicSyntax topic && topic.baseTopic().isPresent()) {
            recordReference(decl, topic.baseTopic().get(), baseOf.get(decl.id()),
                    ReferenceUsage.BASE_TYPE);
        }
    }

    private void recordReference(DeclInfo owner, guru.interlis.syntax.NameSyntax name,
                                 SymbolId target, ReferenceUsage usage) {
        ReferenceId id = new ReferenceId("ref-" + (++referenceCounter));
        List<ReferenceSegment> segments = name.segments().stream()
                .map(s -> new ReferenceSegment(s.text(), s.range(), Optional.ofNullable(target)))
                .toList();
        BindingResolution resolution = target == null
                ? new BindingResolution.UnresolvedTarget(guru.interlis.model.UnresolvedReason.NAME_NOT_FOUND,
                List.of())
                : new BindingResolution.BoundTarget(target);
        references.put(id, new NameReference(id, new ScopeId(owner.id().value()), usage,
                segments, name.range(), resolution));
    }

    private AnalysisCoverage buildCoverage() {
        List<AnalysisCoverage.CoverageEntry> entries = new ArrayList<>();
        List<guru.interlis.diagnostics.Diagnostic> diagnostics = collector.snapshot();
        for (SourceId source : closure.sources()) {
            boolean lexing = diagnostics.stream().noneMatch(d -> d.primaryRange()
                    .filter(r -> r.source().equals(source)).isPresent()
                    && d.code().value().equals("ILI-SYN-0001"));
            boolean parsing = diagnostics.stream().noneMatch(d -> d.primaryRange()
                    .filter(r -> r.source().equals(source)).isPresent()
                    && d.code().value().matches("ILI-SYN-000[2-6]"));
            boolean indexing = diagnostics.stream().noneMatch(d -> d.code().value().equals("ILI-SEM-0003")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean binding = diagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-000[124]")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean typeChecking = diagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-000[578]")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            boolean semantic = diagnostics.stream().noneMatch(d -> d.code().value().matches("ILI-SEM-0006|ILI-SEM-0010")
                    && d.primaryRange().filter(r -> r.source().equals(source)).isPresent());
            entries.add(entry(Phase.LEXING, source, lexing ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.PARSING, source, parsing ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.DECLARATION_INDEXING, source,
                    indexing ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.BINDING, source,
                    binding ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.TYPE_CHECKING, source,
                    typeChecking ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
            entries.add(entry(Phase.SEMANTIC_CHECKING, source,
                    semantic ? CoverageStatus.COMPLETE : CoverageStatus.BLOCKED_BY_ERROR));
        }
        for (Map.Entry<SourceId, Set<String>> entry : unsupportedBySource.entrySet()) {
            for (String feature : entry.getValue()) {
                entries.add(new AnalysisCoverage.CoverageEntry(Phase.SEMANTIC_CHECKING,
                        Optional.of(entry.getKey()), CoverageStatus.NOT_IMPLEMENTED,
                        Optional.of(feature), Optional.empty()));
            }
        }
        return new AnalysisCoverage(entries);
    }

    private AnalysisCoverage.CoverageEntry entry(Phase phase, SourceId source,
                                                 CoverageStatus status) {
        return new AnalysisCoverage.CoverageEntry(phase, Optional.of(source), status,
                Optional.empty(), Optional.empty());
    }

    private Optional<SymbolId> findModelId(String name, int offset, SourceId source) {
        return index.models().stream()
                .filter(e -> e.model().sourceId().equals(source))
                .filter(e -> e.model().name().equals(name))
                .filter(e -> e.model().declarationOffset() == offset)
                .map(IndexedDeclarations.ModelEntry::id)
                .findFirst();
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.charAt(0) == '"') {
            return text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return text;
    }

    record GraphResult(SemanticGraph graph, AnalysisCoverage coverage, List<SymbolId> roots,
                       List<SymbolId> models, Map<SourceId, Set<String>> unsupportedBySource) {
        GraphResult {
            Objects.requireNonNull(graph, "graph");
            Objects.requireNonNull(coverage, "coverage");
            roots = List.copyOf(Objects.requireNonNull(roots, "roots"));
            models = List.copyOf(Objects.requireNonNull(models, "models"));
            unsupportedBySource = Map.copyOf(Objects.requireNonNull(unsupportedBySource,
                    "unsupportedBySource"));
        }
    }

    private record ExpressionResult(ExpressionId id, TypeId typeId, boolean error) {
    }

}

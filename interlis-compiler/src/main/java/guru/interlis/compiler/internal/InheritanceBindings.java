package guru.interlis.compiler.internal;

import guru.interlis.diagnostics.DiagnosticArgument;
import guru.interlis.diagnostics.DiagnosticCode;
import guru.interlis.diagnostics.DiagnosticDraft;
import guru.interlis.diagnostics.Phase;
import guru.interlis.diagnostics.Severity;
import guru.interlis.model.AssociationSymbol;
import guru.interlis.model.AttributeSymbol;
import guru.interlis.model.ClassSymbol;
import guru.interlis.model.DomainReferenceType;
import guru.interlis.model.EffectiveMember;
import guru.interlis.model.EffectiveMemberOrigin;
import guru.interlis.model.Origin;
import guru.interlis.model.ReferenceUsage;
import guru.interlis.model.RoleAggregation;
import guru.interlis.model.RoleSymbol;
import guru.interlis.model.StructureSymbol;
import guru.interlis.model.Symbol;
import guru.interlis.model.SymbolId;
import guru.interlis.model.SymbolKind;
import guru.interlis.model.TopicSymbol;
import guru.interlis.model.TypeDescriptor;
import guru.interlis.syntax.ClassDeclarationSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Binds inheritance and effective members in their declared order. */
final class InheritanceBindings {
    private final SemanticGraphRead.Inheritance state;
    private final SemanticWork work;
    private final IndexedDeclarations index;
    private final SemanticDiagnostics diagnostics;
    private final NameBindings names;
    private final Supplier<TypeBindings> typeBindings;
    private final TopicBindings topics;
    private final Supplier<MetadataBindings> metadata;
    private final Consumer<AttributeSymbol> replaceOverride;
    private final Function<SymbolId, SymbolId> scopeRoot;
    private final Function<DeclInfo, Symbol> ensureSymbol;
    private final Set<SymbolId> resolvedObjectBases = new HashSet<>();
    private final Set<SymbolId> effectiveProcessed = new HashSet<>();

    InheritanceBindings(SemanticGraphRead.Inheritance state,
                        SemanticWork work,
                        IndexedDeclarations index,
                        SemanticDiagnostics diagnostics,
                        NameBindings names,
                        Supplier<TypeBindings> typeBindings,
                        TopicBindings topics,
                        Supplier<MetadataBindings> metadata,
                        Consumer<AttributeSymbol> replaceOverride,
                        Function<SymbolId, SymbolId> scopeRoot,
                        Function<DeclInfo, Symbol> ensureSymbol) {
        this.state = state;
        this.work = work;
        this.index = index;
        this.diagnostics = diagnostics;
        this.names = names;
        this.typeBindings = typeBindings;
        this.topics = topics;
        this.metadata = metadata;
        this.replaceOverride = replaceOverride;
        this.scopeRoot = scopeRoot;
        this.ensureSymbol = ensureSymbol;
    }

    Optional<SymbolId> baseTopicMember(SymbolId context, String name,
                                               Set<SymbolKind> expected) {
        SymbolId current = scopeRoot.apply(context);
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            work.checkpoint();
            DeclInfo topicDecl = index.byId(current).orElse(null);
            if (topicDecl == null || !(topicDecl.syntax() instanceof guru.interlis.syntax.TopicSyntax syntax)
                    || syntax.baseTopic().isEmpty()) {
                return Optional.empty();
            }
            Optional<SymbolId> base = names.tryResolve(topicDecl, syntax.baseTopic().orElseThrow(),
                    Set.of(SymbolKind.TOPIC));
            if (base.isEmpty()) {
                return Optional.empty();
            }
            for (DeclInfo child : index.children(base.orElseThrow())) {
                work.checkpoint();
                if (expected.contains(child.kind()) && child.name().equals(name)) {
                    return Optional.of(child.id());
                }
            }
            current = base.orElseThrow();
        }
        return Optional.empty();
    }

    /** Import pairs follow their bound targets, never the available language variants. */
    void validateClassExtensions() {
        Set<SymbolId> extendedBases = new LinkedHashSet<>();
        Map<SymbolId, List<SymbolId>> subclasses = new LinkedHashMap<>();
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            subclasses.computeIfAbsent(base, ignored -> new ArrayList<>()).add(clazz.id());
            Symbol baseSymbol = state.symbol(base);
            if (baseSymbol != null && baseSymbol.header().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED)) {
                extendedBases.add(base);
            }
        }
        Map<String, SymbolId> extendedByTopic = new LinkedHashMap<>();
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (symbol instanceof ClassSymbol clazz
                    && clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                SymbolId topic = scopeRoot.apply(clazz.id());
                if (topic != null) {
                    extendedByTopic.put(topic + ":" + clazz.name().value(), clazz.id());
                }
            }
        }
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            Symbol baseSymbol = state.symbol(base);
            SymbolId topic = scopeRoot.apply(clazz.id());
            if (baseSymbol != null && topic != null) {
                SymbolId localExtended = extendedByTopic.get(topic + ":" + baseSymbol.name().value());
                if (localExtended != null && !localExtended.equals(base) && !localExtended.equals(clazz.id())) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            diagnostics.emitRule(decl, "subclass must extend the local EXTENDED class",
                                    "EXTENDED_BASE_CLASS"));
                } else if (localExtended != null
                        && state.symbol(topic) instanceof TopicSymbol topicSymbol
                        && topicSymbol.baseTopic().isEmpty()) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            diagnostics.emitRule(decl, "EXTENDED class may only be extended in a derived topic",
                                    "EXTENDED_BASE_CLASS"));
                }
            }
        }
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (!(symbol instanceof ClassSymbol clazz)
                    || !clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                continue;
            }
            SymbolId topic = scopeRoot.apply(clazz.id());
            if (!(state.symbol(topic) instanceof TopicSymbol topicSymbol)
                    || topicSymbol.baseTopic().isEmpty()) {
                continue;
            }
            SymbolId baseTopic = topicSymbol.baseTopic().orElseThrow();
            for (DeclInfo baseChild : index.children(baseTopic)) {
                work.checkpoint();
                boolean specialized = index.children(baseTopic).stream()
                        .filter(child -> child.kind() == SymbolKind.CLASS)
                        .anyMatch(child -> state.baseOf(child.id()) != null
                                && state.baseOf(child.id()).equals(baseChild.id()));
                if (baseChild.kind() == SymbolKind.CLASS
                        && baseChild.name().equals(clazz.name().value()) && specialized) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            diagnostics.emitRule(decl, "EXTENDED class conflicts with a specialized base class",
                                    "EXTENDED_CLASS_CONFLICT"));
                }
            }
        }
    }

    void validateAttributeOverrides() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (!(symbol instanceof ClassSymbol) && !(symbol instanceof StructureSymbol) && !(symbol instanceof AssociationSymbol)) {
                continue;
            }
            SymbolId base = state.baseOf(symbol.id());
            if (base == null) {
                continue;
            }
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members() : symbol instanceof StructureSymbol structure ? structure.members()
                    : ((AssociationSymbol) symbol).attributes();
            for (SymbolId member : members) {
                work.checkpoint();
                Symbol memberSymbol = state.symbol(member);
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
                validateAttributeOverrideProperties(attributeDecl, attribute, baseAttribute);
                TypeDescriptor baseType = typeBindings.get().effectiveDescriptor(baseAttribute.declaredType());
                TypeDescriptor derivedType = typeBindings.get().effectiveDescriptor(attribute.declaredType());
                if (baseType != null && derivedType != null) {
                    String problem = overrideProblem(baseType, derivedType, attributeDecl.language());
                    if (problem != null) {
                        diagnostics.emitRule(attributeDecl, problem, "ATTRIBUTE_OVERRIDE_TYPE");
                    }
                }
            }
        }
    }

    /** Shared mandatory, transient and domain-identity rules for attribute overrides. */
    void validateAttributeOverrideProperties(DeclInfo attributeDecl, AttributeSymbol attribute, AttributeSymbol baseAttribute) {
        if (baseAttribute.mandatory() && !attribute.mandatory()) {
            diagnostics.emitRule(attributeDecl, "extended attribute may not remove MANDATORY",
                    "ATTRIBUTE_OVERRIDE_MANDATORY");
        }
        if (baseAttribute.transientAttribute() != attribute.transientAttribute()) {
            diagnostics.emitRule(attributeDecl, "extended attribute changes TRANSIENT mode",
                    "ATTRIBUTE_OVERRIDE_TRANSIENT");
        }
        if (state.type(baseAttribute.declaredType()) instanceof DomainReferenceType baseDomainRef
                && state.type(attribute.declaredType()) instanceof DomainReferenceType derivedDomainRef
                && !domainExtends(derivedDomainRef.targetDomain(), baseDomainRef.targetDomain())) {
            diagnostics.emitRule(attributeDecl, "extended attribute changes domain",
                    "ATTRIBUTE_OVERRIDE_TYPE");
        }
    }

    AttributeSymbol findBaseAttribute(SymbolId base, String name) {
        SymbolId current = base;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null && seen.add(current)) {
            work.checkpoint();
            Symbol symbol = state.symbol(current);
            List<SymbolId> members = symbol instanceof ClassSymbol classSymbol
                    ? classSymbol.members()
                    : symbol instanceof StructureSymbol structure ? structure.members()
                    : symbol instanceof AssociationSymbol association ? association.attributes() : List.of();
            for (SymbolId member : members) {
                work.checkpoint();
                Symbol memberSymbol = state.symbol(member);
                if (memberSymbol instanceof AttributeSymbol attribute && attribute.name().value().equals(name)) {
                    return attribute;
                }
            }
            current = state.baseOf(current);
        }
        return null;
    }

    boolean domainExtends(SymbolId derived, SymbolId base) {
        SymbolId current = derived;
        Set<SymbolId> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            work.checkpoint();
            if (current.equals(base)) {
                return true;
            }
            current = state.baseOf(current);
        }
        return false;
    }

    private String overrideProblem(TypeDescriptor base, TypeDescriptor derived, guru.interlis.model.LanguageVersion language) {
        var relation=typeBindings.get().extension(derived,base,language);
        return relation.decision()==guru.interlis.model.RelationDecision.ALLOWED?null:relation.reason();
    }

    void validateAssociationRules() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (symbol instanceof AssociationSymbol association) {
                Set<String> roleNames = new LinkedHashSet<>();
                for (SymbolId roleId : association.roles()) {
                    work.checkpoint();
                    Symbol role = state.symbol(roleId);
                    if (role != null) {
                        roleNames.add(role.name().value());
                    }
                }
                for (SymbolId memberId : association.attributes()) {
                    work.checkpoint();
                    Symbol member = state.symbol(memberId);
                    if (member != null && roleNames.contains(member.name().value())) {
                        DeclInfo associationDecl = index.byId(association.id()).orElse(null);
                        if (associationDecl != null) {
                            diagnostics.emitRule(associationDecl,
                                    "role and attribute share a name in the association",
                                    "ROLE_ATTRIBUTE_CONFLICT");
                        }
                    }
                }
            }
        }
        Map<SymbolId, Map<String, DeclInfo>> roleNamesByTarget = new LinkedHashMap<>();
        for (AssociationSymbol association : state.symbolsInBuildOrder().stream()
                .filter(AssociationSymbol.class::isInstance)
                .map(AssociationSymbol.class::cast).toList()) {
            work.checkpoint();
            DeclInfo decl = index.byId(association.id()).orElse(null);
            if (decl == null) {
                continue;
            }
            guru.interlis.syntax.AssociationDeclarationSyntax syntax =
                    (guru.interlis.syntax.AssociationDeclarationSyntax) decl.syntax();
            List<RoleSymbol> effectiveRoles = associationRoles(association.id());
            if (effectiveRoles.size() < 2) diagnostics.emitRule(decl, "association requires at least two effective roles", "ASSOCIATION_ROLES");
            for (RoleSymbol role : effectiveRoles) {
                if (role.targetRoots().stream().anyMatch(t -> targetSpecializes(association.id(), t)
                        && index.byId(t).map(DeclInfo::kind).orElse(null) == SymbolKind.ASSOCIATION)) {
                    diagnostics.emitRule(decl, "association may not specialize a role target", "ASSOCIATION_TARGET_BASE");
                }
            }
            for (RoleSymbol hiding : effectiveRoles) {
                if (!hiding.hiding()) continue;
                for (Symbol other : state.symbolsInBuildOrder()) {
                    work.checkpoint();
                    if (!(other instanceof AssociationSymbol sibling)
                            || baseChain(association.id()).contains(sibling.id())
                            || baseChain(sibling.id()).contains(association.id())
                            || baseChain(association.id()).stream().noneMatch(baseChain(sibling.id())::contains)) continue;
                    for (RoleSymbol role : associationRoles(sibling.id())) {
                        if (!role.hiding() && role.name().equals(hiding.name())
                                && role.targetRoots().stream().anyMatch(t -> hiding.targetRoots().stream()
                                .anyMatch(h -> targetSpecializes(t, h) || targetSpecializes(h, t)))) {
                            diagnostics.emitRule(decl, "HIDING conflicts with a non-hiding sibling role", "ROLE_HIDING_CONFLICT");
                        }
                    }
                }
            }
            long aggregations = effectiveRoles.stream()
                    .filter(role -> role.aggregation() != RoleAggregation.NONE).count();
            if (aggregations > 1) {
                diagnostics.emitRule(decl, "only one aggregation role is allowed", "MULTIPLE_AGGREGATION");
            }
            if (effectiveRoles.size()!=2 && effectiveRoles.stream().anyMatch(role->role.aggregation()==RoleAggregation.AGGREGATION)) {
                diagnostics.emitRule(decl, "aggregation requires exactly two effective roles", "AGGREGATION_ARITY");
            }
            boolean associationAbstract = syntax.modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT);
            for (guru.interlis.syntax.RoleDeclarationSyntax roleSyntax : syntax.roles()) {
                work.checkpoint();
                if (roleSyntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)
                        && !associationAbstract) {
                    DeclInfo roleDecl = index.children(decl.id()).stream()
                            .filter(child -> child.kind() == SymbolKind.ROLE
                                    && child.name().equals(roleSyntax.name()))
                            .findFirst().orElse(decl);
                    diagnostics.emitRule(roleDecl, "abstract role requires an abstract association",
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
                    diagnostics.emitRule(roleDecl, "composition role cardinality must be 1", "COMPOSITION_CARDINALITY");
                }
            }
            List<RoleSymbol> roles = association.roles().stream()
                    .map(state::symbol).filter(RoleSymbol.class::isInstance)
                    .map(RoleSymbol.class::cast).toList();
            if (roles.size() == 2) {
                roles.get(0).targetRoots().forEach(t -> addRoleNamespace(roleNamesByTarget, t, roles.get(1)));
                roles.get(1).targetRoots().forEach(t -> addRoleNamespace(roleNamesByTarget, t, roles.get(0)));
            }
        }
        for (ClassSymbol classSymbol : state.symbolsInBuildOrder().stream()
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast).toList()) {
            work.checkpoint();
            Set<SymbolId> chain = new LinkedHashSet<>();
            SymbolId current = classSymbol.id();
            while (current != null && chain.add(current)) {
                work.checkpoint();
                current = state.baseOf(current);
            }
            Map<String, DeclInfo> attributes = new LinkedHashMap<>();
            for (SymbolId ancestor : chain) for (DeclInfo member : index.children(ancestor)) {
                work.checkpoint();
                if (member.kind() == SymbolKind.ATTRIBUTE) attributes.putIfAbsent(member.name(), member);
            }
            for (SymbolId classId : chain) {
                work.checkpoint();
                Map<String, DeclInfo> names = roleNamesByTarget.get(classId);
                if (names == null) {
                    continue;
                }
                for (DeclInfo memberDecl : attributes.values()) {
                    work.checkpoint();
                    DeclInfo role = names.get(memberDecl.name());
                    if (role == null) continue;
                    DeclInfo association = role.owner().flatMap(index::byId).orElse(null);
                    DeclInfo attributeOwner = memberDecl.owner().flatMap(index::byId).orElse(null);
                    // A later association cannot retroactively add an inherited name to
                    // an already declared subclass. The direct target still owns its
                    // own namespace and is checked regardless of declaration order.
                    if (!memberDecl.owner().filter(classId::equals).isPresent()
                            && association != null && attributeOwner != null
                            && association.sourceId().equals(attributeOwner.sourceId())
                            && association.syntax().range().startOffset() > attributeOwner.syntax().range().startOffset()) continue;
                    {
                        diagnostics.emitRule(memberDecl, "attribute name conflicts with role name",
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
            SymbolId firstTopic = scopeRoot.apply(role.id());
            SymbolId secondTopic = scopeRoot.apply(previous.id());
            boolean sharedBase = firstTopic != null && secondTopic != null
                    && !firstTopic.equals(secondTopic)
                    && topics.shareBaseTopic(firstTopic, secondTopic);
            SymbolId currentOwner = role.header().owner().orElseThrow();
            SymbolId previousOwner = previous.owner().orElseThrow();
            boolean sameFamily = baseChain(currentOwner).stream().anyMatch(baseChain(previousOwner)::contains);
            if (!sharedBase && !sameFamily) {
                diagnostics.emitRule(roleDecl, "role name conflicts with another role in the same namespace",
                        "ROLE_NAMESPACE_CONFLICT");
            }
        }
    }

    void validateTopicInheritanceCycles() {
        for (Symbol symbol : state.symbolsInBuildOrder()) {
            work.checkpoint();
            if (symbol instanceof TopicSymbol && hasCycle(baseChain(symbol.id())))
                index.byId(symbol.id()).ifPresent(this::emitCycle);
        }
    }

    /** Bind inheritance before members, including members reached from another source. */
    Optional<SymbolId> objectInheritanceBase(SymbolId owner) {
        DeclInfo decl = index.byId(owner).orElse(null);
        if (decl == null || (decl.kind() != SymbolKind.CLASS && decl.kind() != SymbolKind.STRUCTURE)
                || !resolvedObjectBases.add(owner)) return Optional.ofNullable(state.baseOf(owner));
        Optional<guru.interlis.syntax.NameSyntax> name = decl.syntax() instanceof ClassDeclarationSyntax c
                ? c.baseType() : ((StructureDeclarationSyntax) decl.syntax()).baseType();
        if (name.isPresent() && decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED))
            diagnostics.emitRule(decl, "EXTENDED and EXTENDS cannot be combined in one declaration", "EXTENDED_EXPLICIT_BASE");
        Set<SymbolKind> kinds = decl.kind() == SymbolKind.CLASS
                ? Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE) : Set.of(SymbolKind.STRUCTURE);
        Optional<SymbolId> base=name.flatMap(n->names.bindTarget(decl,n,kinds,ReferenceUsage.BASE_TYPE,new ArrayList<>()));

        if (base.isEmpty() && decl.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
            base = baseTopicMember(owner, decl.name(), Set.of(decl.kind()));
            if (base.isEmpty()) diagnostics.emitRule(decl, "EXTENDED needs a same-named inherited declaration", "EXTENDED_BASE");
        }
        base.ifPresent(id -> {
            state.bindBase(owner, id);
            if (isFinalDeclaration(id))
                diagnostics.emitRule(decl, "cannot extend FINAL object type", "OBJECT_FINAL");
        });
        return base;
    }

    /** Declaration metadata is available before an imported base is built. */
    boolean isFinalDeclaration(SymbolId id) {
        return index.byId(id).map(info -> info.modifiers().contains(guru.interlis.model.Modifier.FINAL))
                .orElseGet(() -> state.hasSymbol(id) && state.symbol(id).header().has(guru.interlis.model.Modifier.FINAL));
    }

    List<RoleSymbol> associationRoles(SymbolId association) {
        Map<String, RoleSymbol> roles = new LinkedHashMap<>();
        List<SymbolId> chain = baseChain(association);
        for (int i = chain.size() - 1; i >= 0; i--) {
            for (DeclInfo child : index.children(chain.get(i))) {
                work.checkpoint();
                if (child.kind() == SymbolKind.ROLE) {
                    roles.put(child.name(), (RoleSymbol) ensureSymbol.apply(child));
                }
            }
        }
        return List.copyOf(roles.values());
    }

    boolean targetSpecializes(SymbolId candidate, SymbolId base) {
        return objectBase(base).isEmpty()
                || guru.interlis.model.internal.TypeCompatibility.specializes(candidate, base, state::baseOf);
    }

    boolean roleAccepts(RoleSymbol role, SymbolId candidate) {
        return role.targetRoots().stream().anyMatch(base -> targetSpecializes(candidate, base));
    }

    Optional<SymbolId> objectBase(SymbolId target) {
        Symbol symbol = state.symbol(target);
        return symbol != null && symbol.origin() instanceof Origin.PredefinedOrigin predefined
                && predefined.key().equals("INTERLIS.ANYCLASS") ? Optional.empty() : Optional.of(target);
    }

    void resolveOverridesAndEffectiveMembers() {
        for (IndexedDeclarations.ModelEntry entry : index.models()) {
            work.checkpoint();
            for (DeclInfo child : index.children(entry.id())) {
                work.checkpoint();
                collectEffective(child);
            }
        }
    }

    void collectEffective(DeclInfo decl) {
        if (!effectiveProcessed.add(decl.id())) {
            return;
        }
        if (decl.kind() == SymbolKind.CLASS || decl.kind() == SymbolKind.STRUCTURE
                || decl.kind() == SymbolKind.ASSOCIATION) {
            if (decl.kind() != SymbolKind.ASSOCIATION) metadata.get().ensureBases(decl.id());
            List<SymbolId> chain = baseChain(decl.id());
            if (hasCycle(chain)) {
                emitCycle(decl);
                state.setEffectiveMembers(decl.id(), localMembers(decl));
                return;
            }
            List<EffectiveMember> result = new ArrayList<>();
            Map<String, Integer> positions = new HashMap<>();
            for (int depth = chain.size() - 1; depth >= 0; depth--) {
                work.checkpoint();
                SymbolId current = chain.get(depth);
                if (state.symbol(current) != null && state.symbol(current).origin() instanceof Origin.PredefinedOrigin) {
                    var predefined = state.symbol(current);
                    var members = predefined instanceof StructureSymbol s ? s.members() : predefined instanceof ClassSymbol c ? c.members() : List.<SymbolId>of();
                    for (SymbolId member : members) {
                        if (state.symbol(member).kind() != SymbolKind.ATTRIBUTE) continue;
                        String name = state.symbol(member).name().value();
                        Integer existing = positions.get(name);
                        int position = existing == null ? result.size() : existing;
                        var effective = new EffectiveMember(member, decl.id(), pathTo(chain, depth), position,
                                EffectiveMemberOrigin.INHERITED, existing != null);
                        if (existing == null) { positions.put(name, position); result.add(effective); }
                        else result.set(position, effective);
                    }
                }
                for (DeclInfo attribute : index.children(current)) {
                    work.checkpoint();
                    if (attribute.kind() != SymbolKind.ATTRIBUTE && attribute.kind() != SymbolKind.ROLE) {
                        continue;
                    }
                    ensureSymbol.apply(attribute);
                    Integer existing = positions.get(attribute.name());
                    if (existing != null) {
                        if (depth == 0 && !attribute.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                            diagnostics.emitRule(attribute, "override requires EXTENDED", "OVERRIDE");
                        }
                        EffectiveMember old = result.get(existing);
                        if (depth == 0 && attribute.kind() == SymbolKind.ATTRIBUTE && isFinalDeclaration(old.declaredSymbol()))
                            diagnostics.emitRule(attribute, "cannot override FINAL attribute", "ATTRIBUTE_FINAL");
                        result.set(existing, new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth), existing,
                                depth == 0 ? EffectiveMemberOrigin.OVERRIDDEN : EffectiveMemberOrigin.INHERITED, true));
                        AttributeSymbol override = attribute.kind() == SymbolKind.ATTRIBUTE
                                ? overrideAttribute(attribute.id(), old.declaredSymbol()) : null;
                        if (override != null) {
                            replaceOverride.accept(override);
                        }
                    } else {
                        if (depth == 0 && attribute.kind() == SymbolKind.ATTRIBUTE
                                && attribute.modifiers().contains(guru.interlis.model.Modifier.EXTENDED))
                            diagnostics.emitRule(attribute, "EXTENDED attribute needs an inherited attribute", "EXTENDED_BASE");
                        int position = result.size();
                        positions.put(attribute.name(), position);
                        result.add(new EffectiveMember(attribute.id(), decl.id(),
                                pathTo(chain, depth), position,
                                depth == 0 ? EffectiveMemberOrigin.LOCAL
                                        : EffectiveMemberOrigin.INHERITED, false));
                    }
                }
            }
            state.setEffectiveMembers(decl.id(), List.copyOf(result));
        }
        for (DeclInfo child : index.children(decl.id())) {
            work.checkpoint();
            collectEffective(child);
        }
    }

    private AttributeSymbol overrideAttribute(SymbolId id, SymbolId baseId) {
        if (state.symbol(id) instanceof AttributeSymbol attribute) {
            return new AttributeSymbol(attribute.header(), attribute.declaredType(),
                    attribute.mandatory(), attribute.transientAttribute(), attribute.continuous(),
                    attribute.subdivision(), Optional.of(baseId), attribute.derivations());
        }
        return null;
    }

    List<SymbolId> baseChain(SymbolId id) {
        List<SymbolId> chain = new ArrayList<>();
        SymbolId current = id;
        Set<SymbolId> seen = new HashSet<>();
        while (current != null) {
            work.checkpoint();
            chain.add(current);
            if (!seen.add(current)) break;
            current = state.baseOf(current);
        }
        return chain;
    }

    private boolean hasCycle(List<SymbolId> chain) {
        return chain.size() != new HashSet<>(chain).size();
    }

    private void emitCycle(DeclInfo decl) {
        diagnostics.emit(DiagnosticDraft.of(new DiagnosticCode("ILI-SEM-0006"), Severity.ERROR,
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
            work.checkpoint();
            if (child.kind() == SymbolKind.ATTRIBUTE) {
                result.add(new EffectiveMember(child.id(), decl.id(), List.of(decl.id()), position++,
                        EffectiveMemberOrigin.LOCAL, false));
            }
        }
        return List.copyOf(result);
    }

    private List<SymbolId> pathTo(List<SymbolId> chain, int depth) {
        List<SymbolId> result = new ArrayList<>();
        for (int i = depth; i >= 0; i--) {
            work.checkpoint();
            result.add(chain.get(i));
        }
        return List.copyOf(result);
    }

    void bindBase(SymbolId symbol, SymbolId base) { state.bindBase(symbol, base); }

    void setEffectiveMembers(SymbolId owner, List<EffectiveMember> members) { state.setEffectiveMembers(owner, members); }
}

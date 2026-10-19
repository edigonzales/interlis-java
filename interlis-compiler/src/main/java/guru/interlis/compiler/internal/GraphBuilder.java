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
import guru.interlis.model.ClassValueType;
import guru.interlis.model.CoordinateType;
import guru.interlis.model.FormattedType;
import guru.interlis.model.GenericType;
import guru.interlis.model.GeometryType;
import guru.interlis.model.StructureValueType;
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
import guru.interlis.model.UnitSymbol;
import guru.interlis.model.FunctionSymbol;
import guru.interlis.model.FunctionParameter;
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
import guru.interlis.syntax.AlignmentTypeSyntax;
import guru.interlis.syntax.BlackboxTypeSyntax;
import guru.interlis.syntax.ClassValueTypeSyntax;
import guru.interlis.syntax.CollectionTypeSyntax;
import guru.interlis.syntax.OidTypeSyntax;
import guru.interlis.syntax.ConstantExpressionSyntax;
import guru.interlis.syntax.ConstraintDeclarationSyntax;
import guru.interlis.syntax.CoordinateTypeSyntax;
import guru.interlis.syntax.DefinedExpressionSyntax;
import guru.interlis.syntax.DomainDeclarationSyntax;
import guru.interlis.syntax.ExternalConstraintSyntax;
import guru.interlis.syntax.FormattedTypeSyntax;
import guru.interlis.syntax.BuiltinFunctionExpressionSyntax;
import guru.interlis.syntax.ClassConstantExpressionSyntax;
import guru.interlis.syntax.GenericConstantExpressionSyntax;
import guru.interlis.syntax.GenericTypeSyntax;
import guru.interlis.syntax.EnumTreeValueSyntax;
import guru.interlis.syntax.EnumerationConstantExpressionSyntax;
import guru.interlis.syntax.FunctionCallExpressionSyntax;
import guru.interlis.syntax.LineTypeSyntax;
import guru.interlis.syntax.ViewDeclarationSyntax;
import guru.interlis.syntax.UnitDeclarationSyntax;
import guru.interlis.syntax.FunctionDeclarationSyntax;
import guru.interlis.syntax.DomainReferenceSyntax;
import guru.interlis.syntax.EnumerationTypeSyntax;
import guru.interlis.syntax.ExpressionSyntax;
import guru.interlis.syntax.LiteralExpressionSyntax;
import guru.interlis.syntax.ParenthesizedExpressionSyntax;
import guru.interlis.syntax.PathExpressionSyntax;
import guru.interlis.syntax.ReferenceTypeSyntax;
import guru.interlis.syntax.RoleDeclarationSyntax;
import guru.interlis.syntax.StructureDeclarationSyntax;
import guru.interlis.syntax.TemporalTypeSyntax;
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
    private final Set<TypeId> enumTreeTypes = new LinkedHashSet<>();
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
        validateClassExtensions();
        validateTranslations();
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

    private void validateTranslations() {
        List<ModelSymbol> models = symbols.values().stream()
                .filter(ModelSymbol.class::isInstance).map(ModelSymbol.class::cast).toList();
        for (ModelSymbol model : models) {
            if (model.translation().isEmpty()) {
                continue;
            }
            guru.interlis.model.ModelTranslation translation = model.translation().orElseThrow();
            ModelSymbol source = models.stream()
                    .filter(candidate -> candidate.name().value().equals(translation.sourceModel()))
                    .findFirst().orElse(null);
            DeclInfo decl = index.byId(model.id()).orElse(null);
            if (source == null) {
                if (decl != null) {
                    emitRule(decl, "translation source model not found", "TRANSLATION_SOURCE");
                }
                continue;
            }
            if (translation.sourceVersion().isPresent()
                    && !translation.sourceVersion().orElseThrow().equals(source.modelVersion())) {
                if (decl != null) {
                    emitRule(decl, "translation source version mismatch", "TRANSLATION_VERSION");
                }
            }
            if (decl != null) {
                Map<SymbolId, SymbolId> correspondence = correspondence(source, model);
                if (correspondence.isEmpty()
                        || !syntaxEquivalent(source, model, correspondence)) {
                    emitRule(decl, "translation syntax does not match source model",
                            "TRANSLATION_STRUCTURE");
                } else {
                    if (!metaObjectNames(source).equals(metaObjectNames(model))) {
                        emitRule(decl, "translation REFSYSTEM objects differ",
                                "TRANSLATION_METAOBJECT");
                    }
                    Map<SymbolId, SymbolId> reverse = new LinkedHashMap<>();
                    correspondence.forEach((sourceId, targetId) -> reverse.put(targetId, sourceId));
                    for (SymbolId sourceMember : source.members()) {
                        SymbolId targetMember = correspondence.get(sourceMember);
                        if (targetMember != null) {
                            compareCorrespondingConstraints(sourceMember, targetMember, reverse,
                                    decl, correspondence);
                        }
                    }
                }
            }
        }
    }

    private boolean syntaxEquivalent(ModelSymbol source, ModelSymbol target,
                                     Map<SymbolId, SymbolId> correspondence) {
        if (source.modelKind() != target.modelKind()
                || source.contracted() != target.contracted()
                || source.imports().size() != target.imports().size()) {
            return false;
        }
        Map<String, String> translatedModels = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            if (symbol instanceof ModelSymbol candidate && candidate.translation().isPresent()) {
                translatedModels.put(candidate.translation().orElseThrow().sourceModel(),
                        candidate.name().value());
            }
        }
        List<String> expectedImports = new ArrayList<>();
        List<String> actualImports = new ArrayList<>();
        for (int i = 0; i < source.imports().size(); i++) {
            ModelImport sourceImport = source.imports().get(i);
            ModelImport targetImport = target.imports().get(i);
            if (sourceImport.unqualified() != targetImport.unqualified()) {
                return false;
            }
            expectedImports.add(translatedModels.getOrDefault(sourceImport.importedModel(),
                    sourceImport.importedModel()));
            actualImports.add(targetImport.importedModel());
        }
        expectedImports.sort(String::compareTo);
        actualImports.sort(String::compareTo);
        if (!expectedImports.equals(actualImports)) {
            return false;
        }
        Map<String, String> names = new LinkedHashMap<>();
        correspondence.forEach((sourceId, targetId) -> {
            Symbol sourceSymbol = symbols.get(sourceId);
            Symbol targetSymbol = symbols.get(targetId);
            if (sourceSymbol != null && targetSymbol != null) {
                names.put(targetSymbol.name().value(), sourceSymbol.name().value());
            }
        });
        Set<String> sourceNames = new LinkedHashSet<>();
        Set<String> targetNames = new LinkedHashSet<>();
        for (Symbol symbol : symbols.values()) {
            if (correspondence.containsKey(symbol.id())) {
                sourceNames.add(symbol.name().value());
            }
            if (correspondence.containsValue(symbol.id())) {
                targetNames.add(symbol.name().value());
            }
        }
        correspondence.forEach((sourceId, targetId) -> {
            Symbol sourceSymbol = symbols.get(sourceId);
            Symbol targetSymbol = symbols.get(targetId);
            if (sourceSymbol instanceof DomainSymbol sourceDomain
                    && targetSymbol instanceof DomainSymbol targetDomain
                    && types.get(sourceDomain.typeId()) instanceof EnumerationType sourceEnum
                    && types.get(targetDomain.typeId()) instanceof EnumerationType targetEnum) {
                for (int i = 0; i < Math.min(sourceEnum.values().size(), targetEnum.values().size()); i++) {
                    String sourceValue = sourceEnum.values().get(i).path();
                    String targetValue = targetEnum.values().get(i).path();
                    String[] sourceParts = sourceValue.split("\\.");
                    String[] targetParts = targetValue.split("\\.");
                    if (sourceParts.length == targetParts.length) {
                        for (int part = 0; part < sourceParts.length; part++) {
                            names.put(targetParts[part], sourceParts[part]);
                            sourceNames.add(sourceParts[part]);
                            targetNames.add(targetParts[part]);
                        }
                    }
                }
            }
        });
        List<String> sourceForms = lineFormNames(source);
        List<String> targetForms = lineFormNames(target);
        if (sourceForms.size() == targetForms.size()) {
            for (int i = 0; i < sourceForms.size(); i++) {
                names.put(targetForms.get(i), sourceForms.get(i));
                sourceNames.add(sourceForms.get(i));
                targetNames.add(targetForms.get(i));
            }
        }
        List<UnitSymbol> sourceUnits = unitsOf(source);
        List<UnitSymbol> targetUnits = unitsOf(target);
        if (sourceUnits.size() == targetUnits.size()) {
            for (int i = 0; i < sourceUnits.size(); i++) {
                names.put(targetUnits.get(i).name().value(), sourceUnits.get(i).name().value());
                String sourceAlias = unitAlias(sourceUnits.get(i));
                String targetAlias = unitAlias(targetUnits.get(i));
                if (sourceAlias != null && targetAlias != null) {
                    names.put(targetAlias, sourceAlias);
                    sourceNames.add(sourceAlias);
                    targetNames.add(targetAlias);
                }
            }
        }
        Map<String, String> unitNames = new LinkedHashMap<>();
        for (int i = 0; i < sourceUnits.size(); i++) {
            unitNames.put(targetUnits.get(i).name().value(), sourceUnits.get(i).name().value());
        }
        if (!unitDefinitionsEquivalent(sourceUnits, targetUnits, unitNames)) {
            return false;
        }
        List<String> sourceTokens = modelTokens(source, Map.of(), sourceNames, false);
        List<String> targetTokens = modelTokens(target, names, targetNames, true);
        if (sourceTokens.isEmpty() || sourceTokens.size() != targetTokens.size()) {
            return false;
        }
        for (int i = 0; i < sourceTokens.size(); i++) {
            String sourceToken = sourceTokens.get(i);
            String targetToken = targetTokens.get(i);
            if (!sourceToken.equals(targetToken)
                    && !"ID:*".equals(sourceToken) && !"ID:*".equals(targetToken)) {
                return false;
            }
        }
        return true;
    }

    private boolean unitDefinitionsEquivalent(List<UnitSymbol> sourceUnits,
                                              List<UnitSymbol> targetUnits,
                                              Map<String, String> unitNames) {
        if (sourceUnits.size() != targetUnits.size()) {
            return false;
        }
        Map<String, String> aliases = new LinkedHashMap<>();
        for (int i = 0; i < sourceUnits.size(); i++) {
            String sourceAlias = unitAlias(sourceUnits.get(i));
            String targetAlias = unitAlias(targetUnits.get(i));
            if (sourceAlias != null && targetAlias != null) {
                aliases.put(targetAlias, sourceAlias);
            }
        }
        for (int i = 0; i < sourceUnits.size(); i++) {
            String sourceDefinition = sourceUnits.get(i).definition().orElse("");
            String targetDefinition = targetUnits.get(i).definition().orElse("");
            List<String> sourceIds = identifiers(sourceDefinition);
            List<String> targetIds = identifiers(targetDefinition).stream()
                    .map(name -> aliases.getOrDefault(name,
                            unitNames.getOrDefault(name, name))).toList();
            if (!sourceIds.equals(targetIds)) {
                return false;
            }
        }
        return true;
    }

    private String unitAlias(UnitSymbol unit) {
        if (!(unit.header().origin() instanceof Origin.DeclaredOrigin declared)) {
            return null;
        }
        guru.interlis.source.SourceText text = sourceSet.sources().get(declared.range().source());
        if (text == null) {
            return null;
        }
        String full = text.text();
        int start = Math.max(0, declared.range().startOffset());
        int end = Math.min(full.length(), declared.range().endOffset() + 1);
        if (start >= end) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\\[([A-Za-z_][A-Za-z0-9_]*)\\]")
                .matcher(full.substring(start, end));
        return matcher.find() ? matcher.group(1) : null;
    }

    private List<String> identifiers(String text) {
        List<String> result = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[A-Za-z_][A-Za-z0-9_]*").matcher(text);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private List<String> metaObjectNames(ModelSymbol model) {
        String text = modelText(model);
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?is)OBJECTS\\s+OF\\s+[A-Za-z_][A-Za-z0-9_.]*\\s*:\\s*"
                        + "([A-Za-z_][A-Za-z0-9_]*(?:\\s*,\\s*[A-Za-z_][A-Za-z0-9_]*)*)")
                .matcher(text);
        while (matcher.find()) {
            for (String part : matcher.group(1).split(",")) {
                names.add(part.trim());
            }
        }
        return names;
    }

    private List<String> lineFormNames(ModelSymbol model) {
        String text = modelText(model);
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)LINE\\s+FORM\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:")
                .matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private String modelText(ModelSymbol model) {
        if (!(model.header().origin() instanceof Origin.DeclaredOrigin declared)) {
            return "";
        }
        guru.interlis.source.SourceText text = sourceSet.sources().get(declared.range().source());
        if (text == null) {
            return "";
        }
        int start = Math.max(0, declared.range().startOffset());
        int end = Math.min(text.text().length(), declared.range().endOffset() + 1);
        return text.text().substring(start, end);
    }

    private Optional<SymbolId> baseTopicMember(SymbolId context, String name) {
        SymbolId current = scopeRoot(context);
        int guard = 0;
        while (current != null && guard++ < 32) {
            Symbol topicSymbol = symbols.get(current);
            if (!(topicSymbol instanceof TopicSymbol topic) || topic.baseTopic().isEmpty()) {
                return Optional.empty();
            }
            SymbolId base = topic.baseTopic().orElseThrow();
            for (DeclInfo child : index.children(base)) {
                if ((child.kind() == SymbolKind.STRUCTURE || child.kind() == SymbolKind.DOMAIN)
                        && child.name().equals(name)) {
                    return Optional.of(child.id());
                }
            }
            current = base;
        }
        return Optional.empty();
    }

    private Optional<SymbolId> unitByAlias(String qualifiedName) {
        String alias = qualifiedName.contains(".")
                ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
                : qualifiedName;
        for (Symbol symbol : symbols.values()) {
            if (!(symbol instanceof UnitSymbol unit)) {
                continue;
            }
            if (!(unit.header().origin() instanceof Origin.DeclaredOrigin declared)) {
                continue;
            }
            guru.interlis.source.SourceText text = sourceSet.sources().get(declared.range().source());
            if (text == null) {
                continue;
            }
            int start = Math.max(0, declared.range().startOffset());
            int end = Math.min(text.text().length(), declared.range().endOffset() + 1);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\\[([A-Za-z_][A-Za-z0-9_]*)\\]")
                    .matcher(text.text().substring(start, end));
            if (matcher.find() && matcher.group(1).equals(alias)) {
                return Optional.of(unit.id());
            }
        }
        return Optional.empty();
    }

    private List<UnitSymbol> unitsOf(ModelSymbol model) {
        if (!(model.header().origin() instanceof Origin.DeclaredOrigin declared)) {
            return List.of();
        }
        return symbols.values().stream()
                .filter(UnitSymbol.class::isInstance)
                .map(UnitSymbol.class::cast)
                .filter(unit -> unit.header().origin() instanceof Origin.DeclaredOrigin origin
                        && origin.range().source().equals(declared.range().source())
                        && origin.range().startOffset() >= declared.range().startOffset()
                        && origin.range().endOffset() <= declared.range().endOffset())
                .sorted(java.util.Comparator.comparingInt(unit ->
                        ((Origin.DeclaredOrigin) unit.header().origin()).range().startOffset()))
                .toList();
    }

    private List<String> modelTokens(ModelSymbol model, Map<String, String> names,
                                      Set<String> symbolNames, boolean targetSide) {
        if (!(model.header().origin() instanceof Origin.DeclaredOrigin declared)) {
            return List.of();
        }
        guru.interlis.source.SourceId sourceId = declared.range().source();
        guru.interlis.source.SourceText text = sourceSet.sources().get(sourceId);
        if (text == null) {
            return List.of();
        }
        String full = text.text();
        int start = Math.min(declared.range().startOffset(), full.length());
        int end = Math.min(declared.range().endOffset() + 1, full.length());
        if (start >= end) {
            return List.of();
        }
        guru.interlis.compiler.internal.antlr.InterlisLexer lexer =
                new guru.interlis.compiler.internal.antlr.InterlisLexer(
                        org.antlr.v4.runtime.CharStreams.fromString(full.substring(start, end)));
        List<String> tokens = new ArrayList<>();
        boolean body = false;
        boolean imports = false;
        int previousType = -1;
        int previousPreviousType = -1;
        for (org.antlr.v4.runtime.Token token : lexer.getAllTokens()) {
            if (token.getChannel() != org.antlr.v4.runtime.Token.DEFAULT_CHANNEL) {
                continue;
            }
            int type = token.getType();
            if (type == guru.interlis.compiler.internal.antlr.InterlisLexer.EOF) {
                continue;
            }
            if (!body) {
                if (type == guru.interlis.compiler.internal.antlr.InterlisLexer.EQ) {
                    body = true;
                }
                continue;
            }
            if (type == guru.interlis.compiler.internal.antlr.InterlisLexer.IMPORTS) {
                imports = true;
            }
            if (imports) {
                if (type == guru.interlis.compiler.internal.antlr.InterlisLexer.SEMI) {
                    imports = false;
                }
                continue;
            }
            boolean interlisUnit = type == guru.interlis.compiler.internal.antlr.InterlisLexer.Name
                    && previousType == guru.interlis.compiler.internal.antlr.InterlisLexer.DOT
                    && previousPreviousType
                    == guru.interlis.compiler.internal.antlr.InterlisLexer.INTERLIS;
            if (type == guru.interlis.compiler.internal.antlr.InterlisLexer.Name) {
                String name = token.getText();
                if (interlisUnit) {
                    tokens.add("ID:" + name);
                } else if (targetSide) {
                    String mapped = names.get(name);
                    tokens.add(mapped != null ? "ID:" + mapped : "ID:*");
                } else {
                    tokens.add(symbolNames.contains(name) ? "ID:" + name : "ID:*");
                }
            } else if (type == guru.interlis.compiler.internal.antlr.InterlisLexer.UUIDOID) {
                tokens.add("ID:" + names.getOrDefault(token.getText(), token.getText()));
            } else {
                tokens.add(type + ":" + token.getText());
            }
            previousPreviousType = previousType;
            previousType = type;
        }
        return canonicalizeDepends(tokens);
    }

    private List<String> canonicalizeDepends(List<String> tokens) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            result.add(token);
            if (token.endsWith(":DEPENDS")) {
                int end = i + 1;
                while (end < tokens.size() && !tokens.get(end).endsWith(":SEMI")) {
                    end++;
                }
                List<String> clause = new ArrayList<>(tokens.subList(i + 1, end));
                clause.sort(String::compareTo);
                result.addAll(clause);
                i = end - 1;
            }
        }
        return result;
    }

    private Map<SymbolId, SymbolId> correspondence(Symbol source, Symbol target) {
        Map<SymbolId, SymbolId> map = new LinkedHashMap<>();
        if (!correspondenceInto(source, target, map)) {
            return Map.of();
        }
        return map;
    }

    private boolean correspondenceInto(Symbol source, Symbol target,
                                       Map<SymbolId, SymbolId> map) {
        if (source.kind() != target.kind()) {
            return false;
        }
        map.put(source.id(), target.id());
        if (source instanceof ModelSymbol sourceModel && target instanceof ModelSymbol targetModel) {
            return childCorrespondence(sourceModel.members(), targetModel.members(), map);
        }
        if (source instanceof TopicSymbol sourceTopic && target instanceof TopicSymbol targetTopic) {
            return childCorrespondence(sourceTopic.members(), targetTopic.members(), map);
        }
        if (source instanceof ClassSymbol sourceClass && target instanceof ClassSymbol targetClass) {
            if (!childCorrespondence(sourceClass.members(), targetClass.members(), map)) {
                return false;
            }
            return constraintCorrespondence(sourceClass.constraints(), targetClass.constraints(), map);
        }
        if (source instanceof StructureSymbol sourceStructure
                && target instanceof StructureSymbol targetStructure) {
            if (!childCorrespondence(sourceStructure.members(), targetStructure.members(), map)) {
                return false;
            }
            return constraintCorrespondence(sourceStructure.constraints(),
                    targetStructure.constraints(), map);
        }
        if (source instanceof AssociationSymbol sourceAssociation
                && target instanceof AssociationSymbol targetAssociation) {
            if (!childCorrespondence(sourceAssociation.roles(), targetAssociation.roles(), map)
                    || !childCorrespondence(sourceAssociation.attributes(),
                    targetAssociation.attributes(), map)) {
                return false;
            }
            return constraintCorrespondence(sourceAssociation.constraints(),
                    targetAssociation.constraints(), map);
        }
        return true;
    }

    private boolean childCorrespondence(List<SymbolId> sourceIds, List<SymbolId> targetIds,
                                        Map<SymbolId, SymbolId> map) {
        if (sourceIds.size() != targetIds.size()) {
            return false;
        }
        for (int i = 0; i < sourceIds.size(); i++) {
            Symbol source = symbols.get(sourceIds.get(i));
            Symbol target = symbols.get(targetIds.get(i));
            if (source == null || target == null || !correspondenceInto(source, target, map)) {
                return false;
            }
        }
        return true;
    }

    private boolean constraintCorrespondence(List<SymbolId> sourceIds, List<SymbolId> targetIds,
                                             Map<SymbolId, SymbolId> map) {
        if (sourceIds.size() != targetIds.size()) {
            return false;
        }
        for (int i = 0; i < sourceIds.size(); i++) {
            map.put(sourceIds.get(i), targetIds.get(i));
        }
        return true;
    }

    private void compareCorrespondingConstraints(SymbolId sourceId, SymbolId targetId,
                                                Map<SymbolId, SymbolId> reverse,
                                                DeclInfo translationDecl,
                                                Map<SymbolId, SymbolId> correspondence) {
        compareConstraints(sourceId, targetId, reverse, translationDecl);
        Symbol source = symbols.get(sourceId);
        Symbol target = symbols.get(targetId);
        if (source != null && target != null) {
            compareSymbolReferences(source, target, correspondence, translationDecl);
        }
        List<SymbolId> sourceChildren;
        List<SymbolId> targetChildren;
        if (source instanceof TopicSymbol sourceTopic && target instanceof TopicSymbol targetTopic) {
            sourceChildren = sourceTopic.members();
            targetChildren = targetTopic.members();
        } else if (source instanceof ClassSymbol sourceClass && target instanceof ClassSymbol targetClass) {
            sourceChildren = sourceClass.members();
            targetChildren = targetClass.members();
        } else if (source instanceof StructureSymbol sourceStructure
                && target instanceof StructureSymbol targetStructure) {
            sourceChildren = sourceStructure.members();
            targetChildren = targetStructure.members();
        } else if (source instanceof AssociationSymbol sourceAssociation
                && target instanceof AssociationSymbol targetAssociation) {
            sourceChildren = new ArrayList<>(sourceAssociation.roles());
            sourceChildren.addAll(sourceAssociation.attributes());
            targetChildren = new ArrayList<>(targetAssociation.roles());
            targetChildren.addAll(targetAssociation.attributes());
        } else {
            return;
        }
        for (int i = 0; i < Math.min(sourceChildren.size(), targetChildren.size()); i++) {
            SymbolId expectedTarget = correspondence.get(sourceChildren.get(i));
            SymbolId actualTarget = targetChildren.get(i);
            if (expectedTarget != null && expectedTarget.equals(actualTarget)) {
                compareCorrespondingConstraints(sourceChildren.get(i), actualTarget, reverse,
                        translationDecl, correspondence);
            }
        }
    }

    private void compareSymbolReferences(Symbol source, Symbol target,
                                         Map<SymbolId, SymbolId> correspondence,
                                         DeclInfo translationDecl) {
        if (source instanceof ClassSymbol sourceClass && target instanceof ClassSymbol targetClass) {
            if (!sameMapped(sourceClass.baseType(), targetClass.baseType(), correspondence)) {
                emitRule(translationDecl, "translation class base differs",
                        "TRANSLATION_REFERENCE");
            }
            for (SymbolId member : sourceClass.members()) {
                SymbolId mapped = correspondence.get(member);
                if (mapped != null) {
                    Symbol sourceMember = symbols.get(member);
                    Symbol targetMember = symbols.get(mapped);
                    if (sourceMember instanceof AttributeSymbol sourceAttribute
                            && targetMember instanceof AttributeSymbol targetAttribute) {
                        if (!sameMappedType(sourceAttribute.declaredType(),
                                targetAttribute.declaredType(), correspondence)) {
                            emitRule(translationDecl, "translation attribute type target differs",
                                    "TRANSLATION_REFERENCE");
                        }
                    }
                }
            }
        }
        if (source instanceof StructureSymbol sourceStructure
                && target instanceof StructureSymbol targetStructure) {
            if (!sameMapped(sourceStructure.baseType(), targetStructure.baseType(), correspondence)) {
                emitRule(translationDecl, "translation structure base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof TopicSymbol sourceTopic && target instanceof TopicSymbol targetTopic) {
            if (!sameMapped(sourceTopic.baseTopic(), targetTopic.baseTopic(), correspondence)) {
                emitRule(translationDecl, "translation topic base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof RoleSymbol sourceRole && target instanceof RoleSymbol targetRole) {
            if (!sameMapped(Optional.of(sourceRole.target()), Optional.of(targetRole.target()),
                    correspondence)) {
                emitRule(translationDecl, "translation role target differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof UnitSymbol sourceUnit && target instanceof UnitSymbol targetUnit) {
            if (!sameMapped(sourceUnit.base(), targetUnit.base(), correspondence)) {
                emitRule(translationDecl, "translation unit base differs",
                        "TRANSLATION_REFERENCE");
            }
        }
        if (source instanceof DomainSymbol sourceDomain
                && target instanceof DomainSymbol targetDomain) {
            if (!sameMapped(sourceDomain.baseDomain(), targetDomain.baseDomain(),
                    correspondence)) {
                emitRule(translationDecl, "translation domain base differs",
                        "TRANSLATION_REFERENCE");
            }
            if (!sameMappedType(sourceDomain.typeId(), targetDomain.typeId(),
                    correspondence)) {
                emitRule(translationDecl, "translation domain type differs",
                        "TRANSLATION_REFERENCE");
            }
        }
    }

    private boolean sameMapped(Optional<SymbolId> source, Optional<SymbolId> target,
                               Map<SymbolId, SymbolId> correspondence) {
        if (source.isEmpty() || target.isEmpty()) {
            return source.isEmpty() && target.isEmpty();
        }
        SymbolId mapped = correspondence.get(source.orElseThrow());
        return mapped != null && mapped.equals(target.orElseThrow());
    }

    private boolean sameMappedType(TypeId source, TypeId target,
                                   Map<SymbolId, SymbolId> correspondence) {
        TypeDescriptor sourceType = types.get(source);
        TypeDescriptor targetType = types.get(target);
        if (sourceType instanceof ReferenceType sourceReference
                && targetType instanceof ReferenceType targetReference) {
            return sameMapped(Optional.of(sourceReference.target()),
                    Optional.of(targetReference.target()), correspondence);
        }
        if (sourceType instanceof StructureValueType sourceStructure
                && targetType instanceof StructureValueType targetStructure) {
            return sameMapped(Optional.of(sourceStructure.target()),
                    Optional.of(targetStructure.target()), correspondence);
        }
        if (sourceType instanceof DomainReferenceType sourceDomain
                && targetType instanceof DomainReferenceType targetDomain) {
            return sameMapped(Optional.of(sourceDomain.targetDomain()),
                    Optional.of(targetDomain.targetDomain()), correspondence);
        }
        if (sourceType instanceof CollectionType sourceCollection
                && targetType instanceof CollectionType targetCollection) {
            return sameMappedType(sourceCollection.elementType(), targetCollection.elementType(),
                    correspondence);
        }
        if (sourceType instanceof GeometryType sourceGeometry
                && targetType instanceof GeometryType targetGeometry) {
            return sameMapped(sourceGeometry.vertexDomain(), targetGeometry.vertexDomain(),
                    correspondence);
        }
        if (sourceType instanceof CoordinateType sourceCoordinate
                && targetType instanceof CoordinateType targetCoordinate) {
            if (sourceCoordinate.dimensions() != targetCoordinate.dimensions()
                    || !sourceCoordinate.referenceSystem().equals(
                    targetCoordinate.referenceSystem())) {
                return false;
            }
            for (int i = 0; i < sourceCoordinate.axes().size(); i++) {
                NumericRange sourceAxis = sourceCoordinate.axes().get(i);
                NumericRange targetAxis = targetCoordinate.axes().get(i);
                if (!sourceAxis.minimum().equals(targetAxis.minimum())
                        || !sourceAxis.maximum().equals(targetAxis.maximum())
                        || !sourceAxis.unit().equals(targetAxis.unit())
                        || !sourceAxis.referenceSystem().equals(targetAxis.referenceSystem())
                        || sourceAxis.direction() != targetAxis.direction()) {
                    return false;
                }
            }
            return true;
        }
        return true;
    }

    private void compareConstraints(SymbolId sourceMember, SymbolId targetMember,
                                    Map<SymbolId, SymbolId> reverse, DeclInfo translationDecl) {
        Symbol source = symbols.get(sourceMember);
        Symbol target = symbols.get(targetMember);
        List<SymbolId> sourceConstraints = constraintsOf(source);
        List<SymbolId> targetConstraints = constraintsOf(target);
        if (sourceConstraints.size() != targetConstraints.size()) {
            emitRule(translationDecl, "translation constraint count differs",
                    "TRANSLATION_CONSTRAINT");
            return;
        }
        for (int i = 0; i < sourceConstraints.size(); i++) {
            if (!(symbols.get(sourceConstraints.get(i)) instanceof ConstraintSymbol sourceConstraint)
                    || !(symbols.get(targetConstraints.get(i)) instanceof ConstraintSymbol targetConstraint)) {
                continue;
            }
            List<SymbolId> sourceTargets = expressionReferenceTargets(
                    constraintExpression(sourceConstraint.definition()));
            List<SymbolId> targetTargets = expressionReferenceTargets(
                    constraintExpression(targetConstraint.definition()));
            List<SymbolId> mappedTargets = targetTargets.stream()
                    .map(reverse::get).toList();
            if (!sourceTargets.equals(mappedTargets)) {
                emitRule(translationDecl, "translation constraint references differ",
                        "TRANSLATION_REFERENCE");
            }
        }
    }

    private List<SymbolId> constraintsOf(Symbol symbol) {
        if (symbol instanceof ClassSymbol clazz) {
            return clazz.constraints();
        }
        if (symbol instanceof StructureSymbol structure) {
            return structure.constraints();
        }
        if (symbol instanceof AssociationSymbol association) {
            return association.constraints();
        }
        return List.of();
    }

    private guru.interlis.model.ExpressionId constraintExpression(
            ConstraintDefinition definition) {
        if (definition instanceof MandatoryConstraint mandatory) {
            return mandatory.condition();
        }
        if (definition instanceof DomainConstraint domain) {
            return domain.condition();
        }
        if (definition instanceof PlausibilityConstraint plausibility) {
            return plausibility.condition();
        }
        if (definition instanceof guru.interlis.model.SetConstraint set) {
            return set.condition();
        }
        if (definition instanceof UniquenessConstraint uniqueness) {
            return uniqueness.where().orElse(null);
        }
        return null;
    }

    private List<SymbolId> expressionReferenceTargets(ExpressionId id) {
        if (id == null) {
            return List.of();
        }
        TypedExpression expression = expressions.get(id);
        if (expression == null) {
            return List.of();
        }
        List<SymbolId> targets = new ArrayList<>();
        if (expression instanceof guru.interlis.model.PathExpression path) {
            for (guru.interlis.model.PathStep step : path.path().steps()) {
                if (step instanceof guru.interlis.model.PathStep.AttributeStep attribute) {
                    targets.add(attribute.target());
                } else if (step instanceof guru.interlis.model.PathStep.RoleStep role) {
                    targets.add(role.target());
                }
            }
        }
        for (ExpressionId child : expression.children()) {
            targets.addAll(expressionReferenceTargets(child));
        }
        return targets;
    }

    private String modelShape(ModelSymbol model) {
        StringBuilder shape = new StringBuilder()
                .append("M[").append(model.modelKind()).append(':')
                .append(model.contracted()).append(':').append(model.imports().stream()
                        .map(ModelImport::unqualified).toList()).append(']');
        for (SymbolId member : model.members()) {
            shape.append(memberShape(member)).append(';');
        }
        return shape.toString();
    }

    private String memberShape(SymbolId id) {
        Symbol symbol = symbols.get(id);
        if (symbol instanceof TopicSymbol topic) {
            StringBuilder shape = new StringBuilder("T[");
            shape.append(topic.header().modifiers()).append(':')
                    .append(topic.header().oidRule().isPresent()).append(':')
                    .append(topic.baseTopic().isPresent()).append(':')
                    .append(topic.dependencies().size()).append(':');
            for (SymbolId member : topic.members()) {
                shape.append(memberShape(member)).append(',');
            }
            return shape.append(']').toString();
        }
        if (symbol instanceof ClassSymbol clazz) {
            return "C[" + clazz.baseType().map(this::classSignature).orElse("-") + ":"
                    + clazz.constraints().size() + ":" + clazz.header().modifiers() + ":"
                    + clazz.header().oidRule().isPresent() + ":"
                    + memberShapes(clazz.members()) + ":"
                    + constraintShapes(clazz.constraints()) + "]";
        }
        if (symbol instanceof StructureSymbol structure) {
            return "S[" + structure.baseType().map(this::classSignature).orElse("-") + ":"
                    + structure.constraints().size() + ":" + structure.header().modifiers() + ":"
                    + memberShapes(structure.members()) + ":"
                    + constraintShapes(structure.constraints()) + "]";
        }
        if (symbol instanceof AssociationSymbol association) {
            return "A[" + association.roles().size() + ":" + association.attributes().size() + ":"
                    + association.constraints().size() + ":"
                    + association.header().modifiers() + ":"
                    + association.header().oidRule().isPresent() + ":"
                    + constraintShapes(association.constraints()) + "]";
        }
        if (symbol instanceof AttributeSymbol attribute) {
            return "a[" + typeShape(attribute.declaredType()) + ":" + attribute.mandatory()
                    + ":" + attribute.transientAttribute() + ":" + attribute.continuous()
                    + ":" + attribute.subdivision() + ":" + attribute.header().modifiers() + "]";
        }
        if (symbol instanceof RoleSymbol role) {
            return "r[" + role.cardinality().lower() + ":" + role.cardinality().upper() + ":"
                    + role.aggregation() + ":" + role.external() + ":"
                    + role.ordered() + ":" + role.hiding() + ":"
                    + role.header().modifiers() + ":" + classSignature(role.target()) + "]";
        }
        if (symbol instanceof DomainSymbol domain) {
            return "d[" + typeShape(domain.typeId()) + ":"
                    + domain.baseDomain().map(baseId -> symbols.get(baseId) instanceof DomainSymbol base
                    ? typeShape(base.typeId(), 1) : "?").orElse("-") + ":"
                    + domain.generic() + ":" + domain.header().modifiers() + "]";
        }
        if (symbol instanceof UnitSymbol unit) {
            return "u[" + unit.abstractUnit() + ":" + unit.base().isPresent() + ":"
                    + unitDefinitionShape(unit.definition().orElse("")) + "]";
        }
        if (symbol instanceof FunctionSymbol function) {
            return "f[" + function.parameters().stream()
                    .map(parameter -> parameter.typeName()).toList()
                    + ":" + function.returnType() + "]";
        }
        if (symbol instanceof ViewSymbol view) {
            return "v[" + view.formation().isPresent() + ":" + view.body().isPresent()
                    + ":" + viewShape(view.formation().orElse(""))
                    + ":" + viewBodyShape(view.body().orElse("")) + "]";
        }
        if (symbol instanceof ConstraintSymbol constraint) {
            return "k[" + constraintShape(constraint.definition()) + "]";
        }
        return "?";
    }

    private String constraintShape(ConstraintDefinition definition) {
        if (definition instanceof MandatoryConstraint mandatory) {
            return "MAND:" + expressionShape(mandatory.condition());
        }
        if (definition instanceof DomainConstraint domain) {
            return "DOM:" + expressionShape(domain.condition());
        }
        if (definition instanceof PlausibilityConstraint plausibility) {
            return "PLAUS:" + plausibility.direction() + ":" + plausibility.percentage()
                    + ":" + expressionShape(plausibility.condition());
        }
        if (definition instanceof UniquenessConstraint uniqueness) {
            return "UNIQ:" + uniqueness.where().map(this::expressionShape).orElse("-")
                    + ":" + uniqueness.keys().size() + ":" + uniqueness.localKeys().size();
        }
        if (definition instanceof guru.interlis.model.SetConstraint set) {
            return "SET:" + set.scope() + ":" + set.where().map(this::expressionShape).orElse("-")
                    + ":" + expressionShape(set.condition());
        }
        if (definition instanceof guru.interlis.model.ExistenceConstraint existence) {
            return "EXIST:" + existence.source().steps().size() + ":"
                    + existence.targets().size();
        }
        return definition.getClass().getSimpleName();
    }

    private String expressionShape(ExpressionId id) {
        TypedExpression expression = expressions.get(id);
        if (expression == null) {
            return "?";
        }
        String type = typeShape(expression.typeId(), 1) + "|";
        if (expression instanceof guru.interlis.model.LiteralExpression literal) {
            return type + "L:" + literal.literalKind() + ":" + literal.lexeme();
        }
        if (expression instanceof guru.interlis.model.BinaryExpression binary) {
            return type + "B:" + binary.operator() + "(" + expressionShape(binary.left()) + ","
                    + expressionShape(binary.right()) + ")";
        }
        if (expression instanceof guru.interlis.model.UnaryExpression unary) {
            return type + "U:" + unary.operator() + "(" + expressionShape(unary.operand()) + ")";
        }
        if (expression instanceof guru.interlis.model.DefinedExpression defined) {
            return type + "D(" + expressionShape(defined.operand()) + ")";
        }
        if (expression instanceof guru.interlis.model.FunctionCallExpression call) {
            StringBuilder children = new StringBuilder();
            for (ExpressionId child : call.children()) {
                children.append(expressionShape(child)).append(',');
            }
            return type + "F:" + call.functionName() + "[" + children + "]";
        }
        if (expression instanceof guru.interlis.model.PathExpression path) {
            StringBuilder steps = new StringBuilder();
            for (guru.interlis.model.PathStep step : path.path().steps()) {
                steps.append(pathStepShape(step)).append(',');
            }
            return type + "P:" + steps;
        }
        if (expression instanceof guru.interlis.model.ConstantExpression constant) {
            return type + "C:" + constant.constantName();
        }
        if (expression instanceof guru.interlis.model.UndefinedExpression) {
            return type + "UNDEF";
        }
        if (expression instanceof guru.interlis.model.ErrorExpression) {
            return type + "ERR";
        }
        return type + expression.getClass().getSimpleName();
    }

    private String pathStepShape(guru.interlis.model.PathStep step) {
        if (step instanceof guru.interlis.model.PathStep.AttributeStep attribute) {
            return "A:" + (symbols.get(attribute.target()) instanceof AttributeSymbol targetAttribute
                    ? typeShape(targetAttribute.declaredType(), 1) : "?");
        }
        if (step instanceof guru.interlis.model.PathStep.RoleStep role) {
            return "R:" + classSignature(role.target());
        }
        return step.getClass().getSimpleName();
    }

    private String unitDefinitionShape(String definition) {
        int digits = definition.replaceAll("[^0-9]", "").length();
        int mult = count(definition, "*");
        int div = count(definition, "/");
        int pow = count(definition, "^");
        int brackets = count(definition, "[");
        return "d" + digits + "m" + mult + "d" + div + "p" + pow + "b" + brackets;
    }

    private String viewBodyShape(String body) {
        String upper = body.toUpperCase(java.util.Locale.ROOT);
        return "A" + count(upper, "ATTRIBUTE") + "O" + count(upper, "ALL OF")
                + "W" + count(upper, "WHERE") + "C" + count(upper, "CONSTRAINT")
                + "X" + count(upper, "EXTENDS") + "B" + count(upper, "BASE")
                + "T" + count(upper, "TRANSIENT") + "S" + count(upper, "SELECTION")
                + "N" + count(upper, "OR NULL") + ":" + count(body, ":=")
                + ":" + count(body, ";") + ":" + count(body, "->");
    }

    private String typeShape(TypeId id) {
        return typeShape(id, 2);
    }

    private String typeShape(TypeId id, int depth) {
        TypeDescriptor type = types.get(id);
        if (type == null) {
            return "?";
        }
        if (depth <= 0) {
            return type.kind().name();
        }
        if (type instanceof TextType text) {
            return "TEXT:" + text.textKind() + ":" + text.maxLength();
        }
        if (type instanceof NumericType numeric) {
            return "NUM:" + numeric.range().minimum() + ":" + numeric.range().maximum()
                    + ":" + numeric.range().circular() + ":" + numeric.range().direction()
                    + ":" + numeric.range().unit().isPresent() + ":"
                    + numeric.range().referenceSystem();
        }
        if (type instanceof EnumerationType enumeration) {
            return "ENUM:" + enumeration.values().size() + ":" + enumeration.ordered()
                    + ":" + enumeration.circular();
        }
        if (type instanceof CollectionType collection) {
            return "COL:" + collection.collectionKind() + ":" + collection.cardinality()
                    + ":" + typeShape(collection.elementType(), depth - 1);
        }
        if (type instanceof ReferenceType reference) {
            return "REF:" + reference.external() + ":" + classSignature(reference.target());
        }
        if (type instanceof DomainReferenceType reference) {
            Symbol target = symbols.get(reference.targetDomain());
            if (target instanceof DomainSymbol domain) {
                return "DREF:" + typeShape(domain.typeId(), depth - 1);
            }
            return "DREF";
        }
        if (type instanceof StructureValueType structure) {
            return "STRUCT:" + classSignature(structure.target());
        }
        if (type instanceof ClassValueType classValue) {
            return "CLASSVAL:" + classValue.structureValue() + ":"
                    + classValue.restrictions().size();
        }
        if (type instanceof FormattedType formatted) {
            return "FMT:" + formatted.formatName() + ":" + formatted.minimum()
                    + ":" + formatted.maximum();
        }
        if (type instanceof GeometryType geometry) {
            return "GEO:" + geometry.geometryKind() + ":" + geometry.lineForm().isPresent();
        }
        if (type instanceof CoordinateType coordinate) {
            StringBuilder axes = new StringBuilder();
            for (NumericRange axis : coordinate.axes()) {
                axes.append(axis.minimum()).append(':').append(axis.maximum()).append(':')
                        .append(axis.unit()).append(':').append(axis.referenceSystem()).append(':')
                        .append(axis.direction()).append(',');
            }
            return "COORD:" + coordinate.dimensions() + ":" + coordinate.referenceSystem()
                    + ":" + axes;
        }
        if (type instanceof guru.interlis.model.OidType oid) {
            return "OID:" + oid.oidKind();
        }
        return type.kind().name();
    }

    private String classSignature(SymbolId id) {
        Symbol symbol = symbols.get(id);
        if (!(symbol instanceof ClassSymbol clazz)) {
            return "?";
        }
        StringBuilder shape = new StringBuilder("C").append(clazz.members().size()).append('[');
        for (SymbolId member : clazz.members()) {
            if (symbols.get(member) instanceof AttributeSymbol attribute) {
                shape.append(typeShape(attribute.declaredType(), 1)).append(',');
            }
        }
        return shape.append(']').toString();
    }

    private String viewShape(String formation) {
        String normalized = formation.toUpperCase(java.util.Locale.ROOT);
        int joins = count(normalized, "JOIN");
        int unions = count(normalized, "UNION");
        int projections = count(normalized, "PROJECTION");
        int inspections = count(normalized, "INSPECTION");
        int paths = count(normalized, "->");
        int equalities = count(normalized, "==");
        int extended = count(normalized, "EXTENDS");
        int base = count(normalized, "BASE");
        int transientCount = count(normalized, "TRANSIENT");
        int abstractCount = count(normalized, "ABSTRACT");
        int selection = count(normalized, "SELECTION");
        int constraints = count(normalized, "CONSTRAINT");
        return "J" + joins + "U" + unions + "P" + projections + "I" + inspections
                + "X" + extended + "B" + base + "T" + transientCount + "A" + abstractCount
                + "S" + selection + "C" + constraints + "->" + paths + "==" + equalities;
    }

    private int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private String constraintShapes(List<SymbolId> constraints) {
        StringBuilder shape = new StringBuilder();
        for (SymbolId id : constraints) {
            if (symbols.get(id) instanceof ConstraintSymbol constraint) {
                shape.append(constraintShape(constraint.definition())).append(',');
            }
        }
        return shape.toString();
    }

    private String memberShapes(List<SymbolId> members) {
        StringBuilder shape = new StringBuilder();
        for (SymbolId member : members) {
            shape.append(memberShape(member)).append(',');
        }
        return shape.toString();
    }

    private void validateClassExtensions() {
        Set<SymbolId> extendedBases = new LinkedHashSet<>();
        Map<SymbolId, List<SymbolId>> subclasses = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            subclasses.computeIfAbsent(base, ignored -> new ArrayList<>()).add(clazz.id());
            Symbol baseSymbol = symbols.get(base);
            if (baseSymbol != null && baseSymbol.header().modifiers()
                    .contains(guru.interlis.model.Modifier.EXTENDED)) {
                extendedBases.add(base);
            }
        }
        Map<String, SymbolId> extendedByTopic = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            if (symbol instanceof ClassSymbol clazz
                    && clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                SymbolId topic = scopeRoot(clazz.id());
                if (topic != null) {
                    extendedByTopic.put(topic + ":" + clazz.name().value(), clazz.id());
                }
            }
        }
        for (Symbol symbol : symbols.values()) {
            if (!(symbol instanceof ClassSymbol clazz) || clazz.baseType().isEmpty()) {
                continue;
            }
            SymbolId base = clazz.baseType().orElseThrow();
            Symbol baseSymbol = symbols.get(base);
            SymbolId topic = scopeRoot(clazz.id());
            if (baseSymbol != null && topic != null) {
                SymbolId localExtended = extendedByTopic.get(topic + ":" + baseSymbol.name().value());
                if (localExtended != null && !localExtended.equals(base)) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "subclass must extend the local EXTENDED class",
                                    "EXTENDED_BASE_CLASS"));
                } else if (localExtended != null
                        && symbols.get(topic) instanceof TopicSymbol topicSymbol
                        && topicSymbol.baseTopic().isEmpty()) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "EXTENDED class may only be extended in a derived topic",
                                    "EXTENDED_BASE_CLASS"));
                }
            }
        }
        for (Symbol symbol : symbols.values()) {
            if (!(symbol instanceof ClassSymbol clazz)
                    || !clazz.header().modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                continue;
            }
            SymbolId topic = scopeRoot(clazz.id());
            if (!(symbols.get(topic) instanceof TopicSymbol topicSymbol)
                    || topicSymbol.baseTopic().isEmpty()) {
                continue;
            }
            SymbolId baseTopic = topicSymbol.baseTopic().orElseThrow();
            for (DeclInfo baseChild : index.children(baseTopic)) {
                boolean specialized = index.children(baseTopic).stream()
                        .filter(child -> child.kind() == SymbolKind.CLASS)
                        .anyMatch(child -> baseOf.get(child.id()) != null
                                && baseOf.get(child.id()).equals(baseChild.id()));
                if (baseChild.kind() == SymbolKind.CLASS
                        && baseChild.name().equals(clazz.name().value()) && specialized) {
                    index.byId(clazz.id()).ifPresent(decl ->
                            emitRule(decl, "EXTENDED class conflicts with a specialized base class",
                                    "EXTENDED_CLASS_CONFLICT"));
                }
            }
        }
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
                if (baseAttribute.transientAttribute() != attribute.transientAttribute()) {
                    emitRule(attributeDecl, "extended attribute changes TRANSIENT mode",
                            "ATTRIBUTE_OVERRIDE_TRANSIENT");
                }
                TypeDescriptor baseType = effectiveDescriptor(baseAttribute.declaredType());
                TypeDescriptor derivedType = effectiveDescriptor(attribute.declaredType());
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
        if (base != null && derived != null && base.kind() == derived.kind()
                && (base instanceof EnumerationType || base instanceof CoordinateType
                || base instanceof NumericType || base instanceof FormattedType
                || base instanceof guru.interlis.model.ClassValueType)) {
            return null;
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
        Map<SymbolId, Set<String>> classAttributes = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            if (symbol instanceof ClassSymbol clazz) {
                Set<String> names = new LinkedHashSet<>();
                SymbolId current = clazz.id();
                int guard = 0;
                while (current != null && guard++ < 64) {
                    Symbol currentSymbol = symbols.get(current);
                    if (currentSymbol instanceof ClassSymbol currentClass) {
                        for (SymbolId member : currentClass.members()) {
                            Symbol memberSymbol = symbols.get(member);
                            if (memberSymbol instanceof AttributeSymbol attribute) {
                                names.add(attribute.name().value());
                            }
                        }
                    }
                    current = baseOf.get(current);
                }
                classAttributes.put(clazz.id(), names);
            }
        }
        for (Symbol symbol : symbols.values()) {
            if (symbol instanceof AssociationSymbol association) {
                Set<String> roleNames = new LinkedHashSet<>();
                for (SymbolId roleId : association.roles()) {
                    Symbol role = symbols.get(roleId);
                    if (role != null) {
                        roleNames.add(role.name().value());
                    }
                }
                for (SymbolId memberId : association.attributes()) {
                    Symbol member = symbols.get(memberId);
                    if (member != null && roleNames.contains(member.name().value())) {
                        DeclInfo associationDecl = index.byId(association.id()).orElse(null);
                        if (associationDecl != null) {
                            emitRule(associationDecl,
                                    "role and attribute share a name in the association",
                                    "ROLE_ATTRIBUTE_CONFLICT");
                        }
                    }
                }
            }
        }
        Map<SymbolId, Set<String>> topicRoleNames = new LinkedHashMap<>();
        Map<SymbolId, Set<String>> topicAttributeNames = new LinkedHashMap<>();
        for (Symbol symbol : symbols.values()) {
            if (symbol instanceof AssociationSymbol association) {
                SymbolId scope = scopeRoot(association.id());
                if (scope != null && !association.name().value().startsWith("<anonymous")) {
                    for (SymbolId roleId : association.roles()) {
                        Symbol role = symbols.get(roleId);
                        if (role != null) {
                            topicRoleNames.computeIfAbsent(scope, ignored -> new LinkedHashSet<>())
                                    .add(role.name().value());
                        }
                    }
                }
            } else if (symbol instanceof ClassSymbol clazz) {
                SymbolId scope = scopeRoot(clazz.id());
                if (scope != null) {
                    for (SymbolId member : clazz.members()) {
                        Symbol memberSymbol = symbols.get(member);
                        if (memberSymbol instanceof AttributeSymbol attribute) {
                            topicAttributeNames.computeIfAbsent(scope,
                                    ignored -> new LinkedHashSet<>()).add(attribute.name().value());
                        }
                    }
                }
            }
        }
        for (Map.Entry<SymbolId, Set<String>> entry : topicRoleNames.entrySet()) {
            Set<String> attributes = topicAttributeNames.getOrDefault(entry.getKey(), Set.of());
            for (String roleName : entry.getValue()) {
                if (attributes.contains(roleName)) {
                    DeclInfo scope = index.byId(entry.getKey()).orElse(null);
                    if (scope != null) {
                        emitRule(scope, "role name conflicts with an attribute name in the topic",
                                "ROLE_ATTRIBUTE_CONFLICT");
                    }
                }
            }
        }
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
                if (!syntax.roles().isEmpty() && base.isPresent()
                        && symbols.get(base.orElseThrow()) instanceof AssociationSymbol baseAssociation) {
                    Set<String> baseRoles = baseAssociation.roles().stream()
                            .map(symbols::get).filter(Objects::nonNull)
                            .map(Symbol::name).map(DeclaredName::value)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                    guru.interlis.syntax.AssociationDeclarationSyntax baseSyntax =
                            index.byId(base.orElseThrow())
                                    .filter(baseDecl -> baseDecl.syntax()
                                            instanceof guru.interlis.syntax.AssociationDeclarationSyntax)
                                    .map(baseDecl -> (guru.interlis.syntax.AssociationDeclarationSyntax)
                                            baseDecl.syntax()).orElse(null);
                    for (guru.interlis.syntax.RoleDeclarationSyntax role : syntax.roles()) {
                        if (!baseRoles.contains(role.name())) {
                            emitRule(decl, "extended association must not add roles",
                                    "ASSOCIATION_EXTENDED_ROLE");
                            continue;
                        }
                        if (baseSyntax == null) {
                            continue;
                        }
                        baseSyntax.roles().stream()
                                .filter(baseRole -> baseRole.name().equals(role.name()))
                                .findFirst().ifPresent(baseRole -> {
                                    if (role.cardinality().lower()
                                            .compareTo(baseRole.cardinality().lower()) < 0
                                            || widerUpper(role.cardinality().upper(),
                                            baseRole.cardinality().upper())) {
                                        emitRule(decl, "extended role widens cardinality",
                                                "ROLE_EXTENSION");
                                    }
                                    if (role.aggregation().ordinal()
                                            < baseRole.aggregation().ordinal()) {
                                        emitRule(decl, "extended role weakens aggregation",
                                                "ROLE_EXTENSION");
                                    }
                                });
                    }
                }
            }
            for (SymbolId roleId : ((AssociationSymbol) symbols.get(decl.id())).roles()) {
                Symbol roleSymbol = symbols.get(roleId);
                if (roleSymbol instanceof RoleSymbol role) {
                    for (Map.Entry<SymbolId, Set<String>> entry : classAttributes.entrySet()) {
                        SymbolId classId = entry.getKey();
                        boolean subtype = classId.equals(role.target());
                        SymbolId current = classId;
                        int guard = 0;
                        while (!subtype && current != null && guard++ < 64) {
                            current = baseOf.get(current);
                            subtype = role.target().equals(current);
                        }
                        if (subtype && entry.getValue().contains(role.name().value())) {
                            emitRule(decl, "role name conflicts with an attribute in its target hierarchy",
                                    "ROLE_ATTRIBUTE_CONFLICT");
                            break;
                        }
                    }
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
                    if (memberDecl == null || memberDecl.kind() != SymbolKind.ATTRIBUTE) {
                        continue;
                    }
                    boolean conflict = names.containsKey(memberDecl.name());
                    SymbolId memberTopic = scopeRoot(classSymbol.id());
                    for (AssociationSymbol association : symbols.values().stream()
                            .filter(AssociationSymbol.class::isInstance)
                            .map(AssociationSymbol.class::cast).toList()) {
                        for (SymbolId roleId : association.roles()) {
                            DeclInfo roleDecl = index.byId(roleId).orElse(null);
                            if (roleDecl == null || !roleDecl.name().equals(memberDecl.name())) {
                                continue;
                            }
                            if (memberTopic != null && memberTopic.equals(scopeRoot(roleId))) {
                                conflict = true;
                            }
                        }
                    }
                    for (AssociationSymbol association : symbols.values().stream()
                            .filter(AssociationSymbol.class::isInstance)
                            .map(AssociationSymbol.class::cast).toList()) {
                        for (SymbolId roleId : association.roles()) {
                            if (!(symbols.get(roleId) instanceof RoleSymbol role)
                                    || !role.target().equals(classId)) {
                                continue;
                            }
                            DeclInfo roleDecl = index.byId(roleId).orElse(null);
                            if (roleDecl != null && roleDecl.name().equals(memberDecl.name())) {
                                conflict = true;
                            }
                        }
                    }
                    if (conflict) {
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
            SymbolId firstTopic = scopeRoot(role.id());
            SymbolId secondTopic = scopeRoot(previous.id());
            boolean sharedBase = firstTopic != null && secondTopic != null
                    && !firstTopic.equals(secondTopic)
                    && shareBaseTopic(firstTopic, secondTopic);
            if (!sharedBase) {
                emitRule(roleDecl, "role name conflicts with another role in the same namespace",
                        "ROLE_NAMESPACE_CONFLICT");
            }
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
        Set<String> parameterNames = new LinkedHashSet<>();
        for (guru.interlis.source.SourceText sourceText : sourceSet.sources().values()) {
            java.util.regex.Matcher parameterMatcher = java.util.regex.Pattern
                    .compile("(?i)PARAMETER\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:")
                    .matcher(sourceText.text());
            while (parameterMatcher.find()) {
                if (!parameterNames.add(parameterMatcher.group(1))) {
                    emitRule(decl, "runtime parameter name must be unique",
                            "PARAMETER_NAME_UNIQUE");
                }
            }
        }
        String modelText = declarationText(decl);
        Set<String> genericDomains = new LinkedHashSet<>();
        java.util.regex.Matcher genericMatcher = java.util.regex.Pattern
                .compile("(?i)([A-Za-z_][A-Za-z0-9_]*)\\s*\\(GENERIC\\)")
                .matcher(modelText);
        while (genericMatcher.find()) {
            genericDomains.add(genericMatcher.group(1));
        }
        java.util.regex.Matcher contextMatcher = java.util.regex.Pattern
                .compile("(?is)CONTEXT\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=(.*?)(?:\\bEND\\b|\\bTOPIC\\b|\\bMODEL\\b|$)")
                .matcher(modelText);
        while (contextMatcher.find()) {
            java.util.regex.Matcher entryMatcher = java.util.regex.Pattern
                    .compile("([A-Za-z_][A-Za-z0-9_.]*)\\s*=")
                    .matcher(contextMatcher.group(1));
            while (entryMatcher.find()) {
                String domain = entryMatcher.group(1).contains(".")
                        ? entryMatcher.group(1).substring(entryMatcher.group(1).lastIndexOf('.') + 1)
                        : entryMatcher.group(1);
                if (!genericDomains.contains(domain)
                        && !genericDomains.contains(entryMatcher.group(1))) {
                    emitRule(decl, "CONTEXT domain must be GENERIC", "CONTEXT_GENERIC");
                }
            }
        }
        Set<String> importedModels = new LinkedHashSet<>();
        for (guru.interlis.syntax.ImportSyntax site : modelSyntax.imports()) {
            importedModels.add(site.modelName());
        }
        java.util.regex.Matcher refSystemMatcher = java.util.regex.Pattern
                .compile("(?is)REFSYSTEM\\s+BASKET\\s+[A-Za-z_][A-Za-z0-9_]*\\s*~\\s*([A-Za-z_][A-Za-z0-9_]*)\\.")
                .matcher(modelText);
        while (refSystemMatcher.find()) {
            if (!importedModels.contains(refSystemMatcher.group(1))) {
                emitRule(decl, "REFSYSTEM target model must be imported", "REFSYSTEM_IMPORT");
            }
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
        guru.interlis.syntax.ModelSyntax syntax = entry.model().syntax();
        Optional<guru.interlis.model.ModelTranslation> translation = syntax.translationSource()
                .map(source -> new guru.interlis.model.ModelTranslation(source.text(),
                        syntax.translationVersion(), syntax.language()));
        symbols.put(decl.id(), new ModelSymbol(header(decl), syntax.modelKind(),
                syntax.contracted(), entry.model().version(), syntax.issuer(), imports, members,
                translation));
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
            case UNIT -> buildUnit(decl);
            case FUNCTION -> buildFunction(decl);
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
        if (base.isPresent() && symbols.get(base.orElseThrow()) instanceof TopicSymbol baseTopic) {
            boolean abstractBase = baseTopic.header().modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT);
            for (SymbolId member : baseTopic.members()) {
                if (symbols.get(member) instanceof ClassSymbol baseClass
                        && baseClass.header().modifiers()
                        .contains(guru.interlis.model.Modifier.ABSTRACT)) {
                    abstractBase = true;
                }
            }
            Set<String> baseDomains = new LinkedHashSet<>();
            for (SymbolId member : baseTopic.members()) {
                if (symbols.get(member) instanceof DomainSymbol domain) {
                    baseDomains.add(domain.name().value());
                }
            }
            for (DeclInfo child : index.children(decl.id())) {
                if (child.kind() == SymbolKind.DOMAIN && baseDomains.contains(child.name())) {
                    emitRule(child, "domain name conflicts with an inherited topic member",
                            "INHERITED_NAME_CONFLICT");
                }
            }
            boolean baseHasConcrete = base.flatMap(index::byId).map(baseDecl -> {
                for (DeclInfo child : index.children(baseDecl.id())) {
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
                    && topicSyntax.oidRule().isPresent())
                    || declarationText(decl).toUpperCase(java.util.Locale.ROOT)
                    .contains("DEFERRED");
            if (abstractBase && !derivedHasContent && !baseHasConcrete && !syntax.modifiers()
                    .contains(guru.interlis.model.Modifier.ABSTRACT)) {
                emitRule(decl, "empty topic extending an abstract topic must be abstract",
                        "ABSTRACT_TOPIC_EXTENSION");
            }
            for (DeclInfo child : index.children(decl.id())) {
                if (child.kind() != SymbolKind.CLASS
                        || !(symbols.get(child.id()) instanceof ClassSymbol derivedClass)
                        || derivedClass.baseType().isEmpty()) {
                    continue;
                }
                for (SymbolId member : baseTopic.members()) {
                    if (!(symbols.get(member) instanceof ClassSymbol baseClass)
                            || !baseClass.name().equals(derivedClass.name())) {
                        continue;
                    }
                    if (!child.modifiers().contains(guru.interlis.model.Modifier.EXTENDED)) {
                        emitRule(child, "class in an extending topic may not extend the "
                                + "same-named base class without EXTENDED", "TOPIC_CLASS_EXTENSION");
                    }
                    if (baseClass.header().modifiers()
                            .contains(guru.interlis.model.Modifier.EXTENDED)) {
                        emitRule(child, "cannot extend a class marked EXTENDED",
                                "EXTENDED_BASE_CLASS");
                    }
                }
            }
        }
        List<SymbolId> dependencies = new ArrayList<>();
        for (guru.interlis.syntax.NameSyntax dependency : syntax.dependencies()) {
            Optional<SymbolId> resolved = resolve(decl, dependency,
                    Set.of(SymbolKind.TOPIC, SymbolKind.VIEW));
            if (resolved.isPresent() && symbols.get(resolved.orElseThrow()) instanceof ViewSymbol) {
                emitRule(decl, "TOPIC DEPENDS ON must not target a VIEW",
                        "TOPIC_DEPENDS_ON_VIEW");
            }
            resolved.filter(id -> symbols.get(id) instanceof TopicSymbol)
                    .ifPresent(dependencies::add);
        }
        Set<String> memberNames = new LinkedHashSet<>();
        for (DeclInfo child : index.children(decl.id())) {
            if (child.kind() == SymbolKind.CLASS || child.kind() == SymbolKind.DOMAIN) {
                if (!memberNames.add(child.name())) {
                    emitRule(child, "name conflict in topic", "TOPIC_MEMBER_NAME");
                }
            }
        }
        if (!syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            boolean abstractClass = false;
            boolean concreteClass = false;
            for (DeclInfo child : index.children(decl.id())) {
                if (child.kind() == SymbolKind.CLASS) {
                    if (child.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
                        abstractClass = true;
                    } else {
                        concreteClass = true;
                    }
                }
            }
            if (abstractClass && !concreteClass) {
                emitRule(decl, "abstract class requires an abstract topic or a concrete class",
                        "ABSTRACT_TOPIC");
            }
        }
        List<SymbolId> members = index.children(decl.id()).stream().map(DeclInfo::id).toList();
        return new TopicSymbol(header(decl), base, members, dependencies);
    }

    private ClassSymbol buildClass(DeclInfo decl) {
        ClassDeclarationSyntax syntax = (ClassDeclarationSyntax) decl.syntax();
        if (syntax.endName().isPresent() && !syntax.endName().orElseThrow().equals(syntax.name())) {
            emitRule(decl, "CLASS end name does not match declaration", "CLASS_END_NAME");
        }
        for (DeclInfo child : index.children(decl.id())) {
            buildSymbol(child);
        }
        Optional<SymbolId> base = syntax.baseType().flatMap(name -> {
            Optional<SymbolId> resolved = tryResolve(decl, name, Set.of(SymbolKind.CLASS));
            if (resolved.isEmpty() && name.text().startsWith("INTERLIS.")) {
                return Optional.empty();
            }
            return resolved;
        });
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
        Optional<SymbolId> base = syntax.baseType().flatMap(name -> {
            if (name.text().startsWith("INTERLIS.")) {
                return Optional.empty();
            }
            Optional<SymbolId> resolved = tryResolve(decl, name, Set.of(SymbolKind.STRUCTURE));
            if (resolved.isPresent()) {
                return resolved;
            }
            SymbolId topic = scopeRoot(decl.owner().orElse(decl.id()));
            SymbolId current = topic;
            int guard = 0;
            while (current != null && guard++ < 32) {
                Symbol topicSymbol = symbols.get(current);
                if (!(topicSymbol instanceof TopicSymbol t) || t.baseTopic().isEmpty()) {
                    break;
                }
                SymbolId parent = t.baseTopic().orElseThrow();
                for (DeclInfo child : index.children(parent)) {
                    if (child.kind() == SymbolKind.STRUCTURE
                            && child.name().equals(name.text())) {
                        return Optional.of(child.id());
                    }
                }
                current = parent;
            }
            unresolved(name);
            return Optional.empty();
        });
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
        List<SymbolId> attributes = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.ATTRIBUTE)
                .map(DeclInfo::id).toList();
        if (roles.isEmpty() && attributes.isEmpty() && syntax.baseAssociation().isEmpty()) {
            emitRule(decl, "association requires at least two roles", "ASSOCIATION_ROLES");
        }
        List<SymbolId> constraints = index.children(decl.id()).stream()
                .filter(child -> child.kind() == SymbolKind.CONSTRAINT)
                .map(DeclInfo::id).toList();
        return new AssociationSymbol(header(decl), roles, attributes, constraints);
    }

    private FunctionSymbol buildFunction(DeclInfo decl) {
        FunctionDeclarationSyntax syntax = (FunctionDeclarationSyntax) decl.syntax();
        List<FunctionParameter> parameters = syntax.parameters().stream()
                .map(parameter -> new FunctionParameter(parameter.name(), parameter.typeName()))
                .toList();
        return new FunctionSymbol(header(decl), parameters, syntax.returnType());
    }

    private UnitSymbol buildUnit(DeclInfo decl) {
        UnitDeclarationSyntax syntax = (UnitDeclarationSyntax) decl.syntax();
        Optional<SymbolId> base = syntax.base().flatMap(name -> {
            if (name.text().startsWith("INTERLIS.")) {
                return Optional.empty();
            }
            return resolve(decl, name, Set.of(SymbolKind.UNIT));
        });
        String definition = syntax.definition();
        return new UnitSymbol(header(decl), syntax.abstractUnit(), base,
                definition.isBlank() ? Optional.empty() : Optional.of(definition));
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
        if (base.isPresent() && syntax.type() instanceof EnumerationTypeSyntax derived
                && symbols.get(base.orElseThrow()) instanceof DomainSymbol baseSymbol
                && types.get(baseSymbol.typeId()) instanceof EnumerationType baseEnum) {
            Set<String> baseValues = new LinkedHashSet<>();
            baseEnum.values().forEach(value -> baseValues.add(value.path()));
            for (EnumerationTypeSyntax.EnumerationValueSyntax value : derived.values()) {
                if (baseValues.contains(value.path()) && !value.hasChildren()) {
                    emitRule(decl, "extended enumeration repeats an inherited value without subvalues: "
                            + value.path(), "ENUM_VALUE_UNIQUE");
                }
            }
        }
        return new DomainSymbol(header(decl), type, base,
                decl.modifiers().contains(guru.interlis.model.Modifier.GENERIC));
    }

    private AttributeSymbol buildAttribute(DeclInfo decl) {
        AttributeDeclarationSyntax syntax = (AttributeDeclarationSyntax) decl.syntax();
        TypeId type = buildType(syntax.type(), decl.id(), decl.sourceId());
        declaredTypes.put(decl.id(), type);
        if (syntax.type() instanceof GenericTypeSyntax generic
                && "ATTRIBUTE".equals(generic.name())) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("(?i)\\bOF\\s+([A-Za-z_][A-Za-z0-9_]*)")
                    .matcher(declarationText(decl));
            if (matcher.find()) {
                Optional<SymbolId> baseAttribute = findMember(decl.owner().orElse(decl.id()),
                        matcher.group(1));
                TypeDescriptor baseType = baseAttribute.map(declaredTypes::get)
                        .map(this::effectiveDescriptor).orElse(null);
                if (!(baseType instanceof ClassValueType) && !(baseType instanceof ReferenceType)) {
                    emitRule(decl, "ATTRIBUTE OF requires a class-typed attribute",
                            "ATTRIBUTE_PATH_TYPE");
                }
            }
        }
        if (syntax.transientAttribute() && !syntax.assigned()
                && !syntax.modifiers().contains(guru.interlis.model.Modifier.ABSTRACT)) {
            emitRule(decl, "transient attribute requires an assignment", "TRANSIENT_ASSIGNMENT");
        }
        return new AttributeSymbol(header(decl), type, syntax.mandatory(),
                syntax.transientAttribute(), syntax.continuous(), syntax.subdivision(),
                Optional.empty());
    }

    private RoleSymbol buildRole(DeclInfo decl) {
        RoleDeclarationSyntax syntax = (RoleDeclarationSyntax) decl.syntax();
        Optional<SymbolId> target = tryResolve(decl, syntax.target(), Set.of(SymbolKind.CLASS));
        boolean structureTarget = symbols.values().stream()
                .filter(StructureSymbol.class::isInstance)
                .map(Symbol::name).map(DeclaredName::value)
                .anyMatch(name -> name.equals(syntax.target().text()));
        if (target.isEmpty()) {
            SymbolId roleTopic = scopeRoot(decl.owner().orElse(decl.id()));
            SymbolId current = roleTopic;
            int guard = 0;
            while (target.isEmpty() && current != null && guard++ < 32) {
                Symbol topicSymbol = symbols.get(current);
                if (!(topicSymbol instanceof TopicSymbol topic) || topic.baseTopic().isEmpty()) {
                    break;
                }
                SymbolId base = topic.baseTopic().orElseThrow();
                for (DeclInfo child : index.children(base)) {
                    if (child.kind() == SymbolKind.CLASS && child.name().equals(syntax.target().text())) {
                        target = Optional.of(child.id());
                        break;
                    }
                }
                current = base;
            }
        }
        if (target.isEmpty()) {
            List<SymbolId> classes = symbols.values().stream()
                    .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                    .filter(clazz -> clazz.name().value().equals(syntax.target().text()))
                    .map(ClassSymbol::id).toList();
            boolean structureConflict = symbols.values().stream()
                    .filter(StructureSymbol.class::isInstance)
                    .map(Symbol::name).map(DeclaredName::value)
                    .anyMatch(name -> name.equals(syntax.target().text()));
            if (classes.size() == 1 && !structureConflict) {
                target = Optional.of(classes.getFirst());
            }
        }
        SymbolId resolvedTarget = target.orElseGet(() -> syntheticClass(decl, syntax.target()));
        if (structureTarget || symbols.get(resolvedTarget) instanceof StructureSymbol) {
            emitRule(decl, "role target must be a class", "ROLE_STRUCTURE");
        }
        if (!decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL)) {
            SymbolId roleScope = scopeRoot(decl.owner().orElse(decl.id()));
            SymbolId targetScope = scopeRoot(resolvedTarget);
            boolean modelScope = roleScope != null
                    && index.byId(roleScope).map(owner -> owner.kind() == SymbolKind.MODEL)
                    .orElse(false) || targetScope != null
                    && index.byId(targetScope).map(owner -> owner.kind() == SymbolKind.MODEL)
                    .orElse(false);
            boolean inherited = isTopicSubtype(roleScope, targetScope)
                    || inheritedClassFromBaseTopic(roleScope, resolvedTarget)
                    || syntax.target().text().equals(syntax.name());
            if (!modelScope && !inherited && roleScope != null && targetScope != null
                    && !roleScope.equals(targetScope)) {
                emitRule(decl, "cross-topic role requires EXTERNAL", "ROLE_EXTERNAL");
            }
        }
        return new RoleSymbol(header(decl), resolvedTarget, syntax.cardinality(),
                syntax.aggregation(),
                decl.modifiers().contains(guru.interlis.model.Modifier.ORDERED),
                decl.modifiers().contains(guru.interlis.model.Modifier.EXTERNAL),
                decl.modifiers().contains(guru.interlis.model.Modifier.HIDING));
    }

    private SymbolId syntheticDomain(SymbolId owner, guru.interlis.syntax.NameSyntax name) {
        SymbolId id = new SymbolId("sym-unresolved-domain-" + (++typeCounter));
        SymbolId model = index.modelOfSymbol(owner).orElse(owner);
        TypeId type = nextTypeId();
        types.put(type, new GenericType(type, new Origin.DeclaredOrigin(name.range()), "UNDEFINED"));
        SymbolHeader header = new SymbolHeader(id, SymbolKind.DOMAIN,
                DeclaredName.synthetic("<unresolved-domain>"), Optional.of(model), 0,
                new Origin.SyntheticOrigin(guru.interlis.model.SyntheticReason.MISSING_REFERENCE,
                        Optional.of(name.range())), Optional.of(name.range()),
                index.byId(owner).orElseThrow().language(),
                Documentation.empty(), MetaAttributeSet.empty(), Set.of(), Optional.empty());
        symbols.put(id, new DomainSymbol(header, type, Optional.empty(), false));
        return id;
    }

    private boolean shareBaseTopic(SymbolId first, SymbolId second) {
        Set<SymbolId> firstBases = new LinkedHashSet<>();
        SymbolId current = first;
        int guard = 0;
        while (current != null && guard++ < 32) {
            firstBases.add(current);
            current = symbols.get(current) instanceof TopicSymbol topic
                    ? topic.baseTopic().orElse(null) : null;
        }
        current = second;
        guard = 0;
        while (current != null && guard++ < 32) {
            if (firstBases.contains(current)) {
                return true;
            }
            current = symbols.get(current) instanceof TopicSymbol topic
                    ? topic.baseTopic().orElse(null) : null;
        }
        return false;
    }

    private boolean inheritedClassFromBaseTopic(SymbolId roleTopic, SymbolId targetClass) {
        if (!(symbols.get(targetClass) instanceof ClassSymbol target)) {
            return false;
        }
        SymbolId current = roleTopic;
        int guard = 0;
        while (current != null && guard++ < 32) {
            Symbol symbol = symbols.get(current);
            if (!(symbol instanceof TopicSymbol topic)) {
                return false;
            }
            if (topic.baseTopic().isPresent()) {
                SymbolId base = topic.baseTopic().orElseThrow();
                for (DeclInfo child : index.children(base)) {
                    if (child.kind() == SymbolKind.CLASS
                            && child.name().equals(target.name().value())) {
                        return true;
                    }
                }
                current = base;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean isTopicSubtype(SymbolId candidate, SymbolId base) {
        SymbolId current = candidate;
        int guard = 0;
        while (current != null && guard++ < 32) {
            if (current.equals(base)) {
                return true;
            }
            Symbol symbol = symbols.get(current);
            if (symbol instanceof TopicSymbol topic) {
                current = topic.baseTopic().orElse(null);
            } else {
                return false;
            }
        }
        return false;
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
                MetaAttributeSet.empty(), Set.of(), Optional.empty());
        ClassSymbol synthetic = new ClassSymbol(header, Optional.empty(),
                List.of(), List.of());
        symbols.put(id, synthetic);
        return id;
    }

    private ConstraintSymbol buildConstraint(DeclInfo decl) {
        ConstraintDeclarationSyntax syntax;
        Optional<SymbolId> externalTarget = Optional.empty();
        if (decl.syntax() instanceof ExternalConstraintSyntax external) {
            syntax = external.constraint();
            externalTarget = tryResolve(decl, external.target(),
                    Set.of(SymbolKind.CLASS, SymbolKind.ASSOCIATION));
            if (externalTarget.isEmpty()) {
                String targetName = external.target().text();
                externalTarget = symbols.values().stream()
                        .filter(symbol -> (symbol instanceof ClassSymbol
                                || symbol instanceof AssociationSymbol)
                                && symbol.name().value().equals(targetName))
                        .map(Symbol::id).findFirst();
            }
        } else {
            syntax = (ConstraintDeclarationSyntax) decl.syntax();
        }
        if (syntax.existence().isPresent()) {
            guru.interlis.syntax.ExistenceConstraintSyntax existence = syntax.existence().orElseThrow();
            SymbolId owner = decl.owner().orElse(decl.id());
            Optional<SymbolId> sourceAttribute = findMember(owner, existence.source().text());
            List<guru.interlis.model.ObjectPath> sourceSteps = sourceAttribute
                    .map(id -> new guru.interlis.model.ObjectPath(List.of(
                            new guru.interlis.model.PathStep.AttributeStep(
                                    existence.source().text(), id, existence.source().range()))))
                    .map(List::of).orElseGet(List::of);
            if (sourceSteps.isEmpty()) {
                emitRule(decl, "EXISTENCE source attribute does not resolve", "EXISTENCE_SOURCE");
            }
            SymbolId existenceOwnerScope = scopeRoot(owner);
            if (declarationText(decl).contains(".")) {
                emitRule(decl, "EXISTENCE target topic requires DEPENDS ON",
                        "EXISTENCE_DEPENDS");
            }
            List<guru.interlis.model.ExistenceConstraint.ExistenceTarget> targets = new ArrayList<>();
            for (guru.interlis.syntax.ExistenceConstraintSyntax.ExistenceTargetSyntax target
                    : existence.targets()) {
                Optional<SymbolId> viewable = tryResolve(decl, target.viewable(),
                        Set.of(SymbolKind.CLASS));
                if (viewable.isPresent()) {
                    String targetText = target.viewable().text();
                    if (targetText.contains(".")) {
                        SymbolId ownerScope = scopeRoot(owner);
                        boolean dependencyFound = !(symbols.get(ownerScope) instanceof TopicSymbol ownerTopic)
                                || ownerTopic.dependencies().stream()
                                .map(symbols::get).filter(TopicSymbol.class::isInstance)
                                .map(TopicSymbol.class::cast)
                                .anyMatch(topic -> targetText.contains(topic.name().value()));
                        if (!dependencyFound) {
                            emitRule(decl, "EXISTENCE target topic requires DEPENDS ON",
                                    "EXISTENCE_DEPENDS");
                        }
                    }
                    Optional<SymbolId> attribute = findMember(viewable.orElseThrow(),
                            target.path().text());
                    TypeId sourceType = sourceAttribute.map(declaredTypes::get).orElse(null);
                    TypeId targetType = attribute.map(declaredTypes::get).orElse(null);
                    TypeDescriptor sourceDescriptor = sourceType == null ? null
                            : effectiveDescriptor(sourceType);
                    TypeDescriptor targetDescriptor = targetType == null ? null
                            : effectiveDescriptor(targetType);
                    boolean geometryCoordinate = sourceDescriptor != null && targetDescriptor != null
                            && (sourceDescriptor.kind() == guru.interlis.model.TypeKind.GEOMETRY
                            || sourceDescriptor.kind() == guru.interlis.model.TypeKind.COORDINATE)
                            && (targetDescriptor.kind() == guru.interlis.model.TypeKind.GEOMETRY
                            || targetDescriptor.kind() == guru.interlis.model.TypeKind.COORDINATE);
                    boolean compatible = sourceType == null || targetType == null
                            || geometryCoordinate || sameTypeFamily(sourceType, targetType)
                            || comparable(sourceType, targetType);
                    if (!compatible) {
                        emitRule(decl, "EXISTENCE source and target types differ",
                                "EXISTENCE_TYPE");
                    }
                    SymbolId ownerTopic = scopeRoot(owner);
                    SymbolId targetTopic = scopeRoot(viewable.orElseThrow());
                    if (ownerTopic != null && targetTopic != null
                            && !ownerTopic.equals(targetTopic)
                            && symbols.get(ownerTopic) instanceof TopicSymbol topic
                            && !topic.dependencies().contains(targetTopic)) {
                        emitRule(decl, "EXISTENCE target topic requires DEPENDS ON",
                                "EXISTENCE_DEPENDS");
                    }
                    attribute.ifPresent(id -> targets.add(
                            new guru.interlis.model.ExistenceConstraint.ExistenceTarget(
                                    viewable.orElseThrow(),
                                    new guru.interlis.model.ObjectPath(List.of(
                                            new guru.interlis.model.PathStep.AttributeStep(
                                                    target.path().text(), id,
                                                    target.path().range()))))));
                }
            }
            if (targets.isEmpty()) {
                emitRule(decl, "EXISTENCE target does not resolve", "EXISTENCE_TARGET");
            }
            guru.interlis.model.ObjectPath sourcePath = sourceSteps.isEmpty()
                    ? new guru.interlis.model.ObjectPath(List.of())
                    : sourceSteps.getFirst();
            return new ConstraintSymbol(header(decl),
                    new guru.interlis.model.ExistenceConstraint(syntax.name(), sourcePath, targets),
                    externalTarget);
        }
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.SET
                && decl.owner().flatMap(index::byId)
                .filter(owner -> owner.kind() == SymbolKind.STRUCTURE).isPresent()) {
            emitRule(decl, "SET CONSTRAINT is not allowed in a structure", "SET_IN_STRUCTURE");
        }
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.UNIQUENESS
                && isIllegalBasketLocal(decl)) {
            emitRule(decl, "UNIQUE cannot combine BASKET and LOCAL", "UNIQUE_KEY");
            return new ConstraintSymbol(header(decl), new UniquenessConstraint(syntax.name(),
                    true, Optional.empty(), List.of(), List.of()), externalTarget);
        }
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.UNIQUENESS
                && isLocalUniqueness(decl)) {
            return new ConstraintSymbol(header(decl), new UniquenessConstraint(syntax.name(),
                    true, Optional.empty(), List.of(), List.of()), externalTarget);
        }
        if (syntax.constraintKind() == guru.interlis.syntax.ConstraintKind.UNIQUENESS) {
            List<guru.interlis.model.ObjectPath> keys = new ArrayList<>();
            SymbolId targetId = externalTarget.orElse(null);
            if (targetId == null && !syntax.localUniqueness()) {
                for (guru.interlis.syntax.NameSyntax key : syntax.uniqueKeys()) {
                    symbols.values().stream()
                            .filter(AttributeSymbol.class::isInstance)
                            .map(AttributeSymbol.class::cast)
                            .filter(attribute -> attribute.name().value().equals(key.text()))
                            .filter(attribute -> effectiveDescriptor(attribute.declaredType())
                                    instanceof CollectionType)
                            .findFirst()
                            .ifPresent(ignored -> emitRule(decl,
                                    "UNIQUE key uses a collection attribute", "UNIQUE_KEY"));
                }
            }
            if (targetId != null) {
                for (guru.interlis.syntax.NameSyntax key : syntax.uniqueKeys()) {
                    boolean found = false;
                    if (symbols.get(targetId) instanceof ClassSymbol targetClass) {
                        for (SymbolId member : targetClass.members()) {
                            DeclInfo memberDecl = index.byId(member).orElse(null);
                            if (memberDecl != null && memberDecl.kind() == SymbolKind.ATTRIBUTE
                                    && memberDecl.name().equals(key.text())) {
                                keys.add(new guru.interlis.model.ObjectPath(List.of(
                                        new guru.interlis.model.PathStep.AttributeStep(
                                                key.text(), member, key.range()))));
                                found = true;
                                if (symbols.get(member) instanceof AttributeSymbol attribute
                                        && effectiveDescriptor(attribute.declaredType())
                                        instanceof CollectionType) {
                                    emitRule(decl, "UNIQUE key uses a collection attribute",
                                            "UNIQUE_KEY");
                                }
                                break;
                            }
                        }
                    }
                    if (!found) {
                        Optional<SymbolId> role = tryResolve(decl, key,
                                Set.of(SymbolKind.ROLE, SymbolKind.ATTRIBUTE));
                        if (role.isEmpty()) {
                            role = symbols.values().stream()
                                    .filter(RoleSymbol.class::isInstance)
                                    .map(RoleSymbol.class::cast)
                                    .filter(candidate -> candidate.name().value().equals(key.text()))
                                    .map(RoleSymbol::id).findFirst();
                        }
                        if (role.isPresent()
                                && symbols.get(role.orElseThrow()) instanceof RoleSymbol roleSymbol) {
                            keys.add(new guru.interlis.model.ObjectPath(List.of(
                                    new guru.interlis.model.PathStep.RoleStep(key.text(),
                                            roleSymbol.id(), key.range()))));
                            found = true;
                            if (roleSymbol.cardinality().upper() instanceof
                                    guru.interlis.model.CardinalityUpper.Unbounded) {
                                emitRule(decl, "UNIQUE role key is not singular",
                                        "UNIQUE_KEY");
                            }
                        }
                    }
                    if (!found) {
                        emitRule(decl, "UNIQUE key was not found in the target class",
                                "UNIQUE_KEY");
                    }
                }
            }
            return new ConstraintSymbol(header(decl), new UniquenessConstraint(syntax.name(),
                    false, Optional.empty(), keys, List.of()), externalTarget);
        }
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
            case UNIQUENESS -> {
                List<guru.interlis.model.ObjectPath> keys = new ArrayList<>();
                SymbolId targetId = externalTarget.orElseGet(() ->
                        decl.owner().flatMap(index::byId)
                                .filter(owner -> owner.kind() == SymbolKind.CLASS)
                                .map(DeclInfo::id).orElse(null));
                if (targetId != null) {
                    for (guru.interlis.syntax.NameSyntax key : syntax.uniqueKeys()) {
                        Optional<SymbolId> attribute = resolve(decl, key,
                                Set.of(SymbolKind.ATTRIBUTE));
                        attribute.ifPresent(id -> keys.add(new guru.interlis.model.ObjectPath(List.of(
                                new guru.interlis.model.PathStep.AttributeStep(key.text(), id,
                                        key.range())))));
                    }
                }
                yield new UniquenessConstraint(syntax.name(), false, Optional.empty(), keys,
                        List.of());
            }
            case PLAUSIBILITY -> new PlausibilityConstraint(syntax.name(),
                    java.math.BigDecimal.ZERO, guru.interlis.model.BinaryOperator.LTE,
                    expression.id());
            case DOMAIN -> new DomainConstraint(expression.id());
            case SET -> new guru.interlis.model.SetConstraint(syntax.name(),
                    guru.interlis.model.SetConstraint.SetConstraintScope.LOCAL, Optional.empty(),
                    expression.id());
            default -> new MandatoryConstraint(syntax.name(), expression.id());
        };
        return new ConstraintSymbol(header(decl), definition, externalTarget);
    }

    private boolean isIllegalBasketLocal(DeclInfo decl) {
        String text = declarationText(decl).toUpperCase(java.util.Locale.ROOT);
        return text.contains("(BASKET)") && text.contains("(LOCAL)");
    }

    private String declarationText(DeclInfo decl) {
        if (!(decl.origin() instanceof Origin.DeclaredOrigin declared)) {
            return "";
        }
        guru.interlis.source.SourceText text = sourceSet.sources().get(declared.range().source());
        if (text == null) {
            return "";
        }
        int start = Math.max(0, declared.range().startOffset());
        int end = Math.min(text.text().length(), declared.range().endOffset() + 1);
        return text.text().substring(start, end);
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

    private boolean isLocalUniqueness(DeclInfo decl) {
        if (!(decl.origin() instanceof Origin.DeclaredOrigin declared)) {
            return false;
        }
        guru.interlis.source.SourceText text = sourceSet.sources().get(declared.range().source());
        if (text == null) {
            return false;
        }
        int start = Math.max(0, declared.range().startOffset());
        int end = Math.min(text.text().length(), declared.range().endOffset() + 1);
        return text.text().substring(start, end).toUpperCase(java.util.Locale.ROOT)
                .contains("(LOCAL)");
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
        ViewDeclarationSyntax syntax = (ViewDeclarationSyntax) decl.syntax();
        List<SymbolId> bases = new ArrayList<>();
        syntax.baseView().flatMap(name -> resolve(decl, name, Set.of(SymbolKind.VIEW)))
                .ifPresent(bases::add);
        if (syntax.formation().isPresent() && decl.owner().isPresent()) {
            DeclInfo ownerDecl = index.byId(decl.owner().orElseThrow()).orElse(null);
            if (ownerDecl != null && ownerDecl.syntax() instanceof guru.interlis.syntax.TopicSyntax topic) {
                Set<String> dependencies = new LinkedHashSet<>();
                for (guru.interlis.syntax.NameSyntax dependency : topic.dependencies()) {
                    dependencies.add(dependency.text());
                }
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile("([A-Za-z_][A-Za-z0-9_]*\\.[A-Za-z_][A-Za-z0-9_]*)\\.[A-Za-z_][A-Za-z0-9_]*")
                        .matcher(syntax.formation().orElseThrow());
                while (matcher.find()) {
                    String prefix = matcher.group(1);
                    if (!dependencies.contains(prefix)
                            && !prefix.equals(ownerDecl.qualifiedName())) {
                        emitRule(decl, "VIEW formation references a topic without DEPENDS ON",
                                "VIEW_TOPIC_DEPENDENCY");
                    }
                }
            }
        }
        Map<String, SymbolId> aliases = new LinkedHashMap<>();
        if (syntax.formation().isPresent()) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("([A-Za-z_][A-Za-z0-9_]*)\\s*~\\s*([A-Za-z_][A-Za-z0-9_.]*)")
                    .matcher(syntax.formation().orElseThrow());
            while (matcher.find()) {
                String alias = matcher.group(1);
                String targetName = matcher.group(2);
                List<guru.interlis.syntax.NameSyntax.NameSegmentSyntax> segments = new ArrayList<>();
                for (String part : targetName.split("\\.")) {
                    segments.add(new guru.interlis.syntax.NameSyntax.NameSegmentSyntax(
                            part, syntax.range()));
                }
                guru.interlis.syntax.NameSyntax targetSyntax =
                        new guru.interlis.syntax.NameSyntax(
                                new guru.interlis.syntax.SyntaxNodeId(
                                        "view-alias-" + expressionCounter++),
                                syntax.range(), segments);
                tryResolve(decl, targetSyntax, Set.of(SymbolKind.CLASS))
                        .ifPresent(target -> aliases.put(alias, target));
            }
        }
        for (ViewDeclarationSyntax.BaseExtension extension : syntax.baseExtensions()) {
            SymbolId aliasTarget = aliases.get(extension.base().text());
            Optional<SymbolId> baseClass = aliasTarget != null
                    ? Optional.of(aliasTarget)
                    : tryResolve(decl, extension.base(), Set.of(SymbolKind.CLASS));
            if (baseClass.isEmpty()) {
                baseClass = symbols.values().stream()
                        .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                        .filter(clazz -> clazz.name().value().equals(extension.base().text())
                                || clazz.name().value().equals("Class" + extension.base().text()))
                        .map(ClassSymbol::id).findFirst();
            }
            for (guru.interlis.syntax.NameSyntax targetName : extension.extensions()) {
                Optional<SymbolId> targetClass = tryResolveSuffixes(decl, targetName);
                if (baseClass.isEmpty() || targetClass.isEmpty()) {
                    emitRule(decl, "view base extension target does not resolve",
                            "VIEW_BASE_EXTENSION");
                    continue;
                }
                boolean subtype = false;
                SymbolId current = targetClass.orElseThrow();
                int guard = 0;
                while (current != null && guard++ < 64) {
                    if (current.equals(baseClass.orElseThrow())) {
                        subtype = true;
                        break;
                    }
                    current = baseOf.get(current);
                }
                boolean sameName = symbols.get(targetClass.orElseThrow()) instanceof ClassSymbol targetSymbol
                        && symbols.get(baseClass.orElseThrow()) instanceof ClassSymbol baseSymbol
                        && targetSymbol.name().value().equals(baseSymbol.name().value());
                if (!subtype && !sameName) {
                    emitRule(decl, "view base extension target is not a subclass",
                            "VIEW_BASE_EXTENSION");
                }
            }
        }
        String formationText = syntax.formation().orElse("").toUpperCase(java.util.Locale.ROOT);
        String bodyText = syntax.body().orElse("").toUpperCase(java.util.Locale.ROOT);
        boolean areaInspection = formationText.contains("AREA")
                && formationText.contains("INSPECTION");
        if (areaInspection && bodyText.contains("PARENT")) {
            emitRule(decl, "PARENT is not allowed in an area inspection",
                    "AREA_PARENT");
        }
        if (!areaInspection && (bodyText.contains("THISAREA")
                || bodyText.contains("THATAREA"))) {
            emitRule(decl, "THISAREA/THATAREA requires an area inspection",
                    "AREA_CONTEXT");
        }
        Set<String> attributeNames = new LinkedHashSet<>();
        for (String attribute : syntax.attributeNames()) {
            if (!attributeNames.add(attribute)) {
                emitRule(decl, "duplicate view attribute", "VIEW_ATTRIBUTE_DUPLICATE");
            }
        }
        for (guru.interlis.syntax.NameSyntax allOf : syntax.allOf()) {
            SymbolId target = aliases.get(allOf.text());
            if (target == null) {
                target = tryResolve(decl, allOf, Set.of(SymbolKind.CLASS)).orElse(null);
            }
            if (target != null && symbols.get(target) instanceof ClassSymbol clazz) {
                for (SymbolId member : clazz.members()) {
                    DeclInfo memberDecl = index.byId(member).orElse(null);
                    if (memberDecl != null && memberDecl.kind() == SymbolKind.ATTRIBUTE) {
                        if (!attributeNames.add(memberDecl.name())) {
                            emitRule(decl, "duplicate view attribute inherited via ALL OF",
                                    "VIEW_ATTRIBUTE_DUPLICATE");
                        }
                        if (aliases.containsKey(memberDecl.name())) {
                            emitRule(decl, "view alias conflicts with an attribute name",
                                    "VIEW_ALIAS_CONFLICT");
                        }
                    }
                }
            }
        }
        return new ViewSymbol(header(decl), bases, List.of(), syntax.formation(), syntax.body());
    }

    private SymbolHeader header(DeclInfo decl) {
        DeclaredName declaredName = isSyntheticName(decl.name())
                ? DeclaredName.synthetic(decl.name()) : DeclaredName.declared(decl.name());
        Set<guru.interlis.model.Modifier> modifiers = new LinkedHashSet<>(decl.modifiers());
        if (decl.syntax() instanceof AttributeDeclarationSyntax attribute
                && attribute.transientAttribute()) {
            modifiers.add(guru.interlis.model.Modifier.TRANSIENT);
        }
        Optional<String> oidRule = switch (decl.syntax()) {
            case guru.interlis.syntax.ClassDeclarationSyntax value -> value.oidRule();
            case guru.interlis.syntax.TopicSyntax value -> value.oidRule();
            case guru.interlis.syntax.AssociationDeclarationSyntax value -> value.oidRule();
            default -> Optional.empty();
        };
        return new SymbolHeader(decl.id(), decl.kind(), declaredName, decl.owner(),
                decl.order(), decl.origin(), decl.nameRange(), decl.language(),
                Documentation.empty(), MetaAttributeSet.empty(), modifiers, oidRule);
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
            case TemporalTypeSyntax temporal -> new guru.interlis.model.TemporalType(id,
                    new Origin.DeclaredOrigin(temporal.range()), temporal.temporalKind());
            case OidTypeSyntax oid -> new guru.interlis.model.OidType(id,
                    new Origin.DeclaredOrigin(oid.range()), oid.oidKind());
            case BlackboxTypeSyntax blackbox -> new guru.interlis.model.BlackboxType(id,
                    new Origin.DeclaredOrigin(blackbox.range()), blackbox.blackboxKind());
            case AlignmentTypeSyntax alignment -> new guru.interlis.model.AlignmentType(id,
                    new Origin.DeclaredOrigin(alignment.range()), alignment.alignment());
            case EnumTreeValueSyntax enumTree -> {
                Optional<SymbolId> domain = resolveName(context, enumTree.target(),
                        Set.of(SymbolKind.DOMAIN), source);
                enumTreeTypes.add(id);
                yield new DomainReferenceType(id, new Origin.DeclaredOrigin(enumTree.range()),
                        domain.orElseGet(() -> syntheticDomain(context, enumTree.target())));
            }
            case GenericTypeSyntax generic -> new GenericType(id,
                    new Origin.DeclaredOrigin(generic.range()), generic.name());
            case ClassValueTypeSyntax classValue -> new guru.interlis.model.ClassValueType(id,
                    new Origin.DeclaredOrigin(classValue.range()), classValue.structureValue(),
                    classValue.restrictions());
            case LineTypeSyntax line -> {
                Optional<SymbolId> vertexDomain = line.vertexDomain().flatMap(name ->
                        resolveName(context, name, Set.of(SymbolKind.DOMAIN), source));
                if (line.overlap().isPresent()) {
                    try {
                        if (new java.math.BigDecimal(line.overlap().orElseThrow())
                                .compareTo(new java.math.BigDecimal("0.001")) < 0) {
                            index.byId(context).ifPresent(decl -> emitRule(decl,
                                    "WITHOUT OVERLAPS value is too small",
                                    "OVERLAP_TOO_SMALL"));
                        }
                    } catch (NumberFormatException ignored) {
                        // Leave lexical validation to the syntax layer.
                    }
                }
                if (line.lineAttributes().isPresent()) {
                    guru.interlis.syntax.NameSyntax name = new guru.interlis.syntax.NameSyntax(
                            new guru.interlis.syntax.SyntaxNodeId("line-attr-" + typeCounter),
                            line.range(), List.of(
                            new guru.interlis.syntax.NameSyntax.NameSegmentSyntax(
                                    line.lineAttributes().orElseThrow(), line.range())));
                    Optional<SymbolId> target = resolveName(context, name,
                            Set.of(SymbolKind.CLASS, SymbolKind.STRUCTURE), source);
                    if (target.isPresent() && symbols.get(target.orElseThrow()) instanceof ClassSymbol) {
                        index.byId(context).ifPresent(decl -> emitRule(decl,
                                "LINE ATTRIBUTES must reference a STRUCTURE",
                                "LINE_ATTRIBUTES_STRUCTURE"));
                    }
                }
                yield new guru.interlis.model.GeometryType(id,
                        new Origin.DeclaredOrigin(line.range()), line.geometryKind(),
                        line.lineForm(), vertexDomain, line.lineAttributes());
            }
            case FormattedTypeSyntax formatted -> new guru.interlis.model.FormattedType(id,
                    new Origin.DeclaredOrigin(formatted.range()), formatted.formatName(),
                    formatted.minimum(), formatted.maximum());
            case guru.interlis.syntax.NumericTypeSyntax numeric -> {
                Optional<SymbolId> unit = numeric.unit().flatMap(name -> {
                    NameResolver.Resolution resolution = resolver.resolve(context, name,
                            Set.of(SymbolKind.UNIT));
                    if (resolution.status() == NameResolver.Status.BOUND) {
                        return resolution.target();
                    }
                    Optional<SymbolId> alias = unitByAlias(name.text());
                    if (alias.isPresent()) {
                        return alias;
                    }
                    if (!name.text().startsWith("INTERLIS.")) {
                        unresolved(name);
                    }
                    return Optional.empty();
                });
                yield new NumericType(id, new Origin.DeclaredOrigin(numeric.range()),
                        new NumericRange(numeric.minimum(), numeric.maximum(), numeric.circular(),
                                numeric.direction(), unit, Optional.empty()));
            }
            case EnumerationTypeSyntax enumeration -> {
                Set<String> paths = new LinkedHashSet<>();
                for (EnumerationTypeSyntax.EnumerationValueSyntax value : enumeration.values()) {
                    if (!paths.add(value.path())) {
                        index.byId(context).ifPresent(decl -> emitRule(decl,
                                "duplicate enumeration value " + value.path(),
                                "ENUM_VALUE_UNIQUE"));
                    }
                }
                yield new EnumerationType(id, new Origin.DeclaredOrigin(enumeration.range()),
                        enumeration.values().stream()
                                .map(v -> new EnumerationValue(v.path(), 0,
                                        Optional.of(v.range())))
                                .toList(), enumeration.ordered(), enumeration.circular(), false);
            }
            case DomainReferenceSyntax reference -> {
                if (reference.name().text().equals("INTERLIS.XMLDate")
                        || reference.name().text().equals("INTERLIS.XMLTime")) {
                    yield new guru.interlis.model.FormattedType(id,
                            new Origin.DeclaredOrigin(reference.range()),
                            reference.name().text(), Optional.empty(), Optional.empty());
                }
                NameResolver.Resolution resolution = resolver.resolve(context, reference.name(),
                        Set.of(SymbolKind.DOMAIN, SymbolKind.STRUCTURE));
                if (resolution.status() == NameResolver.Status.BOUND) {
                    SymbolId targetId = resolution.target().orElseThrow();
                    Symbol targetSymbol = symbols.get(targetId);
                    if (targetSymbol instanceof StructureSymbol) {
                        yield new guru.interlis.model.StructureValueType(id,
                                new Origin.DeclaredOrigin(reference.range()), targetId);
                    }
                    yield new DomainReferenceType(id, new Origin.DeclaredOrigin(reference.range()),
                            targetId);
                }
                Optional<SymbolId> inherited = baseTopicMember(context, reference.name().text());
                if (inherited.isPresent()) {
                    Symbol inheritedSymbol = symbols.get(inherited.orElseThrow());
                    if (inheritedSymbol instanceof StructureSymbol) {
                        yield new guru.interlis.model.StructureValueType(id,
                                new Origin.DeclaredOrigin(reference.range()), inherited.orElseThrow());
                    }
                    yield new DomainReferenceType(id, new Origin.DeclaredOrigin(reference.range()),
                            inherited.orElseThrow());
                }
                if (reference.name().text().startsWith("INTERLIS.")) {
                    yield new DomainReferenceType(id, new Origin.DeclaredOrigin(reference.range()),
                            syntheticDomain(context, reference.name()));
                }
                yield new ErrorType(id, new Origin.DeclaredOrigin(reference.range()),
                        List.of(unresolved(reference.name())));
            }
            case ReferenceTypeSyntax reference -> {
                if (reference.target().text().equals("<missing-name>")) {
                    DeclInfo owner = index.byId(context).orElse(null);
                    if (owner != null) {
                        yield new guru.interlis.model.ReferenceType(id,
                                new Origin.DeclaredOrigin(reference.range()),
                                syntheticClass(owner, reference.target()), reference.external());
                    }
                }
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
                    coordinate.axes(), coordinate.multiCoordinate(),
                    coordinate.referenceSystem());
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

    private Optional<SymbolId> tryResolveSuffixes(DeclInfo context,
                                                  guru.interlis.syntax.NameSyntax name) {
        Optional<SymbolId> direct = tryResolve(context, name, Set.of(SymbolKind.CLASS));
        if (direct.isPresent()) {
            return direct;
        }
        List<guru.interlis.syntax.NameSyntax.NameSegmentSyntax> segments = name.segments();
        for (int start = 1; start < segments.size(); start++) {
            guru.interlis.syntax.NameSyntax suffix = new guru.interlis.syntax.NameSyntax(
                    new guru.interlis.syntax.SyntaxNodeId("view-target-" + expressionCounter++),
                    name.range(), segments.subList(start, segments.size()));
            Optional<SymbolId> resolved = tryResolve(context, suffix, Set.of(SymbolKind.CLASS));
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        String lastName = segments.getLast().text();
        return symbols.values().stream().filter(ClassSymbol.class::isInstance)
                .map(ClassSymbol.class::cast)
                .filter(clazz -> clazz.name().value().equals(lastName))
                .map(ClassSymbol::id).findFirst();
    }

    private Optional<SymbolId> tryResolve(DeclInfo context,
                                          guru.interlis.syntax.NameSyntax name,
                                          Set<SymbolKind> expected) {
        NameResolver.Resolution resolution = resolver.resolve(context.id(), name, expected);
        return resolution.status() == NameResolver.Status.BOUND
                ? resolution.target() : Optional.empty();
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
            case FunctionCallExpressionSyntax call -> functionCallExpression(id, call, owner);
            case BuiltinFunctionExpressionSyntax builtin -> builtinFunctionExpression(id, builtin, owner);
            case EnumerationConstantExpressionSyntax constant -> enumerationConstantExpression(id, constant, owner);
            case ClassConstantExpressionSyntax constant -> classConstantExpression(id, constant, owner);
            case GenericConstantExpressionSyntax constant -> genericConstantExpression(id, constant, owner);
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

    private ExpressionResult genericConstantExpression(ExpressionId id,
                                                       GenericConstantExpressionSyntax syntax,
                                                       SymbolId owner) {
        TypeId type = "PARAMETER".equals(syntax.name()) ? builtinText() : nextTypeId();
        if (!"PARAMETER".equals(syntax.name())) {
            types.put(type, new GenericType(type, new Origin.DeclaredOrigin(syntax.range()),
                    syntax.name()));
        }
        expressions.put(id, new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.name()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult classConstantExpression(ExpressionId id,
                                                     ClassConstantExpressionSyntax syntax,
                                                     SymbolId owner) {
        Optional<SymbolId> target = resolveName(owner, syntax.target(),
                Set.of(SymbolKind.CLASS), ownerSource(owner));
        TypeId type = nextTypeId();
        List<String> restrictions = target.map(idValue -> symbols.get(idValue))
                .filter(ClassSymbol.class::isInstance).map(ClassSymbol.class::cast)
                .map(clazz -> clazz.name().value()).map(List::of).orElseGet(List::of);
        types.put(type, new guru.interlis.model.ClassValueType(type,
                new Origin.DeclaredOrigin(syntax.range()), false, restrictions));
        expressions.put(id, new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.target().text()));
        return new ExpressionResult(id, type, target.isEmpty());
    }

    private ExpressionResult enumerationConstantExpression(ExpressionId id,
                                                           EnumerationConstantExpressionSyntax syntax,
                                                           SymbolId owner) {
        String text = syntax.text().toLowerCase(java.util.Locale.ROOT);
        if (text.equals("#true") || text.equals("#false")) {
            TypeId type = builtinBoolean();
            expressions.put(id, new guru.interlis.model.LiteralExpression(id, type,
                    guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                    guru.interlis.model.LiteralKind.BOOLEAN, syntax.text(), Optional.empty(),
                    Optional.empty()));
            return new ExpressionResult(id, type, false);
        }
        TypeId type = nextTypeId();
        String value = syntax.text().startsWith("#")
                ? syntax.text().substring(1) : syntax.text();
        types.put(type, new EnumerationType(type, new Origin.DeclaredOrigin(syntax.range()),
                List.of(new guru.interlis.model.EnumerationValue(value, 0,
                        Optional.of(syntax.range()))), true, false, false));
        expressions.put(id, new guru.interlis.model.ConstantExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), List.of(),
                syntax.text()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult builtinFunctionExpression(ExpressionId id,
                                                       BuiltinFunctionExpressionSyntax syntax,
                                                       SymbolId owner) {
        List<ExpressionId> children = new ArrayList<>();
        for (guru.interlis.syntax.ExpressionSyntax argument : syntax.arguments()) {
            children.add(buildExpression(argument, owner).id());
        }
        String name = syntax.functionName().toLowerCase(java.util.Locale.ROOT);
        TypeId type;
        if (name.contains("objectcount") || name.equals("len") || name.contains("length")) {
            type = numericLiteralType("0");
        } else if (name.contains("text") || name.contains("name") || name.contains("uri")) {
            type = builtinText();
        } else {
            type = builtinBoolean();
        }
        expressions.put(id, new guru.interlis.model.FunctionCallExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), children,
                syntax.functionName(), Optional.empty()));
        return new ExpressionResult(id, type, false);
    }

    private ExpressionResult functionCallExpression(ExpressionId id,
                                                    FunctionCallExpressionSyntax syntax,
                                                    SymbolId owner) {
        List<ExpressionId> children = new ArrayList<>();
        for (guru.interlis.syntax.ExpressionSyntax argument : syntax.arguments()) {
            children.add(buildExpression(argument, owner).id());
        }
        guru.interlis.syntax.NameSyntax name = new guru.interlis.syntax.NameSyntax(
                new guru.interlis.syntax.SyntaxNodeId("expr-name-" + expressionCounter),
                syntax.range(), List.of(new guru.interlis.syntax.NameSyntax.NameSegmentSyntax(
                        syntax.functionName(), syntax.range())));
        Optional<SymbolId> target = resolveName(owner, name, Set.of(SymbolKind.FUNCTION),
                ownerSource(owner));
        TypeId type = builtinBoolean();
        if (target.isPresent() && symbols.get(target.orElseThrow()) instanceof FunctionSymbol function) {
            String returnType = function.returnType().toUpperCase(java.util.Locale.ROOT);
            if (returnType.contains("TEXT") || returnType.contains("NAME")
                    || returnType.contains("URI") || returnType.contains("MTEXT")) {
                type = builtinText();
            }
        }
        expressions.put(id, new guru.interlis.model.FunctionCallExpression(id, type,
                guru.interlis.model.ExpressionTypeState.RESOLVED, syntax.range(), children,
                syntax.functionName(), target));
        return new ExpressionResult(id, type, false);
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
                currentType = builtinBoolean();
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
                currentType = builtinBoolean();
                currentScope = symbols.get(member.get()) instanceof RoleSymbol role
                        ? role.target() : member.get();
            } else {
                steps.add(new guru.interlis.model.PathStep.AttributeStep(text, member.get(),
                        name.range()));
                currentType = declaredTypes.get(member.get());
                if (currentType != null) {
                    if (types.get(currentType) instanceof ReferenceType reference) {
                        currentScope = reference.target();
                        currentType = builtinBoolean();
                    } else if (types.get(currentType) instanceof StructureValueType structure) {
                        currentScope = structure.target();
                        currentType = builtinBoolean();
                    }
                }
                currentScope = currentScope == null ? member.get() : currentScope;
            }
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
        return symbols.values().stream()
                .filter(symbol -> (symbol instanceof RoleSymbol || symbol instanceof AttributeSymbol)
                        && symbol.name().value().equals(name))
                .map(Symbol::id).findFirst();
    }

    private ExpressionResult binaryExpression(ExpressionId id, BinaryExpressionSyntax syntax,
                                              SymbolId owner) {
        ExpressionResult left = buildExpression(syntax.left(), owner);
        ExpressionResult right = buildExpression(syntax.right(), owner);
        TypeId result = builtinBoolean();
        boolean error = left.error() || right.error();
        switch (syntax.operator()) {
            case AND, OR, IMPLIES -> {
                if (!isBoolean(left.typeId()) || !isBoolean(right.typeId())) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()), "BOOLEAN");
                    error = true;
                }
            }
            case EQ, NEQ -> {
                if (!comparable(left.typeId(), right.typeId())
                        && !sameTypeFamily(left.typeId(), right.typeId())) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()),
                            "COMPARABLE");
                    error = true;
                } else if (!formattedTextValid(left, right)
                        || !enumConstantValid(left, right)) {
                    emitTypeMismatch(ownerSource(owner), syntax.range(),
                            typeName(left.typeId()) + "," + typeName(right.typeId()),
                            "COMPARABLE");
                    error = true;
                }
            }
            case LT, LTE, GT, GTE -> {
                TypeDescriptor leftDescriptor = effectiveDescriptor(left.typeId());
                TypeDescriptor rightDescriptor = effectiveDescriptor(right.typeId());
                boolean enumConstant = isEnumConstant(left) || isEnumConstant(right);
                boolean sameEnum = !(leftDescriptor instanceof EnumerationType leftEnum
                        && rightDescriptor instanceof EnumerationType rightEnum)
                        || enumConstant || leftEnum.id().equals(rightEnum.id());
                boolean orderedEnums = leftDescriptor instanceof EnumerationType leftEnum
                        && rightDescriptor instanceof EnumerationType rightEnum
                        && (leftEnum.ordered() && rightEnum.ordered()
                        || isEnumTreeType(left.typeId()) || isEnumTreeType(right.typeId()))
                        && !leftEnum.circular() && !rightEnum.circular();
                if (!comparable(left.typeId(), right.typeId())
                        || !enumConstantValid(left, right)
                        || (leftDescriptor instanceof EnumerationType
                        && rightDescriptor instanceof EnumerationType && (!orderedEnums || !sameEnum))
                        || (leftDescriptor instanceof FormattedType
                        && rightDescriptor instanceof TextType
                        && !formattedTextValid(left, right))) {
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

    private boolean formattedTextValid(ExpressionResult left, ExpressionResult right) {
        TypeDescriptor leftDescriptor = effectiveDescriptor(left.typeId());
        TypeDescriptor rightDescriptor = effectiveDescriptor(right.typeId());
        guru.interlis.model.FormattedType formatted;
        ExpressionId textId;
        if (leftDescriptor instanceof guru.interlis.model.FormattedType f) {
            formatted = f;
            textId = right.id();
        } else if (rightDescriptor instanceof guru.interlis.model.FormattedType f) {
            formatted = f;
            textId = left.id();
        } else {
            return true;
        }
        TypedExpression expression = expressions.get(textId);
        if (!(expression instanceof guru.interlis.model.LiteralExpression literal)
                || literal.textValue().isEmpty()) {
            return true;
        }
        String value = literal.textValue().orElseThrow();
        if (formatted.formatName().endsWith("XMLDate")) {
            return value.matches("\\d{4}-\\d{2}-\\d{2}");
        }
        if (formatted.formatName().endsWith("XMLTime")) {
            return value.matches("\\d{2}:\\d{2}:\\d{2}");
        }
        return true;
    }

    private boolean isEnumTreeType(TypeId id) {
        TypeId current = id;
        int guard = 0;
        while (current != null && guard++ < 16) {
            if (enumTreeTypes.contains(current)) {
                return true;
            }
            if (types.get(current) instanceof DomainReferenceType reference) {
                Symbol domain = symbols.get(reference.targetDomain());
                current = domain instanceof DomainSymbol symbol ? symbol.typeId() : null;
            } else {
                return false;
            }
        }
        return false;
    }

    private boolean isEnumConstant(ExpressionResult result) {
        TypedExpression expression = expressions.get(result.id());
        return expression instanceof guru.interlis.model.ConstantExpression constant
                && constant.constantName().startsWith("#");
    }

    private boolean enumConstantValid(ExpressionResult left, ExpressionResult right) {
        ExpressionResult enumSide = null;
        ExpressionResult constSide = null;
        if (effectiveDescriptor(left.typeId()) instanceof EnumerationType) {
            enumSide = left;
            constSide = right;
        } else if (effectiveDescriptor(right.typeId()) instanceof EnumerationType) {
            enumSide = right;
            constSide = left;
        }
        if (enumSide == null || constSide == null) {
            return true;
        }
        TypedExpression constant = expressions.get(constSide.id());
        if (!(constant instanceof guru.interlis.model.ConstantExpression value)
                || !value.constantName().startsWith("#")) {
            return true;
        }
        String path = value.constantName().substring(1);
        EnumerationType enumeration = (EnumerationType) effectiveDescriptor(enumSide.typeId());
        boolean exact = enumeration.values().stream().anyMatch(v -> v.path().equals(path));
        boolean hasChildren = enumeration.values().stream().anyMatch(v -> v.path().startsWith(path + "."));
        return exact && (!hasChildren || isEnumTreeType(enumSide.typeId()));
    }

    private boolean sameTypeFamily(TypeId left, TypeId right) {
        TypeDescriptor leftDescriptor = effectiveDescriptor(left);
        TypeDescriptor rightDescriptor = effectiveDescriptor(right);
        if (leftDescriptor instanceof guru.interlis.model.FormattedType
                && rightDescriptor instanceof TextType
                || leftDescriptor instanceof TextType
                && rightDescriptor instanceof guru.interlis.model.FormattedType) {
            return true;
        }
        return leftDescriptor != null && rightDescriptor != null
                && leftDescriptor.kind() == rightDescriptor.kind();
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
        if (l instanceof EnumerationType && r instanceof EnumerationType) {
            return true;
        }
        if (l instanceof FormattedType && r instanceof TextType
                || l instanceof TextType && r instanceof FormattedType) {
            return true;
        }
        if (l instanceof guru.interlis.model.GenericType leftGeneric
                && r instanceof guru.interlis.model.GenericType rightGeneric
                && leftGeneric.name().equals(rightGeneric.name())) {
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
                    attribute.mandatory(), attribute.transientAttribute(), attribute.continuous(),
                    attribute.subdivision(), Optional.of(baseId));
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

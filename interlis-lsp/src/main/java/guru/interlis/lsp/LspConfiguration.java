package guru.interlis.lsp;

import com.google.gson.*;
import guru.interlis.compiler.*;
import guru.interlis.model.LanguageVersion;
import guru.interlis.source.SourceId;
import guru.interlis.workspace.*;
import guru.interlis.workspace.resolution.ResolutionPolicy;
import java.util.*;

/** Strict transport decoding only. Compiler and resolver validate their own immutable values. */
final class LspConfiguration {
    private LspConfiguration() { }

    static WorkspaceConfiguration read(Object settings,WorkspaceConfiguration previous) {
        JsonElement encoded=new Gson().toJsonTree(settings);
        JsonObject root=object(encoded);
        if(root.has("interlis")) root=object(root.get("interlis"));
        keys(root,"warningPolicy","allowedLanguages","resolutionPolicy","cacheLimits","analysisBudget");
        var old=previous.compilerOptions();
        var warnings=root.has("warningPolicy")?WarningPolicy.valueOf(string(root.get("warningPolicy"))):old.warningPolicy();
        var languages=old.allowedLanguages();
        if(root.has("allowedLanguages")) {
            var selected=EnumSet.noneOf(LanguageVersion.class);
            for(var item:array(root.get("allowedLanguages"))) {
                String literal=string(item);
                selected.add(Arrays.stream(LanguageVersion.values()).filter(v -> v.literal().equals(literal)).findFirst().orElseThrow(IllegalArgumentException::new));
            }
            languages=selected;
        }
        var budget=old.budget();
        if(root.has("analysisBudget")) {
            var b=object(root.get("analysisBudget"));
            keys(b,"maxSourceUtf16Length","maxTotalSourceUtf16Length","maxTokens","maxSyntaxNodes","maxRecoveryEvents","maxParseRuleDepth","maxSemanticWorkUnits","maxDiagnostics","maxNumericLiteralDigits","maxAbsoluteDecimalExponent");
            budget=new AnalysisBudget(integer(b,"maxSourceUtf16Length",budget.maxSourceUtf16Length()),number(b,"maxTotalSourceUtf16Length",budget.maxTotalSourceUtf16Length()),
                    number(b,"maxTokens",budget.maxTokens()),number(b,"maxSyntaxNodes",budget.maxSyntaxNodes()),integer(b,"maxRecoveryEvents",budget.maxRecoveryEvents()),
                    integer(b,"maxParseRuleDepth",budget.maxParseRuleDepth()),number(b,"maxSemanticWorkUnits",budget.maxSemanticWorkUnits()),integer(b,"maxDiagnostics",budget.maxDiagnostics()),
                    integer(b,"maxNumericLiteralDigits",budget.maxNumericLiteralDigits()),integer(b,"maxAbsoluteDecimalExponent",budget.maxAbsoluteDecimalExponent()));
        }
        var policy=previous.resolutionPolicy();
        if(root.has("resolutionPolicy")) {
            var p=object(root.get("resolutionPolicy")); keys(p,"preferWorkspaceSource","modelSelections","sourcePriorities");
            boolean prefer=policy.preferWorkspaceSource();
            if(p.has("preferWorkspaceSource")) {
                var v=p.get("preferWorkspaceSource");
                if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException();
                prefer=v.getAsBoolean();
            }
            var selections=policy.modelSelections();
            if(p.has("modelSelections")) {
                selections=new LinkedHashMap<>();
                for(var item:array(p.get("modelSelections"))) {
                    var s=object(item); keys(s,"modelName","modelVersion","sourceHint");
                    var selector=new ModelSelector.ByName(string(s.get("modelName")),optional(s,"modelVersion"),optional(s,"sourceHint").map(SourceId::new));
                    if(selections.putIfAbsent(selector.modelName(),selector)!=null) throw new IllegalArgumentException();
                }
            }
            var priorities=policy.sourcePriorities();
            if(p.has("sourcePriorities")) {
                priorities=new LinkedHashMap<>();
                for(var item:array(p.get("sourcePriorities"))) {
                    var s=object(item); keys(s,"sourceId","priority");
                    if(!s.has("priority") || priorities.putIfAbsent(new SourceId(string(s.get("sourceId"))),integer(s,"priority",0))!=null) throw new IllegalArgumentException();
                }
            }
            policy=new ResolutionPolicy(prefer,selections,priorities);
        }
        var limits=previous.cacheLimits();
        if(root.has("cacheLimits")) {
            var c=object(root.get("cacheLimits")); keys(c,"maxSyntaxEntries","maxAnalysisEntries","maxQueryEntries","maxRetainedSourceUtf16Units");
            limits=new CacheLimits(integer(c,"maxSyntaxEntries",limits.maxSyntaxEntries()),integer(c,"maxAnalysisEntries",limits.maxAnalysisEntries()),
                    integer(c,"maxQueryEntries",limits.maxQueryEntries()),number(c,"maxRetainedSourceUtf16Units",limits.maxRetainedSourceUtf16Units()));
        }
        return new WorkspaceConfiguration(new CompilerOptions(old.semanticProfile(),languages,warnings,budget,old.overlays()),policy,previous.resolverCatalog(),limits);
    }

    private static JsonObject object(JsonElement value) { if(value==null || !value.isJsonObject()) throw new IllegalArgumentException(); return value.getAsJsonObject(); }
    private static JsonArray array(JsonElement value) { if(value==null || !value.isJsonArray()) throw new IllegalArgumentException(); return value.getAsJsonArray(); }
    private static String string(JsonElement value) {
        if(value==null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }
    private static Optional<String> optional(JsonObject value,String key) { return value.has(key)?Optional.of(string(value.get(key))):Optional.empty(); }
    private static void keys(JsonObject value,String... allowed) { if(!Set.of(allowed).containsAll(value.keySet())) throw new IllegalArgumentException(); }
    private static int integer(JsonObject value,String key,int fallback) { return Math.toIntExact(number(value,key,fallback)); }
    private static long number(JsonObject value,String key,long fallback) {
        if(!value.has(key)) return fallback;
        var v=value.get(key); if(!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        return v.getAsBigDecimal().longValueExact();
    }
}

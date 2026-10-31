package guru.interlis.bridge;

import guru.interlis.compiler.*;
import guru.interlis.workspace.*;
import guru.interlis.workspace.resolution.ResolverCatalog;
import java.util.*;

/** Complete configuration replacement, with the same immutable Domain values as JVM hosts. */
final class WorkspaceConfigurationCodec {
    static WorkspaceConfiguration read(JsonValue.JsonObject value) {
        try { return decode(value); }
        catch(IllegalArgumentException | ArithmeticException | ClassCastException | NullPointerException invalid) {
            throw new BridgeException("INVALID_REQUEST","invalid workspace configuration");
        }
    }
    private static WorkspaceConfiguration decode(JsonValue.JsonObject value) {
        keys(value,"expectedWorkspaceRevision","configuration","semanticProfile","allowedLanguages","warningPolicy","analysisBudget","overlays","resolutionPolicy","resolverCatalog","cacheLimits");
        if(value.members().containsKey("configuration")) {
            keys(value,"expectedWorkspaceRevision","configuration");
            return decode(BridgeCodec.optionalObject(value,"configuration").orElseThrow(() -> new BridgeException("INVALID_REQUEST","configuration is required")));
        }
        var defaults=WorkspaceConfiguration.defaults();var old=defaults.compilerOptions();
        var overlays=OverlayCodec.options(value,old);
        var warnings=value.members().containsKey("warningPolicy")?WarningPolicy.valueOf(BridgeCodec.string(value,"warningPolicy")):old.warningPolicy();
        var profile=value.members().containsKey("semanticProfile")?new SemanticProfileId(BridgeCodec.string(value,"semanticProfile")):old.semanticProfile();
        var budget=old.budget();
        if(value.members().containsKey("analysisBudget")) {
            var b=BridgeCodec.optionalObject(value,"analysisBudget").orElseThrow();
            keys(b,"maxSourceUtf16Length","maxTotalSourceUtf16Length","maxTokens","maxSyntaxNodes","maxRecoveryEvents","maxParseRuleDepth","maxSemanticWorkUnits","maxDiagnostics","maxNumericLiteralDigits","maxAbsoluteDecimalExponent");
            budget=new AnalysisBudget(integer(b,"maxSourceUtf16Length",budget.maxSourceUtf16Length()),number(b,"maxTotalSourceUtf16Length",budget.maxTotalSourceUtf16Length()),
                    number(b,"maxTokens",budget.maxTokens()),number(b,"maxSyntaxNodes",budget.maxSyntaxNodes()),integer(b,"maxRecoveryEvents",budget.maxRecoveryEvents()),
                    integer(b,"maxParseRuleDepth",budget.maxParseRuleDepth()),number(b,"maxSemanticWorkUnits",budget.maxSemanticWorkUnits()),integer(b,"maxDiagnostics",budget.maxDiagnostics()),
                    integer(b,"maxNumericLiteralDigits",budget.maxNumericLiteralDigits()),integer(b,"maxAbsoluteDecimalExponent",budget.maxAbsoluteDecimalExponent()));
        }
        var cache=defaults.cacheLimits();
        if(value.members().containsKey("cacheLimits")) {
            var c=BridgeCodec.optionalObject(value,"cacheLimits").orElseThrow();keys(c,"maxSyntaxEntries","maxAnalysisEntries","maxQueryEntries","maxRetainedSourceUtf16Units");
            cache=new CacheLimits(integer(c,"maxSyntaxEntries",cache.maxSyntaxEntries()),integer(c,"maxAnalysisEntries",cache.maxAnalysisEntries()),integer(c,"maxQueryEntries",cache.maxQueryEntries()),number(c,"maxRetainedSourceUtf16Units",cache.maxRetainedSourceUtf16Units()));
        }
        var entries=new LinkedHashMap<String,String>();
        if(value.members().containsKey("resolverCatalog")) {
            var catalog=BridgeCodec.optionalObject(value,"resolverCatalog").orElseThrow();
            for(var key:catalog.members().keySet()) entries.put(key,BridgeCodec.string(catalog,key));
        }
        return new WorkspaceConfiguration(new CompilerOptions(profile,overlays.allowedLanguages(),warnings,budget,overlays.overlays()),ResolutionPolicyCodec.read(value),new ResolverCatalog(entries),cache);
    }
    private static void keys(JsonValue.JsonObject value,String... allowed) { if(!Set.of(allowed).containsAll(value.members().keySet()))throw new BridgeException("INVALID_REQUEST","unknown configuration field"); }
    private static long number(JsonValue.JsonObject value,String name,long fallback) { return value.members().containsKey(name)?BridgeCodec.longValue(value.members().get(name),name):fallback; }
    private static int integer(JsonValue.JsonObject value,String name,int fallback) { return Math.toIntExact(number(value,name,fallback)); }
}

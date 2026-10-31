package guru.interlis.bridge;

import java.util.Set;

/** Accepted host bounds, separate from language rules and semantic analysis budgets. */
record SessionLimits(int maxSourceUtf16Length,long maxTotalSourceUtf16Length,int maxSources,
                     int maxRetainedSnapshots,int maxMessageUtf16Length,int maxConcurrentSessions) {
    static final int MAX_MESSAGE=16_777_216;
    static SessionLimits defaults() { return new SessionLimits(2_097_152,8_388_608L,128,32,4_194_304,8); }
    static SessionLimits read(JsonValue.JsonObject params) {
        var d=defaults(); var optional=BridgeCodec.optionalObject(params,"limits");
        if(optional.isEmpty()) return d;
        var value=optional.orElseThrow();
        if(!Set.of("maxSourceUtf16Length","maxTotalSourceUtf16Length","maxSources","maxRetainedSnapshots","maxMessageUtf16Length","maxConcurrentSessions").containsAll(value.members().keySet()))
            throw new BridgeException("INVALID_REQUEST","unknown session limit");
        var result=new SessionLimits((int)number(value,"maxSourceUtf16Length",d.maxSourceUtf16Length,8_388_608),
                number(value,"maxTotalSourceUtf16Length",d.maxTotalSourceUtf16Length,67_108_864),
                (int)number(value,"maxSources",d.maxSources,4096),(int)number(value,"maxRetainedSnapshots",d.maxRetainedSnapshots,256),
                (int)number(value,"maxMessageUtf16Length",d.maxMessageUtf16Length,MAX_MESSAGE),
                (int)number(value,"maxConcurrentSessions",d.maxConcurrentSessions,8));
        if(result.maxTotalSourceUtf16Length<result.maxSourceUtf16Length) throw new BridgeException("INVALID_REQUEST","total source limit must allow one source");
        return result;
    }
    private static long number(JsonValue.JsonObject value,String name,long fallback,long ceiling) {
        if(!value.members().containsKey(name)) return fallback;
        long result=BridgeCodec.longValue(value.members().get(name),name);
        if(result<=0 || result>ceiling) throw new BridgeException("INVALID_REQUEST","session limit outside supported range: "+name);
        return result;
    }
    JsonValue.JsonObject encode() { return JsonValues.object(
            "maxSourceUtf16Length",JsonValues.number(maxSourceUtf16Length),"maxTotalSourceUtf16Length",JsonValues.string(Long.toString(maxTotalSourceUtf16Length)),
            "maxSources",JsonValues.number(maxSources),"maxRetainedSnapshots",JsonValues.number(maxRetainedSnapshots),
            "maxMessageUtf16Length",JsonValues.number(maxMessageUtf16Length),"maxConcurrentSessions",JsonValues.number(maxConcurrentSessions)); }
}

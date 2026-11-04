package build.interlis;

import java.util.*;
import java.util.stream.Collectors;

/** Small deterministic JSON writer for build receipts; no product serialization contract. */
final class JsonOutput {
    static String write(Object value) {
        if(value==null)return "null";
        if(value instanceof String text)return "\""+build.jenesis.Json.escaped(text)+"\"";
        if(value instanceof Boolean||value instanceof Number)return value.toString();
        if(value instanceof Map<?,?> map)return map.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString())).map(e->write(e.getKey().toString())+":"+write(e.getValue())).collect(Collectors.joining(",","{","}"));
        if(value instanceof Collection<?> list)return list.stream().map(JsonOutput::write).collect(Collectors.joining(",","[","]"));
        throw new IllegalArgumentException("Unsupported receipt type: "+value.getClass());
    }
}

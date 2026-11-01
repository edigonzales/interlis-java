package guru.interlis.cli.verification;

import com.google.gson.*;
import guru.interlis.bridge.BridgeDispatcher;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** A3: actual public graph projections on JVM, JS, WasmGC and the pinned ili2c metamodel.
 * Only the enumerated common properties are comparable. Internal ids, diagnostics,
 * origins, expression graphs and translation semantics are deliberately not equated. */
public final class SemanticComparison {
    private SemanticComparison() { }
    static final List<String> ELEMENTS=List.of("F.Child","F.T.Link","F.U.Link");
    static JsonObject call(BridgeDispatcher bridge,String operation,String session,Map<String,Object> params) {
        var request=new LinkedHashMap<String,Object>();request.put("protocol","interlis.compiler");request.put("protocolVersion","1.0");
        request.put("requestId","a3");request.put("operation",operation);if(session!=null)request.put("sessionId",session);request.put("params",params);
        var response=JsonParser.parseString(bridge.handle(JSON.toJson(request))).getAsJsonObject();
        require(s(response,"status").equals("ok"),"Bridge failure: "+response);return response.getAsJsonObject("result");
    }
    static JsonObject project(Path file) throws Exception {
        String text=Files.readString(file),source="memory:/a3.ili";var bridge=new BridgeDispatcher();
        String session=s(call(bridge,"openSession",null,Map.of("sources",List.of(Map.of("sourceId",source,"text",text)))),"sessionId");
        try {
            var compiled=call(bridge,"compile",session,Map.of("expectedWorkspaceRevision","1","roots",List.of(Map.of("kind","byName","modelName","F"))));
            var result=new JsonObject();result.addProperty("outcome",s(compiled,"outcome"));var elements=new JsonObject();result.add("elements",elements);
            if(s(compiled,"outcome").equals("succeeded"))for(String name:ELEMENTS) {
                String simple=name.substring(name.lastIndexOf('.')+1);int offset=text.indexOf(name.contains(".U.")?"TOPIC U":"TOPIC T");
                offset=text.indexOf(simple+" ",name.equals("F.Child")?0:offset);
                var properties=call(bridge,"query",session,Map.of("snapshotId",s(compiled,"snapshotId"),"query",Map.of("kind","semanticProperties","sourceId",source,"revision","1","startOffset",offset,"endOffset",offset)));
                require(s(properties,"qualifiedName").equals(name),"Wrong projection owner: "+properties);
                var members=new JsonArray();for(var member:rows(properties,"effectiveMembers"))members.add(JSON.toJsonTree(Map.of("name",s(member,"name"),"targets",strings(member.get("targets")))));
                elements.add(name,members);
            }
            return result;
        } finally { call(bridge,"closeSession",session,Map.of()); }
    }
    static Object invoke(Object object,String method,Object... args) throws Exception {
        for(var m:object.getClass().getMethods())if(m.getName().equals(method)&&m.getParameterCount()==args.length)return m.invoke(object,args);
        throw new NoSuchMethodException(method);
    }
    static JsonObject reference(ClassLoader loader,Path file) throws Exception {
        Class<?> configuration=loader.loadClass("ch.interlis.ili2c.config.Configuration"),entry=loader.loadClass("ch.interlis.ili2c.config.FileEntry");
        var config=configuration.getConstructor().newInstance();invoke(config,"setAutoCompleteModelList",false);
        invoke(config,"addFileEntry",entry.getConstructor(String.class,int.class).newInstance(file.toString(),1));
        var description=loader.loadClass("ch.interlis.ili2c.Main").getMethod("runCompiler",configuration).invoke(null,config);
        var result=new JsonObject();result.addProperty("outcome",description==null?"rejected":"succeeded");var elements=new JsonObject();result.add("elements",elements);
        if(description!=null)for(String name:ELEMENTS) {
            var owner=invoke(description,"getElement",name);require(owner!=null,"Reference owner absent: "+name);
            var members=new JsonArray();var iterator=(Iterator<?>)invoke(owner,"getAttributesAndRoles2");
            while(iterator.hasNext()) {
                var transfer=iterator.next();var member=transfer.getClass().getField("obj").get(transfer);var targets=new ArrayList<String>();
                if(loader.loadClass("ch.interlis.ili2c.metamodel.RoleDef").isInstance(member)) {
                    var destinations=(Iterator<?>)invoke(member,"iteratorDestination");while(destinations.hasNext())targets.add((String)invoke(destinations.next(),"getScopedName"));
                }
                members.add(JSON.toJsonTree(Map.of("name",invoke(member,"getName"),"targets",targets)));
            }
            elements.add(name,members);
        }
        return result;
    }
    static void compare(JsonObject expected,JsonObject actual) { require(expected.equals(actual),"Semantic mismatch: expected "+expected+" actual "+actual); }
    static int counterchecks(JsonObject good,JsonObject rejected) {
        int checks=0;
        var missing=good.deepCopy();missing.getAsJsonObject("elements").getAsJsonArray("F.Child").remove(0);mustReject(good,missing);checks++;
        var wrong=good.deepCopy();wrong.getAsJsonObject("elements").getAsJsonArray("F.T.Link").get(0).getAsJsonObject().add("targets",JSON.toJsonTree(List.of("F.U.A")));mustReject(good,wrong);checks++;
        var order=good.deepCopy();var members=order.getAsJsonObject("elements").getAsJsonArray("F.Child");var first=members.get(0);members.set(0,members.get(1));members.set(1,first);mustReject(good,order);checks++;
        var falseValid=rejected.deepCopy();falseValid.addProperty("outcome","succeeded");mustReject(rejected,falseValid);return checks+1;
    }
    static void mustReject(JsonObject expected,JsonObject mutant) {
        try {compare(expected,mutant);}catch(IllegalArgumentException expectedFailure){return;}throw new AssertionError("Undetected semantic mutation");
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),out=root.resolve("build/reports/g2/semantic-comparison.json");Files.deleteIfExists(out);
        Path jar=ReferenceProcesses.prepare(root);var cases=new ArrayList<Map<String,Object>>();int mutants=0;
        try(var loader=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},ClassLoader.getPlatformClassLoader())) {
            for(String version:List.of("2.3","2.4")) {
                JsonObject good=null,bad=null;
                for(String name:List.of("ordered-targets-","invalid-")) {
                    Path file=root.resolve("testdata/g2/a3/"+name+version+".ili");var jvm=project(file);var ref=reference(loader,file);compare(ref,jvm);
                    String expected=name.startsWith("invalid")?"rejected":"succeeded";require(s(jvm,"outcome").equals(expected),"Wrong gate: "+file);
                    if(expected.equals("succeeded")) {good=jvm;require(jvm.getAsJsonObject("elements").getAsJsonArray("F.Child").size()==3,"Lost inherited member");}else bad=jvm;
                    cases.add(Map.of("source",root.relativize(file).toString(),"sha256",hash(file),"jvm",jvm,"reference",ref));
                }
                mutants+=counterchecks(good,bad);
            }
        }
        Path input=root.resolve("build/reports/g2/a3-jvm.json");write(input,cases);
        var portable=run(root,List.of(args[1],root.resolve("tools/reference/semantic-comparison.cjs").toString(),input.toString()),Duration.ofSeconds(90));
        require(portable.exitCode()==0,"Portable semantic comparison failed: "+portable);
        pin(jar,ReferenceProcesses.JAR);write(out,Map.of("status","passed","referenceJarSha256",ReferenceProcesses.JAR,"cases",cases,"mutationChecks",mutants,"portable",portable,
                "limits",List.of("Three selected viewables only; not a whole-language reference oracle","Only compilation outcome, ordered inherited members and full qualified role targets are common","Origins, diagnostic identities/ranges, expression graphs, generic and translation contracts remain covered by separate direct assertions; not reference-equated")));
        System.out.println("SEMANTIC_COMPARISON_PASS 4 actual reference/JVM/JS/WasmGC cases; "+mutants+" mutation checks");
    }
}

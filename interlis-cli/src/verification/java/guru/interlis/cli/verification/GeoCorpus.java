package guru.interlis.cli.verification;

import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import com.google.gson.*;
import static guru.interlis.cli.verification.HostChecks.*;

/** Verification of the existing thirteen-file corpus; never repins or discovers replacements. */
public final class GeoCorpus {
    private GeoCorpus() { }
    public static void main(String[] args) throws Exception { verify(Path.of(args[0]).toAbsolutePath()); }
    static Path cacheDirectory(Path root) {
        return System.getenv("GEO_CORPUS_CACHE")!=null ? cache(root,"GEO_CORPUS_CACHE","geo-corpus") : cache(root,"CONFORMANCE_CACHE","geo-corpus");
    }
    static JsonObject verify(Path root) throws Exception {
        Path report=root.resolve("build/reports/performance/corpus-verification.json");Files.deleteIfExists(report);
        JsonObject manifest=read(root.resolve("docs/evidence/p3/geo-corpus.json"));Path cache=cacheDirectory(root);
        var index=manifest.getAsJsonObject("index");Path indexPath=cache.resolve("ilimodels.xml");
        seed(root.resolve("docs/evidence/p3/corpus-seed.zip"),cache,manifest);
        pin(indexPath,s(index,"sha256"));
        validateIndex(indexPath,manifest);
        var models=rows(manifest,"models");require(models.size()==13,"Changed corpus selection");Set<String> files=new HashSet<>();
        for(var model:models) {
            String file=s(model,"file");require(files.add(file) && !Path.of(file).isAbsolute() && !List.of(file.split("/")).contains(".."),"Duplicate/unsafe corpus file");
            Path target=cache.resolve(file.replace("/","__"));pin(target,s(model,"sha256"));
            require(Files.size(target)==model.get("bytes").getAsLong(),"Changed corpus byte size");
        }
        write(report,Map.of("status","passed","models",manifest.get("models"),"indexSha256",s(index,"sha256"),"scope","historical seed corpus, not full P3"));
        System.out.println("CORPUS_PASS "+models.size()+" models");return manifest;
    }
    static void seed(Path archive,Path cache,JsonObject manifest) throws Exception {
        Map<String,String> expected=new TreeMap<>();expected.put("ilimodels.xml",s(manifest.getAsJsonObject("index"),"sha256"));
        for(var row:rows(manifest,"models"))require(expected.put(s(row,"file").replace("/","__"),s(row,"sha256"))==null,"Duplicate seed source");
        try(var zip=new ZipFile(archive.toFile())) {
            var names=zip.stream().map(e->e.getName()).toList();require(names.size()==expected.size() && new HashSet<>(names).equals(expected.keySet()),"Unexpected/missing corpus seed entry");
            Files.createDirectories(cache);
            for(var item:expected.entrySet()) {
                byte[] bytes;try(var input=zip.getInputStream(zip.getEntry(item.getKey()))){bytes=input.readAllBytes();}
                require(hash(bytes).equals(item.getValue()),"Changed corpus seed hash: "+item.getKey());
                Path target=cache.resolve(item.getKey());if(!Files.exists(target))Files.write(target,bytes,StandardOpenOption.CREATE_NEW);
            }
        }
    }
    static void validateIndex(Path path, JsonObject manifest) throws Exception {
        pin(path,s(manifest.getAsJsonObject("index"),"sha256"));
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var all=factory.newDocumentBuilder().parse(path.toFile()).getElementsByTagName("*");
        for(var model:rows(manifest,"models")) {
            List<Map<String,String>> matches=new ArrayList<>();
            for(int i=0;i<all.getLength();i++) {
                Element element=(Element)all.item(i);if(!element.getLocalName().endsWith("ModelMetadata"))continue;
                Map<String,String> fields=new HashMap<>();var children=element.getChildNodes();
                for(int j=0;j<children.getLength();j++)if(children.item(j) instanceof Element child)fields.put(child.getLocalName(),child.getTextContent());
                if(s(model,"file").equals(fields.get("File")) && s(model,"model").equals(fields.get("Name")))matches.add(fields);
            }
            require(matches.size()==1,"Missing/ambiguous corpus metadata: "+s(model,"file"));var actual=matches.getFirst();
            require(s(model,"version").equals(actual.get("Version")) && ("ili"+s(model,"language").replace(".","_")).equals(actual.get("SchemaLanguage")),"Changed corpus version/language");
        }
    }
}

package guru.interlis.cli.verification;

import com.google.gson.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import static guru.interlis.cli.verification.HostChecks.*;

/** Host-only loader for the complete pinned P3 corpus; no discovery or network fallback. */
public final class FullCorpus {
    static final String INDEX_SHA="ff9561b4e248da29c0bde4f65e77c1e22e00e45a4f7c8538756debd11451d93c";
    private FullCorpus() { }
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),directory=root.resolve("docs/evidence/p3/full-corpus");
        Path output=root.resolve("build/reports/performance/full-corpus/prepared.json");Files.deleteIfExists(output);
        var prepared=prepare(directory);
        write(output,prepared);
        write(output.resolveSibling("input-verification.json"),Map.of("status","passed","roots",347,"files",366,
                "manifestSha256",hash(directory.resolve("manifest.json")),"archiveSha256",hash(directory.resolve("sources.zip")),"indexSha256",INDEX_SHA));
        System.out.println("FULL_CORPUS_INPUT_PASS roots=347 files=366");
    }
    static JsonObject prepare(Path directory) throws Exception {
        var manifest=read(directory.resolve("manifest.json"));
        require(s(manifest,"schemaVersion").equals("1.0")&&s(manifest,"profile").equals("geo-so-complete-2.3-2.4-v1"),"Unsupported full corpus profile");
        var index=manifest.getAsJsonObject("index");var archive=manifest.getAsJsonObject("archive");
        require(s(index,"sha256").equals(INDEX_SHA)&&s(archive,"file").equals("sources.zip"),"Changed corpus index/archive");
        Path archivePath=directory.resolve("sources.zip");pin(archivePath,s(archive,"sha256"));
        require(Files.size(archivePath)==archive.get("bytes").getAsLong(),"Changed archive size");
        var files=rows(manifest,"files");var roots=rows(manifest,"roots");
        require(files.size()==366&&roots.size()==347,"Incomplete corpus selection");
        Map<String,JsonObject> byFile=new LinkedHashMap<>();
        Map<String,String> texts=new LinkedHashMap<>();
        byte[] indexBytes;
        try(var zip=new ZipFile(archivePath.toFile())) {
            Set<String> expected=new HashSet<>(List.of("ilimodels.xml"));
            for(var file:files) {
                String name=s(file,"file");
                require(!name.startsWith("/")&&!name.contains("\\")&&!List.of(name.split("/",-1)).contains("..")&&expected.add(name),"Unsafe or duplicate corpus path");
                require(s(file,"sourceId").equals("memory:/p3-corpus/"+name),"Changed corpus source identity");
                byFile.put(name,file);
            }
            var entries=zip.stream().map(ZipEntry::getName).toList();
            require(entries.size()==expected.size()&&new HashSet<>(entries).equals(expected),"Missing/extra/duplicate archive entry");
            indexBytes=bytes(zip,"ilimodels.xml",index);
            for(var file:files) {
                byte[] bytes=bytes(zip,s(file,"file"),file);
                String encoding=s(file,"encoding");require(Set.of("UTF-8","windows-1252").contains(encoding),"Unsupported corpus encoding");
                String text=Charset.forName(encoding).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                require(text.length()==file.get("utf16Length").getAsInt()&&hash(text.getBytes(StandardCharsets.UTF_16BE)).equals(s(file,"textSha256")),"Changed decoded corpus text: "+s(file,"file"));
                texts.put(s(file,"file"),text);
            }
        }
        validateIndex(indexBytes,roots);
        JsonArray preparedRoots=new JsonArray();Set<String> used=new HashSet<>();
        for(var root:roots) {
            require(byFile.containsKey(s(root,"file")),"Missing root source");
            JsonObject prepared=root.deepCopy();JsonArray sources=new JsonArray();Set<String> rootFiles=new HashSet<>();
            for(String file:strings(root.get("sources"))) {
                require(byFile.containsKey(file)&&rootFiles.add(file),"Missing/duplicate closure source");used.add(file);
                var source=byFile.get(file).deepCopy();source.addProperty("text",texts.get(file));sources.add(source);
            }
            require(rootFiles.contains(s(root,"file")),"Root absent from its closure");
            var selections=root.getAsJsonObject("configuration").getAsJsonObject("resolutionPolicy").getAsJsonArray("modelSelections");
            Set<String> selected=new HashSet<>();for(var value:selections){var selection=value.getAsJsonObject();require(selected.add(s(selection,"modelName"))&&rootFiles.stream().anyMatch(f->s(byFile.get(f),"sourceId").equals(s(selection,"sourceHint"))),"Invalid/duplicate model selection");}
            require(selected.contains(s(root,"model")),"Missing requested root selection");
            prepared.add("sources",sources);preparedRoots.add(prepared);
        }
        require(used.equals(byFile.keySet()),"Unused corpus source");
        JsonObject result=new JsonObject();result.addProperty("schemaVersion","1.0");result.addProperty("profile",s(manifest,"profile"));result.addProperty("manifestSha256",hash(directory.resolve("manifest.json")));result.addProperty("indexSha256",INDEX_SHA);result.add("roots",preparedRoots);return result;
    }
    private static byte[] bytes(ZipFile zip,String name,JsonObject pin) throws Exception {
        byte[] bytes;try(var input=zip.getInputStream(zip.getEntry(name))){bytes=input.readNBytes(4_000_001);}
        require(bytes.length<=4_000_000&&bytes.length==pin.get("bytes").getAsInt()&&hash(bytes).equals(s(pin,"sha256")),"Changed/oversized corpus entry: "+name);return bytes;
    }
    private static void validateIndex(byte[] bytes,List<JsonObject> roots) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
        var nodes=factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getElementsByTagName("*");
        Set<List<String>> actual=new HashSet<>();int count=0,unsupported=0;
        for(int i=0;i<nodes.getLength();i++) {var element=(Element)nodes.item(i);if(!element.getLocalName().endsWith("ModelMetadata"))continue;
            Map<String,String> fields=new HashMap<>();for(var child=element.getFirstChild();child!=null;child=child.getNextSibling())if(child instanceof Element e)fields.put(e.getLocalName(),e.getTextContent());
            String language=fields.get("SchemaLanguage");if(!Set.of("ili2_3","ili2_4").contains(language)){unsupported++;continue;}
            count++;require(actual.add(List.of(fields.get("Name"),fields.get("Version"),fields.get("File"),language,fields.get("md5"))),"Duplicate index root");
        }
        Set<List<String>> expected=new HashSet<>();for(var root:roots)require(expected.add(List.of(s(root,"model"),s(root,"version"),s(root,"file"),s(root,"language"),s(root,"indexMd5"))),"Duplicate selected root");
        require(count==347&&unsupported==6&&actual.equals(expected),"Corpus roots differ from complete official index");
    }
}

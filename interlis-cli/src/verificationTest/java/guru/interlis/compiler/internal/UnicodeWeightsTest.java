package guru.interlis.compiler.internal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent explicit-NFD oracle using the original pinned Unicode 17 inputs, no generator. */
class UnicodeWeightsTest {
    static final Path ROOT=Path.of(System.getProperty("repo.root"));
    static String sha(byte[] value) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    static String pinned(String name,String sha) throws Exception {
        return pinned(ROOT.resolve("docs/evidence/stabilization/registries/unicode-17.0.0/"+name+".txt.gz"),sha);
    }
    static String pinned(Path path,String sha) throws Exception {
        byte[] bytes;
        try(var input=new GZIPInputStream(Files.newInputStream(path))) {bytes=input.readAllBytes();}
        assertEquals(sha,sha(bytes));return new String(bytes,StandardCharsets.UTF_8);
    }
    @Test void frozenTableAndBothOriginalUnicodeSourcesRemainPinned() throws Exception {
        assertEquals("47e90b02842b0305ff9ef63fc20a8eb9478d2c9f44385408392a9381eaecd207",sha(Files.readAllBytes(ROOT.resolve("interlis-compiler/src/main/java/guru/interlis/compiler/internal/TextLengths.java"))));
        pinned("UnicodeData","2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c");
        pinned("NormalizationTest","5019ffd530751a741900c849c0e010332f142a3612234639bd200b82138a87db");
        Path changed=Files.createTempFile("interlis-unicode-", ".gz");
        try {
            try(var output=new java.util.zip.GZIPOutputStream(Files.newOutputStream(changed))) {output.write("tampered".getBytes(StandardCharsets.UTF_8));}
            assertThrows(AssertionError.class,()->pinned(changed,"5019ffd530751a741900c849c0e010332f142a3612234639bd200b82138a87db"));
        } finally {Files.deleteIfExists(changed);}
    }
    @Test void everyExplicitNfdNormalizationColumnAgreesWithTheExistingTable() throws Exception {
        String unicode=pinned("UnicodeData","2e1efc1dcb59c575eedf5ccae60f95229f706ee6d031835247d843c11d96470c");
        String tests=pinned("NormalizationTest","5019ffd530751a741900c849c0e010332f142a3612234639bd200b82138a87db");
        Map<Integer,Integer> ccc=new HashMap<>();for(String line:unicode.lines().toList()){String[] fields=line.split(";",-1);ccc.put(Integer.parseInt(fields[0],16),Integer.parseInt(fields[3]));}
        int checked=0;
        for(String original:tests.lines().toList()) {
            String line=original.split("#",2)[0].trim();if(line.isEmpty()||line.startsWith("@"))continue;
            var fields=line.split(";");List<String> columns=new ArrayList<>();
            for(int i=0;i<5;i++){StringBuilder text=new StringBuilder();for(String hex:fields[i].trim().split(" +"))if(!hex.isEmpty())text.appendCodePoint(Integer.parseInt(hex,16));columns.add(text.toString());}
            for(int[] pair:new int[][]{{0,2},{1,2},{2,2},{3,4},{4,4}}) {
                int expected=(int)columns.get(pair[1]).codePoints().filter(cp->ccc.getOrDefault(cp,0)==0).count();
                assertEquals(expected,TextLengths.canonicalLength(columns.get(pair[0])),line);checked++;
            }
        }
        assertEquals(100170,checked);
    }
}

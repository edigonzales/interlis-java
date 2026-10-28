package guru.interlis.compiler.internal;

import java.nio.file.*;
import java.util.*;

/** Independent explicit-NFD oracle from pinned Unicode NormalizationTest data. */
public final class TextLengthNormalizationProbe {
    public static void main(String[] args) throws Exception {
        var ccc=new HashMap<Integer,Integer>();
        for(var line:Files.readAllLines(Path.of(args[0]))) {
            var fields=line.split(";",-1);ccc.put(Integer.parseInt(fields[0],16),Integer.parseInt(fields[3]));
        }
        int checked=0;
        for(var original:Files.readAllLines(Path.of(args[1]))) {
            var line=original.split("#",2)[0].trim();
            if(line.isEmpty()||line.startsWith("@"))continue;
            var fields=line.split(";");var columns=new ArrayList<String>();
            for(int i=0;i<5;i++) {
                var text=new StringBuilder();
                for(var hex:fields[i].trim().split(" +"))if(!hex.isEmpty())text.appendCodePoint(Integer.parseInt(hex,16));
                columns.add(text.toString());
            }
            for(var pair:new int[][]{{0,2},{1,2},{2,2},{3,4},{4,4}}) {
                int expected=(int)columns.get(pair[1]).codePoints().filter(cp->ccc.getOrDefault(cp,0)==0).count();
                int actual=TextLengths.canonicalLength(columns.get(pair[0]));
                if(expected!=actual)throw new AssertionError("normalization row "+line+": "+actual+" != "+expected);
                checked++;
            }
        }
        System.out.println("{\"unicode\":\"17.0.0\",\"status\":\"passed\",\"normalizationChecks\":"+checked+"}");
    }
}

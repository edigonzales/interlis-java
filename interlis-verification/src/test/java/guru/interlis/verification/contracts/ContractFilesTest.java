package guru.interlis.verification.contracts;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ContractFilesTest {
    @TempDir Path temporary;
    @Test void codePointOffsetsHashesAndPathConfinementAreExact() throws Exception {
        assertEquals("😀", ContractFiles.slice("x😀y",1,2));
        assertEquals("y", ContractFiles.slice("x😀y",2,3));
        for(int[] range:new int[][]{{-1,1},{2,1},{0,2}})
            assertThrows(IllegalArgumentException.class,()->ContractFiles.slice("x",range[0],range[1]));
        var files=new ContractFiles(temporary);Files.writeString(temporary.resolve("input"),"hello");
        files.requireHash("input",ContractFiles.hash("hello"));
        assertThrows(IllegalArgumentException.class,()->files.requireHash("input","0".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->files.inside("../outside"));
        assertThrows(IllegalArgumentException.class,()->files.inside(temporary.toString()));
        var outside=Files.createTempFile("contract-external", ".txt");
        try { Files.createSymbolicLink(temporary.resolve("escape"),outside);
            assertThrows(IllegalArgumentException.class,()->files.inside("escape"));
        } finally { Files.delete(outside); }
    }
}

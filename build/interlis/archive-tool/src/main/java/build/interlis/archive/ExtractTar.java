package build.interlis.archive;

import java.nio.file.*;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Extracts a pinned suite archive into a fresh directory; links and traversal are rejected. */
public final class ExtractTar {
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Expected verified tar.gz and fresh output");
        Path archive=Path.of(args[0]),output=Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectory(output);String prefix=null;long bytes=0;int count=0;
        try(var gzip=new GzipCompressorInputStream(Files.newInputStream(archive));var tar=new TarArchiveInputStream(gzip)) {
            for(var entry=tar.getNextEntry();entry!=null;entry=tar.getNextEntry()) {
                String name=entry.getName();
                if(++count>100_000||entry.isLink()||entry.isSymbolicLink()||(!entry.isDirectory()&&!entry.isFile())||name.startsWith("/")||name.contains("\\")||name.contains(":"))throw new IllegalArgumentException("Unsafe suite archive entry");
                var parts=name.split("/",2);if(prefix==null)prefix=parts[0];
                if(!prefix.equals(parts[0]))throw new IllegalArgumentException("Multiple suite archive roots");
                if(parts.length==1||parts[1].isEmpty())continue;
                Path relative=Path.of(parts[1]);if(relative.isAbsolute()||java.util.List.of(parts[1].split("/")).contains(".."))throw new IllegalArgumentException("Suite traversal");
                Path file=output.resolve(relative).normalize();if(!file.startsWith(output))throw new IllegalArgumentException("Suite traversal");
                if(entry.isDirectory()){Files.createDirectories(file);continue;}
                if(entry.getSize()<0||(bytes+=entry.getSize())>512L*1024*1024)throw new IllegalArgumentException("Oversize suite archive");
                Files.createDirectories(file.getParent());try(var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){tar.transferTo(out);}
            }
        }
        if(!Files.isRegularFile(output.resolve("InterlisConformance.java")))throw new IllegalArgumentException("Missing pinned runner");
    }
}

package build.interlis;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/** Deterministic file bindings. Missing files and escaping symbolic links fail closed. */
public final class JvmBuildInputs {
    public static List<String> projectModules(Path root) throws Exception {
        var modules = xml(root.resolve("pom.xml")).getElementsByTagName("modules").item(0);
        var names = new ArrayList<String>();
        for (var child = modules.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof org.w3c.dom.Element element && element.getTagName().equals("module")) {
                names.add(element.getTextContent().strip());
            }
        }
        return List.copyOf(names);
    }

    /** Authoritative product version from the root POM, shared by builders and consumers. */
    public static String projectVersion(Path root) throws Exception {
        var document=xml(root.resolve("pom.xml"));
        for(var node=document.getDocumentElement().getFirstChild();node!=null;node=node.getNextSibling())
            if(node instanceof org.w3c.dom.Element element && element.getTagName().equals("version")) {
                String value=element.getTextContent().trim();
                if(value.matches("[0-9][A-Za-z0-9_.-]*"))return value;
                throw new IllegalStateException("Invalid product version: "+value);
            }
        throw new IllegalStateException("Missing product version");
    }

    /** Rejects symbolic-link ancestors before writing or trusting a generated output. */
    public static Path requireOutputRoot(Path root,String relative) throws IOException {
        root=root.toAbsolutePath().normalize();Path path=root.resolve(relative).normalize();
        if(!path.startsWith(root))throw new IllegalArgumentException("Output escapes repository");
        for(Path ancestor=path;ancestor!=null&&ancestor.startsWith(root)&&!ancestor.equals(root);ancestor=ancestor.getParent())
            if(Files.isSymbolicLink(ancestor))throw new IllegalStateException("Symlink in generated output: "+ancestor);
        return path;
    }

    public static Document xml(Path file) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        try (var input = Files.newInputStream(file)) { return factory.newDocumentBuilder().parse(input); }
    }

    public static Path confined(Path base, String relative) throws IOException {
        Path fragment = Path.of(relative);
        if (fragment.isAbsolute() || relative.contains("\\") || relative.contains(":"))
            throw new IllegalArgumentException("Expected relative input: " + relative);
        Path file = base.resolve(fragment).normalize();
        if (!file.startsWith(base) || !file.toRealPath().startsWith(base.toRealPath()))
            throw new IllegalArgumentException("Input escapes root: " + relative);
        return file;
    }

    public static String sha256(Path file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = in.read(buffer)) >= 0;) digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
}

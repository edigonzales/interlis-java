package guru.interlis.repository;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Reads IliSite09 metadata without external entities. Links preserve XML order; cross-repository links are intentional. */
public final class RepositorySiteReader {
    public record Site(List<URI> parents, List<URI> subsidiaries) {
        public Site { parents=List.copyOf(parents); subsidiaries=List.copyOf(subsidiaries); }
    }
    /** Resolves relative links at the supplied base; accepts only HTTP(S) and file repository locations. */
    public Site read(IndexPayload payload, RepositoryBase base) {
        Objects.requireNonNull(payload,"payload"); Objects.requireNonNull(base,"base");
        try {
            var factory=DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder(); builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            var document=builder.parse(new ByteArrayInputStream(payload.content().getBytes(StandardCharsets.UTF_8)));
            var parents=new ArrayList<URI>(); var subsidiaries=new ArrayList<URI>(); boolean found=false;
            var all=document.getElementsByTagName("*");
            for(int i=0;i<all.getLength();i++) {
                var element=(Element)all.item(i); String name=local(element);
                if(name.equals("IliSite09.SiteMetadata.Site") || name.equals("Site")) found=true;
                if(!name.equals("parentSite") && !name.equals("subsidiarySite"))continue;
                var values=element.getElementsByTagName("*");
                for(int j=0;j<values.getLength();j++)if(local(values.item(j)).equals("value")) {
                    String value=values.item(j).getTextContent().trim();
                    if(value.isEmpty())continue;
                    URI uri=base.uri().resolve(value);
                    if(uri.getScheme()==null || !Set.of("http","https","file").contains(uri.getScheme().toLowerCase(Locale.ROOT)) || uri.getQuery()!=null || uri.getFragment()!=null)
                        throw new IllegalArgumentException("unsafe repository site link");
                    if (!"file".equalsIgnoreCase(base.uri().getScheme()) && "file".equalsIgnoreCase(uri.getScheme()))
                        throw new IllegalArgumentException("remote site cannot introduce local file access");
                    var normalized=URI.create(SourceUris.canonicalFile(uri.toString()));
                    (name.equals("parentSite")?parents:subsidiaries).add(normalized);
                }
            }
            if(!found)throw new IllegalArgumentException("missing site metadata");
            return new Site(parents,subsidiaries);
        } catch(Exception invalid) {
            throw new RepositoryIndexException(RepositoryIndexException.Reason.MALFORMED,"invalid repository site metadata",invalid);
        }
    }
    private static String local(Node node) { String name=node.getNodeName();int colon=name.indexOf(':');return colon<0?name:name.substring(colon+1); }
}

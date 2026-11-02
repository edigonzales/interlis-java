package guru.interlis.lsp;

import guru.interlis.source.*;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import java.util.*;

/** Session-scoped immutable readonly handles, bounded by the configured source budget. Owner queue only. */
final class ExternalSourceRegistry {
    record Entry(String uri,SourceText source,String digest) { }
    private final String session=UUID.randomUUID().toString();
    private final Map<SourceId,Entry> current=new LinkedHashMap<>();
    private final Map<String,Entry> handles=new LinkedHashMap<>();
    private long units;
    void register(SourceText source,long limit) {
        var old=current.get(source.id());
        if(old!=null && old.source.revision().equals(source.revision()) && old.source.text().equals(source.text())) return;
        if(units+source.length()>limit || handles.size()>=10_000) throw LspFailures.error(ResponseErrorCode.RequestFailed,"readonly source registry limit reached; restart required");
        String path=java.net.URI.create(source.id().value()).getPath();
        String name=path==null?"source.ili":path.substring(path.lastIndexOf('/')+1);
        if(name.isBlank() || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".ili")) name="source.ili";
        String uri;
        try { uri=new java.net.URI("interlis-source",null,"/"+session+"/"+UUID.randomUUID()+"/"+name,Long.toString(source.revision().value()),null).toASCIIString(); }
        catch(java.net.URISyntaxException invalid) { throw new IllegalArgumentException("invalid readonly source name",invalid); }
        var entry=new Entry(uri,source,source.contentFingerprint().value());
        handles.put(uri,entry);current.put(source.id(),entry);units+=source.length();
    }
    boolean contains(SourceId id) { return current.containsKey(id); }
    Optional<Entry> lookup(String uri) { return Optional.ofNullable(handles.get(uri)); }
    Map<SourceId,String> uris() { var result=new LinkedHashMap<SourceId,String>();current.forEach((id,e) -> result.put(id,e.uri));return Map.copyOf(result); }
    Entry read(String uri,String revision) {
        var entry=lookup(uri).orElseThrow(() -> LspFailures.error(ResponseErrorCode.InvalidParams,"unknown readonly source handle"));
        if(!Long.toString(entry.source.revision().value()).equals(revision)) throw LspFailures.error(ResponseErrorCode.ContentModified,"readonly source revision differs");
        return entry;
    }
    void close() { current.clear();handles.clear();units=0; }
}

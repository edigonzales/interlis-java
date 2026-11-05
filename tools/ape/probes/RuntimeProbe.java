import java.net.*;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.channels.spi.SelectorProvider;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.time.*;
import java.util.*;

/** Pure JDK runtime qualification; no INTERLIS, builder APIs or relaxed TLS checks. */
public final class RuntimeProbe {
    public static void main(String[] args) throws Exception {
        try {
            if(args.length==1 && args[0].equals("clock")) { clocks(); return; }
            if(args.length==1 && args[0].equals("selector")) {
                try(var selector=Selector.open()) { emit(Map.of("provider",SelectorProvider.provider().getClass().getName(),"selector",selector.getClass().getName())); } return;
            }
            if(args.length==2 && args[0].equals("certificate")) {
                try(var input=Files.newInputStream(Path.of(args[1]))) {
                    var certificate=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(input);
                    certificate.checkValidity(); emit(Map.of("certificate","VALID","systemMillis",System.currentTimeMillis()));
                } return;
            }
            if(args.length==2 && args[0].equals("http")) {
                var proxy=new ProxySelector(){public List<Proxy> select(URI uri){return List.of(Proxy.NO_PROXY);}public void connectFailed(URI uri,SocketAddress address,java.io.IOException error){}};
                try(var client=HttpClient.newBuilder().proxy(proxy).connectTimeout(Duration.ofSeconds(3)).build()) {
                    var response=client.send(HttpRequest.newBuilder(URI.create(args[1])).timeout(Duration.ofSeconds(8)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                    emit(Map.of("status",response.statusCode(),"bytes",response.body().length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body()))));
                    if(response.statusCode()!=200)throw new java.io.IOException("Unexpected HTTP status "+response.statusCode());
                } return;
            }
            throw new IllegalArgumentException("Expected clock, selector, certificate PEM or http URI");
        } catch(Throwable failure) {
            var causes=new ArrayList<String>();for(Throwable e=failure;e!=null;e=e.getCause())causes.add(e.getClass().getName()+": "+e.getMessage());
            emit(Map.of("error",causes));failure.printStackTrace(System.err);System.exit(1);
        }
    }
    private static void clocks() throws Exception {
        var samples=new ArrayList<Map<String,Object>>();
        sample(samples,"main");
        var error=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var thread=new Thread(()->{try{sample(samples,"worker");}catch(Throwable e){error.set(e);}},"clock-worker");thread.start();thread.join();
        if(error.get()!=null)throw new AssertionError("Worker clock",error.get());
        emit(Map.of("samples",samples));
    }
    private static void sample(List<Map<String,Object>> samples,String thread) throws Exception {
        long first=System.nanoTime();
        for(int i=0;i<3;i++) {
            long millis=System.currentTimeMillis(),instant=Instant.now().toEpochMilli(),nano=System.nanoTime();
            samples.add(Map.of("thread",thread,"systemMillis",millis,"instantMillis",instant,"nanoTime",nano));
            if(Math.abs(millis-instant)>10_000)throw new AssertionError("Wall clocks disagree");
            if(i<2)Thread.sleep(100);
        }
        long elapsed=System.nanoTime()-first;
        if(elapsed<150_000_000L || elapsed>10_000_000_000L)throw new AssertionError("Implausible monotonic elapsed nanos: "+elapsed);
    }
    private static void emit(Object value){System.out.println(json(value));}
    private static String json(Object value){
        if(value instanceof String s)return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
        if(value instanceof Map<?,?> m)return m.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).collect(java.util.stream.Collectors.joining(",","{","}"));
        if(value instanceof Collection<?> c)return c.stream().map(RuntimeProbe::json).collect(java.util.stream.Collectors.joining(",","[","]"));
        if(value instanceof Number||value instanceof Boolean)return value.toString();throw new IllegalArgumentException("JSON value");
    }
}

import ch.interlis.ili2c.config.Configuration;
import ch.interlis.ili2c.config.FileEntry;
import ch.interlis.ili2c.config.FileEntryKind;
import ch.interlis.ili2c.metamodel.TransferDescription;
import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.xtf.Xtf24Reader;
import ch.interlis.iom_j.xtf.XtfReader;
import ch.interlis.iom_j.xtf.XtfStartTransferEvent;
import ch.interlis.iox.EndBasketEvent;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxException;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;
import ch.interlis.iox_j.IoxIliReader;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

final class LegacyProbe {
    private static final int SAMPLE_INTERVAL_MILLIS = 5;

    public static void main(String[] args) throws Exception {
        measureHeap(Path.of(args[0]), args[1].equals("benchmark"));
        switch (args[1]) {
            case "read" -> {
                int exitCode = read(args[2], args[3], Path.of(args[4]));
                if (exitCode != 0) {
                    System.exit(exitCode);
                }
            }
            case "validate", "benchmark" -> org.interlis2.validator.Main.main(Arrays.copyOfRange(args, 2, args.length));
            default -> throw new IllegalArgumentException("Unknown probe: " + args[1]);
        }
    }

    private static int read(String version, String model, Path xtf) throws Exception {
        var config = new Configuration();
        config.addFileEntry(new FileEntry(model, FileEntryKind.ILIMODELFILE));
        TransferDescription schema = ch.interlis.ili2c.Main.runCompiler(config);
        if (schema == null) {
            throw new IllegalStateException("Legacy model compilation failed");
        }
        try (var input = Files.newInputStream(xtf)) {
            var reader = switch (version) {
                case "2.3" -> new XtfReader(input);
                case "2.4" -> new Xtf24Reader(input);
                default -> throw new IllegalArgumentException("Unknown XTF version: " + version);
            };
            try {
                ((IoxIliReader) reader).setModel(schema);
                while (true) {
                    IoxEvent event;
                    try {
                        event = reader.read();
                    } catch (IoxException failure) {
                        System.err.println(json(fields("error", failure.getClass().getSimpleName(),
                                "message", failure.getMessage())));
                        return 2;
                    }
                    System.out.println(json(snapshot(event)));
                    if (event instanceof EndTransferEvent) {
                        return 0;
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    private static Map<String, Object> snapshot(IoxEvent event) {
        return switch (event) {
            case XtfStartTransferEvent start -> {
                var headerObjects = new TreeMap<String, Object>();
                if (start.getHeaderObjects() != null) {
                    start.getHeaderObjects().forEach((key, value) -> headerObjects.put(key, snapshot(value)));
                }
                var oidSpaces = new ArrayList<Map<String, Object>>();
                if (start.getOidSpaces() != null) {
                    start.getOidSpaces().forEach(space ->
                            oidSpaces.add(fields("name", space.getName(), "domain", space.getOiddomain())));
                }
                yield fields("event", "startTransfer", "sender", start.getSender(), "comment", start.getComment(),
                        "version", start.getVersion(), "oidSpaces", oidSpaces, "headerObjects", headerObjects);
            }
            case StartBasketEvent start -> fields("event", "startBasket", "type", start.getType(),
                    "bid", start.getBid(), "kind", start.getKind(), "consistency", start.getConsistency(),
                    "startState", start.getStartstate(), "endState", start.getEndstate(),
                    "topicTypes", start.getTopicv() == null ? null : Arrays.asList(start.getTopicv()),
                    "domains", new TreeMap<>(((ch.interlis.iox_j.StartBasketEvent) start).getDomains()));
            case ObjectEvent object -> fields("event", "object", "value", snapshot(object.getIomObject()));
            case EndBasketEvent ignored -> fields("event", "endBasket");
            case EndTransferEvent ignored -> fields("event", "endTransfer");
            case null -> throw new IllegalStateException("Missing endTransfer event");
            default -> throw new IllegalArgumentException("Unexpected IOX event: " + event.getClass());
        };
    }

    @SuppressWarnings("deprecation") // Preserve the operation and consistency exposed by legacy IOX.
    private static Map<String, Object> snapshot(IomObject object) {
        var attributes = new TreeMap<String, List<Object>>();
        for (int i = 0; i < object.getattrcount(); i++) {
            String name = object.getattrname(i);
            var values = new ArrayList<>();
            for (int j = 0; j < object.getattrvaluecount(name); j++) {
                IomObject child = object.getattrobj(name, j);
                values.add(child == null ? object.getattrprim(name, j) : snapshot(child));
            }
            attributes.put(name, values);
        }
        Object reference = null;
        if (object.getobjectrefoid() != null || object.getobjectrefbid() != null
                || object.getobjectreforderpos() != 0) {
            reference = fields("oid", object.getobjectrefoid(), "bid", object.getobjectrefbid(),
                    "orderPosition", object.getobjectreforderpos());
        }
        return fields("tag", object.getobjecttag(), "oid", object.getobjectoid(), "reference", reference,
                "operation", object.getobjectoperation(), "consistency", object.getobjectconsistency(),
                "attributes", attributes);
    }

    private static void measureHeap(Path output, boolean includeGc) {
        var memory = ManagementFactory.getMemoryMXBean();
        var peak = new AtomicLong();
        Runnable sample = () -> peak.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
        sample.run();
        Thread.ofPlatform().daemon().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                sample.run();
                try {
                    Thread.sleep(SAMPLE_INTERVAL_MILLIS);
                } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(Thread.ofPlatform().unstarted(() -> {
            sample.run();
            try {
                var measurements = fields("heapLimitBytes", memory.getHeapMemoryUsage().getMax(),
                        "sampledPeakHeapBytes", peak.get(), "heapSampleIntervalMillis", SAMPLE_INTERVAL_MILLIS);
                if (includeGc) {
                    measurements.put("gcCollectionMillis", gcCollectionMillis());
                }
                Files.writeString(output, json(measurements));
            } catch (IOException failure) {
                failure.printStackTrace(System.err);
            }
        }));
    }

    private static Long gcCollectionMillis() {
        var collectors = ManagementFactory.getGarbageCollectorMXBeans();
        if (collectors.isEmpty()) {
            return null;
        }
        long total = 0;
        for (var collector : collectors) {
            long millis = collector.getCollectionTime();
            if (millis < 0) {
                return null;
            }
            total += millis;
        }
        return total;
    }

    private static Map<String, Object> fields(Object... values) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], values[i + 1]);
        }
        return map;
    }

    private static String json(Object value) {
        var out = new StringBuilder();
        appendJson(out, value);
        return out.toString();
    }

    private static void appendJson(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String string -> {
                out.append('"');
                for (int i = 0; i < string.length(); i++) {
                    char c = string.charAt(i);
                    switch (c) {
                        case '"' -> out.append("\\\"");
                        case '\\' -> out.append("\\\\");
                        default -> {
                            if (c < 0x20) {
                                out.append("\\u").append(Integer.toHexString(0x10000 + c).substring(1));
                            } else {
                                out.append(c);
                            }
                        }
                    }
                }
                out.append('"');
            }
            case Number number -> out.append(number);
            case List<?> list -> {
                out.append('[');
                String delimiter = "";
                for (Object element : list) {
                    out.append(delimiter);
                    appendJson(out, element);
                    delimiter = ",";
                }
                out.append(']');
            }
            case Map<?, ?> map -> {
                out.append('{');
                String delimiter = "";
                for (var entry : map.entrySet()) {
                    out.append(delimiter);
                    appendJson(out, entry.getKey());
                    out.append(':');
                    appendJson(out, entry.getValue());
                    delimiter = ",";
                }
                out.append('}');
            }
            default -> throw new IllegalArgumentException("Unexpected JSON value: " + value.getClass());
        }
    }
}

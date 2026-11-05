import java.lang.classfile.*;
import java.lang.classfile.attribute.*;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import build.jenesis.Json;

/** Structural, non-initializing inspection for one pinned compatibility method. */
public final class InspectCosmoClock {
    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("inspect")) {
            Path jar = absolute(args[1]); String entry = args[2];
            Map<String,Object> archive = new TreeMap<>();
            try (var zip = new ZipFile(jar.toFile())) {
                var entries = zip.stream().toList(); var names = new HashSet<String>();
                for (var e : entries) {
                    if (!names.add(e.getName())) throw new IllegalArgumentException("Duplicate JAR entry");
                    if (e.getName().startsWith("META-INF/versions/") && e.getName().endsWith(entry))
                        throw new IllegalArgumentException("Ambiguous multi-release target");
                }
                var target = zip.getEntry(entry); if (target == null) throw new IllegalArgumentException("Missing target class");
                archive.putAll(inspectClass(zip.getInputStream(target).readAllBytes()));
                archive.put("jarSha256", sha(Files.readAllBytes(jar)));
                archive.put("signedArchive", names.stream().anyMatch(n -> n.toUpperCase(Locale.ROOT).matches("META-INF/.*\\.(SF|RSA|DSA|EC)")));
                archive.put("entries", names.stream().sorted().toList());
            }
            write(absolute(args[3]), archive); return;
        }
        if (args.length == 3 && args[0].equals("class")) {
            write(absolute(args[2]), inspectClass(Files.readAllBytes(absolute(args[1])))); return;
        }
        if (args.length == 4 && args[0].equals("pack")) {
            Path source=absolute(args[1]), output=absolute(args[3]); String name=args[2];
            if (!name.matches("[A-Za-z0-9_/]+\\.class") || name.contains("..")) throw new IllegalArgumentException("Unsafe entry");
            try (var zip = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
                var entry=new ZipEntry(name); entry.setTimeLocal(LocalDateTime.of(1980,1,1,0,0));
                zip.putNextEntry(entry); zip.write(Files.readAllBytes(source)); zip.closeEntry();
            } return;
        }
        throw new IllegalArgumentException("Expected inspect JAR ENTRY OUTPUT, class FILE OUTPUT or pack CLASS ENTRY JAR");
    }
    private static Path absolute(String value) {
        Path p=Path.of(value); if(!p.isAbsolute())throw new IllegalArgumentException("Absolute path required"); return p;
    }
    public static Map<String,Object> inspectClass(byte[] bytes) throws Exception {
        var model=ClassFile.of().parse(bytes);
        if(!ClassFile.of().verify(model).isEmpty())throw new IllegalArgumentException("Invalid classfile");
        var methods=new ArrayList<Map<String,Object>>();
        for(var method:model.methods()) {
            var instructions=new ArrayList<String>(); var invokes=new ArrayList<Map<String,String>>(); var strings=new ArrayList<String>();
            if(method.code().isPresent())for(var element:method.code().get()) {
                if(!(element instanceof Instruction i))continue;
                String value=i.opcode().name();
                if(i instanceof InvokeInstruction call) {
                    var site=Map.of("owner",call.owner().asInternalName(),"name",call.name().stringValue(),"descriptor",call.type().stringValue(),"opcode",value);
                    invokes.add(site); value += " "+site.get("owner")+"."+site.get("name")+site.get("descriptor");
                } else if(i instanceof ConstantInstruction c) {
                    value+=" "+(c.constantValue() instanceof java.lang.constant.ClassDesc d?d.descriptorString():c.constantValue()); if(c.constantValue() instanceof String s)strings.add(s);
                } else if(i instanceof LoadInstruction l)value+=" "+l.slot();
                else if(i instanceof StoreInstruction s)value+=" "+s.slot();
                else if(i instanceof TypeCheckInstruction t)value+=" "+t.type().asInternalName();
                else if(i instanceof InvokeDynamicInstruction d)value+=" "+d.name().stringValue()+d.type().stringValue()+d.bootstrapMethod()+d.bootstrapArgs();
                else if(i instanceof FieldInstruction f)value+=" "+f.owner().asInternalName()+"."+f.name().stringValue()+f.type().stringValue();
                else if(!(i instanceof OperatorInstruction || i instanceof ReturnInstruction || i instanceof StackInstruction))throw new IllegalArgumentException("Unexpected instruction: "+i.opcode());
                instructions.add(value);
            }
            var facts=new TreeMap<String,Object>(); facts.put("name",method.methodName().stringValue());facts.put("descriptor",method.methodType().stringValue());
            facts.put("flags",method.flags().flagsMask());facts.put("instructions",instructions);facts.put("invokes",invokes);facts.put("strings",strings);
            facts.put("exceptionHandlers",method.code().map(c->c.exceptionHandlers().size()).orElse(0));facts.put("annotations",annotations(method.attributes()));facts.put("structuralDigest",sha(json(facts).getBytes(java.nio.charset.StandardCharsets.UTF_8)));methods.add(facts);
        }
        return Map.of("className",model.thisClass().asInternalName(),"classSha256",sha(bytes),"major",model.majorVersion(),"minor",model.minorVersion(),"flags",model.flags().flagsMask(),"annotations",annotations(model.attributes()),"fields",model.fields().stream().map(f->f.fieldName().stringValue()+":"+f.fieldType().stringValue()+":"+f.flags().flagsMask()).toList(),"methods",methods);
    }
    private static List<String> annotations(List<? extends Attribute<?>> attributes) {
        return attributes.stream().filter(a->a instanceof RuntimeVisibleAnnotationsAttribute).flatMap(a->((RuntimeVisibleAnnotationsAttribute)a).annotations().stream()).map(InspectCosmoClock::annotation).sorted().toList();
    }
    private static String annotation(Annotation a) {
        return a.className().stringValue()+a.elements().stream().map(e->e.name().stringValue()+"="+annotationValue(e.value())).sorted().toList();
    }
    private static String annotationValue(AnnotationValue value) {
        return switch(value) {
            case AnnotationValue.OfClass c -> c.className().stringValue();
            case AnnotationValue.OfArray a -> a.values().stream().map(InspectCosmoClock::annotationValue).toList().toString();
            case AnnotationValue.OfAnnotation a -> annotation(a.annotation());
            case AnnotationValue.OfConstant c -> String.valueOf(c.resolvedValue());
            case AnnotationValue.OfEnum e -> e.className().stringValue()+"."+e.constantName().stringValue();
        };
    }
    private static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void write(Path output,Object value) throws Exception {Files.writeString(output,json(value)+"\n",StandardOpenOption.CREATE_NEW);}
    private static String json(Object value) {
        if(value instanceof String s)return "\""+Json.escaped(s)+"\"";
        if(value instanceof Map<?,?> m)return m.entrySet().stream().sorted(Comparator.comparing(e->e.getKey().toString())).map(e->json(e.getKey().toString())+":"+json(e.getValue())).collect(java.util.stream.Collectors.joining(",","{","}"));
        if(value instanceof Collection<?> c)return c.stream().map(InspectCosmoClock::json).collect(java.util.stream.Collectors.joining(",","[","]"));
        if(value instanceof Number||value instanceof Boolean)return value.toString();throw new IllegalArgumentException("Unsupported JSON fact");
    }
}

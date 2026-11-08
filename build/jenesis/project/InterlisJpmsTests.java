package build.jenesis.project;

import build.jenesis.*;
import build.jenesis.step.Bind;
import build.jenesis.step.ProcessBuildStep;
import java.io.IOException;
import java.io.Serial;
import java.nio.file.*;
import java.lang.module.ModuleFinder;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.regex.Pattern;

/** Runs Jenesis-compiled white-box tests in their existing named product module. */
final class InterlisJpmsTests {
    /** Commas inside a JVM method signature are not selection separators. */
    public static List<String> selections(String value) {
        var parts=new ArrayList<String>();int depth=0,start=0;
        for(int i=0;i<value.length();i++) {
            char token=value.charAt(i);if(token=='(')depth++;else if(token==')')depth--;
            if(depth<0)throw new IllegalArgumentException("Unbalanced test selector");
            if(token==','&&depth==0){parts.add(value.substring(start,i));start=i+1;}
        }
        if(depth!=0)throw new IllegalArgumentException("Unbalanced test selector");parts.add(value.substring(start));
        if(parts.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("Empty test selector");return List.copyOf(parts);
    }
    public static BuildExecutorModule observe(TestModule nativeModule,Path root,String project,Map<String,String> settings,boolean selected) throws IOException {
        String requested = settings.get("test.filter");
        List<String> selectors = requested == null ? List.of(".*") : selections(requested).stream()
                .filter(selection -> {
                    int slash = selection.indexOf('/');
                    return slash < 0 || selection.substring(0, slash).equals(project);
                })
                .map(selection -> selection.substring(selection.indexOf('/') + 1))
                .toList();
        boolean run = selected && !selectors.isEmpty();
        var fixtures = new Properties();
        Path fixtureConfig = root.resolve(project).resolve("build.jenesis/test-inputs.properties");
        if (run && Files.isRegularFile(fixtureConfig)) {
            try (var reader = Files.newBufferedReader(fixtureConfig)) {
                fixtures.load(reader);
            }
        }
        var fixtureInputs = new TreeMap<String, Path>();
        for (String key : fixtures.stringPropertyNames()) {
            fixtureInputs.put("fixtures-" + key, Bind.contained(root, root.resolve(fixtures.getProperty(key)), "Test fixture " + key));
        }
        return (executor,inherited)->{
            // Keep the original pinned JUnit/tool resolver, replacing only test execution.
            nativeModule.skip(true).accept(executor,inherited);
            if (!run) return;
            if(Files.isDirectory(root.resolve(project+"/src/test/resources")))executor.addSource("patch-resources",root.resolve(project+"/src/test/resources"));
            var required = new ArrayList<>(inherited.keySet());
            required.add("dependencies");
            fixtureInputs.forEach((key, path) -> {
                executor.addSource(key, path);
                required.add(key);
            });
            if(Files.isDirectory(root.resolve(project+"/src/test/resources")))required.add("patch-resources");
            executor.addStep("executed",new Run(root, project, selectors),required.stream());
        };
    }
    private static String className(java.lang.constant.ClassDesc descriptor) {
        if(descriptor.isArray())return className(descriptor.componentType())+"[]";
        String value=descriptor.descriptorString();return descriptor.isPrimitive()?descriptor.displayName():value.substring(1,value.length()-1).replace('/','.');
    }
    private record Run(Path root, String project, List<String> selectors) implements BuildStep {
        @Serial private static final long serialVersionUID = 2L;

        public CompletionStage<BuildStepResult> apply(Executor executor,BuildStepContext context,SequencedMap<String,BuildStepArgument> arguments)throws IOException {
            try {execute(context,arguments);return CompletableFuture.completedFuture(new BuildStepResult(true));}
            catch(IOException|RuntimeException failure){throw failure;}
            catch(Exception failure){throw new IOException("JPMS test execution failed",failure);}
        }
        void execute(BuildStepContext context,SequencedMap<String,BuildStepArgument> arguments)throws Exception {
            var jars=new TreeMap<String,Path>();
            for (var entry : arguments.entrySet()) {
                var argument = entry.getValue();
                if (argument.removed() || entry.getKey().startsWith("fixtures-")) continue;
                Path index=argument.folder().resolve("dependencies.properties");if(!Files.isRegularFile(index))continue;
                var properties=new Properties();try(var reader=Files.newBufferedReader(index)){properties.load(reader);}
                for(String key:properties.stringPropertyNames())if(key.startsWith("main/runtime/maven/")) {
                    String coordinate=key.substring("main/runtime/maven/".length());
                    String value=properties.getProperty(key).replaceFirst(" SHA-[0-9]+/[a-f0-9]+$","");
                    Path jar=argument.folder().resolve(value).normalize();
                    if(!Files.isRegularFile(jar))throw new IllegalStateException("Missing actual test dependency: "+coordinate);
                    Path prior=jars.putIfAbsent(coordinate,jar);
                    if(prior!=null&&!prior.equals(jar)&&Files.mismatch(prior, jar)!=-1)throw new IllegalStateException("Conflicting test dependency: "+coordinate);
                }
            }
            var modulePath=new ArrayList<Path>();var classpath=new ArrayList<Path>();var names=new TreeSet<String>();
            Path product=null;String moduleName=null;
            for(var entry:jars.entrySet()) {
                String[] coordinate=entry.getKey().split("/");if(coordinate.length!=3)throw new IllegalStateException("Unexpected test GAV");
                boolean junit=coordinate[0].startsWith("org.junit")||coordinate[0].equals("org.opentest4j")||coordinate[0].equals("org.apiguardian");
                Path jar = entry.getValue();
                if(junit)classpath.add(jar);else {
                    var found=ModuleFinder.of(jar).findAll();if(found.size()!=1)throw new IllegalStateException("Unidentified test module");
                    var descriptor=found.iterator().next().descriptor();String name=descriptor.name();
                    if(!names.add(name))throw new IllegalStateException("Duplicate test module");modulePath.add(jar);
                    if(coordinate[0].equals("guru.interlis")&&coordinate[1].equals(project)) {
                        if(descriptor.isAutomatic())throw new IllegalStateException("Tested product is not explicit JPMS");
                        product=jar;moduleName=name;
                    }
                }
            }
            if(product==null||jars.keySet().stream().noneMatch(key -> key.startsWith("org.junit.platform/junit-platform-console/")))throw new IllegalStateException("Missing producing module or pinned JUnit console: "+jars.keySet());
            var compiledTests = arguments.entrySet().stream()
                    .filter(entry -> !entry.getValue().removed())
                    .filter(entry -> entry.getKey().endsWith("binary/classes"))
                    .map(entry -> entry.getValue().folder().resolve("classes"))
                    .filter(Files::isDirectory)
                    .toList();
            if (compiledTests.size() != 1) {
                throw new IllegalStateException("Expected compiled test classes in " + arguments.keySet());
            }
            Path classes = compiledTests.getFirst();
            var testClasses = new TreeSet<String>();
            var packages = new TreeSet<String>();
            var methods = new TreeMap<String, Set<String>>();
            try (var archive = new JarFile(product.toFile()); var walk = Files.walk(classes)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String name = classes.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                    if (archive.getJarEntry(name) != null) {
                        throw new IllegalStateException("Compiled test shadows product: " + name);
                    }
                    if (!name.endsWith(".class")) continue;
                    int slash = name.lastIndexOf('/');
                    if (slash < 0) throw new IllegalStateException("Test class lacks a package: " + name);
                    packages.add(name.substring(0, slash).replace('/', '.'));
                    String simple = name.substring(slash + 1, name.length() - ".class".length());
                    if (simple.contains("$") || simple.equals("package-info")) continue;
                    String type = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                    for (String selector : selectors) {
                        int separator = selector.indexOf('#');
                        if (Pattern.matches(separator < 0 ? selector : selector.substring(0, separator), type)) {
                            if (separator < 0) testClasses.add(type);
                            else methods.computeIfAbsent(type, unused -> new TreeSet<>()).add(selector.substring(separator + 1));
                        }
                    }
                }
            }
            if(testClasses.isEmpty()&&methods.isEmpty())throw new IllegalStateException("Empty required JPMS test selection");
            var testJdkModules=Set.of("interlis-repository","interlis-compiler-cli","interlis-lsp","interlis-compiler-gui").contains(project)?List.of("jdk.httpserver"):project.equals("interlis-documentation")?List.of("java.xml"):List.<String>of();
            var resolvedNames=new TreeSet<>(names);resolvedNames.addAll(testJdkModules);
            String mp=join(modulePath),cp=join(classpath),resolved=String.join(",",resolvedNames);
            var access=new ArrayList<String>();access.addAll(List.of("--module-path",mp,"--add-modules",resolved,"--add-reads",moduleName+"=ALL-UNNAMED"));
            for(String jdkModule:testJdkModules)access.addAll(List.of("--add-reads",moduleName+"="+jdkModule));
            for(String name:names)if(!name.equals(moduleName))access.addAll(List.of("--add-reads",moduleName+"="+name));
            var patch = new ArrayList<>(List.of(classes));
            var resources=arguments.get("patch-resources");
            if(resources!=null)try(var archive=new JarFile(product.toFile());var walk=Files.walk(resources.folder())) {
                Path base=resources.folder();for(Path file:walk.filter(Files::isRegularFile).toList()) {
                    String relative=base.relativize(file).toString().replace('\\','/');
                    if(relative.startsWith("META-INF/services/")||archive.getJarEntry(relative)!=null||relative.endsWith(".class"))throw new IllegalStateException("Test resource shadows product: "+relative);

                }
            }
            if (resources != null) patch.add(resources.folder());
            var run=new ArrayList<String>(List.of("-Dinterlis.test.modulepath="+mp,"-Dinterlis.test.patch="+classes,"-ea","-Dfile.encoding=UTF-8","-Drepo.root="+root,"-Dtestdata.dir="+root.resolve("testdata")));
            run.addAll(access);run.addAll(List.of("--patch-module",moduleName+"="+join(patch)));
            for(String pkg:packages)run.addAll(List.of("--add-opens",moduleName+"/"+pkg+"=ALL-UNNAMED"));
            var platform = new JUnitPlatform().runningOn(classpath);
            SequencedMap<String, SequencedSet<String>> selectedMethods = new TreeMap<>();
            for (var entry : methods.entrySet()) {
                if (testClasses.contains(entry.getKey())) continue;
                var signatures = new TreeSet<String>();
                for (String method : entry.getValue()) {
                    if (method.contains("(")) {
                        signatures.add(method);
                        continue;
                    }
                    Path compiled = classes.resolve(entry.getKey().replace('.', '/') + ".class");
                    var model = java.lang.classfile.ClassFile.of().parse(compiled);
                    boolean found = false;
                    for (var member : model.methods()) {
                        if (!member.methodName().stringValue().equals(method)) continue;
                        String parameters = String.join(",", member.methodTypeSymbol().parameterList().stream()
                                .map(InterlisJpmsTests::className).toList());
                        signatures.add(method + "(" + parameters + ")");
                        found = true;
                    }
                    if (!found) throw new IllegalStateException("No method in selected test class: " + method);
                }
                selectedMethods.put(entry.getKey(), signatures);
            }
            run.addAll(List.of("--class-path", cp, platform.runnerClass()));
            run.addAll(platform.arguments(context.supplement(), context.next(), testClasses,
                    selectedMethods, false, true));
            run.add("--fail-if-no-tests");
            process(context, run, root.resolve(project));
        }
        static String join(List<Path> paths){return String.join(java.io.File.pathSeparator,paths.stream().map(p->p.toAbsolutePath().toString()).toList());}
        static void process(BuildStepContext context, List<String> arguments, Path cwd)throws Exception {
            Path argumentFile = ProcessBuildStep.argumentFile(context.supplement().resolve("test.args"), arguments);
            var command=List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"@"+argumentFile);
            var builder=new ProcessBuilder(command).directory(cwd.toFile()).redirectOutput(context.supplement().resolve("test.stdout").toFile()).redirectError(context.supplement().resolve("test.stderr").toFile());
            var process=builder.start();try {
                boolean finished=process.waitFor(180,TimeUnit.SECONDS);
                if(!finished||process.exitValue()!=0)throw new IllegalStateException("JPMS test failed: "+context.supplement());
            }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        }
    }
}

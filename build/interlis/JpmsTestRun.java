package build.interlis;

import build.jenesis.*;
import build.jenesis.project.TestModule;
import build.jenesis.step.Bind;
import java.io.IOException;
import java.nio.file.*;
import java.lang.module.ModuleFinder;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.regex.Pattern;

/** Test-only patch compilation/execution in the pinned engine graph. Production is never compiled here. */
public final class JpmsTestRun {
    private static final Pattern PACKAGE=Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
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
        var plan=new TreeMap<String,String>();
        for(var entry:settings.entrySet())if(entry.getKey().startsWith("variable.")||entry.getKey().startsWith("test."))plan.put(entry.getKey(),entry.getValue());
        plan.put("project",project);plan.put("selected",Boolean.toString(selected));
        Path file=JvmBuildInputs.requireOutputRoot(root,"target/jenesis-jvm/test-options/"+project+".json");Files.createDirectories(file.getParent());
        String json=JsonOutput.write(plan);if(!Files.exists(file)||!Files.readString(file).equals(json))Files.writeString(file,json);
        Map<String,String> frozen=Map.copyOf(plan);
        return (executor,inherited)->{
            // Keep the original pinned JUnit/tool resolver, replacing only test execution.
            nativeModule.skip(true).accept(executor,inherited);
            if(!selected)return;
            executor.addSource("patch-sources",Bind.asSources().extensions(Set.of(".java")),root.resolve(project+"/src/test/java"));
            if(Files.isDirectory(root.resolve(project+"/src/test/resources")))executor.addSource("patch-resources",Bind.asResources(),root.resolve(project+"/src/test/resources"));
            executor.addSource("test-options",Bind.asResources(),file);
            var required=new ArrayList<>(inherited.keySet());required.addAll(List.of("dependencies","patch-sources","test-options"));
            if(Files.isDirectory(root.resolve(project+"/src/test/resources")))required.add("patch-resources");
            executor.addStep("executed",new Run(root,project,frozen),required.stream());
        };
    }
    private static String className(java.lang.constant.ClassDesc descriptor) {
        if(descriptor.isArray())return className(descriptor.componentType())+"[]";
        String value=descriptor.descriptorString();return descriptor.isPrimitive()?descriptor.displayName():value.substring(1,value.length()-1).replace('/','.');
    }
    private record Run(Path root,String project,Map<String,String> plan) implements BuildStep {
        public CompletionStage<BuildStepResult> apply(Executor executor,BuildStepContext context,SequencedMap<String,BuildStepArgument> arguments)throws IOException {
            try {execute(context,arguments);return CompletableFuture.completedFuture(new BuildStepResult(true));}
            catch(IOException|RuntimeException failure){throw failure;}
            catch(Exception failure){throw new IOException("JPMS test execution failed",failure);}
        }
        void execute(BuildStepContext context,SequencedMap<String,BuildStepArgument> arguments)throws Exception {
            var jars=new TreeMap<String,Path>();
            for(var argument:arguments.values())if(!argument.removed()) {
                Path index=argument.folder().resolve("dependencies.properties");if(!Files.isRegularFile(index))continue;
                var properties=new Properties();try(var reader=Files.newBufferedReader(index)){properties.load(reader);}
                for(String key:properties.stringPropertyNames())if(key.startsWith("main/runtime/maven/")) {
                    String coordinate=key.substring("main/runtime/maven/".length());
                    String value=properties.getProperty(key).replaceFirst(" SHA-[0-9]+/[a-f0-9]+$","");
                    Path jar=argument.folder().resolve(value).normalize();
                    if(!Files.isRegularFile(jar))throw new IllegalStateException("Missing actual test dependency: "+coordinate);
                    Path prior=jars.putIfAbsent(coordinate,jar);
                    if(prior!=null&&!JvmBuildInputs.sha256(prior).equals(JvmBuildInputs.sha256(jar)))throw new IllegalStateException("Conflicting test dependency: "+coordinate);
                }
            }
            Path modules=Files.createDirectory(context.next().resolve("modules")),tools=Files.createDirectory(context.next().resolve("test-tools"));
            var modulePath=new ArrayList<Path>();var classpath=new ArrayList<Path>();var names=new TreeSet<String>();
            Path product=null;String moduleName=null;
            for(var entry:jars.entrySet()) {
                String[] coordinate=entry.getKey().split("/");if(coordinate.length!=3)throw new IllegalStateException("Unexpected test GAV");
                boolean junit=coordinate[0].startsWith("org.junit")||coordinate[0].equals("org.opentest4j")||coordinate[0].equals("org.apiguardian");
                Path copy=(junit?tools:modules).resolve(coordinate[1]+"-"+coordinate[2]+".jar");Files.copy(entry.getValue(),copy);
                if(!JvmBuildInputs.sha256(copy).equals(JvmBuildInputs.sha256(entry.getValue())))throw new IllegalStateException("Test dependency copy changed");
                if(junit)classpath.add(copy);else {
                    var found=ModuleFinder.of(copy).findAll();if(found.size()!=1)throw new IllegalStateException("Unidentified test module");
                    var descriptor=found.iterator().next().descriptor();String name=descriptor.name();
                    if(!names.add(name))throw new IllegalStateException("Duplicate test module");modulePath.add(copy);
                    if(coordinate[0].equals("guru.interlis")&&coordinate[1].equals(project)) {
                        if(descriptor.isAutomatic())throw new IllegalStateException("Tested product is not explicit JPMS");
                        product=copy;moduleName=name;
                    }
                }
            }
            if(product==null||classpath.stream().noneMatch(p->p.getFileName().toString().startsWith("junit-platform-console-")))throw new IllegalStateException("Missing producing module or pinned JUnit console: "+jars.keySet());
            Path sources=arguments.get("patch-sources").folder().resolve("sources"),classes=Files.createDirectory(context.next().resolve("classes"));
            var javaSources=new ArrayList<Path>();try(var walk=Files.walk(sources)){walk.filter(p->p.toString().endsWith(".java")).sorted().forEach(javaSources::add);}
            if(javaSources.isEmpty())throw new IllegalStateException("No test sources");
            var testClasses=new TreeSet<String>();var packages=new TreeSet<String>();
            var methods=new TreeMap<String,Set<String>>();
            String filter=plan.getOrDefault("test.filter",project+"/.*");
            var selectors=selections(filter).stream().filter(p->p.startsWith(project+"/")).map(p->p.substring(project.length()+1)).toList();
            try(var archive=new JarFile(product.toFile())) {
                for(Path source:javaSources) {
                    if(source.getFileName().toString().equals("module-info.java"))throw new IllegalStateException("Test descriptor may not replace product module");
                    var matcher=PACKAGE.matcher(Files.readString(source));if(!matcher.find())throw new IllegalStateException("Test source lacks package: "+source);
                    String pkg=matcher.group(1),simple=source.getFileName().toString().replaceFirst("\\.java$","");
                    String type=pkg+"."+simple;packages.add(pkg);
                    if(archive.getJarEntry(type.replace('.','/')+".class")!=null)throw new IllegalStateException("Test shadows production FQCN: "+type);
                    for(String selector:selectors) {
                        int separator=selector.indexOf('#');String wanted=separator<0?selector:selector.substring(0,separator);
                        if(!simple.equals("package-info")&&Pattern.matches(wanted,type)) {
                            if(separator<0)testClasses.add(type);
                            else methods.computeIfAbsent(type,_ ->new TreeSet<>()).add(selector.substring(separator+1));
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
            var compile=new ArrayList<String>(List.of("--release","25","-encoding","UTF-8"));compile.addAll(access);
            compile.addAll(List.of("--patch-module",moduleName+"="+sources,"-cp",cp,"-d",classes.toString()));javaSources.forEach(p->compile.add(p.toString()));
            process(context,"javac",compile,"compile",root.resolve(project));
            try(var archive=new JarFile(product.toFile());var walk=Files.walk(classes)) {
                for(Path file:walk.filter(Files::isRegularFile).toList())if(archive.getJarEntry(classes.relativize(file).toString().replace('\\','/'))!=null)throw new IllegalStateException("Compiled test shadows product");
            }
            var resources=arguments.get("patch-resources");
            if(resources!=null)try(var archive=new JarFile(product.toFile());var walk=Files.walk(resources.folder().resolve("resources"))) {
                Path base=resources.folder().resolve("resources");for(Path file:walk.filter(Files::isRegularFile).toList()) {
                    String relative=base.relativize(file).toString().replace('\\','/');
                    if(relative.startsWith("META-INF/services/")||archive.getJarEntry(relative)!=null||relative.endsWith(".class"))throw new IllegalStateException("Test resource shadows product: "+relative);
                    Path copy=classes.resolve(relative);Files.createDirectories(copy.getParent());Files.copy(file,copy);
                }
            }
            var run=new ArrayList<String>(List.of("-Dinterlis.test.modulepath="+mp,"-Dinterlis.test.patch="+classes,"-ea","-Dfile.encoding=UTF-8","-Drepo.root="+root,"-Dtestdata.dir="+root.resolve("testdata"),"-Dinterlis.build.inputs="+plan.get("variable.inputFingerprint")));
            run.addAll(access);run.addAll(List.of("--patch-module",moduleName+"="+classes));
            for(String pkg:packages)run.addAll(List.of("--add-opens",moduleName+"/"+pkg+"=ALL-UNNAMED"));
            Path reports=Files.createDirectories(context.next().resolve("reports/tests"));
            run.addAll(List.of("-cp",cp,"org.junit.platform.console.ConsoleLauncher","execute","--disable-ansi-colors","--fail-if-no-tests","--reports-dir",reports.toString()));
            for(String type:testClasses)run.addAll(List.of("--select-class",type));
            for(var entry:methods.entrySet())if(!testClasses.contains(entry.getKey()))for(String method:entry.getValue()) {
                if(method.contains("("))run.addAll(List.of("--select-method",entry.getKey()+"#"+method));
                else {
                    Path compiled=classes.resolve(entry.getKey().replace('.','/')+".class");
                    var model=java.lang.classfile.ClassFile.of().parse(compiled);boolean found=false;
                    for(var member:model.methods())if(member.methodName().stringValue().equals(method)) {
                        String types=String.join(",",member.methodTypeSymbol().parameterList().stream().map(JpmsTestRun::className).toList());
                        run.addAll(List.of("--select-method",entry.getKey()+"#"+method+"("+types+")"));found=true;
                    }
                    if(!found)throw new IllegalStateException("No method in selected test class: "+method);
                }
            }
            process(context,"java",run,"test",root.resolve(project));
            JenesisBridge.requireSuccessfulReports(reports);
        }
        static String join(List<Path> paths){return String.join(java.io.File.pathSeparator,paths.stream().map(p->p.toAbsolutePath().toString()).toList());}
        static void process(BuildStepContext context,String tool,List<String> arguments,String role,Path cwd)throws Exception {
            Path argumentFile=context.supplement().resolve(role+".args");
            Files.write(argumentFile,arguments.stream().map(arg->"\""+arg.replace("\\","\\\\").replace("\"","\\\"")+"\"").toList());
            var command=List.of(Path.of(System.getProperty("java.home"),"bin",tool).toString(),"@"+argumentFile);
            Files.writeString(context.supplement().resolve(role+".argv.json"),JsonOutput.write(command));
            var builder=new ProcessBuilder(command).directory(cwd.toFile()).redirectOutput(context.supplement().resolve(role+".stdout").toFile()).redirectError(context.supplement().resolve(role+".stderr").toFile());
            var process=builder.start();try {
                boolean finished=process.waitFor(180,TimeUnit.SECONDS);
                Files.writeString(context.supplement().resolve(role+".process.json"),JsonOutput.write(Map.of("exitCode",finished?process.exitValue():-1,"timedOut",!finished)));
                if(!finished||process.exitValue()!=0)throw new IllegalStateException("JPMS "+role+" failed: "+context.supplement());
            }finally{if(process.isAlive()){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}}
        }
    }
}

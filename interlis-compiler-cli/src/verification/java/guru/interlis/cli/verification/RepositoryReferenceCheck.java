package guru.interlis.cli.verification;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.repository.*;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import javax.tools.ToolProvider;
import static guru.interlis.cli.verification.HostChecks.*;

/** Targeted differential probes against the pinned ili2c jar; never used by production. */
public final class RepositoryReferenceCheck {
    private static final String PROBE = """
        import java.util.*;
        import ch.interlis.ilirepository.*;
        import ch.interlis.ilirepository.impl.*;
        import ch.ehi.basics.logging.*;
        public class RepositoryProbe {
          public static void main(String[] args) throws Exception {
            var notices=new ArrayList<String>();
            EhiLogger.getInstance().addListener(e -> notices.add(e.getEventMsg()));
            if(args[0].equals("versions")) {
              var entries=new ArrayList<ModelMetadata>();
              for(int i=1;i<args.length;i++) {
                var f=args[i].split("\\\\|",-1);var m=new ModelMetadata();m.setName("Dep");m.setSchemaLanguage("ili2_4");
                m.setVersion(f[0]);m.setFile(f[0]+".ili");if(!f[1].isEmpty())m.setPrecursorVersion(f[1]);m.setBrowseOnly(Boolean.parseBoolean(f[2]));entries.add(m);
              }
              var selected=RepositoryAccess.getLatestVersions2(entries);
              System.out.println("RESULT="+(selected.isEmpty()?"missing":selected.get(0).getVersion()));
              System.out.println("NOTICES="+notices.size());
              notices.forEach(s -> System.out.println("NOTICE="+s));
            } else {
              var access=new RepositoryAccess() {
                public IliSite getIliSite(String uri) {
                  var site=new IliSite();
                  if(uri.equals("https://a.example/")){site.addParentSite("https://p.example/");site.addSubsidiarySite("https://b.example/");site.addSubsidiarySite("https://c.example/");}
                  if(uri.equals("https://p.example/"))site.addParentSite("https://a.example/");
                  if(uri.equals("https://b.example/")){site.addParentSite("https://q.example/");site.addSubsidiarySite("https://a.example/");}
                  return site;
                }
              };
              var visits=new ArrayList<String>();
              var visitor=new RepositoryVisitor(access,(uri,rep)->{visits.add(uri);return uri.equals(args[1]);});
              visitor.setRepositories(new String[]{"https://a.example/"});visitor.visitRepositories();
              System.out.println("VISITS="+String.join(",",visits));
            }
          }
        }
        """;
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath(),jar=ReferenceProcesses.prepare(root);
        Path work=root.resolve("build/repository-reference");Files.createDirectories(work);
        Path probe=work.resolve("RepositoryProbe.java");Files.writeString(probe,PROBE);
        require(ToolProvider.getSystemJavaCompiler().run(null,null,null,"-cp",jar.toString(),"-d",work.toString(),probe.toString())==0,"reference probe compilation");
        var evidence=new ArrayList<Map<String,Object>>();
        var cases=List.of(List.of("z||false","a|z|false"),List.of("1||false","2||false"),
                List.of("1||false","2|1|false","3|1|false"),List.of("1||false","2|missing|false"),
                List.of("1|2|false","2|1|false"),List.of("1||false","2|1|true"));
        for(var entries:cases) {
            var command=new ArrayList<>(List.of(java().toString(),"-cp",work+java.io.File.pathSeparator+jar,"RepositoryProbe","versions"));command.addAll(entries);
            var ref=run(root,command,Duration.ofSeconds(20));require(ref.exitCode()==0,"reference versions: "+ref.stderr());
            var material=new HashMap<String,String>();StringBuilder index=new StringBuilder("<TRANSFER>");
            for(String entry:entries) {
                String[] f=entry.split("\\|",-1);String text="INTERLIS 2.4; MODEL Dep AT \"x\" VERSION \""+f[0]+"\" = END Dep.";
                material.put("https://a.example/"+f[0]+".ili",text);
                index.append("<ModelMetadata><Name>Dep</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>").append(f[0]).append("</Version><File>").append(f[0]).append(".ili</File>");
                if(!f[1].isEmpty())index.append("<precursorVersion>").append(f[1]).append("</precursorVersion>");
                index.append("<browseOnly>").append(f[2]).append("</browseOnly></ModelMetadata>");
            }
            material.put("https://a.example/ilimodels.xml",index.append("</TRANSFER>").toString());
            var out=resolve(material);String version="missing";long notices;List<RepositorySearchStep> steps;
            if(out instanceof RepositoryResolutionOutcome.Prepared p) {version=p.update().lock().models().get("Dep").getFirst().modelVersion();steps=p.update().lock().steps();}
            else steps=((RepositoryResolutionOutcome.Rejected)out).steps();
            notices=steps.stream().filter(RepositorySearchStep::warning).count();
            require(ref.stdout().contains("RESULT="+version+"\n"),"version mismatch: "+ref.stdout()+version);
            require(ref.stdout().contains("NOTICES="+notices+"\n"),"notice mismatch: "+ref.stdout()+steps);
            evidence.add(Map.of("entries",entries,"selected",version,"notices",notices,"referenceStdout",ref.stdout()));
        }
        for(String target:List.of("https://p.example/","https://q.example/","https://c.example/","missing")) {
            var ref=run(root,List.of(java().toString(),"-cp",work+java.io.File.pathSeparator+jar,"RepositoryProbe","visit",target),Duration.ofSeconds(20));
            require(ref.exitCode()==0,"reference traversal");var material=new HashMap<String,String>();
            for(String host:List.of("a","p","b","q","c")) {
                String base="https://"+host+".example/";
                material.put(base+"ilimodels.xml","<TRANSFER><ModelMetadata><Name>"+(base.equals(target)?"Dep":"Other")+"</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>dep.ili</File></ModelMetadata></TRANSFER>");
                material.put(base+"dep.ili","INTERLIS 2.4; MODEL Dep AT \"x\" VERSION \"1\" = END Dep.");
            }
            material.put("https://a.example/ilisite.xml",site(List.of("p"),List.of("b","c")));
            material.put("https://p.example/ilisite.xml",site(List.of("a"),List.of()));
            material.put("https://b.example/ilisite.xml",site(List.of("q"),List.of("a")));
            var out=resolve(material);var steps=out instanceof RepositoryResolutionOutcome.Prepared p?p.update().lock().steps():((RepositoryResolutionOutcome.Rejected)out).steps();
            String visits=String.join(",",steps.stream().filter(s->s.kind()==RepositorySearchStep.Kind.LOOKUP).map(s->s.repository().toString()).toList());
            require(ref.stdout().contains("VISITS="+visits+"\n"),"visit mismatch: "+ref.stdout()+visits);
            evidence.add(Map.of("target",target,"visits",visits,"referenceStdout",ref.stdout()));
        }
        write(work.resolve("results.json"),Map.of("referenceVersion","5.6.9","jarSha256",ReferenceProcesses.JAR,"cases",evidence,"passed",true));
        System.out.println("Repository reference: "+evidence.size()+" differential probes passed");
    }
    private static String site(List<String> parents,List<String> children) {
        var s=new StringBuilder("<TRANSFER><IliSite09.SiteMetadata.Site>");
        for(var group:List.of(Map.entry("parentSite",parents),Map.entry("subsidiarySite",children))) {
            s.append('<').append(group.getKey()).append('>');
            for(String host:group.getValue())s.append("<RepositoryLocation_><value>https://").append(host).append(".example/</value></RepositoryLocation_>");
            s.append("</").append(group.getKey()).append('>');
        }
        return s.append("</IliSite09.SiteMetadata.Site></TRANSFER>").toString();
    }
    private static RepositoryResolutionOutcome resolve(Map<String,String> material) {
        try(var workspace=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults())) {
            SourceLoader loader=(locator,policy,token)->{
                String text=material.get(locator.location());if(text==null)throw new SourceLoadException(SourceLoadException.Reason.NOT_FOUND,"fixture absent",null);
                return new LoadedSource(new SourceId(locator.location()),text,ContentDigest.sha256(text.getBytes(StandardCharsets.UTF_8)).value(),"fixture");
            };
            var base=new RepositoryBase(java.net.URI.create("https://a.example/"));
            return new RepositoryClosureService(loader,new InMemoryContentStore()).resolve(workspace,
                    new AnalysisRequest(workspace.currentRevision(),List.of(new ModelSelector.ByName("Dep",Optional.empty(),Optional.empty()))),
                    RepositoryResolveOptions.defaults(new RepositoryConfig(List.of(new RepositorySource(base,new SourceLocator(base.uri()+"ilimodels.xml"))),FetchPolicy.defaults())),CancellationToken.none());
        }
    }
}

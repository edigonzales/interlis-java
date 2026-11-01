package guru.interlis.repository;

import guru.interlis.compiler.*;
import guru.interlis.execution.CancellationToken;
import guru.interlis.source.*;
import guru.interlis.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class CapturedRepositoryResolutionTest {
    @TempDir Path directory;
    @Test void detachedResolutionNeverReadsTheOwnerAndStaleApplyCannotOverwriteDirtyInputs() throws Exception {
        Files.writeString(directory.resolve("ilimodels.xml"),"<TRANSFER><ModelMetadata><Name>Dep</Name><SchemaLanguage>ili2_4</SchemaLanguage><Version>1</Version><File>Dep.ili</File></ModelMetadata></TRANSFER>");
        Files.writeString(directory.resolve("Dep.ili"),"INTERLIS 2.4; MODEL Dep AT \"x\" VERSION \"1\" = END Dep.");
        var source=new SourceId("memory:/Root.ili");
        String text="INTERLIS 2.4; MODEL Root AT \"x\" VERSION \"1\" = IMPORTS Dep; END Root.";
        for(boolean dirty:List.of(false,true)) try(var real=Workspaces.create(CompilerEngines.create(),WorkspaceConfiguration.defaults());var pool=Executors.newSingleThreadExecutor()) {
            real.update(new SourceUpdate(source,new SourceExpectation.Absent(),new SourceChange.Replace(text)));
            Thread ownerThread=Thread.currentThread();
            Workspace owner=(Workspace)Proxy.newProxyInstance(Workspace.class.getClassLoader(),new Class<?>[]{Workspace.class},(proxy,method,args) -> {
                assertSame(ownerThread,Thread.currentThread(),"detached repository work called live owner: "+method.getName());
                try { return method.invoke(real,args); } catch(InvocationTargetException failure) { throw failure.getCause(); }
            });
            var config=owner.configuration();
            var capture=owner.capture(new AnalysisRequest(owner.currentRevision(),List.of(new ModelSelector.BySource(source))));
            var arrived=new CountDownLatch(1);var resume=new CountDownLatch(1);var files=new FileSourceLoader(directory);
            SourceLoader loader=(locator,policy,token) -> {
                arrived.countDown();try { assertTrue(resume.await(5,TimeUnit.SECONDS)); } catch(InterruptedException failure) { Thread.currentThread().interrupt();throw new AssertionError(failure); }
                return files.load(locator,policy,token);
            };
            var service=new RepositoryClosureService(loader,new InMemoryContentStore());
            var options=RepositoryResolveOptions.defaults(new RepositoryConfig(List.of(new RepositorySource(new RepositoryBase(directory.toUri()),new SourceLocator(directory.resolve("ilimodels.xml").toUri().toString()))),FetchPolicy.defaults()));
            var future=pool.submit(() -> service.resolveCaptured(owner,capture,config,options,CancellationToken.none()));
            assertTrue(arrived.await(5,TimeUnit.SECONDS));
            if(dirty) owner.update(new SourceUpdate(source,new SourceExpectation.At(owner.source(source).orElseThrow().revision()),new SourceChange.Replace(text+" !! dirty 😀")));
            resume.countDown();
            var prepared=assertInstanceOf(RepositoryResolutionOutcome.Prepared.class,future.get(10,TimeUnit.SECONDS));
            if(dirty) {
                assertThrows(UpdateRejectedException.class,()->prepared.update().apply(owner));
                assertEquals(text+" !! dirty 😀",owner.source(source).orElseThrow().text());assertEquals(1,owner.state().sourceRevisions().size());
            } else {
                prepared.update().apply(owner);assertEquals(2,owner.state().sourceRevisions().size());
                assertTrue(owner.capture(new AnalysisRequest(owner.currentRevision(),List.of(new ModelSelector.BySource(source)))).resolution().complete());
            }
        }
    }
}

package guru.interlis.compiler;

import guru.interlis.compiler.query.LanguageQueries;
import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import guru.interlis.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static guru.interlis.compiler.CompilerTestSupport.*;

/** AC2-01/02: ATTRIBUTE OF actual arguments supplies a binding context, not only a final type check. */
class DependentFunctionArgumentsContractTest {
    private static final CancellationToken TOKEN=CancellationToken.none();
    private static final List<String> VERSIONS=List.of("2.3","2.4");
    private static CompilationRequest input(String v,String body) {
        var s=new SourceText(new SourceId("memory:/dependent-call.ili"),new SourceRevision(9),
                "INTERLIS "+v+";\r\n!! 🦊\r\nCONTRACTED MODEL F AT \"https://example.org\" VERSION \"1\"="+body+"END F.");
        return request(List.of("F"),Map.of(s.id(),s),List.of(),CompilerOptions.defaults());
    }
    private static CompilationOutcome.Succeeded ok(CompilationRequest in) {
        var out=CompilerEngines.create().compile(in,TOKEN);
        return assertInstanceOf(CompilationOutcome.Succeeded.class,out,()->out instanceof CompilationOutcome.Rejected r?r.analysis().diagnostics().toString():out.toString());
    }
    private static Symbol named(SemanticGraph g,String name) {return g.symbols().stream().filter(s->s.name().value().equals(name)).findFirst().orElseThrow();}
    private static AttributeSymbol attribute(SemanticGraph g,String owner,String name) {
        return g.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast)
                .filter(a->a.name().value().equals(name)&&a.owner().filter(named(g,owner).id()::equals).isPresent()).findFirst().orElseThrow();
    }
    private static FunctionCallExpression call(SemanticGraph g) {return g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast).findFirst().orElseThrow();}
    @Test void unqualifiedConstantsUseTheActualClassArgumentEvenWhenTheLexicalNameConflicts() {
        for(String v:VERSIONS)for(String expression:List.of(">>label","(>>label)")) {
            var in=input(v,"FUNCTION check(objects:CLASS;attr:ATTRIBUTE OF @objects RESTRICTION(TEXT)):BOOLEAN;"
                    +"TOPIC T=CLASS Data=label:TEXT*10;END Data;CLASS Host=label:BOOLEAN;"
                    +"MANDATORY CONSTRAINT F.check(>Data,"+expression+");END Host;END T;");
            var out=ok(in);var g=out.schema().semanticGraph();var invocation=call(g);
            var actual=assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().get(1)).orElseThrow());
            assertEquals(attribute(g,"Data","label").id(),actual.target());
            assertNotEquals(attribute(g,"Host","label").id(),actual.target());
            var type=assertInstanceOf(AttributePathType.class,g.type(actual.typeId()).orElseThrow());
            assertEquals(new AttributePathType.Viewable(named(g,"Data").id()),type.owner().orElseThrow());
            assertEquals(attribute(g,"Data","label").declaredType(),type.restrictions().getFirst());
            var source=in.sources().sources().values().iterator().next();int offset=source.text().lastIndexOf(">>label")+2;
            var cursor=source.range(offset,offset);var definition=LanguageQueries.create().definition(out.analysis(),cursor,TOKEN).known().orElseThrow();
            assertEquals(actual.target(),definition.symbol().symbol());assertEquals("label",source.slice(definition.nameRange()));
            assertEquals(new SourceRevision(9),definition.nameRange().revision());
        }
    }
    @Test void chainedAttributeArgumentsBindEachUnqualifiedConstantInItsOwnActualRoot() {
        for(String v:VERSIONS) {
            var out=ok(input(v,"FUNCTION check(objects:CLASS;bag:ATTRIBUTE OF @objects RESTRICTION(BAG OF ANYSTRUCTURE);"
                    +"field:ATTRIBUTE OF @bag RESTRICTION(TEXT)):BOOLEAN;"
                    +"STRUCTURE Item=label:TEXT*10;END Item;TOPIC T=CLASS Data=items:BAG OF Item;END Data;"
                    +"CLASS Host=MANDATORY CONSTRAINT F.check(>Data,>>items,>>label);END Host;END T;"));
            var g=out.schema().semanticGraph();var invocation=call(g);
            for(int i=1;i<3;i++) {
                var actual=assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().get(i)).orElseThrow());
                var target=i==1?attribute(g,"Data","items"):attribute(g,"Item","label");
                assertEquals(target.id(),actual.target());
                var descriptor=assertInstanceOf(AttributePathType.class,g.type(actual.typeId()).orElseThrow());
                assertEquals(new AttributePathType.Viewable(target.owner().orElseThrow()),descriptor.owner().orElseThrow());
            }
        }
    }
    @Test void explicitAttributeOwnersRemainExactAndUnrestrictedParametersKeepLexicalContext() {
        for(String v:VERSIONS) {
            String prefix="FUNCTION check(objects:CLASS;attr:ATTRIBUTE OF @objects RESTRICTION(TEXT)):BOOLEAN;"
                    +"TOPIC T=CLASS Base=label:TEXT*10;END Base;CLASS Child EXTENDS Base=childOnly:TEXT*10;END Child;CLASS Host=";
            for(String arguments:List.of(">Base,>>Child->childOnly",">Child,>>Base->label")) {
                var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,prefix+"MANDATORY CONSTRAINT F.check("+arguments+");END Host;END T;"),TOKEN));
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
                assertTrue(out.analysis().diagnostics().toString().contains("FUNCTION_ATTRIBUTE"));
            }
            var g=ok(input(v,prefix+"MANDATORY CONSTRAINT F.check(>Child,>>Child->label);END Host;END T;")).schema().semanticGraph();
            var constant=assertInstanceOf(SymbolValueExpression.class,g.expression(call(g).children().get(1)).orElseThrow());
            assertEquals(attribute(g,"Base","label").id(),constant.target());
            assertEquals(new AttributePathType.Viewable(named(g,"Child").id()),((AttributePathType)g.type(constant.typeId()).orElseThrow()).owner().orElseThrow());
            ok(input(v,"FUNCTION check(path:ATTRIBUTE):BOOLEAN;TOPIC T=CLASS Host=label:TEXT;MANDATORY CONSTRAINT F.check(>>label);END Host;END T;"));
        }
    }
    @Test void aDependencyOnALaterActualArgumentDoesNotChangeThePublishedArgumentOrder() {
        for(String v:VERSIONS) {
            var out=ok(input(v,"FUNCTION check(attr:ATTRIBUTE OF @objects RESTRICTION(TEXT);objects:CLASS):BOOLEAN;"
                    +"TOPIC T=CLASS Data=label:TEXT*10;END Data;CLASS Host=MANDATORY CONSTRAINT F.check(>>label,>Data);END Host;END T;"));
            var g=out.schema().semanticGraph();var invocation=call(g);
            assertEquals(attribute(g,"Data","label").id(),assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().getFirst()).orElseThrow()).target());
            assertEquals(named(g,"Data").id(),assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().get(1)).orElseThrow()).target());
        }
    }
    @Test void builtinAttributeArgumentsUseTheSameDependentContextAsUserFunctions() {
        for(String v:VERSIONS) {
            var out=ok(input(v,"DOMAIN P=COORD 0.000..100.000,0.000..100.000;"
                    +"STRUCTURE S=surface:SURFACE WITH(STRAIGHTS) VERTEX P WITHOUT OVERLAPS >0.010;END S;"
                    +"TOPIC T=CLASS C=bag:BAG OF S;surface:BOOLEAN;SET CONSTRAINT INTERLIS.areAreas(ALL,>>bag,>>surface);END C;END T;"));
            var g=out.schema().semanticGraph();var invocation=call(g);
            var actual=assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().get(2)).orElseThrow());
            assertEquals(attribute(g,"S","surface").id(),actual.target());
            assertNotEquals(attribute(g,"C","surface").id(),actual.target());
        }
    }
    @Test void aFailedOwnerArgumentDoesNotPublishALexicallyBoundReplacementConstant() {
        for(String v:VERSIONS) {
            var out=assertInstanceOf(CompilationOutcome.Rejected.class,CompilerEngines.create().compile(input(v,
                    "FUNCTION check(objects:CLASS;attr:ATTRIBUTE OF @objects RESTRICTION(TEXT)):BOOLEAN;"
                    +"TOPIC T=CLASS Host=label:TEXT;MANDATORY CONSTRAINT F.check(>Missing,>>label);END Host;END T;"),TOKEN));
            assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),out.rejection().reasons());
            assertEquals(1,out.analysis().diagnostics().size());assertEquals("ILI-SEM-0001",out.analysis().diagnostics().getFirst().code().value());
            var g=out.analysis().semanticGraph();var invocation=call(g);
            var owner=assertInstanceOf(ErrorExpression.class,g.expression(invocation.children().getFirst()).orElseThrow());
            var dependent=assertInstanceOf(ErrorExpression.class,g.expression(invocation.children().get(1)).orElseThrow());
            assertEquals(owner.causes(),dependent.causes());
            assertEquals(ExpressionTypeState.ERROR,invocation.typeState());
        }
    }
    @Test void longForwardArgumentDependenciesAreResolvedWithoutReorderingOrFixedDepthCutoffs() {
        for(String v:VERSIONS) {
            int length=100;var declarations=new ArrayList<String>();var actuals=new ArrayList<String>();
            for(int i=0;i<length;i++) {declarations.add("arg"+i+":ATTRIBUTE OF @arg"+(i+1)+" RESTRICTION(BAG OF ANYSTRUCTURE)");actuals.add(">>items");}
            declarations.add("arg"+length+":STRUCTURE");actuals.add(">Item");
            var out=ok(input(v,"FUNCTION check("+String.join(";",declarations)+"):BOOLEAN;"
                    +"STRUCTURE Item=items:BAG OF Item;END Item;TOPIC T=CLASS Host=MANDATORY CONSTRAINT F.check("
                    +String.join(",",actuals)+");END Host;END T;"));
            var g=out.schema().semanticGraph();var invocation=call(g);assertEquals(length+1,invocation.children().size());
            for(int i=0;i<length;i++)assertEquals(attribute(g,"Item","items").id(),
                    assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().get(i)).orElseThrow()).target());
            assertEquals(named(g,"Item").id(),assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().getLast()).orElseThrow()).target());
        }
    }
    @Test void importedDependentContextsFollowRevisionsAndMatchFreshAnalysisInEitherFileOrder() {
        for(String v:VERSIONS)for(boolean reverse:List.of(false,true)) {
            var baseId=new SourceId(reverse?"memory:/z-base.ili":"memory:/a-base.ili");
            var use=source(reverse?"memory:/a-use.ili":"memory:/z-use.ili","INTERLIS "+v+";CONTRACTED MODEL F AT \"x\" VERSION \"1\"="
                    +"IMPORTS Base;TOPIC T=CLASS Host=label:BOOLEAN;MANDATORY CONSTRAINT Base.check(>Base.T.Data,>>label);END Host;END T;END F.");
            var engine=CompilerEngines.create();AnalysisSnapshot previous=null;long revision=0;
            for(String type:List.of("TEXT","BOOLEAN","TEXT")) {
                var base=new SourceText(baseId,new SourceRevision(++revision),"INTERLIS "+v+";CONTRACTED MODEL Base AT \"x\" VERSION \"1\"="
                        +"FUNCTION check(objects:CLASS;attr:ATTRIBUTE OF @objects RESTRICTION(TEXT)):BOOLEAN;"
                        +"TOPIC T=CLASS Data=label:"+type+";END Data;END T;END Base.");
                var in=request(List.of("F"),Map.of(base.id(),base,use.id(),use),List.of(binding(parse(use),"Base",base,parse(base))),CompilerOptions.defaults());
                var fresh=CompilerEngines.create().analyze(in,TOKEN);
                var reused=engine.analyze(in,previous==null?AnalysisReuse.none():new AnalysisReuse(Optional.of(previous),previous.syntaxSnapshots()),TOKEN);
                assertEquals(fresh.diagnostics(),reused.diagnostics());assertEquals(type.equals("TEXT"),fresh.diagnostics().isEmpty());
                assertEquals(OriginalViewReuseTest.semantic(fresh.semanticGraph().expressions(),fresh.semanticGraph()),
                        OriginalViewReuseTest.semantic(reused.semanticGraph().expressions(),reused.semanticGraph()));
                for(var analysis:List.of(fresh,reused)) {
                    var g=analysis.semanticGraph();var actual=assertInstanceOf(SymbolValueExpression.class,g.expression(call(g).children().get(1)).orElseThrow());
                    assertEquals(attribute(g,"Data","label").id(),actual.target());
                    int offset=use.text().lastIndexOf(">>label")+2;
                    var definition=LanguageQueries.create().definition(analysis,use.range(offset,offset),TOKEN).known().orElseThrow();
                    assertEquals(baseId,definition.nameRange().source());assertEquals(base.revision(),definition.nameRange().revision());
                }
                previous=reused;
            }
        }
    }
    @Test void dependentReturnTypesAreBoundToEachInvocationWithoutChangingTheDeclaration() {
        for(String v:VERSIONS)for(String actual:List.of(">Data","cls")) {
            var out=ok(input(v,"FUNCTION pick(objects:CLASS):ATTRIBUTE OF @objects RESTRICTION(TEXT);"
                    +"FUNCTION check(objects:CLASS;attr:ATTRIBUTE OF @objects RESTRICTION(TEXT)):BOOLEAN;"
                    +"TOPIC T=CLASS Data=label:TEXT;END Data;CLASS Host=cls:CLASS RESTRICTION(Data);"
                    +"MANDATORY CONSTRAINT F.check("+actual+",F.pick("+actual+"));END Host;END T;"));
            var g=out.schema().semanticGraph();
            var pick=assertInstanceOf(FunctionSymbol.class,named(g,"pick"));
            var declaration=assertInstanceOf(AttributePathType.class,g.type(pick.returnType()).orElseThrow());
            assertEquals(new AttributePathType.Argument(pick.id(),0),declaration.owner().orElseThrow());
            var nested=g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast)
                    .filter(c->c.targetFunction().filter(pick.id()::equals).isPresent()).findFirst().orElseThrow();
            assertNotEquals(pick.returnType(),nested.typeId());
            var result=assertInstanceOf(AttributePathType.class,g.type(nested.typeId()).orElseThrow());
            if(actual.startsWith(">"))assertEquals(new AttributePathType.Viewable(named(g,"Data").id()),result.owner().orElseThrow());
            else assertEquals(new AttributePathType.ClassAttribute(nested.children().getFirst()),result.owner().orElseThrow());
            assertEquals(declaration.restrictions(),result.restrictions());
        }
    }
    @Test void undefinedAndUnrestrictedActualOwnersRetainLegalSymbolicReturnDependencies() {
        for(String v:VERSIONS)for(boolean objectSet:List.of(false,true)) {
            String signature=objectSet?"OBJECTS OF ANYCLASS":"CLASS";
            String actual=objectSet?"F.objects(#true)":"UNDEFINED";
            var out=ok(input(v,"FUNCTION objects(flag:BOOLEAN):OBJECTS OF ANYCLASS;"
                    +"FUNCTION pick(objects:"+signature+"):ATTRIBUTE OF @objects RESTRICTION(TEXT);"
                    +"TOPIC T=CLASS Host="+(objectSet?"SET":"MANDATORY")+" CONSTRAINT DEFINED(F.pick("+actual+"));END Host;END T;"));
            var g=out.schema().semanticGraph();var pick=assertInstanceOf(FunctionSymbol.class,named(g,"pick"));
            var invocation=g.expressions().stream().filter(FunctionCallExpression.class::isInstance).map(FunctionCallExpression.class::cast)
                    .filter(c->c.targetFunction().filter(pick.id()::equals).isPresent()).findFirst().orElseThrow();
            assertEquals(ExpressionTypeState.RESOLVED,invocation.typeState());
            assertEquals(pick.returnType(),invocation.typeId());
            var descriptor=assertInstanceOf(AttributePathType.class,g.type(invocation.typeId()).orElseThrow());
            assertEquals(new AttributePathType.Argument(pick.id(),0),descriptor.owner().orElseThrow());
            assertEquals(1,invocation.children().size());
            var argument=g.expression(invocation.children().getFirst()).orElseThrow();
            if(objectSet) {
                var objects=assertInstanceOf(FunctionCallExpression.class,argument);
                assertEquals(named(g,"objects").id(),objects.targetFunction().orElseThrow());
                var type=assertInstanceOf(ObjectArgumentType.class,g.type(argument.typeId()).orElseThrow());
                assertTrue(type.objects());assertTrue(type.base().isEmpty());assertTrue(type.restrictions().isEmpty());
            } else assertTrue(guru.interlis.model.internal.TypeCompatibility.isUndefined(g.type(argument.typeId()).orElseThrow()));
        }
    }
    @Test void anIndeterminateOwnerKeepsTheNormativeLexicalConstantContextWithoutInventingAClass() {
        for(String v:VERSIONS)for(String actual:List.of("cls","UNDEFINED")) {
            var out=ok(input(v,"FUNCTION check(objects:CLASS;attr:ATTRIBUTE OF @objects RESTRICTION(TEXT)):BOOLEAN;"
                    +"TOPIC T=CLASS Host=cls:CLASS;label:TEXT;MANDATORY CONSTRAINT F.check("+actual+",>>label);END Host;END T;"));
            var g=out.schema().semanticGraph();var invocation=call(g);
            var constant=assertInstanceOf(SymbolValueExpression.class,g.expression(invocation.children().get(1)).orElseThrow());
            assertEquals(attribute(g,"Host","label").id(),constant.target());
            var descriptor=assertInstanceOf(AttributePathType.class,g.type(constant.typeId()).orElseThrow());
            assertEquals(new AttributePathType.Viewable(named(g,"Host").id()),descriptor.owner().orElseThrow());
        }
    }
    @Test void aClassValuedAttributeSuppliesItsDeclaredDependentAttributeArgument() {
        for(String v:VERSIONS) {
            var out=ok(input(v,"FUNCTION check(objects:CLASS;attr:ATTRIBUTE OF @objects RESTRICTION(TEXT)):BOOLEAN;"
                    +"TOPIC T=CLASS Data=label:TEXT;END Data;CLASS Host=cls:CLASS RESTRICTION(Data);"
                    +"path:ATTRIBUTE OF cls RESTRICTION(TEXT);MANDATORY CONSTRAINT F.check(cls,path);END Host;END T;"));
            var g=out.schema().semanticGraph();var invocation=call(g);
            var actual=assertInstanceOf(PathExpression.class,g.expression(invocation.children().get(1)).orElseThrow());
            assertEquals(attribute(g,"Host","path").id(),assertInstanceOf(PathStep.AttributeStep.class,actual.path().steps().getFirst()).target());
            var type=assertInstanceOf(AttributePathType.class,g.type(actual.typeId()).orElseThrow());
            var owner=assertInstanceOf(PathExpression.class,g.expression(((AttributePathType.ClassAttribute)type.owner().orElseThrow()).path()).orElseThrow());
            assertEquals(attribute(g,"Host","cls").id(),assertInstanceOf(PathStep.AttributeStep.class,owner.path().steps().getFirst()).target());
        }
    }
}

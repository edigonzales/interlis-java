package guru.interlis.compiler;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact historical C findings; reference expectations are assessed separately. */
class GeometryOriginalAcceptanceTest {
    @Test void pinnedOriginalsHaveVersionedOutcomesAndUsablePublicGeometry() throws Exception {
        var pins=Map.of(
                "custom-segment-2.3.ili","5f5c65f5cfdf5f27aed43ccc210b0a0159d53337d1f83910cb1a1f94d871dee3",
                "custom-segment-2.4.ili","810623b4d6ea0785963029c76c6ab0593840f4527bad4a3a8137afbec206b755",
                "fine-tolerance-2.3.ili","e32e6ab7ea0b1d4a040bf1dee1e8a46f1ea3594c9e2179bd386f5f7662920124",
                "fine-tolerance-2.4.ili","ab2d4922191650b6982b2c7a694bb7143748cee00b90a444d9622ae41ff1dc1b",
                "missing-overlap-19.ili","7b1ff6ac859cb3482cebf36c1972a1bf5f7779fae4aff0863d9e180a07d3c639",
                "missing-overlap-21.ili","b9ccd0b3effdfbe90c2da27427e153bba24978cdbad13f560063f87aa8495291",
                "missing-overlap-47.ili","b27c9870817000d9fd33b9e81e738da73f2772b27a14ef3c427b4fc75163f14c",
                "missing-overlap-49.ili","6ea78e720c7abce9a4fe2403999277430fad73ad5952868a0c72cd47894589d8");
        for(var pin:pins.entrySet()) {
            var bytes=Files.readAllBytes(Path.of("../testdata/conformance/g2-geometry",pin.getKey()));
            assertEquals(pin.getValue(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            var source=CompilerTestSupport.source("memory:/"+pin.getKey(),new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            var input=CompilerTestSupport.request(List.of(pin.getKey().startsWith("fine")?"M":"F"),Map.of(source.id(),source),List.of(),CompilerOptions.defaults());
            var result=CompilerEngines.create().compile(input,CancellationToken.none());
            String rule=switch(pin.getKey()) {
                case "fine-tolerance-2.4.ili" -> "OVERLAP_PRECISION";
                case "missing-overlap-19.ili", "missing-overlap-21.ili" -> "GEOMETRY_INCOMPLETE";
                default -> null;
            };
            if(rule!=null) {
                var rejected=assertInstanceOf(CompilationOutcome.Rejected.class,result,pin.getKey());
                assertEquals(Set.of(RejectionReason.SOURCE_ERRORS),rejected.rejection().reasons());
                assertTrue(rejected.analysis().diagnostics().toString().contains(rule));continue;
            }
            var graph=assertInstanceOf(CompilationOutcome.Succeeded.class,result,()->pin.getKey()+": "+result).schema().semanticGraph();
            if(pin.getKey().startsWith("custom")) {
                var original=graph.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast).filter(s->s.name().value().equals("S")).findFirst().orElseThrow();
                var context=graph.symbols().stream().filter(StructureSymbol.class::isInstance).map(StructureSymbol.class::cast).filter(s->s.origin() instanceof Origin.DerivedOrigin d && d.declaredAt().equals(original.id())).findFirst().orElseThrow();
                assertEquals(2,graph.effectiveMembers(context.id()).size());
                var point=(AttributeSymbol)graph.symbol(graph.effectiveMembers(context.id()).getFirst().declaredSymbol()).orElseThrow();
                assertEquals(3,((CoordinateType)graph.type(point.declaredType()).orElseThrow()).dimensions());
                assertEquals(original.members().getFirst(),((Origin.DerivedOrigin)graph.symbol(context.members().getFirst()).orElseThrow().origin()).declaredAt());
            } else if(pin.getKey().startsWith("missing")) {
                var area=graph.symbols().stream().filter(AttributeSymbol.class::isInstance).map(AttributeSymbol.class::cast).filter(a->a.name().value().equals("area")).findFirst().orElseThrow();
                assertInstanceOf(GeometryType.TrackWidth.class,((GeometryType)graph.type(area.declaredType()).orElseThrow()).overlap().orElseThrow());
            } else {
                var domain=graph.symbols().stream().filter(DomainSymbol.class::isInstance).map(DomainSymbol.class::cast).filter(d->d.name().value().equals("L")).findFirst().orElseThrow();
                assertEquals("0.00001",((GeometryType.Explicit)((GeometryType)graph.type(domain.typeId()).orElseThrow()).overlap().orElseThrow()).value().lexeme());
            }
        }
    }
}

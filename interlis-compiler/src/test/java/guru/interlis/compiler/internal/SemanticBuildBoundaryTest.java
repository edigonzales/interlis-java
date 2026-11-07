package guru.interlis.compiler.internal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticBuildBoundaryTest {
    @Test
    void onlyTheCoordinatorOwnsTheWholeBuild() {
        var binders = List.of(NameBindings.class, ReferenceBindings.class, TranslationBindings.class,
                TypeBindings.class, FormattedTypeBindings.class, GenericBindings.class, OidBindings.class,
                UnitBindings.class, ExpressionBindings.class, ObjectPathBindings.class, ConstraintBindings.class,
                InheritanceBindings.class, DeclarationBindings.class, TopicBindings.class, GraphicBuilder.class,
                ViewBuilder.class, SegmentContextualizer.class, MetadataBindings.class, FunctionBindings.class,
                StructureBindings.class, PredefinedTemporal.class, PredefinedModels.class, LineForms.class,
                NumericSubdivisions.class, ExactUnitFactors.class, OverlayBindings.class, SemanticDiagnostics.class);
        for (var binder : binders) {
            assertFalse(Arrays.stream(binder.getDeclaredFields()).anyMatch(field ->
                    field.getGenericType().getTypeName().contains(GraphBuilder.class.getName())), binder.getName());
            assertFalse(Arrays.stream(binder.getDeclaredConstructors()).flatMap(ctor -> Arrays.stream(ctor.getGenericParameterTypes()))
                    .anyMatch(type -> type.getTypeName().contains(GraphBuilder.class.getName())), binder.getName());
            assertFalse(Arrays.stream(binder.getDeclaredMethods()).flatMap(method -> Arrays.stream(method.getGenericParameterTypes()))
                    .anyMatch(type -> type.getTypeName().contains(GraphBuilder.class.getName())), binder.getName());
            assertFalse(Arrays.stream(binder.getDeclaredFields()).anyMatch(field ->
                    field.getType() == SemanticBuildState.class), binder.getName());
        }
    }

    @Test
    void mutableGraphStorageIsPrivate() {
        assertTrue(Arrays.stream(SemanticBuildState.class.getDeclaredFields())
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())));
    }
}

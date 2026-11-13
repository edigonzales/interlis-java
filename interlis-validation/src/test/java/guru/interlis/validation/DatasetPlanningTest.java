package guru.interlis.validation;

import guru.interlis.model.QualifiedName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static guru.interlis.validation.ValidationPlannerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class DatasetPlanningTest {
    @Test
    void groupsSiblingTypesUnderOneInheritedUniqueRule() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data =
                    CLASS Base (ABSTRACT) =
                      key : TEXT;
                      UNIQUE key;
                    END Base;
                    CLASS Left EXTENDS Base = END Left;
                    CLASS Right EXTENDS Base = END Right;
                  END Data;
                END Checks.
                """);
        var transfer = transfer(schema);
        var local = ready(schema, transfer);
        var dataset = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer, ValidationProfile.DATASET)).plan();

        var query = schema.queries();
        var base = query.findQualifiedName(QualifiedName.of("Checks", "Data", "Base")).getFirst();
        var left = query.findQualifiedName(QualifiedName.of("Checks", "Data", "Left")).getFirst();
        var right = query.findQualifiedName(QualifiedName.of("Checks", "Data", "Right")).getFirst();
        var rule = dataset.datasetRules().getFirst();
        assertEquals(1, dataset.datasetRules().size());
        assertEquals(base, rule.key().applicationRoot());
        assertEquals(List.of(left, right), rule.candidateTypes());
        assertEquals(ValidationProfile.LOCAL_ONLY, local.profile());
        assertEquals(2, local.uncheckedRules().size());
        assertTrue(dataset.uncheckedRules().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> LocalValidator.session(dataset, ValidationOptions.defaults()));

        var subjects = SubjectSelection.fromRoots(dataset);
        subjects.check(dataset);
        assertEquals(query.findQualifiedName(QualifiedName.of("Checks", "Data")), subjects.roots());
        assertTrue(subjects.selects(dataset, InputRole.PRIMARY, subjects.roots().getFirst(), left));
        assertFalse(subjects.selects(dataset, InputRole.REFERENCE, subjects.roots().getFirst(), left));
    }
    @Test
    void retainsRulesForAnExplicitlyEmptyPopulation() {
        var schema = compile("""
                INTERLIS 2.4;
                MODEL Checks (en) AT "https://example.org/checks" VERSION "1" =
                  TOPIC Data (ABSTRACT) =
                    CLASS Item (ABSTRACT) =
                      SET CONSTRAINT INTERLIS.objectCount(ALL) > 0;
                    END Item;
                  END Data;
                END Checks.
                """);
        var dataset = assertInstanceOf(ValidationPlanningResult.Ready.class,
                ValidationPlanner.plan(transfer(schema), ValidationProfile.DATASET)).plan();
        var item = schema.queries().findQualifiedName(QualifiedName.of("Checks", "Data", "Item")).getFirst();
        var rule = dataset.datasetRules().getFirst();
        assertEquals(item, rule.key().applicationRoot());
        assertTrue(rule.candidateTypes().isEmpty());

        var input = new InputId("primary");
        var scope = new ExtentSelector(item, Set.of(input), Optional.empty());
        var open = ExtentDeclarations.open();
        var closed = new ExtentDeclarations(List.of(
                new ExtentDeclaration(scope, ExtentAspect.OBJECT_MEMBERSHIP, "complete inventory")));
        assertFalse(open.explicitlyComplete(scope, ExtentAspect.OBJECT_MEMBERSHIP));
        closed.check(dataset, Set.of(input));
        assertTrue(closed.explicitlyComplete(scope, ExtentAspect.OBJECT_MEMBERSHIP));
        assertFalse(closed.explicitlyComplete(scope, ExtentAspect.ATTRIBUTE_VALUES));
        assertThrows(IllegalArgumentException.class, () -> closed.check(dataset, Set.of()));
        var coverage = new DatasetRuleCoverage(rule.key(), DatasetRuleCoverage.ScopeKind.DECLARED_EXTENT,
                DatasetRuleCoverage.Completeness.DECLARED_COMPLETE, DatasetRuleCoverage.Outcome.VIOLATED,
                1, 0, 1, 1, 0);
        assertEquals(0, coverage.candidates());
        assertEquals(1, coverage.evaluations());
    }

}

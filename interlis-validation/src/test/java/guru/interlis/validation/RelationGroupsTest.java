package guru.interlis.validation;

import guru.interlis.execution.CancellationToken;
import guru.interlis.model.SymbolId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationGroupsTest {
    @Test
    void groupsTernaryCounterpartsAndCountsTargetsOnce() {
        try (var store = new InMemoryValidationStorage(100, 10_000)) {
            var groups = new RelationGroups(store);
            var role = new SymbolId("role");
            var a = new ObjectHandle(1);
            var b = new ObjectHandle(2);
            var c = new ObjectHandle(3);
            var d = new ObjectHandle(4);
            groups.observe(role, List.of(a, b), c);
            groups.observe(role, List.of(a, b), c);
            groups.observe(role, List.of(a, b), d);
            groups.observe(role, List.of(a, c), b);
            groups.expect(role, List.of(a, d));
            var observed = new ArrayList<RelationGroups.Group>();
            groups.groups(CancellationToken.none(), observed::add);
            assertEquals(List.of(new RelationGroups.Group(role, List.of(a, b), 2),
                    new RelationGroups.Group(role, List.of(a, c), 1),
                    new RelationGroups.Group(role, List.of(a, d), 0)), observed);
        }
    }
}

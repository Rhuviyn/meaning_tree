package org.vstu.meaningtree.iterators;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;
import org.vstu.meaningtree.nodes.types.user.Class;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Обход узла с несколькими полями-коллекциями. {@link ExceptionCatchStatement} — первый такой
 * узел: ресурсы и ветви лежат в двух отдельных списках.
 */
public class DFSNodeIteratorTests {
    @Test
    void everyElementOfEveryCollectionFieldIsVisited() {
        ExceptionCatchStatement statement = new ExceptionCatchStatement(
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("body"))),
                List.of(new SimpleIdentifier("resource")),
                List.of(clause("first"), clause("second"), clause("third")),
                null,
                null
        );

        List<String> names = new ArrayList<>();
        List<CatchClause> clauses = new ArrayList<>();
        for (NodeInfo info : visit(statement)) {
            if (info.node() instanceof SimpleIdentifier identifier) {
                names.add(identifier.getName());
            }
            if (info.node() instanceof CatchClause clause) {
                clauses.add(clause);
            }
        }

        // Раньше поле-коллекция, идущее после уже начатой, затирало её итератор, и все ветви
        // кроме первой выпадали из обхода
        assertEquals(3, clauses.size(), "Visited catch clauses: " + clauses.size());
        assertTrue(names.containsAll(List.of("body", "resource", "first", "second", "third")),
                "Traversal missed some children: " + names);
    }

    @Test
    void everyVisitedNodeIsReportedExactlyOnce() {
        ExceptionCatchStatement statement = new ExceptionCatchStatement(
                new CompoundStatement(),
                List.of(new SimpleIdentifier("resource")),
                List.of(clause("first"), clause("second")),
                null,
                null
        );

        List<Long> ids = new ArrayList<>();
        for (NodeInfo info : visit(statement)) {
            ids.add(info.node().getId());
        }

        assertEquals(ids.size(), ids.stream().distinct().count(), "Some node was visited twice: " + ids);
    }

    private static List<NodeInfo> visit(ExceptionCatchStatement statement) {
        List<NodeInfo> visited = new ArrayList<>();
        DFSNodeIterator iterator = new DFSNodeIterator(statement, true);
        while (iterator.hasNext()) {
            visited.add(iterator.next());
        }
        return visited;
    }

    private static CatchClause clause(String name) {
        return new CatchClause(
                new Class(new SimpleIdentifier("Error")),
                new SimpleIdentifier(name),
                new CompoundStatement()
        );
    }
}

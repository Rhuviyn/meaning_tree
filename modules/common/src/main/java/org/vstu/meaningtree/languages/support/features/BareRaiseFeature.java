package org.vstu.meaningtree.languages.support.features;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;

/**
 * Повторное возбуждение текущего исключения без указания значения: {@code raise} в Python,
 * {@code throw;} в C++. В Java такой формы нет — {@code throw} всегда требует выражение, а
 * переменной текущего исключения вне ветви {@code catch} не существует.
 */
public class BareRaiseFeature extends SemanticFeature {
    @Override
    public String id() {
        return "bare-raise";
    }

    @Override
    public boolean matches(Node node, @Nullable FeatureContext context) {
        return node instanceof RaiseExceptionStatement stmt && !stmt.hasException();
    }

    @Override
    public String description(Node node) {
        return "Re-raising the current exception without a value is not supported by this language";
    }
}

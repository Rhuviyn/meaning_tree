package org.vstu.meaningtree.languages.support.features;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;

/**
 * Ветвь {@code finally}. В C++ такого синтаксиса нет: гарантированное освобождение выражается
 * там деструкторами и scope guard'ами, а не блоком, поэтому автоматически перевести ветвь
 * нельзя.
 */
public class TryFinallyFeature extends SemanticFeature {
    @Override
    public String id() {
        return "try-finally";
    }

    @Override
    public boolean matches(Node node, @Nullable FeatureContext context) {
        return node instanceof ExceptionCatchStatement stmt && stmt.hasFinallyBranch();
    }

    @Override
    public String description(Node node) {
        return "Finally branch is not supported by this language";
    }
}

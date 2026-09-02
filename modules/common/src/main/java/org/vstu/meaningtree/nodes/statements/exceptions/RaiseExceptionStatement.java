package org.vstu.meaningtree.nodes.statements.exceptions;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;

import java.util.Objects;

/**
 * Возбуждение исключения: <code>throw new Exception()</code> в Java/C++ и
 * <code>raise ValueError()</code> в Python.
 * <p>
 * Выражение отсутствует при повторном возбуждении текущего исключения
 * (<code>raise</code> в Python, <code>throw;</code> в C++).
 */
public class RaiseExceptionStatement extends Statement {
    @TreeNode
    @Nullable
    private Expression exception;

    public RaiseExceptionStatement(@Nullable Expression exception) {
        this.exception = exception;
    }

    /**
     * Повторное возбуждение текущего исключения.
     */
    public RaiseExceptionStatement() {
        this(null);
    }

    @Nullable
    public Expression getException() {
        return exception;
    }

    public boolean hasException() {
        return exception != null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RaiseExceptionStatement nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(exception, nodeInfos.exception);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), exception);
    }

    @Override
    public RaiseExceptionStatement clone() {
        var clone = (RaiseExceptionStatement) super.clone();
        clone.exception = exception == null ? null : exception.clone();
        return clone;
    }
}

package org.vstu.meaningtree.nodes.io;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;

import java.util.List;

public abstract class PrintCommand extends FunctionCall {

    public PrintCommand(@NotNull List<Expression> values) {
        super(null, values);
    }

    // hasFunctionName() не переопределяется: имя команде печати подставляет целевой язык,
    // а в дереве function == null, поэтому для анализа это вызов без имени. Ответ true
    // заставлял бы getFunctionName() возвращать null.
}

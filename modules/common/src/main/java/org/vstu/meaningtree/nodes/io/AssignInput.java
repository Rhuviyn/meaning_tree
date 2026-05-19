package org.vstu.meaningtree.nodes.io;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Expression;

import java.util.List;

public class AssignInput extends InputCommand {
    public final Expression maxInputLength;

    public AssignInput(@NotNull Expression value) {
        super(List.of(value));
        this.maxInputLength = null;
    }

    public AssignInput(@NotNull Expression value, Expression maxInputLength) {
        super(List.of(value));
        this.maxInputLength = maxInputLength;
    }

    public Expression getValue() {
        return arguments.getFirst();
    }

    public boolean hasLimitedLength() {
        return maxInputLength != null;
    }
}

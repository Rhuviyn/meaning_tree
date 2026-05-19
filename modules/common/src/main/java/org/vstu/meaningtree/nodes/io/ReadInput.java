package org.vstu.meaningtree.nodes.io;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;

import java.util.List;
import java.util.Objects;

public class ReadInput extends InputCommand {
    public Type type;
    public final boolean readsLine;

    public ReadInput(@Nullable Expression prompt, Type type, boolean readsLine) {
        super(prompt != null ? List.of(prompt) : List.of());
        this.type = type;
        this.readsLine = readsLine;
    }

    public ReadInput setType(Type type) {
        if (type != null) {
            this.type = type;
        }
        return this;
    }

    public Expression getPrompt() {
        if (hasPrompt()) {
            return arguments.getFirst();
        }
        return null;
    }

    public boolean hasPrompt() {
        return !arguments.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ReadInput nodeInfos = (ReadInput) o;
        return readsLine == nodeInfos.readsLine && Objects.equals(type, nodeInfos.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type, readsLine);
    }
}

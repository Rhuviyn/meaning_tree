package org.vstu.meaningtree.nodes.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;

import java.util.List;
import java.util.Objects;

public class InputValue extends InputCommand {

    @Nullable
    @TreeNode public final Expression promptMessage;

    @Nullable
    @TreeNode public final Expression scannerName;

    public final Type type;

    public final boolean readsLine;

    public InputValue(@NotNull Expression value, @Nullable Expression promptMessage, @Nullable Expression scannerName, Type type, boolean readsLine) {
        super(List.of(value));
        this.promptMessage = promptMessage;
        this.scannerName = scannerName;
        this.type = type;
        this.readsLine = readsLine;
    }

    public Expression getValue() {
        if (hasValue()) {
            return arguments.getFirst();
        }
        return null;
    }

    public boolean hasValue() {
        return !arguments.isEmpty();
    }

    public boolean hasMessage() {
        return promptMessage != null;
    }

    public boolean hasScanner() {
        return scannerName != null;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        InputValue nodeInfos = (InputValue) o;
        return readsLine == nodeInfos.readsLine && Objects.equals(promptMessage, nodeInfos.promptMessage) && Objects.equals(type, nodeInfos.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), promptMessage, type, readsLine);
    }

    public static class InputValueBuilder {
        @Nullable private Expression _promptMessage = null;

        @Nullable private Expression _scannerName = null;

        private Type _varType = null;

        private boolean _readsLine = false;

        @Nullable
        private Expression _value;

        public InputValueBuilder setValue(Expression value) {
            _value = value;
            return this;
        }

        public InputValueBuilder setPromptMessage(Expression message) {
            if (message != null) {
                _promptMessage = message;
            }
            return this;
        }

        public InputValueBuilder setScannerName(Expression scannerName) {
            if (scannerName != null) {
                _scannerName = scannerName;
            }
            return this;
        }

        public InputValueBuilder setVarType(Type varType) {
            if (varType != null) {
                _varType = varType;
            }
            return this;
        }

        public InputValueBuilder readLine() {
            _readsLine = true;
            return this;
        }

        public InputValue build() {
            Objects.requireNonNull(_value);
            return new InputValue(_value, _promptMessage, _scannerName, _varType, _readsLine);
        }
    }
}

package org.vstu.meaningtree.utils.analysis.types;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionKind;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionSemantics;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

/** Static typing compatibility for Python's bool/int/float numeric tower. */
public final class PythonTypeConversionSemantics implements TypeConversionSemantics {
    @Override
    public @NotNull Optional<Boolean> overrideCompatibility(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @NotNull ScopeTable scope) {
        if (!isPythonPrimitive(source) || !isPythonPrimitive(target)) {
            if (isArithmeticPrimitive(source) && isArithmeticPrimitive(target)) {
                return Optional.of(false);
            }
            return Optional.empty();
        }
        if (kind == ConversionKind.EXPLICIT) {
            return Optional.of(true);
        }
        if (sameCategory(source, target)) {
            return Optional.of(true);
        }
        if (source instanceof BooleanType) {
            return Optional.of(target instanceof IntType || target instanceof FloatType);
        }
        return Optional.of(source instanceof IntType && target instanceof FloatType);
    }

    private boolean isPythonPrimitive(Type type) {
        return type instanceof BooleanType || type instanceof IntType || type instanceof FloatType;
    }

    private boolean isArithmeticPrimitive(Type type) {
        return type instanceof org.vstu.meaningtree.nodes.types.builtin.NumericType
                || type instanceof BooleanType;
    }

    private boolean sameCategory(Type source, Type target) {
        return source instanceof BooleanType && target instanceof BooleanType
                || source instanceof IntType && target instanceof IntType
                || source instanceof FloatType && target instanceof FloatType;
    }
}

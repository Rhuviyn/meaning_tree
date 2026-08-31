package org.vstu.meaningtree.utils.analysis.types;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionKind;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionSemantics;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

/** Primitive conversion contexts defined by the Java language. */
public final class JavaTypeConversionSemantics implements TypeConversionSemantics {
    @Override
    public @NotNull Optional<Boolean> overrideCompatibility(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @NotNull ScopeTable scope) {
        if (!isPrimitive(source) || !isPrimitive(target)) {
            return Optional.empty();
        }
        if (source.equals(target)) {
            return Optional.of(true);
        }
        if (source instanceof BooleanType || target instanceof BooleanType) {
            return Optional.of(false);
        }
        if (source instanceof CharacterType && target instanceof CharacterType) {
            return Optional.of(true);
        }
        if (!(source instanceof NumericType) || !(target instanceof NumericType)) {
            return Optional.of(false);
        }
        if (kind == ConversionKind.EXPLICIT) {
            return Optional.of(true);
        }
        return Optional.of(isWideningNumericConversion(source, target));
    }

    private boolean isPrimitive(Type type) {
        return type instanceof NumericType || type instanceof BooleanType;
    }

    private boolean isWideningNumericConversion(Type source, Type target) {
        if (source instanceof FloatType sourceFloat) {
            return target instanceof FloatType targetFloat
                    && sourceFloat.getBitsize() == 32
                    && targetFloat.getBitsize() == 64;
        }
        if (source instanceof CharacterType) {
            return isSignedJavaInt(target, 32, 64) || target instanceof FloatType;
        }
        if (!(source instanceof IntType sourceInt) || sourceInt.isUnsigned) {
            return false;
        }
        if (target instanceof FloatType) {
            return true;
        }
        if (!(target instanceof IntType targetInt) || targetInt.isUnsigned) {
            return false;
        }
        return switch (sourceInt.getBitsize()) {
            case 8 -> isOneOf(targetInt.getBitsize(), 16, 32, 64);
            case 16 -> isOneOf(targetInt.getBitsize(), 32, 64);
            case 32 -> targetInt.getBitsize() == 64;
            default -> false;
        };
    }

    private boolean isSignedJavaInt(Type type, int... bitSizes) {
        return type instanceof IntType intType
                && !intType.isUnsigned
                && isOneOf(intType.getBitsize(), bitSizes);
    }

    private boolean isOneOf(int value, int... candidates) {
        for (int candidate : candidates) {
            if (candidate == value) {
                return true;
            }
        }
        return false;
    }
}

package org.vstu.meaningtree.utils.analysis.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionCompatibility;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionKind;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionSiteKind;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionSemantics;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

/** Primitive conversion contexts defined by the Java language. */
public final class JavaTypeConversionSemantics implements TypeConversionSemantics {
    @Override
    public @NotNull Optional<ConversionCompatibility> overrideCompatibility(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @Nullable ConversionSiteKind siteKind,
            @Nullable Expression value,
            @NotNull ScopeTable scope) {
        if (!isPrimitive(source) || !isPrimitive(target)) {
            return Optional.empty();
        }
        if (source.equals(target)) {
            return Optional.of(ConversionCompatibility.COMPATIBLE);
        }
        if (source instanceof BooleanType || target instanceof BooleanType) {
            return Optional.of(ConversionCompatibility.INCOMPATIBLE);
        }
        if (source instanceof CharacterType && target instanceof CharacterType) {
            return Optional.of(ConversionCompatibility.COMPATIBLE);
        }
        if (!(source instanceof NumericType) || !(target instanceof NumericType)) {
            return Optional.of(ConversionCompatibility.INCOMPATIBLE);
        }
        if (kind == ConversionKind.EXPLICIT) {
            return Optional.of(ConversionCompatibility.COMPATIBLE);
        }
        if (isWideningNumericConversion(source, target)) {
            return Optional.of(ConversionCompatibility.COMPATIBLE);
        }
        return Optional.of(verdict(isConstantNarrowing(source, target, siteKind, value)));
    }

    /**
     * Сужение константного выражения при присваивании (JLS 5.2): {@code byte small = 1;}
     * допустим, хотя {@code int -> byte} расширением не является. Правило контекстное — тот же
     * переход в аргументе вызова запрещён, — поэтому оно опирается на место преобразования и на
     * само значение, а не только на типы.
     */
    private boolean isConstantNarrowing(
            Type source,
            Type target,
            ConversionSiteKind siteKind,
            Expression value) {
        if (siteKind != ConversionSiteKind.INITIALIZER && siteKind != ConversionSiteKind.ASSIGNMENT) {
            return false;
        }
        if (!(source instanceof IntType sourceInt) || sourceInt.isUnsigned || sourceInt.getBitsize() > 32) {
            return false;
        }
        if (!(value instanceof IntegerLiteral literal)) {
            return false;
        }
        long constant = literal.getValue().longValue();
        if (target instanceof CharacterType characterType) {
            return fits(constant, characterType.getBitsize(), true);
        }
        return target instanceof IntType targetInt
                && targetInt.getBitsize() < 32
                && fits(constant, targetInt.getBitsize(), targetInt.isUnsigned);
    }

    private boolean fits(long value, int bitsize, boolean unsigned) {
        if (unsigned) {
            return value >= 0 && value < (1L << bitsize);
        }
        long bound = 1L << (bitsize - 1);
        return value >= -bound && value < bound;
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

    private static ConversionCompatibility verdict(boolean allowed) {
        return allowed ? ConversionCompatibility.COMPATIBLE : ConversionCompatibility.INCOMPATIBLE;
    }
}

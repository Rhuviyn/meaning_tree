package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.utils.scopes.OverloadSemantics;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Objects;

/** Checks language-aware compatibility of explicit and implicit type conversions. */
public class TypeConversionAnalyzer {
    private final TypeConversionSemantics semantics;

    /**
     * Правила перегрузок нужны не самой проверке типов, а разрешению вызова, без которого не
     * узнать тип параметра в месте аргумента. Держать их здесь дешевле, чем требовать от каждого
     * вызывающего собирать те же правила отдельно.
     */
    private final OverloadSemantics overloadSemantics;

    public TypeConversionAnalyzer() {
        this(TypeConversionSemantics.common());
    }

    public TypeConversionAnalyzer(@NotNull TypeConversionSemantics semantics) {
        this(semantics, OverloadSemantics.bySignature());
    }

    public TypeConversionAnalyzer(@NotNull TypeConversionSemantics semantics,
                                  @NotNull OverloadSemantics overloadSemantics) {
        this.semantics = Objects.requireNonNull(semantics, "semantics must not be null");
        this.overloadSemantics = Objects.requireNonNull(overloadSemantics, "overloadSemantics must not be null");
    }

    /** Правила перегрузок, с которыми построен анализатор. */
    @NotNull
    public OverloadSemantics overloadSemantics() {
        return overloadSemantics;
    }

    /**
     * Доказано ли, что преобразование допустимо.
     * <p>
     * Неизвестный ответ здесь равен {@code false}: «не доказано допустимо». Там, где важно
     * отличить пробел анализа от запрета языка, нужен {@link #compatibility}.
     */
    public boolean isCompatible(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind) {
        return isCompatible(source, target, kind, new ScopeTable());
    }

    public boolean isCompatible(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @NotNull ScopeTable scope) {
        return compatibility(source, target, kind, null, null, scope) == ConversionCompatibility.COMPATIBLE;
    }

    /**
     * Полный ответ о преобразовании: разрешено, запрещено или неизвестно.
     *
     * @param siteKind место преобразования либо {@code null} при проверке вне конкретного места
     * @param value    преобразуемое выражение либо {@code null}, если оно недоступно
     */
    @NotNull
    public ConversionCompatibility compatibility(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @Nullable ConversionSiteKind siteKind,
            @Nullable Expression value,
            @NotNull ScopeTable scope) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        // Неизвестный тип отсекается до языковых правил: ни одно из них не может обоснованно
        // высказаться о типе, которого анализ не вывел.
        if (source instanceof UnknownType || target instanceof UnknownType) {
            return ConversionCompatibility.UNKNOWN;
        }
        return semantics.overrideCompatibility(source, target, kind, siteKind, value, scope)
                .orElseGet(() -> commonCompatibility(source, target, kind));
    }

    /** Performs a non-mutating pass over all supported value-transfer sites. */
    public TypeConversionReport analyze(@NotNull MeaningTree tree, @NotNull ScopeTable scope) {
        Objects.requireNonNull(tree, "tree must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        return new TypeConversionSiteAnalyzer(this, tree, scope).analyze();
    }

    private ConversionCompatibility commonCompatibility(Type source, Type target, ConversionKind kind) {
        return commonlyAllowed(source, target, kind)
                ? ConversionCompatibility.COMPATIBLE
                : ConversionCompatibility.INCOMPATIBLE;
    }

    private boolean commonlyAllowed(Type source, Type target, ConversionKind kind) {
        if (source.equals(target)) {
            return true;
        }
        if (source instanceof BooleanType || target instanceof BooleanType) {
            return false;
        }
        if (!(source instanceof NumericType sourceNumeric)
                || !(target instanceof NumericType targetNumeric)) {
            return false;
        }
        if (kind == ConversionKind.EXPLICIT) {
            return true;
        }
        if (source instanceof FloatType) {
            return target instanceof FloatType
                    && targetNumeric.getBitsize() >= sourceNumeric.getBitsize();
        }
        if (target instanceof FloatType) {
            return true;
        }
        if (source instanceof CharacterType) {
            if (target instanceof CharacterType) {
                return targetNumeric.getBitsize() >= sourceNumeric.getBitsize();
            }
            if (target instanceof IntType targetInt) {
                return targetInt.isUnsigned
                        ? targetInt.getBitsize() >= sourceNumeric.getBitsize()
                        : targetInt.getBitsize() > sourceNumeric.getBitsize();
            }
        }
        if (source instanceof IntType sourceInt && target instanceof IntType targetInt) {
            if (sourceInt.isUnsigned == targetInt.isUnsigned) {
                return targetInt.getBitsize() >= sourceInt.getBitsize();
            }
            return sourceInt.isUnsigned
                    && !targetInt.isUnsigned
                    && targetInt.getBitsize() > sourceInt.getBitsize();
        }
        return false;
    }
}

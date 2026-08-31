package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Objects;

/** Checks language-aware compatibility of explicit and implicit type conversions. */
public class TypeConversionAnalyzer {
    private final TypeConversionSemantics semantics;

    public TypeConversionAnalyzer() {
        this(TypeConversionSemantics.common());
    }

    public TypeConversionAnalyzer(@NotNull TypeConversionSemantics semantics) {
        this.semantics = Objects.requireNonNull(semantics, "semantics must not be null");
    }

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
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(scope, "scope must not be null");

        return semantics.overrideCompatibility(source, target, kind, scope)
                .orElseGet(() -> commonCompatibility(source, target, kind));
    }

    /** Performs a non-mutating pass over all supported value-transfer sites. */
    public TypeConversionReport analyze(@NotNull MeaningTree tree, @NotNull ScopeTable scope) {
        Objects.requireNonNull(tree, "tree must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        return new TypeConversionSiteAnalyzer(this, tree, scope).analyze();
    }

    private boolean commonCompatibility(Type source, Type target, ConversionKind kind) {
        if (source instanceof UnknownType || target instanceof UnknownType) {
            return false;
        }
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

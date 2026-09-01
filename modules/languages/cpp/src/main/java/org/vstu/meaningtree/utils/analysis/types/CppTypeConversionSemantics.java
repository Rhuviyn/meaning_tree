package org.vstu.meaningtree.utils.analysis.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;
import org.vstu.meaningtree.nodes.types.builtin.NumericType;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionCompatibility;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionKind;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionSiteKind;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionSemantics;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

/** Standard conversions between C++ arithmetic types. */
public final class CppTypeConversionSemantics implements TypeConversionSemantics {
    @Override
    public @NotNull Optional<ConversionCompatibility> overrideCompatibility(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @Nullable ConversionSiteKind siteKind,
            @Nullable Expression value,
            @NotNull ScopeTable scope) {
        if (isArithmetic(source) && isArithmetic(target)) {
            return Optional.of(ConversionCompatibility.COMPATIBLE);
        }
        return Optional.empty();
    }

    private boolean isArithmetic(Type type) {
        return type instanceof NumericType || type instanceof BooleanType;
    }
}

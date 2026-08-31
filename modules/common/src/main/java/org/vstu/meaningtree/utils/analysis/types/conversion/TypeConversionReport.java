package org.vstu.meaningtree.utils.analysis.types.conversion;

import java.util.List;
import java.util.Objects;

/** Immutable result of a complete type-conversion analysis pass. */
public final class TypeConversionReport {
    private final List<TypeConversionCheck> checks;

    public TypeConversionReport(List<TypeConversionCheck> checks) {
        this.checks = List.copyOf(Objects.requireNonNull(checks, "checks must not be null"));
    }

    public List<TypeConversionCheck> checks() {
        return checks;
    }

    public List<TypeConversionCheck> incompatibleChecks() {
        return checks.stream().filter(check -> !check.compatible()).toList();
    }

    public boolean isCompatible() {
        return checks.stream().allMatch(TypeConversionCheck::compatible);
    }
}

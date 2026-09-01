package org.vstu.meaningtree.utils.analysis.types.conversion;

import java.util.List;
import java.util.Objects;

/** Неизменяемый результат полного прохода анализа преобразований. */
public final class TypeConversionReport {
    private final List<TypeConversionCheck> checks;

    public TypeConversionReport(List<TypeConversionCheck> checks) {
        this.checks = List.copyOf(Objects.requireNonNull(checks, "checks must not be null"));
    }

    public List<TypeConversionCheck> checks() {
        return checks;
    }

    /** Преобразования, запрещённые правилами языка: типы известны, и переход недопустим. */
    public List<TypeConversionCheck> incompatibleChecks() {
        return checks.stream()
                .filter(check -> check.compatibility() == ConversionCompatibility.INCOMPATIBLE)
                .toList();
    }

    /**
     * Места, о которых анализ ничего не знает: не выведен тип либо не найдена цель вызова.
     * Отдельно от {@link #incompatibleChecks()} — это не найденные дефекты, а пробелы анализа.
     */
    public List<TypeConversionCheck> unresolvedChecks() {
        return checks.stream()
                .filter(check -> check.compatibility() == ConversionCompatibility.UNKNOWN)
                .toList();
    }

    /**
     * Все ли преобразования доказано допустимы. Неизвестные считаются недоказанными, поэтому
     * {@code false} здесь означает «нет доказательства», а не «найдена ошибка»; за найденными
     * ошибками — {@link #incompatibleChecks()}.
     */
    public boolean isCompatible() {
        return checks.stream()
                .allMatch(check -> check.compatibility() == ConversionCompatibility.COMPATIBLE);
    }
}

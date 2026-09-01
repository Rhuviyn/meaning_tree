package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

/**
 * Уточнение общих правил преобразования правилами конкретного языка.
 * <p>
 * Пустой результат передаёт решение общим правилам; присутствующий — окончателен, включая
 * {@link ConversionCompatibility#INCOMPATIBLE}.
 * <p>
 * Кроме самих типов правило получает место преобразования и само значение: без них нельзя
 * выразить контекстные правила языков. В Java, например, {@code byte small = 1;} допустим как
 * сужение константного выражения при инициализации, но тот же переход в вызове запрещён — то
 * есть ответ зависит от {@code siteKind} и от того, константа ли справа.
 */
@FunctionalInterface
public interface TypeConversionSemantics {
    /**
     * @param siteKind место преобразования либо {@code null}, если проверка выполняется вне
     *                 конкретного места (прямой вызов API проверки типов)
     * @param value    преобразуемое выражение либо {@code null}, если оно недоступно
     */
    @NotNull
    Optional<ConversionCompatibility> overrideCompatibility(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @Nullable ConversionSiteKind siteKind,
            @Nullable Expression value,
            @NotNull ScopeTable scope);

    static TypeConversionSemantics common() {
        return (source, target, kind, siteKind, value, scope) -> Optional.empty();
    }
}

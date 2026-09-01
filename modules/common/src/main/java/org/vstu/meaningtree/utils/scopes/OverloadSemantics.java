package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.CharacterType;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.NumericType;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;

import java.util.List;

/**
 * Правила языка о том, что считать перегрузкой.
 * <p>
 * Языки расходятся не в деталях, а в самом наличии перегрузок: в Java и C++ одноимённые
 * объявления с разными сигнатурами сосуществуют, а в Python второй {@code def} того же имени
 * просто затеняет первый — во время исполнения остаётся одна функция. Без этого различия
 * повторные Python-определения были бы приняты за перегрузку, которой в исходном коде нет.
 */
public interface OverloadSemantics {
    /**
     * Могут ли одноимённые объявления сосуществовать как разные перегрузки.
     *
     * @return {@code false}, если позднее объявление затеняет раннее и живой остаётся
     *         последняя декларация
     */
    boolean allowsOverloading();

    /**
     * Сигнатура в том виде, в каком язык различает по ней перегрузки.
     * <p>
     * Возвращаемый тип по умолчанию не входит: ни Java, ни C++, ни Python не позволяют
     * перегружать по нему. Метод существует, чтобы язык, который это позволяет, мог включить
     * его, не трогая сам проход индексации.
     */
    @NotNull
    default List<Type> signatureOf(@NotNull FunctionDeclaration declaration) {
        return declaration.getArguments().stream().map(DeclarationArgument::getType).toList();
    }

    /**
     * Какое из двух допустимых преобразований аргумента язык считает лучшим при выборе
     * перегрузки.
     * <p>
     * Без этого сравнения все допустимые преобразования равнозначны, и вызов {@code f(1)} при
     * перегрузках {@code f(long)} и {@code f(double)} остаётся неразрешённым, хотя и Java, и C++
     * выбирают здесь {@code long}. Возвращать «оба одинаковы» там, где язык на самом деле
     * различает, — значит терять достоверную связь.
     * <p>
     * Умолчание описывает правило, общее для Java и C++: сохранение категории важнее ширины
     * (целое к целому лучше, чем целое к дробному), а внутри категории лучше меньшее
     * расширение. Язык, где это не так, метод переопределяет.
     *
     * @return отрицательное, если лучше {@code left}; положительное, если {@code right}; ноль,
     *         если язык эти преобразования не различает
     */
    default int compareConversions(@NotNull Type argument, @NotNull Type left, @NotNull Type right) {
        long leftCost = conversionCost(argument, left);
        long rightCost = conversionCost(argument, right);
        return Long.compare(leftCost, rightCost);
    }

    /**
     * Стоимость преобразования: меньше — лучше, {@link Long#MAX_VALUE} — язык не берётся
     * ранжировать. Разные непревращаемые пары получают одну и ту же максимальную стоимость и
     * потому считаются неразличимыми, а не произвольно упорядоченными.
     */
    private static long conversionCost(Type argument, Type target) {
        if (argument.equals(target)) {
            return 0;
        }
        if (!(argument instanceof NumericType source) || !(target instanceof NumericType destination)) {
            return Long.MAX_VALUE;
        }
        int categoryPenalty = sameCategory(argument, target) ? 0 : 1000;
        int width = destination.getBitsize() - source.getBitsize();
        // Сужение хуже любого расширения: оно теряет значение, а не только точность.
        return categoryPenalty + (width < 0 ? 100_000 - width : width);
    }

    private static boolean sameCategory(Type left, Type right) {
        return left instanceof FloatType == right instanceof FloatType
                && left instanceof CharacterType == right instanceof CharacterType;
    }

    /** Перегрузка по сигнатуре: Java, C++. */
    static OverloadSemantics bySignature() {
        return () -> true;
    }

    /** Затенение вместо перегрузки: Python. */
    static OverloadSemantics shadowing() {
        return () -> false;
    }
}

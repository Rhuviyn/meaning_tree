package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Type;
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

    /** Перегрузка по сигнатуре: Java, C++. */
    static OverloadSemantics bySignature() {
        return () -> true;
    }

    /** Затенение вместо перегрузки: Python. */
    static OverloadSemantics shadowing() {
        return () -> false;
    }
}

package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.io.Serializable;
import java.util.*;

/**
 * Все декларации одного имени в пределах одной области видимости, в порядке регистрации.
 * <p>
 * Одно имя держит несколько деклараций только для callable-сущностей: перегрузка — это
 * единственный случай, когда одноимённые объявления сосуществуют, а не затеняют друг друга.
 * Класс, перечисление или переменная, объявленные повторно, по-прежнему вытесняют предыдущее
 * объявление, как это делал {@code Map.put} до появления перегрузок.
 */
final class DeclarationBucket implements Serializable {
    @NotNull
    private final Map<SimpleIdentifier, List<Declaration>> byName = new LinkedHashMap<>();

    /**
     * Регистрирует объявление, сохраняя порядок.
     * <p>
     * Повторная регистрация той же сигнатуры — это второе упоминание той же callable-сущности
     * (объявление в заголовке и определение в реализации у C++; метод, зарегистрированный
     * сначала как функция, а затем как метод, у Python), поэтому она заменяет прежнюю запись,
     * а не создаёт вторую перегрузку.
     */
    void register(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        List<Declaration> declarations = byName.computeIfAbsent(name, key -> new ArrayList<>());

        if (!(declaration instanceof FunctionDeclaration function)) {
            declarations.clear();
            declarations.add(declaration);
            return;
        }

        // Callable затеняет не-callable того же имени: сосуществовать они не могут, а
        // перегрузками друг другу не являются.
        declarations.removeIf(existing -> !(existing instanceof FunctionDeclaration));

        for (int i = 0; i < declarations.size(); i++) {
            if (sameSignature((FunctionDeclaration) declarations.get(i), function)) {
                declarations.set(i, function);
                return;
            }
        }
        declarations.add(function);
    }

    /**
     * Последняя подходящая декларация имени.
     * <p>
     * Именно последняя, а не первая: до перегрузок хранилище было {@code Map} и повторная
     * регистрация вытесняла прежнее значение, поэтому «последняя» — это ровно то, что видели
     * все существующие вызывающие стороны.
     */
    Optional<Declaration> findLast(@NotNull SimpleIdentifier name,
                                   @Nullable Class<? extends Declaration> clazz) {
        List<Declaration> declarations = byName.get(name);
        if (declarations == null) {
            return Optional.empty();
        }
        for (int i = declarations.size() - 1; i >= 0; i--) {
            if (matches(declarations.get(i), clazz)) {
                return Optional.of(declarations.get(i));
            }
        }
        return Optional.empty();
    }

    /** Все подходящие декларации имени в порядке регистрации. */
    List<Declaration> findAll(@NotNull SimpleIdentifier name,
                              @Nullable Class<? extends Declaration> clazz) {
        List<Declaration> declarations = byName.get(name);
        if (declarations == null) {
            return List.of();
        }
        return declarations.stream().filter(declaration -> matches(declaration, clazz)).toList();
    }

    /** Все декларации области видимости, подходящие под класс, в порядке регистрации. */
    List<Declaration> findAll(@NotNull Class<? extends Declaration> clazz) {
        List<Declaration> result = new ArrayList<>();
        for (List<Declaration> declarations : byName.values()) {
            for (Declaration declaration : declarations) {
                if (matches(declaration, clazz)) {
                    result.add(declaration);
                }
            }
        }
        return List.copyOf(result);
    }

    boolean isEmpty() {
        return byName.isEmpty();
    }

    /** Снимок содержимого: имя — все его декларации в порядке регистрации. */
    Map<SimpleIdentifier, List<Declaration>> asMap() {
        Map<SimpleIdentifier, List<Declaration>> copy = new LinkedHashMap<>();
        byName.forEach((name, declarations) -> copy.put(name, List.copyOf(declarations)));
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Совпадают ли сигнатуры в пределах одной области видимости.
     * <p>
     * Имя здесь уже общее — оно ключ, — поэтому сравниваются только типы параметров. Владелец
     * в сравнение не входит намеренно: в одной области видимости не бывает двух разных
     * владельцев, зато бывает одна и та же функция, зарегистрированная сначала как свободная,
     * а потом как метод (Python), и это одна сущность, а не две перегрузки.
     */
    private static boolean sameSignature(@NotNull FunctionDeclaration left,
                                         @NotNull FunctionDeclaration right) {
        return argumentTypes(left).equals(argumentTypes(right));
    }

    private static List<Type> argumentTypes(@NotNull FunctionDeclaration declaration) {
        return declaration.getArguments().stream().map(DeclarationArgument::getType).toList();
    }

    private static boolean matches(@NotNull Declaration declaration,
                                   @Nullable Class<? extends Declaration> clazz) {
        return clazz == null || clazz.isAssignableFrom(declaration.getClass());
    }
}

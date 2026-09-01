package org.vstu.meaningtree.utils.analysis.symbols;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.interfaces.Callable;
import org.vstu.meaningtree.nodes.types.builtin.IntType;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет связь места вызова с декларацией. Разрешение обязано либо дать достоверный ответ,
 * либо не дать никакого: молчаливый выбор «первого подходящего» неотличим от догадки.
 */
class OverloadCallResolverTests {

    @Test
    void resolvesOverloadByArgumentCount() {
        MeaningTree tree = parse(new JavaTranslator(), """
                public class Ov {
                    static int f(int a) { return a; }
                    static int f(int a, int b) { return a + b; }
                    static void use() {
                        f(1);
                        f(1, 2);
                    }
                }
                """);

        List<Callable> calls = callsNamed(tree, "f");
        assertEquals(2, calls.size());
        assertEquals(1, resolved(calls.get(0)).getArguments().size());
        assertEquals(2, resolved(calls.get(1)).getArguments().size());
    }

    @Test
    void resolvesSameArityOverloadByArgumentType() {
        MeaningTree tree = parse(new CppTranslator(), """
                int f(int a) { return a; }
                double f(double a) { return a; }
                int main() {
                    int x = 1;
                    double y = 2.0;
                    f(x);
                    f(y);
                    return 0;
                }
                """);

        List<Callable> calls = callsNamed(tree, "f");
        assertEquals(2, calls.size());
        assertNotSame(resolved(calls.get(0)), resolved(calls.get(1)),
                "перегрузки одной арности различаются по типу аргумента");
    }

    /**
     * Аргументом здесь намеренно взята локальная переменная, а не параметр метода: тип
     * параметра {@code SimpleTypeInferrer} сейчас не выводит, и вызов остался бы неразрешённым
     * не из-за отбора кандидатов, а из-за неполноты вывода типов.
     */
    @Test
    void prefersExactTypeMatchOverConversion() {
        MeaningTree tree = parse(new JavaTranslator(), """
                public class Ov {
                    static int f(long a) { return 1; }
                    static int f(int a) { return 2; }
                    static void use() {
                        int value = 1;
                        f(value);
                    }
                }
                """);

        FunctionDeclaration declaration = resolved(callsNamed(tree, "f").getFirst());
        assertInstanceOf(IntType.class, declaration.getArguments().getFirst().getType());
        assertEquals(32, ((IntType) declaration.getArguments().getFirst().getType()).getBitsize(),
                "точное совпадение типа выигрывает у расширяющего преобразования");
    }

    /**
     * И Java, и C++ предпочитают меньшее расширение: {@code int -> long} выигрывает у
     * {@code int -> double}. Пока допустимые преобразования считались равнозначными, такой
     * вызов оставался несвязанным, хотя язык различает кандидатов однозначно.
     */
    @Test
    void prefersSmallerWideningConversion() {
        MeaningTree tree = parse(new JavaTranslator(), """
                public class Ov {
                    static int f(long a) { return 1; }
                    static int f(double a) { return 2; }
                    static void use() {
                        f(1);
                    }
                }
                """);

        FunctionDeclaration declaration = resolved(callsNamed(tree, "f").getFirst());
        assertInstanceOf(IntType.class, declaration.getArguments().getFirst().getType(),
                "должна выбраться перегрузка long, а не double");
    }

    /**
     * Тип аргумента не выведен — сравнивать нечего, и вызов остаётся несвязанным. Это пробел
     * вывода типов, а не правило разрешения перегрузок: тот же вызов с литералом выбирает
     * кандидата (см. {@link #prefersSmallerWideningConversion()}).
     */
    @Test
    void leavesCallWithUninferredArgumentUnresolved() {
        MeaningTree tree = parse(new JavaTranslator(), """
                public class Ov {
                    static int f(long a) { return 1; }
                    static int f(double a) { return 2; }
                    static void use(int value) {
                        f(value);
                    }
                }
                """);

        for (Callable call : callsNamed(tree, "f")) {
            assertNull(call.getResolvedDeclaration(), "тип value не выведен, ранжировать нечем");
        }
    }

    /**
     * Внутренняя декларация затеняет внешнюю целиком. Отбор кандидатов перебором всех деклараций
     * дерева по имени считал обе видимыми одновременно, и вызов оставался несвязанным.
     */
    @Test
    void nestedDeclarationShadowsTheOuterOne() {
        MeaningTree tree = parse(new PythonTranslator(), """
                def f(a):
                    return a

                def outer():
                    def f(a, b):
                        return a
                    return f(1, 2)
                """);

        FunctionDeclaration declaration = resolved(callsNamed(tree, "f").getFirst());
        assertEquals(2, declaration.getArguments().size(),
                "должна выбраться вложенная f, а не затенённая внешняя");
    }

    @Test
    void leavesUnknownCallUnresolved() {
        MeaningTree tree = parse(new JavaTranslator(), """
                public class Ov {
                    static void use() {
                        somethingOutside(1);
                    }
                }
                """);

        assertNull(callsNamed(tree, "somethingOutside").getFirst().getResolvedDeclaration());
    }

    @Test
    void resolvesConstructorOverload() {
        MeaningTree tree = parse(new JavaTranslator(), """
                public class Point {
                    Point() {}
                    Point(int x, int y) {}
                    static Point make() { return new Point(1, 2); }
                }
                """);

        // new Point(...) — это ObjectNewExpression; ConstructorCall в модели означает
        // делегирование конструктору (super(...) / this(...)).
        List<Callable> calls = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info.node() instanceof ObjectNewExpression call) {
                calls.add(call);
            }
        }
        assertEquals(1, calls.size());
        assertEquals(2, resolved(calls.getFirst()).getArguments().size());
    }

    @Test
    void doesNotResolveAcrossOwners() {
        MeaningTree tree = parse(new JavaTranslator(), """
                class A {
                    int f(int a) { return a; }
                }
                class B {
                    int f(int a, int b) { return a; }
                    int use() { return f(1, 2); }
                }
                """);

        FunctionDeclaration declaration = resolved(callsNamed(tree, "f").getFirst());
        assertEquals(2, declaration.getArguments().size(),
                "вызов внутри класса разрешается только в методы своего владельца");
    }

    @Test
    void clearedOnClone() {
        MeaningTree tree = parse(new JavaTranslator(), """
                public class Ov {
                    static int f(int a) { return a; }
                    static void use() { f(1); }
                }
                """);

        Callable call = callsNamed(tree, "f").getFirst();
        assertNotNull(call.getResolvedDeclaration());
        assertNull(((FunctionCall) call).clone().getResolvedDeclaration(),
                "клон — ещё не разобранный вызов");
    }

    private static MeaningTree parse(LanguageTranslator translator, String code) {
        return translator.getMeaningTree(code);
    }

    private static List<Callable> callsNamed(MeaningTree tree, String name) {
        List<Callable> calls = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info.node() instanceof FunctionCall call
                    && call.hasFunctionName()
                    && call.getFunctionName().toString().equals(name)) {
                calls.add(call);
            }
        }
        return calls;
    }

    private static FunctionDeclaration resolved(Callable call) {
        FunctionDeclaration declaration = call.getResolvedDeclaration();
        assertNotNull(declaration, () -> "вызов должен был разрешиться: " + call);
        return declaration;
    }
}

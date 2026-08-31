package org.vstu.meaningtree.utils.analysis.symbols;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.utils.scopes.OverloadGroup;
import org.vstu.meaningtree.utils.scopes.OverloadKind;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет построение групп перегрузок на настоящих парсерах, а не на собранном вручную
 * дереве: смысл прохода как раз в том, чтобы сгладить расхождения трёх языков в том, как их
 * парсеры регистрируют члены классов.
 */
class OverloadIndexerTests {

    @Test
    void groupsJavaMethodOverloadsByOwner() {
        ScopeTable scope = parse(new JavaTranslator(), """
                public class Ov {
                    static int f(int a) { return a; }
                    static double f(double a) { return a; }
                    static int f(int a, int b) { return a + b; }
                }
                class Other {
                    int f(int a) { return a; }
                }
                """);

        List<OverloadGroup> named = groupsNamed(scope, "f");
        assertEquals(2, named.size(), "одноимённые методы разных классов — разные группы");

        OverloadGroup overloaded = single(named.stream().filter(OverloadGroup::isOverloaded).toList());
        assertEquals(OverloadKind.METHOD, overloaded.kind());
        assertEquals(3, overloaded.declarations().size());
        assertNotNull(overloaded.owner());

        OverloadGroup lonely = single(named.stream().filter(group -> !group.isOverloaded()).toList());
        assertEquals(1, lonely.declarations().size());
        assertNotEquals(overloaded.owner(), lonely.owner());
    }

    @Test
    void groupsJavaConstructorsSeparatelyFromMethods() {
        ScopeTable scope = parse(new JavaTranslator(), """
                public class Point {
                    Point() {}
                    Point(int x) {}
                    Point(int x, int y) {}
                    void Point(String misleadingName) {}
                }
                """);

        OverloadGroup constructors = single(groups(scope, OverloadKind.CONSTRUCTOR));
        assertEquals(3, constructors.declarations().size());

        OverloadGroup method = single(groups(scope, OverloadKind.METHOD));
        assertEquals(1, method.declarations().size(),
                "метод с именем класса не является перегрузкой конструктора");
    }

    @Test
    void groupsCppFreeFunctions() {
        ScopeTable scope = parse(new CppTranslator(), """
                int f(int a) { return a; }
                double f(double a) { return a; }
                int f(int a, int b) { return a + b; }
                int g(int a) { return a; }
                """);

        OverloadGroup overloaded = single(groupsNamed(scope, "f"));
        assertEquals(OverloadKind.FUNCTION, overloaded.kind());
        assertNull(overloaded.owner());
        assertEquals(3, overloaded.declarations().size());

        assertFalse(single(groupsNamed(scope, "g")).isOverloaded());
    }

    @Test
    void treatsRepeatedPythonDefinitionsAsShadowing() {
        ScopeTable scope = parse(new PythonTranslator(), """
                def f(a):
                    return a

                def f(a, b):
                    return a + b
                """);

        OverloadGroup group = single(groupsNamed(scope, "f"));
        assertFalse(group.isOverloaded(), "повторный def затеняет прежний, а не перегружает его");
        assertEquals(2, group.declarations().getFirst().getArguments().size(),
                "живой остаётся последняя декларация");
    }

    @Test
    void doesNotTreatOverriddenMethodAsOverload() {
        ScopeTable scope = parse(new JavaTranslator(), """
                class Animal {
                    void speak(int volume) {}
                }
                class Dog extends Animal {
                    void speak(int volume) {}
                }
                """);

        List<OverloadGroup> named = groupsNamed(scope, "speak");
        assertEquals(2, named.size());
        assertTrue(named.stream().noneMatch(OverloadGroup::isOverloaded),
                "переопределение описывается моделью override, а не группой перегрузок");
    }

    @Test
    void separatesFunctionsOfDifferentScopes() {
        ScopeTable scope = parse(new JavaTranslator(), """
                public class A {
                    void m() {
                        class Local { int f(int a) { return a; } }
                    }
                    void m(int x) {}
                }
                """);

        assertFalse(single(groupsNamed(scope, "f")).isOverloaded(),
                "метод локального класса не перегружает ничего снаружи");
        assertEquals(2, single(groupsNamed(scope, "m")).declarations().size());
    }

    @Test
    void indexesMembersAndResolvesGroupByDeclaration() {
        ScopeTable scope = parse(new JavaTranslator(), """
                public class Ov {
                    int f(int a) { return a; }
                    int f(int a, int b) { return a + b; }
                }
                """);

        OverloadGroup group = single(groupsNamed(scope, "f"));
        assertNotNull(group.owner());
        assertEquals(2, scope.findMembers(group.owner(), group.name()).size());

        for (FunctionDeclaration declaration : group.declarations()) {
            assertSame(group, scope.findOverloadGroup(declaration).orElseThrow());
            assertTrue(group.contains(declaration));
        }
    }

    private static ScopeTable parse(LanguageTranslator translator, String code) {
        translator.getMeaningTree(code);
        return translator.getLatestScopeTable();
    }

    private static List<OverloadGroup> groupsNamed(ScopeTable scope, String name) {
        return scope.overloadGroups().stream()
                .filter(group -> group.name().toString().equals(name))
                .toList();
    }

    private static List<OverloadGroup> groups(ScopeTable scope, OverloadKind kind) {
        return scope.overloadGroups().stream().filter(group -> group.kind() == kind).toList();
    }

    private static OverloadGroup single(List<OverloadGroup> groups) {
        assertEquals(1, groups.size(), () -> "ожидалась одна группа, получено: " + groups);
        return groups.getFirst();
    }
}

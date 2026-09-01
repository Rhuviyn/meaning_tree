package org.vstu.meaningtree.analysis;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.BooleanType;
import org.vstu.meaningtree.nodes.types.builtin.CharacterType;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.analysis.types.CppTypeConversionSemantics;
import org.vstu.meaningtree.utils.analysis.types.JavaTypeConversionSemantics;
import org.vstu.meaningtree.utils.analysis.types.PythonTypeConversionSemantics;
import org.vstu.meaningtree.utils.analysis.types.conversion.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TypeConversionAnalyzerTests {
    private static final Map<String, Object> JAVA_CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    @Test
    void commonRulesAreConservative() {
        TypeConversionAnalyzer analyzer = new TypeConversionAnalyzer();

        assertTrue(analyzer.isCompatible(new IntType(16), new IntType(32), ConversionKind.IMPLICIT));
        assertFalse(analyzer.isCompatible(new IntType(32), new IntType(16), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new IntType(32), new FloatType(32), ConversionKind.IMPLICIT));
        assertFalse(analyzer.isCompatible(new FloatType(32), new IntType(32), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new FloatType(32), new IntType(32), ConversionKind.EXPLICIT));
        assertFalse(analyzer.isCompatible(new BooleanType(), new IntType(), ConversionKind.EXPLICIT));
        assertFalse(analyzer.isCompatible(new UnknownType(), new IntType(), ConversionKind.IMPLICIT));
    }

    @Test
    void commonRulesAccountForSignedness() {
        TypeConversionAnalyzer analyzer = new TypeConversionAnalyzer();

        assertTrue(analyzer.isCompatible(new IntType(16, true), new IntType(32), ConversionKind.IMPLICIT));
        assertFalse(analyzer.isCompatible(new IntType(32, true), new IntType(32), ConversionKind.IMPLICIT));
        assertFalse(analyzer.isCompatible(new IntType(16), new IntType(32, true), ConversionKind.IMPLICIT));
    }

    @Test
    void javaRulesDistinguishWideningAndExplicitNarrowing() {
        TypeConversionAnalyzer analyzer = new TypeConversionAnalyzer(new JavaTypeConversionSemantics());

        assertTrue(analyzer.isCompatible(new IntType(8), new IntType(64), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new IntType(64), new FloatType(32), ConversionKind.IMPLICIT));
        assertFalse(analyzer.isCompatible(new IntType(64), new IntType(32), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new IntType(64), new IntType(32), ConversionKind.EXPLICIT));
        assertTrue(analyzer.isCompatible(new CharacterType(16), new IntType(32), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new CharacterType(8), new CharacterType(16), ConversionKind.IMPLICIT));
        assertFalse(analyzer.isCompatible(new BooleanType(), new IntType(), ConversionKind.EXPLICIT));
    }

    @Test
    void cppRulesAllowAllArithmeticConversions() {
        TypeConversionAnalyzer analyzer = new TypeConversionAnalyzer(new CppTypeConversionSemantics());

        assertTrue(analyzer.isCompatible(new FloatType(64), new IntType(8, true), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new BooleanType(), new FloatType(32), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new CharacterType(), new BooleanType(), ConversionKind.EXPLICIT));
    }

    @Test
    void pythonRulesFollowTheTypingNumericTower() {
        TypeConversionAnalyzer analyzer = new TypeConversionAnalyzer(new PythonTypeConversionSemantics());

        assertTrue(analyzer.isCompatible(new BooleanType(), new FloatType(), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new IntType(), new FloatType(), ConversionKind.IMPLICIT));
        assertFalse(analyzer.isCompatible(new FloatType(), new IntType(), ConversionKind.IMPLICIT));
        assertTrue(analyzer.isCompatible(new FloatType(), new IntType(), ConversionKind.EXPLICIT));
        assertFalse(analyzer.isCompatible(new CharacterType(), new IntType(), ConversionKind.EXPLICIT));
    }

    @Test
    void javaParserPreservesPrimitiveWidths() {
        JavaTranslator translator = new JavaTranslator(JAVA_CONFIG);
        var tree = translator.getMeaningTree("""
                class Main {
                    void test() {
                        byte a;
                        short b;
                        int c;
                        long d;
                        char e;
                    }
                }
                """);

        List<VariableDeclaration> declarations = tree.iterate().stream()
                .map(info -> info.node())
                .filter(VariableDeclaration.class::isInstance)
                .map(VariableDeclaration.class::cast)
                .toList();

        assertEquals(5, declarations.size());
        assertEquals(8, ((IntType) declarations.get(0).getType()).getBitsize());
        assertEquals(16, ((IntType) declarations.get(1).getType()).getBitsize());
        assertEquals(32, ((IntType) declarations.get(2).getType()).getBitsize());
        assertEquals(64, ((IntType) declarations.get(3).getType()).getBitsize());
        assertEquals(16, ((CharacterType) declarations.get(4).getType()).getBitsize());
    }

    @Test
    void automaticPassReportsAllValueTransferKinds() {
        JavaTranslator translator = new JavaTranslator(JAVA_CONFIG);
        translator.getMeaningTree("""
                class Main {
                    long widen(int value) {
                        return value;
                    }

                    void test() {
                        byte small = 1;
                        long wide = small;
                        wide = small;
                        widen(small);
                        int narrowed = (int) wide;
                        byte truncated = wide;
                    }
                }
                """);

        TypeConversionReport report = translator.getLatestTypeConversionReport().orElseThrow();
        EnumSet<ConversionSiteKind> kinds = report.checks().stream()
                .map(TypeConversionCheck::siteKind)
                .collect(() -> EnumSet.noneOf(ConversionSiteKind.class), EnumSet::add, EnumSet::addAll);

        assertEquals(EnumSet.allOf(ConversionSiteKind.class), kinds);
        assertTrue(report.checks().stream()
                .anyMatch(check -> check.siteKind() == ConversionSiteKind.ARGUMENT && isCompatible(check)));
        assertTrue(report.checks().stream()
                .anyMatch(check -> check.siteKind() == ConversionSiteKind.RETURN && isCompatible(check)),
                report.checks().toString());
        // byte small = 1 — сужение константного выражения при инициализации (JLS 5.2): Java его
        // допускает, и раньше отчёт называл этот допустимый код несовместимым.
        assertTrue(report.checks().stream()
                .anyMatch(check -> check.siteKind() == ConversionSiteKind.INITIALIZER && isCompatible(check)));
        // byte truncated = wide — то же сужение, но не от константы: вот это запрещено.
        assertTrue(report.incompatibleChecks().stream()
                .anyMatch(check -> check.siteKind() == ConversionSiteKind.INITIALIZER),
                report.checks().toString());
        assertFalse(report.isCompatible());
    }

    /**
     * {@code ObjectNewExpression} — тоже {@code Callable} и тоже резолвится, но в разбор мест
     * преобразования не входил, поэтому аргументы обычного {@code new Point(...)} не попадали в
     * отчёт вовсе.
     */
    @Test
    void constructorArgumentsOfNewExpressionAreAnalyzed() {
        JavaTranslator translator = new JavaTranslator(JAVA_CONFIG);

        translator.getMeaningTree("""
                class Point {
                    Point(int x, long y) {}
                }
                class Main {
                    void test() { Point p = new Point(1, 2); }
                }
                """);

        List<TypeConversionCheck> arguments = translator.getLatestTypeConversionReport().orElseThrow()
                .checks().stream()
                .filter(check -> check.siteKind() == ConversionSiteKind.ARGUMENT)
                .toList();

        assertEquals(2, arguments.size(), arguments.toString());
        assertTrue(arguments.stream().allMatch(TypeConversionAnalyzerTests::isCompatible), arguments.toString());
    }

    private static boolean isCompatible(TypeConversionCheck check) {
        return check.compatibility() == ConversionCompatibility.COMPATIBLE;
    }

    @Test
    void skippedOptimizationsDoNotPublishAReport() {
        JavaTranslator translator = new JavaTranslator(Map.of(
                "translationUnitMode", "simple",
                "skipErrors", false,
                "skipOptimizations", true
        ));

        translator.getMeaningTree("class Main { void test() { int value = 1; } }");

        assertTrue(translator.getLatestTypeConversionReport().isEmpty());
    }

    /**
     * Цель вызова не найдена — значит про преобразование ничего не известно. Раньше отчёт называл
     * это доказанной несовместимостью: «тип параметра неизвестен» и «язык запрещает такой
     * переход» — разные факты, и путать их для статического анализатора опасно.
     */
    @Test
    void unresolvedCallArgumentsAreReportedAsUnknown() {
        JavaTranslator translator = new JavaTranslator(JAVA_CONFIG);

        translator.getMeaningTree("class Main { void test() { unknown(1); } }");

        TypeConversionReport report = translator.getLatestTypeConversionReport().orElseThrow();
        TypeConversionCheck check = report.checks().stream()
                .filter(item -> item.siteKind() == ConversionSiteKind.ARGUMENT)
                .findFirst()
                .orElseThrow();
        assertInstanceOf(UnknownType.class, check.targetType());
        assertEquals(ConversionCompatibility.UNKNOWN, check.compatibility());
        assertTrue(report.unresolvedChecks().contains(check));
        assertFalse(report.incompatibleChecks().contains(check),
                "пробел анализа не должен попадать в найденные несовместимости");
    }
}

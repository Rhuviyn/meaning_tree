package org.vstu.meaningtree.scopes;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.utils.analysis.ScopeTableBuilder;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Таблица областей видимости, которую viewer строит по ходу отрисовки, обязана иметь ту же
 * форму, что и построенная отдельным проходом по дереву с политикой того же языка.
 * <p>
 * Раньше она была плоской: все {@code viewingIterateBody} открывали конструктор тела без
 * собственной области, и что бы viewer ни регистрировал, всё складывалось в корень. Теперь
 * границу задаёт {@link org.vstu.meaningtree.utils.scopes.ScopePolicy} на кадре узла, и
 * расхождение с {@link ScopeTableBuilder} — признак того, что один из двух путей потерял
 * область или открыл лишнюю.
 */
public class RenderScopeParityTests {
    private static final String JAVA_CODE = """
            public class Main {
                public static void main(String[] args) {
                    int x = 1;
                    if (x > 0) {
                        int y = 2;
                        System.out.println(y);
                    }
                }
            }
            """;

    private static final String CPP_CODE = """
            int main() {
                int x = 1;
                if (x > 0) {
                    int y = 2;
                }
                return 0;
            }
            """;

    private static final String PYTHON_CODE = """
            def total(n):
                s = 0
                for i in range(n):
                    if i % 2 == 0:
                        s = s + i
                return s

            print(total(5))
            """;

    @Test
    void javaRenderScopesMatchTheBuilder() {
        assertRenderParity(new JavaTranslator(), JAVA_CODE);
    }

    @Test
    void cppRenderScopesMatchTheBuilder() {
        assertRenderParity(new CppTranslator(), CPP_CODE);
    }

    @Test
    void pythonRenderScopesMatchTheBuilder() {
        assertRenderParity(new PythonTranslator(), PYTHON_CODE);
    }

    /**
     * Отрисовка чужого дерева идёт по политике языка-цели, а не языка-источника: одно и то же
     * дерево даёт разное число областей в Java и в Python.
     */
    @Test
    void crossLanguageRenderFollowsTargetLanguagePolicy() {
        MeaningTree tree = new PythonTranslator().getMeaningTree(PYTHON_CODE);

        for (LanguageTranslator target : new LanguageTranslator[]{
                new JavaTranslator(), new CppTranslator(), new PythonTranslator()}) {
            target.getCode(tree);
            assertEquals(
                    ScopeTableBuilder.build(tree, target.getScopePolicy()).allScopes().size(),
                    target.getRenderScopeTable().allScopes().size(),
                    "форма таблицы отрисовки разошлась с политикой " + target.getLanguageName());
        }
    }

    /**
     * Питоновские {@code if}/{@code for} области не открывают, а джавовские открывают — на
     * одном и том же по смыслу коде. Это и есть языковая черта, ради которой таблица отрисовки
     * спрашивает политику, а не форму узлов.
     */
    @Test
    void blockScopedAndDefinitionScopedRenderDifferently() {
        LanguageTranslator java = new JavaTranslator();
        java.getCode(java.getMeaningTree(JAVA_CODE));

        LanguageTranslator python = new PythonTranslator();
        python.getCode(python.getMeaningTree(PYTHON_CODE));

        assertTrue(
                java.getRenderScopeTable().allScopes().size()
                        > python.getRenderScopeTable().allScopes().size(),
                "блочная видимость обязана дать больше областей, чем видимость на уровне определений");
    }

    private void assertRenderParity(LanguageTranslator translator, String code) {
        MeaningTree tree = translator.getMeaningTree(code);
        translator.getCode(tree);

        ScopeTable expected = ScopeTableBuilder.build(tree, translator.getScopePolicy());
        ScopeTable rendered = translator.getRenderScopeTable();

        assertTrue(expected.allScopes().size() > 1,
                "тестовый фрагмент обязан давать вложенные области, иначе проверка бессмысленна");
        assertEquals(expected.allScopes().size(), rendered.allScopes().size(),
                "число областей при отрисовке разошлось с проходом по дереву");
    }
}

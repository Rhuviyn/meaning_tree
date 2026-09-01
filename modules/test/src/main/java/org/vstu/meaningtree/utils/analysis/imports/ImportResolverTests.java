package org.vstu.meaningtree.utils.analysis.imports;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.modules.ImportResolverMetadata;
import org.vstu.meaningtree.nodes.modules.ImportResolverMetadata.ImportKind;
import org.vstu.meaningtree.serializers.json.JsonDeserializer;
import org.vstu.meaningtree.serializers.json.JsonSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты резолвинга импортов. Проверяются обе стороны договорённости: с контекстом проекта узлы
 * получают метаданные, а без него — не получают никаких, потому что выдуманный файл хуже
 * отсутствующего.
 * <p>
 * Проекты собираются во временном каталоге: резолвер — единственный проход анализа, который
 * действительно ходит в файловую систему, и подменить её здесь нечем.
 */
class ImportResolverTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    /**
     * В C++ директивы препроцессора живут только в разборе целой единицы трансляции: в режиме
     * simple разбирается фрагмент, и {@code #include} до дерева не доходит.
     */
    private static final Map<String, Object> FULL_UNIT_CONFIG = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    @Test
    void javaImportIsResolvedExactlyThroughSourceRootComputedFromPackage(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/main/java/app/util/Helper.java", "package app.util; class Helper {}");

        List<ImportResolverMetadata> resolved = resolveAll(
                new JavaTranslator(CONFIG),
                projectRoot,
                Path.of("src/main/java/app/Main.java"),
                "package app; import app.util.Helper; class Main {}"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_RESOLVED_EXACT, resolved.getFirst().kind());
        assertEquals(
                Path.of("src/main/java/app/util/Helper.java"),
                resolved.getFirst().resolvedFile().orElseThrow()
        );
    }

    @Test
    void javaStandardLibraryImportIsNotSearchedOnDisk(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/Main.java", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new JavaTranslator(CONFIG),
                projectRoot,
                Path.of("src/Main.java"),
                "import java.util.List; class Main {}"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LIBRARY, resolved.getFirst().kind());
        assertTrue(resolved.getFirst().resolvedFile().isEmpty());
    }

    @Test
    void javaImportWithoutSourceRootFallsBackToProjectWideSearch(@TempDir Path projectRoot) throws IOException {
        // Пакет не объявлен, поэтому вычесть его из пути файла нельзя и точный корень
        // исходников неизвестен — остаётся перебор, и он обязан быть помечен как перебор
        write(projectRoot, "somewhere/deep/app/util/Helper.java", "class Helper {}");

        List<ImportResolverMetadata> resolved = resolveAll(
                new JavaTranslator(CONFIG),
                projectRoot,
                Path.of("Main.java"),
                "import app.util.Helper; class Main {}"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_RESOLVED_FALLBACK, resolved.getFirst().kind());
        assertEquals(
                Path.of("somewhere/deep/app/util/Helper.java"),
                resolved.getFirst().resolvedFile().orElseThrow()
        );
    }

    @Test
    void javaImportOfMissingFileIsMarkedUnresolved(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/app/Main.java", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new JavaTranslator(CONFIG),
                projectRoot,
                Path.of("src/app/Main.java"),
                "package app; import app.util.Missing; class Main {}"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_UNRESOLVED, resolved.getFirst().kind());
        assertTrue(resolved.getFirst().resolvedFile().isEmpty());
    }

    @Test
    void pythonImportIsResolvedFromPackageRootMarkedByInitFiles(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "pkg/__init__.py", "");
        write(projectRoot, "pkg/other.py", "");
        write(projectRoot, "pkg/sub/__init__.py", "");
        write(projectRoot, "pkg/sub/mod.py", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new PythonTranslator(CONFIG),
                projectRoot,
                Path.of("pkg/sub/mod.py"),
                "import pkg.other"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_RESOLVED_EXACT, resolved.getFirst().kind());
        assertEquals(Path.of("pkg/other.py"), resolved.getFirst().resolvedFile().orElseThrow());
    }

    @Test
    void pythonMemberImportResolvesModuleWhenMemberIsNotAFile(@TempDir Path projectRoot) throws IOException {
        // from pkg import name двусмысленно: name — либо модуль pkg/name.py, либо имя внутри
        // pkg. Файла нет, значит выиграть должен модуль pkg
        write(projectRoot, "pkg/__init__.py", "");
        write(projectRoot, "pkg/name.py", "");
        write(projectRoot, "main.py", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new PythonTranslator(CONFIG),
                projectRoot,
                Path.of("main.py"),
                "from pkg import name"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_RESOLVED_EXACT, resolved.getFirst().kind());
        assertEquals(Path.of("pkg/name.py"), resolved.getFirst().resolvedFile().orElseThrow());
    }

    @Test
    void pythonStandardLibraryImportIsNotSearchedOnDisk(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "main.py", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new PythonTranslator(CONFIG),
                projectRoot,
                Path.of("main.py"),
                "import math"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LIBRARY, resolved.getFirst().kind());
    }

    @Test
    void cppQuotedIncludeIsResolvedRelativeToCurrentFile(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/util/helper.h", "");
        write(projectRoot, "src/main.cpp", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new CppTranslator(FULL_UNIT_CONFIG),
                projectRoot,
                Path.of("src/main.cpp"),
                "#include \"util/helper.h\"\nint main() { return 0; }"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_RESOLVED_EXACT, resolved.getFirst().kind());
        assertEquals(Path.of("src/util/helper.h"), resolved.getFirst().resolvedFile().orElseThrow());
    }

    @Test
    void cppSystemIncludeIsAlwaysLibrary(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/main.cpp", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new CppTranslator(FULL_UNIT_CONFIG),
                projectRoot,
                Path.of("src/main.cpp"),
                "#include <vector>\nint main() { return 0; }"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LIBRARY, resolved.getFirst().kind());
    }

    @Test
    void cppQuotedIncludeOfMissingFileIsMarkedUnresolved(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/main.cpp", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new CppTranslator(FULL_UNIT_CONFIG),
                projectRoot,
                Path.of("src/main.cpp"),
                "#include \"missing.h\"\nint main() { return 0; }"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_UNRESOLVED, resolved.getFirst().kind());
    }

    @Test
    void withoutSourceContextImportsStayWithoutMetadata() {
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree("import pkg.other");

        List<Import> imports = importsOf(tree);
        assertFalse(imports.isEmpty(), "Разбор обязан дать хотя бы один узел импорта");
        assertTrue(imports.stream().allMatch(node -> node.getResolverMetadata().isEmpty()),
                "Без контекста проекта резолвер не запускается, и метаданных быть не должно");
    }

    @Test
    void resolverMetadataSurvivesJsonRoundTrip(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/main/java/app/util/Helper.java", "package app.util; class Helper {}");

        JavaTranslator translator = new JavaTranslator(CONFIG);
        translator.withSourceContext(projectRoot.toAbsolutePath(), Path.of("src/main/java/app/Main.java"));
        MeaningTree tree = translator.getMeaningTree("package app; import app.util.Helper; class Main {}");

        MeaningTree restored = new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree));

        ImportResolverMetadata original = importsOf(tree).getFirst().getResolverMetadata().orElseThrow();
        ImportResolverMetadata roundTripped = importsOf(restored).getFirst().getResolverMetadata().orElseThrow();
        assertEquals(original, roundTripped);
    }

    @Test
    void resolverMetadataSurvivesCloning(@TempDir Path projectRoot) throws IOException {
        // Клон, собранный конструктором, не копирует поля сам: без явного переноса вывод
        // анализа терялся на первом же клонировании, и вьюер видел импорт как нерезолвленный
        write(projectRoot, "src/main/java/app/util/Helper.java", "package app.util; class Helper {}");

        JavaTranslator translator = new JavaTranslator(CONFIG);
        translator.withSourceContext(projectRoot.toAbsolutePath(), Path.of("src/main/java/app/Main.java"));
        MeaningTree tree = translator.getMeaningTree(
                "package app; import java.util.List; import app.util.Helper; class Main {}"
        );

        for (Import original : importsOf(tree)) {
            assertEquals(
                    original.getResolverMetadata().orElseThrow(),
                    ((Import) original.clone()).getResolverMetadata().orElseThrow()
            );
        }
    }

    /**
     * Поиск модуля в Python идёт по {@code sys.path}, где каталог текущего файла стоит первым,
     * поэтому {@code random.py} рядом с исходником перехватывает {@code import random} у
     * стандартной библиотеки. Реестр библиотечных имён проверялся раньше диска, и такой импорт
     * безусловно объявлялся stdlib.
     */
    @Test
    void pythonLocalModuleShadowsStandardLibrary(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "random.py", "def random(): return 4\n");
        write(projectRoot, "main.py", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new PythonTranslator(CONFIG),
                projectRoot,
                Path.of("main.py"),
                "import random"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.LOCAL_RESOLVED_EXACT, resolved.getFirst().kind());
        assertEquals(Path.of("random.py"), resolved.getFirst().resolvedFile().orElseThrow());
    }

    /** Без затеняющего файла тот же импорт остаётся библиотечным. */
    @Test
    void pythonStandardLibraryImportStaysLibraryWithoutAShadowingFile(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "main.py", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new PythonTranslator(CONFIG),
                projectRoot,
                Path.of("main.py"),
                "import random"
        );

        assertEquals(ImportKind.LIBRARY, resolved.getFirst().kind());
    }

    /**
     * В Java затенения нет: имя пакета абсолютно, и файл {@code java/util/List.java} где-то в
     * проекте не перехватывает {@code import java.util.List}.
     */
    @Test
    void javaStandardLibraryImportIsNotShadowedByAProjectFile(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "src/java/util/List.java", "class List {}");
        write(projectRoot, "src/Main.java", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new JavaTranslator(CONFIG),
                projectRoot,
                Path.of("src/Main.java"),
                "import java.util.List; class Main {}"
        );

        assertEquals(ImportKind.LIBRARY, resolved.getFirst().kind());
    }

    /**
     * {@code import math, local_module} — это два независимых импорта в одной строке с разной
     * судьбой: первый библиотечный, второй локальный. Резолвер разбирал их одним списком
     * кандидатов и выдавал один вердикт, приписывая узлу путь к {@code local_module}, — то есть
     * объявлял {@code math} файлом проекта.
     */
    @Test
    void multiImportWithDifferentOutcomesIsMarkedMixed(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "local_module.py", "");
        write(projectRoot, "main.py", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new PythonTranslator(CONFIG),
                projectRoot,
                Path.of("main.py"),
                "import math, local_module"
        );

        assertEquals(1, resolved.size());
        assertEquals(ImportKind.MIXED, resolved.getFirst().kind());
        assertTrue(resolved.getFirst().resolvedFile().isEmpty(),
                "у смешанного импорта не может быть одного файла");
    }

    /** Когда все модули строки библиотечные, вердикт по-прежнему один и определённый. */
    @Test
    void multiImportOfLibraryModulesStaysLibrary(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "main.py", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new PythonTranslator(CONFIG),
                projectRoot,
                Path.of("main.py"),
                "import math, os"
        );

        assertEquals(ImportKind.LIBRARY, resolved.getFirst().kind());
    }

    /**
     * Путь из {@code #include} может содержать {@code ..}. Резолвинг обязан оставаться внутри
     * заявленного корня проекта: найденный за его пределами файл — не результат, а выход за
     * границы, которые задал вызывающий.
     */
    @Test
    void includeEscapingProjectRootIsNotResolved(@TempDir Path outer) throws IOException {
        write(outer, "secret.h", "");
        Path projectRoot = outer.resolve("project");
        write(projectRoot, "src/main.cpp", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new CppTranslator(FULL_UNIT_CONFIG),
                projectRoot,
                Path.of("src/main.cpp"),
                "#include \"../../secret.h\"\nint main() { return 0; }"
        );

        assertEquals(ImportKind.LOCAL_UNRESOLVED, resolved.getFirst().kind());
        assertTrue(resolved.getFirst().resolvedFile().isEmpty());
    }

    /**
     * Перебор по проекту нашёл несколько подходящих файлов — это неоднозначность, а не ответ.
     * Прежний поиск отдавал первый попавшийся, то есть выдавал за результат порядок обхода.
     */
    @Test
    void severalMatchesInFallbackSearchAreAmbiguous(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "one/app/util/Helper.java", "class Helper {}");
        write(projectRoot, "two/app/util/Helper.java", "class Helper {}");
        write(projectRoot, "Main.java", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new JavaTranslator(CONFIG),
                projectRoot,
                Path.of("Main.java"),
                "import app.util.Helper; class Main {}"
        );

        assertEquals(ImportKind.LOCAL_AMBIGUOUS, resolved.getFirst().kind());
        assertTrue(resolved.getFirst().resolvedFile().isEmpty());
    }

    /**
     * Игнорируемые каталоги не обходятся вовсе: совпадение в {@code target} или
     * {@code node_modules} — это артефакт сборки или чужая зависимость, а не исходник проекта.
     */
    @Test
    void ignoredDirectoriesAreNotSearched(@TempDir Path projectRoot) throws IOException {
        write(projectRoot, "target/app/util/Helper.java", "class Helper {}");
        write(projectRoot, "node_modules/app/util/Helper.java", "class Helper {}");
        write(projectRoot, "Main.java", "");

        List<ImportResolverMetadata> resolved = resolveAll(
                new JavaTranslator(CONFIG),
                projectRoot,
                Path.of("Main.java"),
                "import app.util.Helper; class Main {}"
        );

        assertEquals(ImportKind.LOCAL_UNRESOLVED, resolved.getFirst().kind(),
                "найденное в target/node_modules не должно считаться исходником проекта");
    }

    private List<ImportResolverMetadata> resolveAll(LanguageTranslator translator,
                                                    Path projectRoot,
                                                    Path currentFileRelPath,
                                                    String code) {
        translator.withSourceContext(projectRoot.toAbsolutePath(), currentFileRelPath);
        MeaningTree tree = translator.getMeaningTree(code);
        return importsOf(tree).stream()
                .map(node -> node.getResolverMetadata().orElseThrow(
                        () -> new AssertionError("Импорт остался без метаданных: " + node))
                )
                .toList();
    }

    private List<Import> importsOf(MeaningTree tree) {
        List<Import> imports = new ArrayList<>();
        for (Node node : StreamSupport.stream(tree.spliterator(), false).map(info -> info.node()).toList()) {
            if (node instanceof Import importNode) {
                imports.add(importNode);
            }
        }
        return imports;
    }

    private void write(Path projectRoot, String relativePath, String content) throws IOException {
        Path file = projectRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

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
                projectRoot.resolve("src/main/java/app/util/Helper.java"),
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
                projectRoot.resolve("somewhere/deep/app/util/Helper.java"),
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
        assertEquals(projectRoot.resolve("pkg/other.py"), resolved.getFirst().resolvedFile().orElseThrow());
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
        assertEquals(projectRoot.resolve("pkg/name.py"), resolved.getFirst().resolvedFile().orElseThrow());
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
        assertEquals(projectRoot.resolve("src/util/helper.h"), resolved.getFirst().resolvedFile().orElseThrow());
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

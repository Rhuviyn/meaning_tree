package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.utils.modules.ImportPathConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Связывает узлы импорта с реальными файлами проекта.
 * <p>
 * Резолвинг — единственный проход анализа, которому нужна файловая система, поэтому он и
 * единственный, который может не выполниться: без контекста проекта
 * ({@code LanguageTranslator.withSourceContext}) вызывать его нечем, и узлы просто остаются
 * без метаданных. Это не ошибка, а штатное состояние — все существующие сценарии перевода
 * одиночного файла работают именно так.
 * <p>
 * Результат best-effort по построению: одно и то же имя может лежать в нескольких местах
 * проекта, поэтому вид найденного отражён в
 * {@link ImportResolverMetadata.ImportKind} — точное совпадение и совпадение перебором
 * различимы потребителем.
 */
public abstract class ImportResolver {
    /**
     * Насколько глубоко fallback-поиск спускается по дереву проекта. Ограничение нужно, чтобы
     * один нерезолвящийся импорт не обошёл весь диск: на такой глубине лежит любой разумный
     * исходник, а всё, что глубже, скорее артефакты сборки.
     */
    private static final int MAX_SEARCH_DEPTH = 12;

    /**
     * Каталоги, в которые fallback-поиск не заходит: там лежит не исходный код проекта, а
     * результат сборки и чужие зависимости — найденное там совпадение было бы ложным.
     */
    private static final List<String> IGNORED_DIRECTORIES = List.of(
            ".git", ".svn", ".hg", ".idea", ".vscode",
            "target", "build", "out", "bin", "dist", "cmake-build-debug", "cmake-build-release",
            "node_modules", "venv", ".venv", "__pycache__", ".mypy_cache", ".pytest_cache"
    );

    public final void resolve(MeaningTree tree, Path projectRoot, Path currentFileRelPath) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof Import importNode) {
                importNode.setResolverMetadata(resolveImport(importNode, tree, projectRoot, currentFileRelPath));
            }
        }
    }

    /**
     * Общий порядок резолвинга: библиотека → точный корень поиска → перебор по проекту.
     * <p>
     * Порядок не произволен: он идёт от самого достоверного вывода к самому слабому, и первый
     * сработавший выигрывает. Библиотека проверяется до обращения к диску — искать `math` или
     * `java.util.List` среди файлов проекта бессмысленно.
     *
     * @return метаданные импорта; {@code null}, если о нём нельзя сказать даже того, что он
     *         локальный (тогда узел остаётся без метаданных)
     */
    protected ImportResolverMetadata resolveImport(Import importNode,
                                                   MeaningTree tree,
                                                   Path projectRoot,
                                                   Path currentFileRelPath) {
        List<String> candidates = candidateDottedNames(importNode);
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.stream().allMatch(this::isLibraryModule)) {
            return ImportResolverMetadata.library();
        }

        Optional<Path> exactRoot = exactSearchRoot(tree, projectRoot, currentFileRelPath);
        if (exactRoot.isPresent()) {
            for (String candidate : candidates) {
                Optional<Path> resolved = findExact(exactRoot.get(), candidate);
                if (resolved.isPresent()) {
                    return ImportResolverMetadata.resolved(
                            ImportResolverMetadata.ImportKind.LOCAL_RESOLVED_EXACT, resolved.get());
                }
            }
        }
        for (String candidate : candidates) {
            Optional<Path> resolved = findAnywhere(projectRoot, candidate);
            if (resolved.isPresent()) {
                return ImportResolverMetadata.resolved(
                        ImportResolverMetadata.ImportKind.LOCAL_RESOLVED_FALLBACK, resolved.get());
            }
        }
        return ImportResolverMetadata.unresolved();
    }

    /**
     * Принадлежит ли модуль стандартной библиотеке этого языка.
     * <p>
     * Список библиотечных имён — знание о конкретном языке, поэтому он живёт в его модуле, а
     * не здесь: `math` библиотечный в Python и совершенно обычное имя в Java.
     */
    protected abstract boolean isLibraryModule(String dottedName);

    /**
     * Каталог, относительно которого импорт отсчитывается точно: source root в Java, корень
     * пакета в Python.
     *
     * @return пусто, если вычислить его нечем — тогда остаётся только перебор по проекту
     */
    protected abstract Optional<Path> exactSearchRoot(MeaningTree tree,
                                                      Path projectRoot,
                                                      Path currentFileRelPath);

    /**
     * Расширения файлов языка в порядке предпочтения.
     */
    protected abstract List<String> sourceExtensions();

    /**
     * Точечные имена, которые может именовать этот импорт.
     * <p>
     * Их несколько, потому что {@code from a.b import c} двусмысленно: {@code c} — это и
     * возможный модуль {@code a/b/c}, и возможное имя внутри модуля {@code a/b}. Кандидаты
     * идут от более длинного к более короткому, и первый найденный на диске выигрывает.
     */
    protected List<String> candidateDottedNames(Import importNode) {
        List<String> candidates = new ArrayList<>();
        switch (importNode) {
            case ImportMembersFromModule members -> {
                String module = ImportPathConverter.dottedName(members.getModuleName());
                for (Identifier member : members.getMembers()) {
                    candidates.add(module + "." + ImportPathConverter.dottedName(member));
                }
                candidates.add(module);
            }
            case ImportModules modules -> modules.getModulesNames()
                    .forEach(module -> candidates.add(ImportPathConverter.dottedName(module)));
            case ImportModule module -> candidates.add(ImportPathConverter.dottedName(module.getModuleName()));
            default -> { }
        }
        return candidates;
    }

    /**
     * Ищет файл по точному пути относительно {@code baseDirectory}, перебирая расширения языка.
     */
    protected Optional<Path> findExact(Path baseDirectory, String dottedName) {
        String relativePath = ImportPathConverter.dottedNameToPath(dottedName);
        for (String extension : sourceExtensions()) {
            Path candidate = baseDirectory.resolve(relativePath + extension).normalize();
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Ищет файл, чей путь заканчивается на {@code dottedName}, где угодно внутри проекта.
     * <p>
     * Сравнение идёт по хвосту пути, а не по одному имени файла: {@code a.b.c} не должен
     * совпасть с любым попавшимся {@code c} — совпасть должен именно {@code .../a/b/c}.
     */
    protected Optional<Path> findAnywhere(Path projectRoot, String dottedName) {
        return findAnywhereByPath(projectRoot, ImportPathConverter.dottedNameToPath(dottedName));
    }

    /**
     * То же, но по готовому относительному пути: в C++ импорт и так именует файл, и превращать
     * его в точечное имя, чтобы тут же развернуть обратно, значило бы испортить расширение.
     */
    protected Optional<Path> findAnywhereByPath(Path projectRoot, String relativePath) {
        String suffix = relativePath.replace('\\', '/');
        List<String> wantedFiles = sourceExtensions().stream().map(extension -> suffix + extension).toList();
        try (Stream<Path> walk = Files.walk(projectRoot, MAX_SEARCH_DEPTH)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> !isInIgnoredDirectory(projectRoot, path))
                    .filter(path -> {
                        String normalized = projectRoot.relativize(path).toString().replace('\\', '/');
                        return wantedFiles.stream().anyMatch(wanted ->
                                normalized.equals(wanted) || normalized.endsWith("/" + wanted));
                    })
                    .findFirst();
        } catch (IOException | SecurityException e) {
            // Проект может быть недоступен на чтение целиком или частично: это повод не
            // резолвить импорт, но не повод валить весь разбор файла
            return Optional.empty();
        }
    }

    private boolean isInIgnoredDirectory(Path projectRoot, Path file) {
        for (Path segment : projectRoot.relativize(file).getParent() == null
                ? projectRoot.relativize(file)
                : projectRoot.relativize(file).getParent()) {
            if (IGNORED_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }
}

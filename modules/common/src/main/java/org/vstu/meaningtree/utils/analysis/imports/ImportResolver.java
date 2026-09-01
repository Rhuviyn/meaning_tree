package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.modules.ImportResolverMetadata.ImportKind;
import org.vstu.meaningtree.utils.modules.ImportPathConverter;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
    /** Индекс файлов проекта на время одного {@link #resolve}; вне прогона — {@code null}. */
    private ProjectIndex projectIndex;

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
        Path root = projectRoot.toAbsolutePath().normalize();
        // Индекс строится один раз на прогон, а не на каждый импорт: прежний обход стоил
        // imports x candidates x файлы проекта.
        projectIndex = new ProjectIndex(root);
        try {
            for (NodeInfo info : tree) {
                if (info.node() instanceof Import importNode) {
                    importNode.setResolverMetadata(resolveImport(importNode, tree, root, currentFileRelPath));
                }
            }
        } finally {
            projectIndex = null;
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
        List<List<String>> targets = importTargets(importNode);
        if (targets.isEmpty()) {
            return null;
        }
        Optional<Path> exactRoot = exactSearchRoot(tree, projectRoot, currentFileRelPath);

        List<ImportResolverMetadata> perTarget = new ArrayList<>();
        for (List<String> candidates : targets) {
            ImportResolverMetadata resolved = resolveTarget(candidates, projectRoot, exactRoot);
            if (resolved == null) {
                return null;
            }
            perTarget.add(resolved);
        }
        return mergeTargets(perTarget);
    }

    /**
     * Сводит вердикты по отдельным целям к одному, который несёт узел.
     * <p>
     * Одинаковые вердикты сливаются; расходящиеся дают {@link ImportKind#MIXED}. Выбрать любой
     * из них нельзя: {@code import math, local_module} — это библиотечный импорт и локальный
     * одновременно, и приписав узлу путь к {@code local_module}, резолвер объявил бы
     * {@code math} локальным файлом проекта.
     */
    private ImportResolverMetadata mergeTargets(List<ImportResolverMetadata> perTarget) {
        ImportResolverMetadata first = perTarget.getFirst();
        return perTarget.stream().allMatch(first::equals) ? first : ImportResolverMetadata.mixed();
    }

    /** Вердикт для одной цели импорта; {@code candidates} — её равноправные написания. */
    private ImportResolverMetadata resolveTarget(List<String> candidates,
                                                 Path projectRoot,
                                                 Optional<Path> exactRoot) {
        if (candidates.isEmpty()) {
            return null;
        }
        // Точный поиск раньше реестра там, где язык допускает затенение: в Python файл
        // random.py рядом с текущим делает `import random` обращением к нему, а не к stdlib.
        // Только точный поиск, не перебор по проекту: одноимённый файл где-то в другом каталоге
        // стандартную библиотеку не затеняет.
        if (allowsLocalShadowing() && exactRoot.isPresent()) {
            Optional<Path> shadowing = findFirstExact(exactRoot.get(), candidates);
            if (shadowing.isPresent()) {
                return insideProject(projectRoot, shadowing.get(), ImportKind.LOCAL_RESOLVED_EXACT);
            }
        }
        if (candidates.stream().allMatch(this::isLibraryModule)) {
            return ImportResolverMetadata.library();
        }
        if (exactRoot.isPresent()) {
            Optional<Path> resolved = findFirstExact(exactRoot.get(), candidates);
            if (resolved.isPresent()) {
                return insideProject(projectRoot, resolved.get(), ImportKind.LOCAL_RESOLVED_EXACT);
            }
        }
        for (String candidate : candidates) {
            List<Path> matches = findAllByPath(projectRoot, ImportPathConverter.dottedNameToPath(candidate));
            if (matches.size() == 1) {
                return insideProject(projectRoot, matches.getFirst(), ImportKind.LOCAL_RESOLVED_FALLBACK);
            }
            if (matches.size() > 1) {
                // Несколько файлов с подходящим хвостом пути — это не ответ. Выбрать первый
                // значило бы выдать порядок обхода каталогов за результат резолвинга.
                return ImportResolverMetadata.ambiguous();
            }
        }
        return ImportResolverMetadata.unresolved();
    }

    /**
     * Цели импорта, каждая со своим списком равноправных написаний.
     * <p>
     * Целей больше одной у {@code import a, b}: это два независимых импорта в одной строке, и
     * разрешать их надо порознь. У остальных форм цель одна, а список написаний описывает
     * неоднозначность самой этой цели (см. {@link #candidateDottedNames}).
     */
    protected List<List<String>> importTargets(Import importNode) {
        if (importNode instanceof ImportModules modules) {
            return modules.getModulesNames().stream()
                    .map(module -> List.of(ImportPathConverter.dottedName(module)))
                    .collect(Collectors.toList());
        }
        List<String> candidates = candidateDottedNames(importNode);
        return candidates.isEmpty() ? List.of() : List.of(candidates);
    }

    /**
     * Принадлежит ли модуль стандартной библиотеке этого языка.
     * <p>
     * Список библиотечных имён — знание о конкретном языке, поэтому он живёт в его модуле, а
     * не здесь: `math` библиотечный в Python и совершенно обычное имя в Java.
     */
    protected abstract boolean isLibraryModule(String dottedName);

    /**
     * Может ли файл проекта затенить одноимённый модуль стандартной библиотеки.
     * <p>
     * В Python — да: поиск идёт по {@code sys.path}, где каталог текущего файла стоит первым,
     * поэтому {@code random.py} рядом с исходником перехватывает {@code import random}. В Java
     * имя пакета абсолютно, а в C++ форма записи ({@code <...>} против {@code "..."}) разделяет
     * библиотеку и проект явно — там затенения нет, и обращение к диску ради него было бы
     * лишним.
     */
    protected boolean allowsLocalShadowing() {
        return false;
    }

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
     * Метаданные для найденного файла — с путём относительно корня проекта.
     * <p>
     * Относительный, а не абсолютный: абсолютный путь попадает в сериализованное дерево, где он
     * бесполезен на другой машине и заодно раскрывает её структуру. Файл вне корня проекта
     * результатом не считается вовсе — {@code ..} в пути импорта или подменённый корень не
     * должны выводить резолвинг за пределы заявленного проекта.
     */
    protected ImportResolverMetadata insideProject(Path projectRoot, Path file, ImportKind kind) {
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(projectRoot)) {
            return ImportResolverMetadata.unresolved();
        }
        return ImportResolverMetadata.resolved(kind, projectRoot.relativize(normalized));
    }

    /** Первый из кандидатов, найденный точным поиском от указанного корня. */
    private Optional<Path> findFirstExact(Path exactRoot, List<String> candidates) {
        for (String candidate : candidates) {
            Optional<Path> resolved = findExact(exactRoot, candidate);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }

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
     * <p>
     * Возвращает совпадение, только если оно единственное: несколько файлов с подходящим хвостом
     * пути — это не найденный ответ, а неоднозначность, и выбирать из них первый попавшийся
     * значило бы выдавать за факт порядок обхода каталогов.
     */
    protected Optional<Path> findAnywhereByPath(Path projectRoot, String relativePath) {
        List<Path> matches = findAllByPath(projectRoot, relativePath);
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    /** Все файлы проекта, чей путь оканчивается на {@code relativePath} с расширением языка. */
    protected List<Path> findAllByPath(Path projectRoot, String relativePath) {
        String suffix = relativePath.replace('\\', '/');
        List<String> wantedFiles = sourceExtensions().stream().map(extension -> suffix + extension).toList();
        return indexOf(projectRoot).files().stream()
                .filter(path -> {
                    String normalized = projectRoot.relativize(path).toString().replace('\\', '/');
                    return wantedFiles.stream().anyMatch(wanted ->
                            normalized.equals(wanted) || normalized.endsWith("/" + wanted));
                })
                .toList();
    }

    private ProjectIndex indexOf(Path projectRoot) {
        ProjectIndex index = projectIndex;
        // Индекса нет, если поиск вызван вне resolve() — тогда он строится на один вызов.
        return index != null && index.root().equals(projectRoot) ? index : new ProjectIndex(projectRoot);
    }

    /**
     * Список файлов проекта, построенный одним обходом.
     * <p>
     * Игнорируемые каталоги отсекаются в {@code preVisitDirectory} через {@code SKIP_SUBTREE},
     * то есть в них не заходят вовсе. Прежний фильтр применялся к уже перечисленным файлам,
     * поэтому {@code target}, {@code node_modules} и {@code .git} обходились целиком — вопреки
     * комментарию, обещавшему обратное.
     */
    private record ProjectIndex(Path root, List<Path> files) {
        ProjectIndex(Path root) {
            this(root, scan(root));
        }

        private static List<Path> scan(Path root) {
            List<Path> found = new ArrayList<>();
            try {
                Files.walkFileTree(root, Set.of(), MAX_SEARCH_DEPTH, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                        return !directory.equals(root)
                                && IGNORED_DIRECTORIES.contains(directory.getFileName().toString())
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (attributes.isRegularFile()) {
                            found.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException failure) {
                        // Нечитаемый файл или каталог — повод пропустить его, но не повод
                        // прекращать обход проекта.
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException | SecurityException e) {
                // Проект может быть недоступен на чтение целиком: это повод не резолвить
                // импорты, но не повод валить разбор файла.
                return List.of();
            }
            return found;
        }
    }

}

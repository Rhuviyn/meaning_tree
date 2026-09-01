package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.modules.ImportResolverMetadata;
import org.vstu.meaningtree.nodes.modules.Include;
import org.vstu.meaningtree.utils.modules.ImportPathConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Резолвер подключений C/C++.
 * <p>
 * Здесь общий порядок базового класса не годится: {@code #include} именует не модуль, а файл, и
 * превращать путь в точечное имя, чтобы тут же развернуть обратно, значило бы испортить
 * расширение. Поэтому подключение резолвится своей веткой, а базовый порядок остаётся для
 * узлов, пришедших из других языков.
 * <p>
 * Форма подключения сама по себе классификация: {@code <...>} — это путь поиска компилятора, то
 * есть заведомо не файл проекта. Остаётся {@code "..."}, который стандарт велит искать сначала
 * рядом с текущим файлом, — именно в этом порядке и идёт поиск.
 */
public class CppImportResolver extends ImportResolver {
    /** Подключение уже содержит расширение, поэтому дописывать к пути нечего. */
    private static final List<String> EXTENSIONS = List.of("");

    @Override
    protected List<String> sourceExtensions() {
        return EXTENSIONS;
    }

    /**
     * Разбор C++ даёт только {@link Include}, у которого библиотечность видна по форме записи,
     * поэтому по имени модуля здесь ничего не решается.
     */
    @Override
    protected boolean isLibraryModule(String dottedName) {
        return false;
    }

    @Override
    protected Optional<Path> exactSearchRoot(MeaningTree tree, Path projectRoot, Path currentFileRelPath) {
        return Optional.empty();
    }

    @Override
    protected ImportResolverMetadata resolveImport(Import importNode,
                                                   MeaningTree tree,
                                                   Path projectRoot,
                                                   Path currentFileRelPath) {
        if (!(importNode instanceof Include include)) {
            return super.resolveImport(importNode, tree, projectRoot, currentFileRelPath);
        }

        if (include.getIncludeType() == Include.IncludeType.POINTY_BRACKETS_FORM) {
            return ImportResolverMetadata.library();
        }

        String fileName = include.getFileName().getUnescapedValue();
        Path currentDirectory = currentFileRelPath.getParent() == null
                ? projectRoot
                : projectRoot.resolve(currentFileRelPath.getParent()).normalize();

        // insideProject, а не прямая сборка метаданных: путь из #include может содержать "..",
        // и без проверки резолвинг уходил бы за пределы заявленного корня проекта.
        Path nearCurrentFile = currentDirectory.resolve(fileName).normalize();
        if (Files.isRegularFile(nearCurrentFile)) {
            return insideProject(projectRoot, nearCurrentFile,
                    ImportResolverMetadata.ImportKind.LOCAL_RESOLVED_EXACT);
        }
        Path fromProjectRoot = projectRoot.resolve(fileName).normalize();
        if (Files.isRegularFile(fromProjectRoot)) {
            return insideProject(projectRoot, fromProjectRoot,
                    ImportResolverMetadata.ImportKind.LOCAL_RESOLVED_EXACT);
        }
        return findAnywhereByPath(projectRoot, fileName)
                .map(path -> insideProject(projectRoot, path,
                        ImportResolverMetadata.ImportKind.LOCAL_RESOLVED_FALLBACK))
                .orElseGet(ImportResolverMetadata::unresolved);
    }

    /**
     * Импорт из другого языка именует модуль, а в C++ ему соответствует заголовочный файл:
     * точечное имя превращается в путь по той же наивной схеме, что и при выводе
     * (см. {@code CppViewer}), потому что ничего точнее о нём здесь не известно.
     */
    @Override
    protected Optional<Path> findAnywhere(Path projectRoot, String dottedName) {
        return findAnywhereByPath(projectRoot, ImportPathConverter.dottedNameToHeaderPath(dottedName));
    }
}

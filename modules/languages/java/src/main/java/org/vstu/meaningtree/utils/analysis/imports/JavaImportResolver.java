package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.modules.PackageDeclaration;
import org.vstu.meaningtree.utils.modules.ImportPathConverter;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Резолвер импортов Java.
 * <p>
 * Импорт в Java — путь от корня исходников, а не от корня проекта, и сам корень нигде не
 * записан. Зато его можно вычислить: объявление пакета говорит, какому суффиксу обязан
 * соответствовать путь текущего файла, и всё, что осталось слева, — это и есть корень.
 * <p>
 * Без объявления пакета (default package либо файл вне дерева пакетов) вычесть нечего, и
 * остаётся перебор по проекту — базовый класс пометит результат как fallback, потому что
 * одинаковый путь может встретиться в нескольких source root'ах.
 */
public class JavaImportResolver extends ImportResolver {
    private static final List<String> EXTENSIONS = List.of(".java");

    @Override
    protected List<String> sourceExtensions() {
        return EXTENSIONS;
    }

    @Override
    protected boolean isLibraryModule(String dottedName) {
        return JavaLibraryImportRegistry.isLibraryModule(dottedName);
    }

    /**
     * Корень исходников = путь текущего файла минус каталог его пакета и имя самого файла.
     *
     * @return пусто, если пакета в файле нет или путь файла ему не соответствует — тогда
     *         вычесть нечего и догадка была бы вымыслом
     */
    @Override
    protected Optional<Path> exactSearchRoot(MeaningTree tree, Path projectRoot, Path currentFileRelPath) {
        Optional<String> packageName = findPackageName(tree);
        if (packageName.isEmpty()) {
            return Optional.empty();
        }
        Path packageDirectory = currentFileRelPath.getParent();
        if (packageDirectory == null) {
            return Optional.empty();
        }
        Path expectedSuffix = Path.of(ImportPathConverter.dottedNameToPath(packageName.get()));
        if (!packageDirectory.endsWith(expectedSuffix)) {
            return Optional.empty();
        }
        int rootSegments = packageDirectory.getNameCount() - expectedSuffix.getNameCount();
        Path relativeRoot = rootSegments == 0 ? Path.of("") : packageDirectory.subpath(0, rootSegments);
        return Optional.of(projectRoot.resolve(relativeRoot).normalize());
    }

    private Optional<String> findPackageName(MeaningTree tree) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof PackageDeclaration declaration) {
                return Optional.of(ImportPathConverter.dottedName(declaration.getPackageName()));
            }
        }
        return Optional.empty();
    }
}

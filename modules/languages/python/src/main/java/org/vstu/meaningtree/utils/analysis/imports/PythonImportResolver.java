package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.MeaningTree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Резолвер импортов Python.
 * <p>
 * Абсолютный импорт отсчитывается не от корня проекта, а от корня пакета, которому принадлежит
 * файл. Границу пакета отмечает {@code __init__.py}: поднимаясь от каталога текущего файла
 * вверх, первый каталог без него и есть точка отсчёта. Она вычислена по устройству проекта, а
 * не угадана, поэтому найденное там совпадение точное.
 * <p>
 * Namespace-пакеты (PEP 420, без {@code __init__.py}) в эту схему не укладываются: у них
 * граница не отмечена ничем, и корнем окажется каталог самого файла. Для таких проектов
 * остаётся перебор по проекту — им базовый класс и продолжает.
 */
public class PythonImportResolver extends ImportResolver {
    private static final List<String> EXTENSIONS = List.of(".py", ".pyi");

    /** Ограничение подъёма вверх: в норме пакеты много мельче, а цикл должен быть конечным. */
    private static final int MAX_PACKAGE_DEPTH = 64;

    @Override
    protected List<String> sourceExtensions() {
        return EXTENSIONS;
    }

    @Override
    protected boolean isLibraryModule(String dottedName) {
        return PythonLibraryImportRegistry.isLibraryModule(dottedName);
    }

    /**
     * Импорт в Python ищется по {@code sys.path}, где каталог текущего файла идёт первым:
     * {@code random.py} рядом с исходником перехватывает {@code import random} у стандартной
     * библиотеки.
     */
    @Override
    protected boolean allowsLocalShadowing() {
        return true;
    }

    @Override
    protected Optional<Path> exactSearchRoot(MeaningTree tree, Path projectRoot, Path currentFileRelPath) {
        Path directory = currentFileRelPath.getParent() == null
                ? projectRoot
                : projectRoot.resolve(currentFileRelPath.getParent()).normalize();
        for (int depth = 0; depth < MAX_PACKAGE_DEPTH; depth++) {
            if (!Files.isRegularFile(directory.resolve("__init__.py"))) {
                return Optional.of(directory);
            }
            Path parent = directory.getParent();
            // Выше корня проекта не поднимаемся: там уже не наш код
            if (parent == null || !parent.startsWith(projectRoot)) {
                return Optional.of(projectRoot);
            }
            directory = parent;
        }
        return Optional.of(directory);
    }
}

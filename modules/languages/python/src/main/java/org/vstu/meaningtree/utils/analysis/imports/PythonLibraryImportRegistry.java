package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.utils.modules.ImportPathConverter;

import java.util.Set;

/**
 * Что в Python относится к стандартной библиотеке.
 * <p>
 * Реестр живёт в модуле языка: `math` библиотечный здесь и совершенно обычное имя в Java, так
 * что общего списка на все языки быть не может.
 */
public final class PythonLibraryImportRegistry {
    private PythonLibraryImportRegistry() {}

    /**
     * Модули верхнего уровня из стандартной библиотеки. Список неполный намеренно: он
     * покрывает то, что реально встречается в учебном коде, а промах даёт лишь попытку резолва
     * по проекту, которая просто ничего не найдёт.
     */
    private static final Set<String> LIBRARY_ROOTS = Set.of(
            "abc", "argparse", "array", "ast", "asyncio", "base64", "bisect", "builtins",
            "collections", "copy", "csv", "dataclasses", "datetime", "decimal", "enum",
            "fractions", "functools", "glob", "hashlib", "heapq", "io", "itertools", "json",
            "logging", "math", "os", "pathlib", "pickle", "queue", "random", "re", "shutil",
            "socket", "sqlite3", "statistics", "string", "struct", "subprocess", "sys",
            "tempfile", "threading", "time", "timeit", "traceback", "types", "typing",
            "unittest", "urllib", "uuid", "warnings", "weakref", "zipfile"
    );

    public static boolean isLibraryModule(String dottedName) {
        return LIBRARY_ROOTS.contains(ImportPathConverter.topLevelSegment(dottedName));
    }
}

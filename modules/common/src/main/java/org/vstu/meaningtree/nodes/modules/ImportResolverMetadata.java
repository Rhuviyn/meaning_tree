package org.vstu.meaningtree.nodes.modules;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Результат резолвинга импорта: что это за импорт и на какой файл он указывает.
 * <p>
 * Метаданные необязательны — их проставляет {@code ImportResolver}, и только когда у
 * транслятора задан контекст проекта. Без него узел остаётся без метаданных, а не с
 * выдуманными: догадка о файле хуже, чем её отсутствие.
 */
public record ImportResolverMetadata(ImportKind kind, Optional<Path> resolvedFile) {
    public enum ImportKind {
        /** Файл найден по точно вычисленному корню исходников (Java: package + путь файла). */
        LOCAL_RESOLVED_EXACT,
        /** Файл найден перебором по всему проекту — совпадение возможно не единственное. */
        LOCAL_RESOLVED_FALLBACK,
        /** Похоже на локальный импорт, но файла в проекте нет. */
        LOCAL_UNRESOLVED,
        /** Импорт из библиотеки: файла в проекте нет и быть не должно. */
        LIBRARY,
        /**
         * Одна строка импорта называет несколько модулей, и разрешились они по-разному:
         * {@code import math, local_module} — библиотечный и локальный одновременно. Одного
         * вердикта у такого узла нет, и приписывать ему любой из двух значило бы соврать про
         * второй.
         */
        MIXED
    }

    public ImportResolverMetadata {
        java.util.Objects.requireNonNull(kind, "kind must not be null");
        java.util.Objects.requireNonNull(resolvedFile, "resolvedFile must not be null");
    }

    public static ImportResolverMetadata library() {
        return new ImportResolverMetadata(ImportKind.LIBRARY, Optional.empty());
    }

    public static ImportResolverMetadata mixed() {
        return new ImportResolverMetadata(ImportKind.MIXED, Optional.empty());
    }

    public static ImportResolverMetadata unresolved() {
        return new ImportResolverMetadata(ImportKind.LOCAL_UNRESOLVED, Optional.empty());
    }

    public static ImportResolverMetadata resolved(ImportKind kind, Path file) {
        return new ImportResolverMetadata(kind, Optional.of(file));
    }

    public boolean isLibrary() {
        return kind == ImportKind.LIBRARY;
    }
}

package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.utils.modules.ImportPathConverter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Что в Java относится к стандартной библиотеке и по какому полному имени.
 * <p>
 * Реестр живёт в модуле языка, а не в общем: список библиотечных корней и полные имена классов
 * — знание про Java и ни про что больше. В общем модуле он был бы утечкой языковой специфики
 * туда, где её быть не должно.
 */
public final class JavaLibraryImportRegistry {
    private JavaLibraryImportRegistry() {}

    /**
     * Префиксы модулей, которые всегда библиотечные: искать их среди файлов проекта
     * бессмысленно.
     */
    private static final Set<String> LIBRARY_ROOTS = Set.of("java", "javax", "jdk", "sun");

    /**
     * Полные имена классов, которые вьюер печатает сам, когда переводит коллекцию.
     * <p>
     * Нужны обоим режимам {@code preferQualifiedReferences}: при {@code true} отсюда берётся
     * имя для вывода, при {@code false} — модуль и член для {@code import}. Одна таблица на
     * оба режима — иначе имя и импорт могли бы разойтись.
     */
    private static final Map<String, String> QUALIFIED_COLLECTION_NAMES = Map.of(
            "ArrayList", "java.util.ArrayList",
            "List", "java.util.List",
            "HashSet", "java.util.HashSet",
            "Set", "java.util.Set",
            "TreeMap", "java.util.TreeMap",
            "HashMap", "java.util.HashMap",
            "Map", "java.util.Map",
            "Scanner", "java.util.Scanner"
    );

    public static boolean isLibraryModule(String dottedName) {
        return LIBRARY_ROOTS.contains(ImportPathConverter.topLevelSegment(dottedName));
    }

    /**
     * Полное имя класса стандартной библиотеки по его простому имени.
     *
     * @return пусто, если имя реестру неизвестно — тогда сокращать его не от чего
     */
    public static Optional<String> qualifiedName(String simpleName) {
        return Optional.ofNullable(QUALIFIED_COLLECTION_NAMES.get(simpleName));
    }
}

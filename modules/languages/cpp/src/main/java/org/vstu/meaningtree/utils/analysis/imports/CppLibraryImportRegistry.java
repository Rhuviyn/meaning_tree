package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.StringType;
import org.vstu.meaningtree.nodes.types.containers.*;

import java.util.Map;
import java.util.Optional;

/**
 * Заголовки стандартной библиотеки C++, без которых сгенерированный код не соберётся.
 * <p>
 * Реестр живёт в модуле языка: заголовки — знание про C++, и в общем модуле им делать нечего.
 * Заголовки вообще нужны только здесь — Java печатает коллекции полными именами, а в Python
 * они встроенные, так что соответствие принципиально одностороннее.
 * <p>
 * Реестр строк, а не узлов: заводить семантический узел под каждый тип и каждую функцию — цена,
 * несопоставимая с задачей, потому что все эти конструкции уже типизированы или уже узнаются по
 * имени в той самой точке, где вьюер собирается их напечатать.
 */
public final class CppLibraryImportRegistry {
    private CppLibraryImportRegistry() {}

    /**
     * Заголовки под функции, которые вьюер печатает по имени, а не по узлу.
     */
    private static final Map<String, String> FUNCTION_HEADERS = Map.ofEntries(
            Map.entry("pow", "cmath"),
            Map.entry("sqrt", "cmath"),
            Map.entry("cbrt", "cmath"),
            Map.entry("exp", "cmath"),
            Map.entry("log", "cmath"),
            Map.entry("log2", "cmath"),
            Map.entry("log10", "cmath"),
            Map.entry("sin", "cmath"),
            Map.entry("cos", "cmath"),
            Map.entry("tan", "cmath"),
            Map.entry("asin", "cmath"),
            Map.entry("acos", "cmath"),
            Map.entry("atan", "cmath"),
            Map.entry("atan2", "cmath"),
            Map.entry("ceil", "cmath"),
            Map.entry("floor", "cmath"),
            Map.entry("round", "cmath"),
            Map.entry("trunc", "cmath"),
            Map.entry("fmod", "cmath"),
            Map.entry("hypot", "cmath"),
            Map.entry("fabs", "cmath"),
            Map.entry("abs", "cstdlib"),
            Map.entry("min", "algorithm"),
            Map.entry("max", "algorithm")
    );

    /**
     * Заголовки под библиотечные модули других языков — только там, где соответствие
     * действительно однозначно. Таблица нарочно короткая: выдуманное соответствие даёт код,
     * который не собирается, а пропуск — код, которому не хватает одной строки.
     */
    private static final Map<String, String> LIBRARY_MODULE_HEADERS = Map.ofEntries(
            // Python
            Map.entry("math", "cmath"),
            Map.entry("cmath", "cmath"),
            Map.entry("random", "random"),
            Map.entry("string", "string"),
            Map.entry("array", "array"),
            Map.entry("queue", "queue"),
            // Java
            Map.entry("java.lang.Math", "cmath"),
            Map.entry("java.util.List", "vector"),
            Map.entry("java.util.ArrayList", "vector"),
            Map.entry("java.util.Vector", "vector"),
            Map.entry("java.util.Set", "set"),
            Map.entry("java.util.HashSet", "set"),
            Map.entry("java.util.TreeSet", "set"),
            Map.entry("java.util.Map", "unordered_map"),
            Map.entry("java.util.HashMap", "unordered_map"),
            Map.entry("java.util.TreeMap", "map"),
            Map.entry("java.util.Arrays", "algorithm"),
            Map.entry("java.util.Collections", "algorithm")
    );

    /**
     * Заголовок, без которого не соберётся тип, напечатанный вьюером.
     * <p>
     * Разбор идёт от частного к общему ровно в том же порядке, в котором {@code CppViewer}
     * выбирает сам тип: иначе таблица разошлась бы с выводом и подключала не тот заголовок.
     *
     * @return имя заголовка без угловых скобок
     */
    public static Optional<String> headerForType(Type type) {
        return Optional.ofNullable(switch (type) {
            case ArrayType ignored -> "array";
            case UnmodifiableListType ignored -> "array";
            case SetType ignored -> "set";
            case OrderedDictionaryType ignored -> "map";
            case UnorderedDictionaryType ignored -> "unordered_map";
            case PlainCollectionType ignored -> "vector";
            case StringType ignored -> "string";
            default -> null;
        });
    }

    /**
     * Заголовок под свободную функцию, если она из стандартной библиотеки.
     *
     * @return имя заголовка без угловых скобок
     */
    public static Optional<String> headerForFunction(String functionName) {
        return Optional.ofNullable(FUNCTION_HEADERS.get(functionName));
    }

    /**
     * Заголовок под библиотечный модуль другого языка.
     *
     * @return пусто, если однозначного соответствия нет — тогда подключение лучше опустить,
     *         чем сослаться на несуществующий файл
     */
    public static Optional<String> headerForLibraryModule(String dottedName) {
        return Optional.ofNullable(LIBRARY_MODULE_HEADERS.get(dottedName));
    }
}

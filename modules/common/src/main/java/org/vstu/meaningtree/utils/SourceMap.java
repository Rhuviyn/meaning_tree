package org.vstu.meaningtree.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Карта соответствия между деревом и сгенерированным кодом.
 * <p>
 * Таблиц областей видимости здесь две, потому что одна не может описывать обе стороны перевода
 * честно. {@code renderScopeTable} относится к тому, что напечатано: дерево в том виде, до
 * которого его довели оптимизации целевого языка, разобранное правилами целевого языка.
 * {@code originScopeTable} относится к тому, что пришло на вход: то же дерево до оптимизаций,
 * разобранное правилами языка-источника. Раньше поле было одно и заполнялось смесью — исходное
 * дерево с целевой семантикой, — то есть не описывало последовательно ни одну из сторон.
 *
 * @param renderScopeTable области видимости напечатанного кода
 * @param originScopeTable области видимости входного дерева; {@code null}, если язык-источник
 *                         вызывающему неизвестен и построить её не из чего
 */
public record SourceMap(String code, NodeIterable root,
                        Map<Long, Pair<Integer, Integer>> bytePositions,
                        ScopeTable renderScopeTable,
                        @Nullable ScopeTable originScopeTable,
                        String language,
                        Map<String, Number> metrics,
                        @Nullable String projectRootPath,
                        @Nullable String projectFileRelPath)
        implements Serializable {

    public SourceMap {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(bytePositions, "bytePositions must not be null");
        Objects.requireNonNull(renderScopeTable, "renderScopeTable must not be null");
        Objects.requireNonNull(language, "language must not be null");
        metrics = metrics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metrics));
        bytePositions = Map.copyOf(bytePositions);
    }

    public SourceMap(String code, NodeIterable root,
                     Map<Long, Pair<Integer, Integer>> bytePositions,
                     ScopeTable renderScopeTable,
                     String language) {
        this(code, root, bytePositions, renderScopeTable, null, language, Map.of(), null, null);
    }

    public SourceMap(String code, NodeIterable root,
                     Map<Long, Pair<Integer, Integer>> bytePositions,
                     ScopeTable renderScopeTable,
                     String language,
                     Map<String, Number> metrics) {
        this(code, root, bytePositions, renderScopeTable, null, language, metrics, null, null);
    }
}

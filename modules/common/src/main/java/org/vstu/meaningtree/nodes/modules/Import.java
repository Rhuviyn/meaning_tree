package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.nodes.Node;

import java.util.Optional;

public abstract class Import extends Node {
    /**
     * Заполняется анализом ({@code ImportResolver}) и потому живёт как обычное поле, а не как
     * {@code @TreeNode}: это не часть дерева, а вывод о нём — по той же конвенции, что и
     * {@link org.vstu.meaningtree.nodes.statements.Loop#getIterationEstimate()}.
     */
    protected ImportResolverMetadata resolverMetadata;

    public Optional<ImportResolverMetadata> getResolverMetadata() {
        return Optional.ofNullable(resolverMetadata);
    }

    public void setResolverMetadata(ImportResolverMetadata resolverMetadata) {
        this.resolverMetadata = resolverMetadata;
    }

    /**
     * Переносит метаданные на копию узла.
     * <p>
     * Нужно там, где {@code clone()} собирает новый объект конструктором, а не через
     * {@code super.clone()}: поверхностное копирование поля тогда не срабатывает, и вывод
     * анализа молча теряется на первом же клонировании дерева.
     */
    protected <T extends Import> T withResolverMetadataOf(T copy) {
        copy.resolverMetadata = this.resolverMetadata;
        return copy;
    }
}

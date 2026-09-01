package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Одно преобразование, найденное в дереве.
 * <p>
 * Результат — {@link ConversionCompatibility}, а не {@code boolean}: неразрешённый вызов и
 * запрещённый языком переход — разные находки, и потребитель обязан их различать.
 */
public record TypeConversionCheck(
        Node relatedNode,
        ConversionSiteKind siteKind,
        OptionalInt valueIndex,
        Type sourceType,
        Type targetType,
        ConversionKind conversionKind,
        ConversionCompatibility compatibility) {

    public TypeConversionCheck {
        Objects.requireNonNull(relatedNode, "relatedNode must not be null");
        Objects.requireNonNull(siteKind, "siteKind must not be null");
        Objects.requireNonNull(valueIndex, "valueIndex must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(conversionKind, "conversionKind must not be null");
        Objects.requireNonNull(compatibility, "compatibility must not be null");
    }

    public TypeConversionCheck(
            Node relatedNode,
            ConversionSiteKind siteKind,
            Type sourceType,
            Type targetType,
            ConversionKind conversionKind,
            ConversionCompatibility compatibility) {
        this(relatedNode, siteKind, OptionalInt.empty(), sourceType, targetType, conversionKind, compatibility);
    }
}

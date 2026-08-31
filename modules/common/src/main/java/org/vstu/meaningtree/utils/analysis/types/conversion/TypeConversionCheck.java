package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;

import java.util.Objects;
import java.util.OptionalInt;

/** One conversion discovered in a meaning tree. */
public record TypeConversionCheck(
        Node relatedNode,
        ConversionSiteKind siteKind,
        OptionalInt valueIndex,
        Type sourceType,
        Type targetType,
        ConversionKind conversionKind,
        boolean compatible) {

    public TypeConversionCheck {
        Objects.requireNonNull(relatedNode, "relatedNode must not be null");
        Objects.requireNonNull(siteKind, "siteKind must not be null");
        Objects.requireNonNull(valueIndex, "valueIndex must not be null");
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(conversionKind, "conversionKind must not be null");
    }

    public TypeConversionCheck(
            Node relatedNode,
            ConversionSiteKind siteKind,
            Type sourceType,
            Type targetType,
            ConversionKind conversionKind,
            boolean compatible) {
        this(relatedNode, siteKind, OptionalInt.empty(), sourceType, targetType, conversionKind, compatible);
    }
}

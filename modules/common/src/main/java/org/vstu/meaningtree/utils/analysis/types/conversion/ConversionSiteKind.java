package org.vstu.meaningtree.utils.analysis.types.conversion;

/** A syntax position that requires a value to have another type. */
public enum ConversionSiteKind {
    CAST,
    INITIALIZER,
    ASSIGNMENT,
    ARGUMENT,
    RETURN
}

package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

/**
 * Language-specific refinement of the common conversion rules.
 *
 * <p>An empty result delegates the decision to the language-independent rules. A present result is
 * authoritative, including {@code false}. The scope argument is currently unused by primitive
 * implementations, but keeps the contract ready for user-type hierarchy checks.</p>
 */
@FunctionalInterface
public interface TypeConversionSemantics {
    @NotNull
    Optional<Boolean> overrideCompatibility(
            @NotNull Type source,
            @NotNull Type target,
            @NotNull ConversionKind kind,
            @NotNull ScopeTable scope);

    static TypeConversionSemantics common() {
        return (source, target, kind, scope) -> Optional.empty();
    }
}

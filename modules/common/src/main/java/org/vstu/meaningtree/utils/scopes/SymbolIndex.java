package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.io.Serializable;
import java.util.*;

class SymbolIndex implements Serializable {
    @NotNull
    private final DeclarationBucket declarations = new DeclarationBucket();

    @NotNull
    private final Map<Declaration, Definition> definitions = new HashMap<>();

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        declarations.register(name, declaration);
    }

    public void registerDefinition(@NotNull Declaration declaration, @NotNull Definition definition) {
        definitions.put(declaration, definition);
    }

    public boolean removeDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        definitions.remove(declaration);
        return declarations.remove(name, declaration);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name,
                                                 @Nullable java.lang.Class<? extends Declaration> clazz) {
        return declarations.findLast(name, clazz);
    }

    public List<Declaration> findDeclarations(@NotNull SimpleIdentifier name,
                                              @Nullable java.lang.Class<? extends Declaration> clazz) {
        return declarations.findAll(name, clazz);
    }

    public List<Declaration> findDeclaration(@NotNull java.lang.Class<? extends Declaration> clazz) {
        return declarations.findAll(clazz);
    }

    public Optional<Definition> findDefinition(@NotNull Declaration declaration) {
        return Optional.ofNullable(definitions.get(declaration));
    }

    public List<Definition> findDefinition(@NotNull java.lang.Class<? extends Definition> clazz) {
        return definitions.values().stream()
                .filter(definition -> clazz.isAssignableFrom(definition.getClass()))
                .toList();
    }

    public Map<Identifier, List<Declaration>> allDeclarations() {
        return Map.copyOf(new LinkedHashMap<Identifier, List<Declaration>>(declarations.asMap()));
    }

    public Map<Declaration, Definition> allDefinitions() {
        return Map.copyOf(definitions);
    }
}

package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.InterfaceDefinition;
import org.vstu.meaningtree.nodes.types.GenericInterface;
import org.vstu.meaningtree.nodes.types.user.Interface;

public class MultipleInheritanceForJavaFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-multiple-class-inheritance-for-java";
    }

    @Override
    public boolean matches(Node node, FeatureContext context) {
        if (!(node instanceof ClassDefinition definition) || node instanceof InterfaceDefinition) {
            return false;
        }
        long classParents = definition.getDeclaration().getParents().stream()
                .filter(parent -> !isInterface(parent, context))
                .count();
        return classParents > 1;
    }

    @Override
    public String description(Node node) {
        return "Java allows at most one base class; additional bases must be semantic interfaces";
    }

    private boolean isInterface(Type type, FeatureContext context) {
        if (type instanceof Interface || type instanceof GenericInterface) {
            return true;
        }
        if (context == null || context.meaningTree() == null) {
            return false;
        }
        for (var info : context.meaningTree()) {
            if (info.node() instanceof InterfaceDefinition definition
                    && definition.getDeclaration().getName().internalRepresentation()
                    .equals(type.internalRepresentation())) {
                return true;
            }
        }
        return false;
    }
}

package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.GenericInterface;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.user.Interface;

import java.util.List;

public class InterfaceDeclaration extends ClassDeclaration {
    public InterfaceDeclaration(List<DeclarationModifier> modifiers, Identifier name, Type... parents) {
        this(modifiers, name, List.of(), parents);
    }

    public InterfaceDeclaration(List<DeclarationModifier> modifiers,
                                Identifier name,
                                List<Type> typeParameters,
                                Type... parents) {
        super(modifiers, name, typeParameters,
                typeParameters.isEmpty()
                        ? new Interface((Identifier) name.freshClone())
                        : new GenericInterface((Identifier) name.freshClone(), cloneTypes(typeParameters)),
                parents);
    }

    protected InterfaceDeclaration(List<DeclarationModifier> modifiers,
                                   Identifier name,
                                   List<Type> typeParameters,
                                   UserType typeNode,
                                   Type... parents) {
        super(modifiers, name, typeParameters, typeNode, parents);
    }

    public static InterfaceDeclaration withTypeNode(List<DeclarationModifier> modifiers,
                                                    Identifier name,
                                                    List<Type> typeParameters,
                                                    UserType typeNode,
                                                    Type... parents) {
        return new InterfaceDeclaration(modifiers, name, typeParameters, typeNode, parents);
    }
}

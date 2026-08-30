package org.vstu.meaningtree.nodes.definitions;

import org.vstu.meaningtree.nodes.declarations.InterfaceDeclaration;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

public class InterfaceDefinition extends ClassDefinition {
    public InterfaceDefinition(InterfaceDeclaration declaration, CompoundStatement body) {
        super(declaration, body);
    }

    @Override
    public InterfaceDeclaration getDeclaration() {
        return (InterfaceDeclaration) super.getDeclaration();
    }
}

package org.vstu.meaningtree.nodes.types;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.Identifier;

public class GenericInterface extends GenericUserType {
    public GenericInterface(Identifier name, Type... templateParameters) {
        super(name, templateParameters);
    }
}

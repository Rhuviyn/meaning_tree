package org.vstu.meaningtree.utils.analysis.symbols;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.InterfaceDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverrideResolverTests {

    @Test
    void overridesMethodOfParentClass() {
        ClassDeclaration parentDecl = new ClassDeclaration(new SimpleIdentifier("Animal"));
        MethodDeclaration parentMethod = method(parentDecl, "speak", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition parent = classDefinition(parentDecl, new MethodDefinition(parentMethod, body()));

        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("Dog"), List.of(), parentDecl.getTypeNode()
        );
        MethodDeclaration childMethod = method(childDecl, "speak", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(parent, child);

        assertSame(parentMethod, childMethod.getOverriddenFrom());
    }

    @Test
    void implementsMethodOfInterface() {
        InterfaceDeclaration ifaceDecl = new InterfaceDeclaration(List.of(), new SimpleIdentifier("Shape"));
        MethodDeclaration ifaceMethod = method(ifaceDecl, "area", List.of(DeclarationModifier.ABSTRACT));
        InterfaceDefinition iface = new InterfaceDefinition(ifaceDecl, new CompoundStatement(ifaceMethod));

        ClassDeclaration implDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("Circle"), List.of(), ifaceDecl.getTypeNode()
        );
        MethodDeclaration implMethod = method(implDecl, "area", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition impl = classDefinition(implDecl, new MethodDefinition(implMethod, body()));

        resolve(iface, impl);

        assertSame(ifaceMethod, implMethod.getOverriddenFrom());
    }

    @Test
    void resolvesTransitivelyThroughGrandparent() {
        ClassDeclaration grandparentDecl = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDeclaration grandparentMethod = method(grandparentDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition grandparent = classDefinition(grandparentDecl, new MethodDefinition(grandparentMethod, body()));

        ClassDeclaration parentDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), grandparentDecl.getTypeNode()
        );
        ClassDefinition parent = classDefinition(parentDecl);

        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("C"), List.of(), parentDecl.getTypeNode()
        );
        MethodDeclaration childMethod = method(childDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(grandparent, parent, child);

        assertSame(grandparentMethod, childMethod.getOverriddenFrom());
    }

    @Test
    void differentArgumentTypesDoNotMatch() {
        ClassDeclaration parentDecl = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDeclaration parentMethod = method(
                parentDecl, "run", List.of(DeclarationModifier.PUBLIC),
                new DeclarationArgument(new IntType(), new SimpleIdentifier("x"), null)
        );
        ClassDefinition parent = classDefinition(parentDecl, new MethodDefinition(parentMethod, body()));

        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), parentDecl.getTypeNode()
        );
        MethodDeclaration childMethod = method(
                childDecl, "run", List.of(DeclarationModifier.PUBLIC),
                new DeclarationArgument(new org.vstu.meaningtree.nodes.types.builtin.FloatType(), new SimpleIdentifier("x"), null)
        );
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(parent, child);

        assertNull(childMethod.getOverriddenFrom());
    }

    @Test
    void unknownArgumentTypeStillMatches() {
        ClassDeclaration parentDecl = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDeclaration parentMethod = method(
                parentDecl, "run", List.of(DeclarationModifier.PUBLIC),
                new DeclarationArgument(new IntType(), new SimpleIdentifier("x"), null)
        );
        ClassDefinition parent = classDefinition(parentDecl, new MethodDefinition(parentMethod, body()));

        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), parentDecl.getTypeNode()
        );
        MethodDeclaration childMethod = method(
                childDecl, "run", List.of(DeclarationModifier.PUBLIC),
                new DeclarationArgument(new UnknownType(), new SimpleIdentifier("x"), null)
        );
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(parent, child);

        assertSame(parentMethod, childMethod.getOverriddenFrom());
    }

    @Test
    void missingAncestorInFragmentLeavesNull() {
        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), new ClassDeclaration(new SimpleIdentifier("A")).getTypeNode()
        );
        MethodDeclaration childMethod = method(childDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(child);

        assertNull(childMethod.getOverriddenFrom());
    }

    @Test
    void staticMethodAndConstructorAreNotLinked() {
        ClassDeclaration parentDecl = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDeclaration parentStatic = method(parentDecl, "run", List.of(DeclarationModifier.PUBLIC, DeclarationModifier.STATIC));
        ObjectConstructorDeclaration parentCtor = new ObjectConstructorDeclaration(
                parentDecl.getTypeNode(), new SimpleIdentifier("A"), List.of(), List.of(DeclarationModifier.PUBLIC)
        );
        ClassDefinition parent = classDefinition(
                parentDecl, new MethodDefinition(parentStatic, body()), new MethodDefinition(parentCtor, body())
        );

        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), parentDecl.getTypeNode()
        );
        MethodDeclaration childStatic = method(childDecl, "run", List.of(DeclarationModifier.PUBLIC, DeclarationModifier.STATIC));
        ObjectConstructorDeclaration childCtor = new ObjectConstructorDeclaration(
                childDecl.getTypeNode(), new SimpleIdentifier("B"), List.of(), List.of(DeclarationModifier.PUBLIC)
        );
        ClassDefinition child = classDefinition(
                childDecl, new MethodDefinition(childStatic, body()), new MethodDefinition(childCtor, body())
        );

        resolve(parent, child);

        assertNull(childStatic.getOverriddenFrom());
        assertNull(childCtor.getOverriddenFrom());
    }

    @Test
    void cyclicHierarchyDoesNotHang() {
        // Некорректная, но допустимая на уровне AST схема: A наследует B, а B наследует A.
        ClassDeclaration bDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), new ClassDeclaration(new SimpleIdentifier("A")).getTypeNode()
        );
        ClassDeclaration aDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("A"), List.of(), bDecl.getTypeNode()
        );
        MethodDeclaration method = method(aDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition a = classDefinition(aDecl, new MethodDefinition(method, body()));
        ClassDefinition b = classDefinition(bDecl);

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> resolve(a, b));

        assertNull(method.getOverriddenFrom());
    }

    private static void resolve(ClassDefinition... classes) {
        MeaningTree tree = new MeaningTree(new ProgramEntryPoint(List.<Node>of(classes)));
        new OverrideResolver(tree, new ScopeTable()).resolve();
    }

    private static CompoundStatement body() {
        return new CompoundStatement();
    }

    private static ClassDefinition classDefinition(ClassDeclaration declaration, Node... members) {
        return new ClassDefinition(declaration, new CompoundStatement(members));
    }

    private static MethodDeclaration method(ClassDeclaration owner, String name,
                                            List<DeclarationModifier> modifiers,
                                            DeclarationArgument... arguments) {
        return new MethodDeclaration(
                owner.getTypeNode(), new SimpleIdentifier(name), new IntType(),
                List.<Annotation>of(), modifiers, arguments
        );
    }
}

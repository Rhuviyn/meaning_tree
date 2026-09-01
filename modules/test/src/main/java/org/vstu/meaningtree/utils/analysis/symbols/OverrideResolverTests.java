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
import org.vstu.meaningtree.nodes.types.builtin.StringType;
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

        assertSame(parentMethod, childMethod.getOverriddenFromSingle());
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

        assertSame(ifaceMethod, implMethod.getOverriddenFromSingle());
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

        assertSame(grandparentMethod, childMethod.getOverriddenFromSingle());
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

        assertNull(childMethod.getOverriddenFromSingle());
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

        assertSame(parentMethod, childMethod.getOverriddenFromSingle());
    }

    @Test
    void missingAncestorInFragmentLeavesNull() {
        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), new ClassDeclaration(new SimpleIdentifier("A")).getTypeNode()
        );
        MethodDeclaration childMethod = method(childDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(child);

        assertNull(childMethod.getOverriddenFromSingle());
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

        assertNull(childStatic.getOverriddenFromSingle());
        assertNull(childCtor.getOverriddenFromSingle());
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

        assertNull(method.getOverriddenFromSingle());
    }

    /**
     * Метод реализует одинаковую сигнатуру двух интерфейсов. Оба — предки на одном расстоянии,
     * и выбирать между ними по порядку объявления родителей значило бы выдавать порядок за факт.
     */
    @Test
    void keepsBothTargetsWhenTwoInterfacesDeclareTheSameSignature() {
        InterfaceDeclaration firstDecl = new InterfaceDeclaration(List.of(), new SimpleIdentifier("Readable"));
        MethodDeclaration firstMethod = method(firstDecl, "run", List.of(DeclarationModifier.ABSTRACT));
        InterfaceDefinition first = new InterfaceDefinition(firstDecl, new CompoundStatement(firstMethod));

        InterfaceDeclaration secondDecl = new InterfaceDeclaration(List.of(), new SimpleIdentifier("Runnable"));
        MethodDeclaration secondMethod = method(secondDecl, "run", List.of(DeclarationModifier.ABSTRACT));
        InterfaceDefinition second = new InterfaceDefinition(secondDecl, new CompoundStatement(secondMethod));

        ClassDeclaration implDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("Task"), List.of(),
                firstDecl.getTypeNode(), secondDecl.getTypeNode()
        );
        MethodDeclaration implMethod = method(implDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition impl = classDefinition(implDecl, new MethodDefinition(implMethod, body()));

        resolve(first, second, impl);

        assertTrue(implMethod.isOverride());
        assertTrue(implMethod.isOverrideAmbiguous(), "оба интерфейса объявляют эту сигнатуру");
        assertEquals(2, implMethod.getOverriddenFrom().size());
        assertNull(implMethod.getOverriddenFromSingle(), "единственного предка здесь нет");
    }

    /**
     * {@code UnknownType} совпадает с любым типом, поэтому при перегруженном имени приблизительное
     * совпадение выбирало бы перегрузку предка наугад. Точное совпадение обязано выигрывать.
     */
    @Test
    void exactSignatureWinsOverUnknownTypeMatch() {
        ClassDeclaration parentDecl = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDeclaration takesInt = method(
                parentDecl, "run", List.of(DeclarationModifier.PUBLIC),
                new DeclarationArgument(new IntType(), new SimpleIdentifier("x"), null)
        );
        MethodDeclaration takesString = method(
                parentDecl, "run", List.of(DeclarationModifier.PUBLIC),
                new DeclarationArgument(new StringType(), new SimpleIdentifier("x"), null)
        );
        ClassDefinition parent = classDefinition(parentDecl,
                new MethodDefinition(takesInt, body()), new MethodDefinition(takesString, body()));

        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), parentDecl.getTypeNode()
        );
        MethodDeclaration childMethod = method(
                childDecl, "run", List.of(DeclarationModifier.PUBLIC),
                new DeclarationArgument(new StringType(), new SimpleIdentifier("x"), null)
        );
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(parent, child);

        assertSame(takesString, childMethod.getOverriddenFromSingle());
    }

    /** Клон — ещё не проанализированный узел: связь с предком проставляет анализ, а не копирование. */
    @Test
    void cloneCarriesNoOverrideLink() {
        ClassDeclaration parentDecl = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDeclaration parentMethod = method(parentDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition parent = classDefinition(parentDecl, new MethodDefinition(parentMethod, body()));

        ClassDeclaration childDecl = new ClassDeclaration(
                List.of(), new SimpleIdentifier("B"), List.of(), parentDecl.getTypeNode()
        );
        MethodDeclaration childMethod = method(childDecl, "run", List.of(DeclarationModifier.PUBLIC));
        ClassDefinition child = classDefinition(childDecl, new MethodDefinition(childMethod, body()));

        resolve(parent, child);

        assertTrue(childMethod.isOverride());
        assertFalse(childMethod.clone().isOverride(), "клон не должен ссылаться в исходное дерево");
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

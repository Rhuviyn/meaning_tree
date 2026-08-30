package org.vstu.meaningtree.nodes.declarations;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.interfaces.NestedDeclaration;
import org.vstu.meaningtree.nodes.types.UserType;

import java.util.List;
import java.util.Objects;

public class MethodDeclaration extends FunctionDeclaration implements NestedDeclaration<ClassDeclaration> {
    private UserType owner;
    private ClassDeclaration parent;

    /**
     * Ближайший метод предка (по классу или интерфейсу), который этот метод переопределяет
     * или реализует. Заполняется {@code OverrideResolver} после парсинга, только если предок
     * присутствует в том же фрагменте кода; иначе остаётся {@code null}. Ссылка не участвует
     * в {@code equals}/{@code hashCode} и не копируется в {@link #clone()} — как и {@link #parent},
     * она указывает на узел вверх по иерархии дерева, а не является его частью.
     * После {@code freshClone()} ссылка продолжает указывать на узел исходного дерева.
     */
    private MethodDeclaration overriddenFrom;

    public MethodDeclaration(UserType owner,
                             Identifier name,
                             Type returnType,
                             List<Annotation> annotations,
                             List<DeclarationModifier> modifiers,
                             DeclarationArgument... arguments
    ) {
        this(owner, name, returnType, annotations, modifiers, List.of(arguments));
    }

    public MethodDeclaration(UserType owner,
                             Identifier name,
                             Type returnType,
                             List<Annotation> annotations,
                             List<DeclarationModifier> modifiers,
                             List<DeclarationArgument> arguments
    ) {
        super(name, returnType, annotations, arguments);
        this.owner = owner;
        this.modifiers = List.copyOf(modifiers);
    }

    public UserType getOwner() {
        return owner;
    }

    public void setOwner(UserType owner) {
        this.owner = owner;
    }

    @Nullable
    public MethodDeclaration getOverriddenFrom() {
        return overriddenFrom;
    }

    public void setOverriddenFrom(@Nullable MethodDeclaration overriddenFrom) {
        this.overriddenFrom = overriddenFrom;
    }

    public boolean isOverride() {
        return overriddenFrom != null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(owner, nodeInfos.owner) && Objects.equals(modifiers, nodeInfos.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), owner, modifiers);
    }

    public MethodDeclaration clone() {
        var clone = (MethodDeclaration) super.clone();
        clone.modifiers = List.copyOf(modifiers);
        clone.owner = owner.clone();
        return clone;
    }

    @Override
    public ClassDeclaration getParentDeclaration() {
        return parent;
    }

    @Override
    public void setParentDeclaration(ClassDeclaration declaration) {
        if (parent == null) parent = declaration;
    }
}

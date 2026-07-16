package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StringFormat extends Expression {
    @TreeNode private StringFormatTemplate template;
    @TreeNode private Expression[] substitutions;
    private final StringLiteral.Type type;

    public StringFormat(StringLiteral.Type type, StringFormatTemplate template, Expression[] substitutions) {
        if (template.getComponentsList().stream().filter(
                comp -> comp instanceof FormatSpecifier specifier
                && !specifier.assignmentIsSuppressed).count() != substitutions.length) {
            throw new IllegalStateException("Amount of specifiers and substitutions in string format doesn't match.");
        }
        this.type = type;
        this.template = template;
        this.substitutions = substitutions;
    }

    public StringFormat(StringLiteral.Type type, StringFormatTemplate template) {
        this(type, template, new Expression[0]);
    }

    public Expression[] getSubstitutions() {
        return this.substitutions;
    }

    public List<Expression> getSubstitutionList() {
        return List.of(this.substitutions);
    }

    public StringFormatTemplate getTemplate() {
        return this.template;
    }

    public String getFormatString() {
        return template.toFormatString();
    }

    public StringLiteral.Type getStringType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        StringFormat nodeInfos = (StringFormat) o;
        return Objects.equals(template, nodeInfos.template) && Objects.deepEquals(substitutions, nodeInfos.substitutions) && type == nodeInfos.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), template, Arrays.hashCode(substitutions), type);
    }

    @Override
    public StringFormat clone() {
        StringFormat obj = (StringFormat) super.clone();
        obj.template = template.clone();
        obj.substitutions = Arrays.copyOf(substitutions, substitutions.length);
        for (int i = 0; i < obj.substitutions.length; i++) {
            obj.substitutions[i] = substitutions[i].clone();
        }
        return obj;
    }
}

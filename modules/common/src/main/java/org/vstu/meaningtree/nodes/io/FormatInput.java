package org.vstu.meaningtree.nodes.io;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;
import org.vstu.meaningtree.nodes.expressions.other.StringFormat;
import org.vstu.meaningtree.nodes.expressions.other.StringFormatTemplate;

import java.util.List;
import java.util.Objects;

public class FormatInput extends InputCommand {
    @TreeNode private StringFormat format;

    public FormatInput(StringFormat format) {
        super(List.of());
        this.format = format;
    }

    public FormatInput(String formatString, Expression ... values) {
        this(new StringFormat(StringLiteral.Type.NONE, StringFormatTemplate.fromFormatString(formatString), values));
    }

    public StringFormat getFormat() {
        return format;
    }

    public String getFormatString() {
        return format.getFormatString();
    }

    public Expression[] getValues() {
        return format.getSubstitutions();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FormatInput nodeInfos = (FormatInput) o;
        return Objects.equals(format, nodeInfos.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), format);
    }

    @Override
    public FormatInput clone() {
        FormatInput obj = (FormatInput) super.clone();
        obj.format = format.clone();
        return obj;
    }
}


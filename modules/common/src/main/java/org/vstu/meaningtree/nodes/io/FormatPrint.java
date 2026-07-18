package org.vstu.meaningtree.nodes.io;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;
import org.vstu.meaningtree.nodes.expressions.other.StringFormat;
import org.vstu.meaningtree.nodes.expressions.other.StringFormatTemplate;

import java.util.List;
import java.util.Objects;

public class FormatPrint extends PrintCommand {
    @TreeNode private StringFormat format;

    public FormatPrint(StringFormat format) {
        super(List.of());
        this.format = format;
    }

    public FormatPrint(String formatString, Expression ... values) {
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
        FormatPrint that = (FormatPrint) o;
        return Objects.equals(format, that.format);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), format);
    }

    @Override
    public FormatPrint clone() {
        FormatPrint obj = (FormatPrint) super.clone();
        obj.format = format.clone();
        return obj;
    }
}

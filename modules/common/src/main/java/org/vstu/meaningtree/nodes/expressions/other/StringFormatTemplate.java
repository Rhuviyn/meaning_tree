package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StringFormatTemplate extends Expression {
    @TreeNode private Expression[] components;

    public StringFormatTemplate(Expression[] components) {
        this.components = components;
    }

    public Expression[] getComponents() {
        return Arrays.copyOf(components, components.length);
    }

    public List<Expression> getComponentsList() {
        return List.of(components);
    }

    public String toFormatString() {
        StringBuilder formatString = new StringBuilder();
        formatString.append("\"");
        for (Expression component : components) {
            switch (component) {
                case StringLiteral literal -> formatString.append(literal.getEscapedValue().replaceAll("(%)", "$1$1"));
                case FormatSpecifier specifier -> {
                    if (specifier.isExpression()) {
                        throw new IllegalArgumentException("String format template with expression placeholders cannot be converted to format string.");
                    }
                    formatString.append("%").append(specifier.asString());
                }
                default -> throw new IllegalArgumentException("Unexpected value in format string: " + component + ". Only StringLiteral and FormatSpecifier are allowed.");
            }
        }
        return formatString.append("\"").toString();
    }

    public static StringFormatTemplate fromFormatString(String formatString) {
        List<Expression> components = new ArrayList<>();
        StringBuilder component = new StringBuilder();
        boolean isSpecifier = false;
        int currentIndex = 0;

        while (currentIndex < formatString.length()) {
            char ch = formatString.charAt(currentIndex);
            if (isSpecifier) {
                if (ch == '[') {
                    while (ch != ']' && currentIndex < formatString.length()) {
                        component.append(ch);
                        ch = formatString.charAt(++currentIndex);
                    }
                }
                component.append(ch);
                if (FormatSpecifier.isSupportedSpecifier(ch)) {
                    components.add(FormatSpecifier.fromFormatString(component.toString()));
                    isSpecifier = false;
                    component.setLength(0);
                }
            } else {
                if (ch == '%') {
                    if (currentIndex + 1 < formatString.length() && formatString.charAt(currentIndex + 1) == '%') {
                        currentIndex++;
                    } else {
                        if (!component.isEmpty()) {
                            components.add(StringLiteral.fromUnescaped(component.toString(), StringLiteral.Type.NONE));
                            component.setLength(0);
                        }
                        isSpecifier = true;
                    }
                }
                component.append(ch);
            }
            currentIndex++;
        }

        if (isSpecifier) {
            throw new UnsupportedParsingException("Incomplete format specifier: " + component);
        }

        if (!component.isEmpty()) {
            components.add(StringLiteral.fromUnescaped(component.toString(), StringLiteral.Type.NONE));
        }

        return new StringFormatTemplate(components.toArray(new Expression[0]));
    }

    public static StringFormatTemplate fromBracedFormatString(String formatString) {
        List<Expression> components = new ArrayList<>();
        StringBuilder component = new StringBuilder();
        boolean insideBraces = false;
        int currentIndex = 0;

        while (currentIndex < formatString.length()) {
            char ch = formatString.charAt(currentIndex);
            if (ch == '{') {
                if (insideBraces) {
                    throw new IllegalArgumentException("Nested braces in formatted string are not allowed");
                }
                if (currentIndex + 1 < formatString.length() && formatString.charAt(currentIndex + 1) == '{') {
                    component.append(ch);
                    currentIndex++;
                } else {
                    components.add(StringLiteral.fromUnescaped(component.toString(), StringLiteral.Type.NONE));
                    insideBraces = true;
                    component.setLength(0);
                }
            } else if (ch == '}') {
                if (insideBraces) {
                    if (component.isEmpty()) {
                        components.add(FormatSpecifier.emptyExpressionSpecifier());
                    } else if (component.length() > 1 && component.charAt(0) == ':') {
                        components.add(FormatSpecifier.fromFormatString(component.toString()));
                    } else {
                        throw new IllegalArgumentException("Invalid placeholder format. Expected '{}' or '{:spec}'");
                    }
                    insideBraces = false;
                    component.setLength(0);
                } else {
                    if (currentIndex + 1 < formatString.length() && formatString.charAt(currentIndex + 1) == '}') {
                        component.append(ch);
                        currentIndex++;
                    } else {
                        throw new IllegalArgumentException("Closing brace without opening brace in formatted string");
                    }
                }
            } else {
                component.append(ch);
            }
            currentIndex++;
        }

        if (insideBraces) {
            throw new IllegalArgumentException("Unclosed opening brace in formatted string");
        }

        if (!component.isEmpty()) {
            components.add(StringLiteral.fromUnescaped(
                    component.toString(),
                    StringLiteral.Type.NONE
            ));
        }

        return new StringFormatTemplate(components.toArray(new Expression[0]));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        StringFormatTemplate nodeInfos = (StringFormatTemplate) o;
        return Objects.deepEquals(getComponents(), nodeInfos.getComponents());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), Arrays.hashCode(getComponents()));
    }

    @Override
    public StringFormatTemplate clone() {
        StringFormatTemplate obj = (StringFormatTemplate) super.clone();
        obj.components = Arrays.copyOf(components, components.length);
        for (int i = 0; i < obj.components.length; i++) {
            obj.components[i] = components[i].clone();
        }
        return obj;
    }
}

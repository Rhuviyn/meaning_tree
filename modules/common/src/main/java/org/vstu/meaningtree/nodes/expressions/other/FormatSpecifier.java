package org.vstu.meaningtree.nodes.expressions.other;

import com.sun.jdi.IntegerType;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.CharacterType;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FormatSpecifier extends Expression {
    public final boolean assignmentIsSuppressed;
    public final boolean hasPlusFlag;
    public final boolean hasZeroFlag;
    public final int width;
    public final int precision;
    public final String scanSet;
    public final boolean scanSetIsNegated;
    public final SpecifierType type;

    private FormatSpecifier(boolean assignmentIsSuppressed, boolean hasPlusFlag, boolean hasZeroFlag, int width, int precision, String scanSet, boolean scanSetIsNegated, SpecifierType type) {
        this.assignmentIsSuppressed = assignmentIsSuppressed;
        this.hasPlusFlag = hasPlusFlag;
        this.hasZeroFlag = hasZeroFlag;
        this.width = width;
        this.precision = precision;
        this.scanSet = scanSet;
        this.scanSetIsNegated = scanSetIsNegated;
        this.type = type;
    }

    public static boolean isSupportedSpecifier(char symbol) {
        return SpecifierType.fromSymbol(symbol).isPresent();
    }

    public static FormatSpecifier emptyExpressionSpecifier() {
        return new FormatSpecifierBuilder().setExpression().build();
    }

    public static SpecifierType getSpecifierTypeForDataType(Type type) {
        switch (type) {
            case IntType ignored -> {
                return SpecifierType.DECIMAL;
            }
            case FloatType ignored -> {
                return SpecifierType.FLOATING;
            }
            case CharacterType ignored -> {
                return SpecifierType.CHARACTER;
            }
            case StringType ignored -> {
                return SpecifierType.STRING;
            }
            default -> throw new IllegalStateException("Specifier for " + type.getNodeUniqueName() + " is not supported.");
        }
    }

    public Optional<Type> getCorrespondingDataType() {
        switch (type) {
            case DECIMAL, OCTAL, HEXADECIMAL_LOWERCASE, HEXADECIMAL_UPPERCASE -> {
                return Optional.of(new IntType());
            }
            case FLOATING, FLOATING_EXP_LOWERCASE, FLOATING_EXP_UPPERCASE -> {
                return Optional.of(new FloatType());
            }
            case CHARACTER -> {
                return Optional.of(new CharacterType());
            }
            case STRING -> {
                return Optional.of(new StringType());
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    public String getCorrespondingCharacterSet() {
        return type.getCharacterSet();
    }

    public boolean isFloating() {
        return type.equals(SpecifierType.FLOATING)
                || type.equals(SpecifierType.FLOATING_EXP_LOWERCASE)
                || type.equals(SpecifierType.FLOATING_EXP_UPPERCASE);
    }

    public boolean isInteger() {
        return type.equals(SpecifierType.DECIMAL)
                || type.equals(SpecifierType.OCTAL)
                || type.equals(SpecifierType.HEXADECIMAL_LOWERCASE)
                || type.equals(SpecifierType.HEXADECIMAL_UPPERCASE);
    }

    public static FormatSpecifier fromFormatString(String formatString) {

        if (formatString == null || formatString.isEmpty()) {
            throw new IllegalArgumentException("Format string is null or empty");
        }
        if (formatString.length() < 2) {
            throw new IllegalArgumentException("Format string is too short to be a specifier");
        }
        if (!(formatString.startsWith("%") || formatString.startsWith(":"))) {
            throw new IllegalArgumentException("Format specifier does not start with % or :");
        }

        FormatSpecifierBuilder builder = new FormatSpecifierBuilder();

        // Пропуск %/:
        int i = 1;

        // Парсинг флагов: +, 0, *
        boolean parsingFlags = true;
        while (parsingFlags && i < formatString.length()) {
            switch (formatString.charAt(i)) {
                case '+':
                    builder.setPlusFlag();
                    i++;
                    break;
                case '0':
                    builder.setZeroFlag();
                    i++;
                    break;
                case '*':
                    builder.suppressAssignment();
                    i++;
                    break;
                default:
                    parsingFlags = false;
                    break;
            }
        }

        // Парсинг ширины
        int startWidth = i;
        while (i < formatString.length() && Character.isDigit(formatString.charAt(i))) {
            i++;
        }
        if (i > startWidth) {
            builder.setWidth(Integer.parseInt(formatString.substring(startWidth, i)));
        }

        // Парсинг точности
        if (i < formatString.length() && formatString.charAt(i) == '.') {
            int startPrecision = ++i;
            while (i < formatString.length() && Character.isDigit(formatString.charAt(i))) {
                i++;
            }
            if (i > startPrecision) {
                builder.setPrecision(Integer.parseInt(formatString.substring(startPrecision, i)));
            } else {
                builder.setPrecision(0);
            }
        }

        // Определение типа спецификатора
        if (i < formatString.length()) {
            char symbol = formatString.charAt(i++);

            if (symbol == '[') {
                int end = formatString.indexOf(']', i);
                if (end < 0) {
                    throw new IllegalArgumentException("Unclosed scan set in format specifier");
                }
                String scanSet = formatString.substring(i, end);
                if (scanSet.startsWith("^")) {
                    builder.negateScanset();
                    scanSet = scanSet.substring(1);
                }
                builder.setScanSet(scanSet);
                builder.setType(SpecifierType.SCANSET);
                i = end + 1;
            } else {
                builder.setType(symbol);
            }
        } else if (formatString.startsWith(":")) {
            builder.setExpression();
        }

        if (i < formatString.length()) {
            throw new UnsupportedParsingException(String.format("Format specifier %s is not valid", formatString));
        }

        return builder.build();
    }

    public String asString() {
        return    (assignmentIsSuppressed ? "*" : "")
                + (hasPlusFlag ? "+" : "")
                + (hasZeroFlag ? "0" : "")
                + (width != -1 ? width : "")
                + (precision != -1 ? "." + precision : "")
                + (!scanSet.isEmpty() ? ("[" + (scanSetIsNegated ? "^" : "") + scanSet + "]") : "")
                + (type.equals(SpecifierType.SCANSET) || type.equals(SpecifierType.EXPRESSION)
                    ? "" : type.getSymbol())
                ;
    }

    public boolean isExpression() {
        return type.equals(SpecifierType.EXPRESSION);
    }

    public boolean isEmptyExpression() {
        return isExpression()
                && !assignmentIsSuppressed
                && !hasPlusFlag
                && !hasZeroFlag
                && width == -1
                && precision == -1
                && scanSet.isEmpty()
                && !scanSetIsNegated;
    }

    public boolean hasWidth() {
        return width != -1;
    }

    public boolean hasPrecision() {
        return precision != -1;
    }

    public enum SpecifierType {
        DECIMAL('d', "\"0123456789\""),
        OCTAL('o', "\"01234567\""),
        HEXADECIMAL_LOWERCASE('x', "\"0123456789abcdefABCDEF\""),
        HEXADECIMAL_UPPERCASE('X', "\"0123456789abcdefABCDEF\""),
        FLOATING('f', "\"0123456789.eE+-\""),
        FLOATING_EXP_LOWERCASE('e', "\"0123456789.eE+-\""),
        FLOATING_EXP_UPPERCASE('E', "\"0123456789.eE+-\""),
        CHARACTER('c', ""),
        STRING('s', " "),
        SCANSET(']', ""),
        EXPRESSION(' ', "");

        private final char symbol;
        private final String characterSet;

        SpecifierType(char symbol, String characterSet) {
            this.symbol = symbol;
            this.characterSet = characterSet;
        }

        public char getSymbol() {
            return symbol;
        }

        public String getCharacterSet() {
            return characterSet;
        }

        public static Optional<SpecifierType> fromSymbol(char symbol) {
            if (symbol != ' ') {
                for (SpecifierType type : values()) {
                    if (type.symbol == symbol) {
                        return Optional.of(type);
                    }
                }
            }
            return Optional.empty();
        }
    }

    public static class FormatSpecifierBuilder {
        private SpecifierType _type = null;
        private boolean _hasPlusFlag = false;
        private boolean _hasZeroFlag = false;
        private int _width = -1;
        private int _precision = -1;
        private String _scanSet = "";
        private boolean _scanSetIsNegated = false;
        private boolean _assignmentIsSuppressed = false;

        public FormatSpecifierBuilder setType(char type) {
            Optional<SpecifierType> optional = SpecifierType.fromSymbol(type);
            if (optional.isEmpty()) {
                throw new UnsupportedParsingException(String.format("Format specifier type %%%c is not supported", type));
            }
            _type = optional.get();
            return this;
        }

        public FormatSpecifierBuilder setType(SpecifierType type) {
            _type = type;
            return this;
        }

        public FormatSpecifierBuilder setExpression() {
            _type = SpecifierType.EXPRESSION;
            return this;
        }

        public FormatSpecifierBuilder setWidth(int width) {
            if (width < 0) {
                throw new UnsupportedParsingException("Specifier cannot have negative width");
            }
            _width = width;
            return this;
        }

        public FormatSpecifierBuilder setPrecision(int precision) {
            if (precision < 0) {
                throw new UnsupportedParsingException("Specifier cannot have negative precision");
            }
            _precision = precision;
            return this;
        }

        public FormatSpecifierBuilder setScanSet(String scanSet) {
            if (scanSet.isEmpty()) {
                throw new UnsupportedParsingException("Specifier cannot have empty scan set");
            }
            _scanSet = scanSet;
            return this;
        }

        public FormatSpecifierBuilder negateScanset() {
            _scanSetIsNegated = true;
            return this;
        }

        public FormatSpecifierBuilder suppressAssignment() {
            _assignmentIsSuppressed = true;
            return this;
        }

        public FormatSpecifierBuilder setZeroFlag() {
            _hasZeroFlag = true;
            return this;
        }

        public FormatSpecifierBuilder setPlusFlag() {
            _hasPlusFlag = true;
            return this;
        }

        public FormatSpecifier build() {
            Objects.requireNonNull(_type);
            if (_type.equals(SpecifierType.SCANSET) == (_scanSet.isEmpty())) {
                throw new IllegalStateException("Scanning format specifier must have scan set");
            }
            return new FormatSpecifier(_assignmentIsSuppressed, _hasPlusFlag, _hasZeroFlag, _width, _precision, _scanSet, _scanSetIsNegated, _type);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FormatSpecifier nodeInfos = (FormatSpecifier) o;
        return assignmentIsSuppressed == nodeInfos.assignmentIsSuppressed && hasPlusFlag == nodeInfos.hasPlusFlag && hasZeroFlag == nodeInfos.hasZeroFlag && width == nodeInfos.width && precision == nodeInfos.precision && scanSetIsNegated == nodeInfos.scanSetIsNegated && Objects.equals(scanSet, nodeInfos.scanSet) && type == nodeInfos.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), assignmentIsSuppressed, hasPlusFlag, hasZeroFlag, width, precision, scanSet, scanSetIsNegated, type);
    }

    @Override
    public FormatSpecifier clone() {
        return (FormatSpecifier) super.clone();
    }
}

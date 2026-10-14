package org.vstu.meaningtree.languages.support.features;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.expressions.literals.InterpolatedStringLiteral;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;

public class SpecificInputOutputFeature extends SemanticFeature {

    private boolean anyInput = false;
    private boolean multipleValuesInput = false;
    private boolean specifier = false;
    private boolean sepPrint = false;
    private boolean endPrint = false;
    private boolean sepEndPy = false;

    @Override
    public String id() {
        return "input-output";
    }

    @Override
    public boolean matches(Node node, @Nullable FeatureContext context) {
        return
               (anyInput && node instanceof InputCommand)
            || (multipleValuesInput && node instanceof InputCommand input && input.getArguments().size() > 1)
            || (specifier && node instanceof InterpolatedStringLiteral interpolation && interpolation.containsSpecifier())
            || (sepPrint && node instanceof PrintValues printSep && printSep.separator != null)
            || (endPrint && node instanceof PrintValues printEnd && printEnd.end != null && !printEnd.addsNewLine())
            || (sepEndPy && node instanceof PrintValues printPy && (!printPy.addsNewLine() || printPy.separator == null || printPy.separator.getUnescapedValue().equals(" ")))
               ;
    }

    @Override
    public String description(Node node) {
        StringBuilder message = new StringBuilder("Some input/output features are not supported by this language:");
        if (anyInput) {
            message.append("\ninput");
        }
        if (multipleValuesInput) {
            message.append("\nmultiple values in input");
        }
        if (specifier) {
            message.append("\nformat specifier in interpolated string");
        }
        if (sepPrint) {
            message.append("\nprint with separator");
        }
        if (sepPrint) {
            message.append("\nprint with end");
        }
        if (sepEndPy) {
            message.append("\nprint with separator and/or end in Python");
        }
        return message.toString();
    }

    public SpecificInputOutputFeature includeAnyInput() {
        anyInput = true;
        return this;
    }

    public SpecificInputOutputFeature includeMultipleValuesInput() {
        multipleValuesInput = true;
        return this;
    }

    public SpecificInputOutputFeature includeSpecifier() {
        specifier = true;
        return this;
    }

    public SpecificInputOutputFeature includeSepPrint() {
        sepPrint = true;
        return this;
    }

    public SpecificInputOutputFeature includeEndPrint() {
        endPrint = true;
        return this;
    }

    public SpecificInputOutputFeature includeSepEndPy() {
        sepEndPy = true;
        return this;
    }
}

package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectConstructorDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression;
import org.vstu.meaningtree.nodes.expressions.other.CastTypeExpression;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.ChainedAssignmentStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.utils.analysis.types.SimpleTypeInferrer;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.*;
import java.util.function.Supplier;

/** Tree traversal and best-effort callable matching used by {@link TypeConversionAnalyzer}. */
final class TypeConversionSiteAnalyzer {
    private final TypeConversionAnalyzer analyzer;
    private final MeaningTree tree;
    private final ScopeTable scope;
    private final List<FunctionDeclaration> callableDeclarations = new ArrayList<>();
    private final List<TypeConversionCheck> checks = new ArrayList<>();

    TypeConversionSiteAnalyzer(TypeConversionAnalyzer analyzer, MeaningTree tree, ScopeTable scope) {
        this.analyzer = analyzer;
        this.tree = tree;
        this.scope = scope;
        indexCallables();
    }

    TypeConversionReport analyze() {
        for (NodeInfo info : tree) {
            withSiteScope(info, () -> {
                analyzeSite(info);
                return null;
            });
        }
        return new TypeConversionReport(checks);
    }

    private void indexCallables() {
        Set<FunctionDeclaration> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NodeInfo info : tree) {
            if (info.node() instanceof FunctionDeclaration declaration && seen.add(declaration)) {
                callableDeclarations.add(declaration);
            }
        }
    }

    private void analyzeSite(NodeInfo info) {
        switch (info.node()) {
            case CastTypeExpression cast -> addCheck(
                    cast,
                    ConversionSiteKind.CAST,
                    infer(cast.getValue()),
                    cast.getCastType(),
                    ConversionKind.EXPLICIT);
            case VariableDeclarator declarator -> analyzeVariableInitializer(info, declarator);
            case DeclarationArgument argument -> analyzeDefaultArgument(argument);
            case AssignmentStatement assignment -> addCheck(
                    assignment,
                    ConversionSiteKind.ASSIGNMENT,
                    knownOrInfer(assignment.getRealType(), assignment.getRValue()),
                    infer(assignment.getLValue()),
                    ConversionKind.IMPLICIT);
            case AssignmentExpression assignment -> addCheck(
                    assignment,
                    ConversionSiteKind.ASSIGNMENT,
                    knownOrInfer(assignment.getRealType(), assignment.getRValue()),
                    infer(assignment.getLValue()),
                    ConversionKind.IMPLICIT);
            case ChainedAssignmentStatement assignment -> analyzeChainedAssignment(assignment);
            case ConstructorCall call -> analyzeCall(call, call.getArguments());
            case MethodCall call -> analyzeCall(call, call.getArguments());
            case FunctionCall call -> analyzeCall(call, call.getArguments());
            case ReturnStatement returnStatement -> analyzeReturn(info, returnStatement);
            default -> {
                // This node is not a value-transfer boundary.
            }
        }
    }

    private void analyzeVariableInitializer(NodeInfo info, VariableDeclarator declarator) {
        if (!declarator.hasInitialization()
                || info.parentNode() == null
                || !(info.parentNode() instanceof VariableDeclaration declaration)) {
            return;
        }
        addCheck(
                declarator,
                ConversionSiteKind.INITIALIZER,
                knownOrInfer(declarator.getRealType(), declarator.getRValue()),
                declaration.getType(),
                ConversionKind.IMPLICIT);
    }

    private void analyzeDefaultArgument(DeclarationArgument argument) {
        if (!argument.hasInitialExpression()) {
            return;
        }
        addCheck(
                argument,
                ConversionSiteKind.INITIALIZER,
                infer(argument.getInitialExpression()),
                argument.getType(),
                ConversionKind.IMPLICIT);
    }

    private void analyzeChainedAssignment(ChainedAssignmentStatement assignment) {
        Type source = knownOrInfer(assignment.getRealType(), assignment.getValue());
        for (int index = 0; index < assignment.getTargets().size(); index++) {
            addCheck(
                    assignment,
                    ConversionSiteKind.ASSIGNMENT,
                    index,
                    source,
                    infer(assignment.getTargets().get(index)),
                    ConversionKind.IMPLICIT);
        }
    }

    private void analyzeCall(Node call, List<Expression> arguments) {
        List<Type> targets = resolveCallTargets(call, arguments);
        for (int index = 0; index < arguments.size(); index++) {
            Expression value = argumentValue(arguments.get(index));
            Type target = targets == null ? new UnknownType() : targets.get(index);
            addCheck(
                    call,
                    ConversionSiteKind.ARGUMENT,
                    index,
                    infer(value),
                    target,
                    ConversionKind.IMPLICIT);
        }
    }

    private void analyzeReturn(NodeInfo info, ReturnStatement returnStatement) {
        if (returnStatement.getExpression() == null) {
            return;
        }
        FunctionDefinition function = nearestParent(info, FunctionDefinition.class);
        Type target = function == null
                ? new UnknownType()
                : function.getDeclaration().getReturnType();
        Type source = infer(returnStatement.getExpression());
        if (source instanceof UnknownType && function != null
                && returnStatement.getExpression() instanceof SimpleIdentifier identifier) {
            source = function.getDeclaration().getArguments().stream()
                    .filter(argument -> argument.getName().equals(identifier))
                    .map(DeclarationArgument::getType)
                    .findFirst()
                    .orElse(source);
        }
        addCheck(
                returnStatement,
                ConversionSiteKind.RETURN,
                source,
                target,
                ConversionKind.IMPLICIT);
    }

    private List<Type> resolveCallTargets(Node call, List<Expression> arguments) {
        List<List<Type>> matches = new ArrayList<>();
        for (FunctionDeclaration declaration : callableDeclarations) {
            if (!matchesCall(call, declaration)) {
                continue;
            }
            List<Type> mapping = mapArguments(arguments, declaration.getArguments());
            if (mapping != null) {
                matches.add(mapping);
            }
        }
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private boolean matchesCall(Node call, FunctionDeclaration declaration) {
        if (call instanceof ConstructorCall constructorCall) {
            return declaration instanceof ObjectConstructorDeclaration constructor
                    && constructor.getOwner() != null
                    && constructor.getOwner().equals(constructorCall.getOwner());
        }
        if (call instanceof MethodCall methodCall) {
            if (!(declaration instanceof MethodDeclaration method)
                    || !method.getName().equals(methodCall.getFunctionName())) {
                return false;
            }
            Type receiverType = infer(methodCall.getObject());
            return receiverType instanceof UserType && method.getOwner() != null
                    && method.getOwner().equals(receiverType);
        }
        if (call instanceof FunctionCall functionCall) {
            if (!functionCall.hasFunctionName()
                    || !declaration.getName().equals(functionCall.getFunctionName())) {
                return false;
            }
            if (!(declaration instanceof MethodDeclaration method)) {
                return true;
            }
            NodeInfo callInfo = tree.getNodeById(call.getId());
            ClassDefinition enclosingClass = callInfo == null
                    ? null
                    : nearestParent(callInfo, ClassDefinition.class);
            return enclosingClass != null
                    && method.getOwner() != null
                    && method.getOwner().equals(enclosingClass.getDeclaration().getTypeNode());
        }
        return false;
    }

    private List<Type> mapArguments(List<Expression> arguments, List<DeclarationArgument> parameters) {
        List<Type> mapped = new ArrayList<>(arguments.size());
        Set<Integer> usedParameters = new HashSet<>();
        int nextPositional = 0;

        for (Expression argument : arguments) {
            if (argument instanceof DefinitionArgument definitionArgument
                    && (definitionArgument.isListUnpacking() || definitionArgument.isDictUnpacking())) {
                return null;
            }

            int parameterIndex;
            if (argument instanceof DefinitionArgument definitionArgument
                    && definitionArgument.hasVisibleName()) {
                parameterIndex = findNamedParameter(parameters, definitionArgument);
            } else {
                while (nextPositional < parameters.size() && usedParameters.contains(nextPositional)) {
                    nextPositional++;
                }
                parameterIndex = nextPositional;
                if (parameterIndex < parameters.size()
                        && !parameters.get(parameterIndex).isListUnpacking()) {
                    nextPositional++;
                }
            }

            if (parameterIndex < 0 || parameterIndex >= parameters.size()) {
                return null;
            }
            DeclarationArgument parameter = parameters.get(parameterIndex);
            if (parameter.isDictUnpacking()
                    || argument instanceof DefinitionArgument && parameter.isListUnpacking()) {
                return null;
            }
            if (!parameter.isListUnpacking() && !usedParameters.add(parameterIndex)) {
                return null;
            }
            mapped.add(parameter.isListUnpacking() ? parameter.getElementType() : parameter.getType());
        }

        for (int index = 0; index < parameters.size(); index++) {
            DeclarationArgument parameter = parameters.get(index);
            if (!usedParameters.contains(index)
                    && !parameter.hasInitialExpression()
                    && !parameter.isListUnpacking()
                    && !parameter.isDictUnpacking()) {
                return null;
            }
        }
        return mapped;
    }

    private int findNamedParameter(List<DeclarationArgument> parameters, DefinitionArgument argument) {
        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index).getName().equals(argument.getName())) {
                return index;
            }
        }
        return -1;
    }

    private Expression argumentValue(Expression argument) {
        return argument instanceof DefinitionArgument definitionArgument
                ? definitionArgument.getInitialExpression()
                : argument;
    }

    private Type knownOrInfer(Type computedType, Expression expression) {
        return computedType instanceof UnknownType ? infer(expression) : computedType;
    }

    private Type infer(Expression expression) {
        return expression == null ? new UnknownType() : SimpleTypeInferrer.inference(expression, scope);
    }

    private void addCheck(
            Node node,
            ConversionSiteKind siteKind,
            Type source,
            Type target,
            ConversionKind kind) {
        checks.add(new TypeConversionCheck(
                node,
                siteKind,
                source,
                target,
                kind,
                analyzer.isCompatible(source, target, kind, scope)));
    }

    private void addCheck(
            Node node,
            ConversionSiteKind siteKind,
            int valueIndex,
            Type source,
            Type target,
            ConversionKind kind) {
        checks.add(new TypeConversionCheck(
                node,
                siteKind,
                OptionalInt.of(valueIndex),
                source,
                target,
                kind,
                analyzer.isCompatible(source, target, kind, scope)));
    }

    private <T> T withSiteScope(NodeInfo info, Supplier<T> action) {
        long previousScopeId = scope.currentScopeId();
        Long siteScopeId = nearestScopeId(info);
        if (siteScopeId != null && scope.findScope(siteScopeId).isPresent()) {
            scope.setCurrentScope(siteScopeId);
        }
        try {
            return action.get();
        } finally {
            if (scope.findScope(previousScopeId).isPresent()) {
                scope.setCurrentScope(previousScopeId);
            }
        }
    }

    private Long nearestScopeId(NodeInfo info) {
        for (NodeInfo current = info; current != null; current = current.parent()) {
            if (current.node() instanceof CompoundStatement compound
                    && compound.getScopeId().isPresent()) {
                return compound.getScopeId().getAsLong();
            }
        }
        return null;
    }

    private <T extends Node> T nearestParent(NodeInfo info, Class<T> type) {
        for (NodeInfo current = info.parent(); current != null; current = current.parent()) {
            if (type.isInstance(current.node())) {
                return type.cast(current.node());
            }
        }
        return null;
    }
}

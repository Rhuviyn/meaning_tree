package org.vstu.meaningtree.utils.analysis.symbols;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectConstructorDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.interfaces.Callable;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.utils.analysis.types.SimpleTypeInferrer;
import org.vstu.meaningtree.utils.analysis.types.conversion.ConversionKind;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionAnalyzer;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.*;

/**
 * Связывает места вызова с декларациями, которые они вызывают.
 *
 * <p>Ссылка ставится только тогда, когда подходящий кандидат ровно один. Ни один вызов не
 * разрешается порядком объявления: если после отбора кандидатов осталось несколько, вызов
 * остаётся несвязанным. Молчаливый выбор «первого попавшегося» был бы хуже отсутствия ответа —
 * потребитель не смог бы отличить достоверную связь от догадки.</p>
 *
 * <p>Класс — единственная реализация отбора кандидатов в проекте. Ей пользуются оба
 * потребителя: постобработка, проставляющая {@link Callable#getResolvedDeclaration()} на всё
 * дерево, и анализ преобразований типов, которому нужен выбранный кандидат для отдельного
 * места вызова. Вторая точка входа существует, чтобы анализ преобразований не зависел от того,
 * выполнялась ли постобработка: {@code TypeConversionAnalyzer} вызывается и напрямую.</p>
 */
public final class OverloadCallResolver {
    private final MeaningTree tree;
    private final ScopeTable scope;
    private final TypeConversionAnalyzer conversions;
    private final List<FunctionDeclaration> callableDeclarations = new ArrayList<>();

    /**
     * @param conversions правила преобразований исходного языка; по ним отбираются кандидаты,
     *                    которым аргумент подходит без явного приведения
     */
    public OverloadCallResolver(@NotNull MeaningTree tree,
                                @NotNull ScopeTable scope,
                                @NotNull TypeConversionAnalyzer conversions) {
        this.tree = Objects.requireNonNull(tree);
        this.scope = Objects.requireNonNull(scope);
        this.conversions = Objects.requireNonNull(conversions);
        indexCallables();
    }

    /** Проставляет ссылку на декларацию всем вызовам дерева, которые удалось разрешить. */
    public void resolveAll() {
        for (NodeInfo info : tree) {
            if (!(info.node() instanceof Callable call)) {
                continue;
            }
            withSiteScope(info, () -> {
                call.setResolvedDeclaration(resolveCall(info).orElse(null));
                return null;
            });
        }
    }

    /**
     * Декларация, которую вызывает место вызова.
     * <p>
     * Область видимости на момент вызова метода должна соответствовать месту вызова: отбор
     * опирается на видимые имена и на тип получателя. {@link #resolveAll()} переключает её сам,
     * внешний потребитель обязан сделать это до вызова.
     *
     * @return пусто, если подходящего кандидата нет или их несколько
     */
    public Optional<FunctionDeclaration> resolveCall(@NotNull NodeInfo info) {
        if (!(info.node() instanceof Callable call)) {
            return Optional.empty();
        }

        List<FunctionDeclaration> applicable = new ArrayList<>();
        for (FunctionDeclaration declaration : callableDeclarations) {
            if (!matchesCall(info, declaration)) {
                continue;
            }
            if (mapArguments(call.getArguments(), declaration.getArguments()) != null) {
                applicable.add(declaration);
            }
        }
        return narrow(call, applicable);
    }

    /**
     * Сужает кандидатов до одного по типам аргументов.
     * <p>
     * Сначала точное совпадение типов, затем допустимые неявные преобразования — так работает
     * разрешение перегрузок в Java и C++: точное совпадение выигрывает у преобразования, даже
     * если подходят оба.
     * <p>
     * Сужение по типам применяется только тогда, когда кандидатов больше одного. Иначе
     * единственный кандидат, у которого не удалось вывести тип аргумента, был бы отвергнут, и
     * заведомо верная связь потерялась бы из-за неполноты вывода типов.
     */
    private Optional<FunctionDeclaration> narrow(Callable call, List<FunctionDeclaration> applicable) {
        if (applicable.size() <= 1) {
            return applicable.isEmpty() ? Optional.empty() : Optional.of(applicable.getFirst());
        }

        List<Type> argumentTypes = call.getArguments().stream().map(this::argumentType).toList();

        List<FunctionDeclaration> exact = applicable.stream()
                .filter(declaration -> allArgumentsMatch(call, argumentTypes, declaration, Type::equals))
                .toList();
        if (exact.size() == 1) {
            return Optional.of(exact.getFirst());
        }

        List<FunctionDeclaration> convertible = applicable.stream()
                .filter(declaration -> allArgumentsMatch(
                        call, argumentTypes, declaration, this::isImplicitlyCompatible))
                .toList();
        return convertible.size() == 1 ? Optional.of(convertible.getFirst()) : Optional.empty();
    }

    private boolean allArgumentsMatch(Callable call,
                                      List<Type> argumentTypes,
                                      FunctionDeclaration declaration,
                                      java.util.function.BiPredicate<Type, Type> accepts) {
        List<Type> targets = mapArguments(call.getArguments(), declaration.getArguments());
        if (targets == null || targets.size() != argumentTypes.size()) {
            return false;
        }
        for (int index = 0; index < targets.size(); index++) {
            if (!accepts.test(argumentTypes.get(index), targets.get(index))) {
                return false;
            }
        }
        return true;
    }

    /** Тип фактического аргумента; именованный аргумент несёт значение внутри себя. */
    private boolean isImplicitlyCompatible(Type source, Type target) {
        return conversions.isCompatible(source, target, ConversionKind.IMPLICIT, scope);
    }

    private Type argumentType(Expression argument) {
        Expression value = argument instanceof DefinitionArgument definitionArgument
                ? definitionArgument.getInitialExpression()
                : argument;
        return infer(value);
    }

    /** Типы параметров выбранной декларации в порядке аргументов вызова. */
    @Nullable
    public List<Type> resolveArgumentTargets(@NotNull NodeInfo info) {
        if (!(info.node() instanceof Callable call)) {
            return null;
        }
        return resolveCall(info)
                .map(declaration -> mapArguments(call.getArguments(), declaration.getArguments()))
                .orElse(null);
    }

    private void indexCallables() {
        Set<FunctionDeclaration> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (NodeInfo info : tree) {
            if (info.node() instanceof FunctionDeclaration declaration && seen.add(declaration)) {
                callableDeclarations.add(declaration);
            }
        }
    }

    /** Может ли объявление вообще относиться к этому месту вызова, без учёта аргументов. */
    private boolean matchesCall(NodeInfo info, FunctionDeclaration declaration) {
        Node call = info.node();
        if (call instanceof ConstructorCall constructorCall) {
            return declaration instanceof ObjectConstructorDeclaration constructor
                    && constructor.getOwner() != null
                    && constructor.getOwner().equals(constructorCall.getOwner());
        }
        if (call instanceof ObjectNewExpression newExpression) {
            return declaration instanceof ObjectConstructorDeclaration constructor
                    && constructor.getOwner() != null
                    && constructor.getOwner().equals(newExpression.getType());
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
            // Вызов без получателя внутри класса — обращение к собственному методу, поэтому
            // кандидатами становятся только методы охватывающего класса.
            ClassDefinition enclosingClass = nearestParent(info, ClassDefinition.class);
            return enclosingClass != null
                    && method.getOwner() != null
                    && method.getOwner().equals(enclosingClass.getDeclaration().getTypeNode());
        }
        return false;
    }

    private Type infer(Expression expression) {
        return expression == null ? new UnknownType() : SimpleTypeInferrer.inference(expression, scope);
    }

    private <T> T withSiteScope(NodeInfo info, java.util.function.Supplier<T> action) {
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
            if (current.node() instanceof CompoundStatement compound && compound.getScopeId().isPresent()) {
                return compound.getScopeId().getAsLong();
            }
        }
        return null;
    }

    private static <T extends Node> T nearestParent(NodeInfo info, Class<T> type) {
        for (NodeInfo current = info.parent(); current != null; current = current.parent()) {
            if (type.isInstance(current.node())) {
                return type.cast(current.node());
            }
        }
        return null;
    }

    /**
     * Сопоставляет аргументы вызова параметрам объявления с учётом позиционных, именованных,
     * умолчательных и variadic-параметров.
     *
     * @return типы параметров в порядке аргументов вызова либо {@code null}, если вызов этому
     *         объявлению не подходит: не хватает обязательного параметра, имя не найдено,
     *         параметр занят дважды или в вызове есть распаковка
     */
    @Nullable
    private static List<Type> mapArguments(@NotNull List<Expression> arguments,
                                          @NotNull List<DeclarationArgument> parameters) {
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

    private static int findNamedParameter(@NotNull List<DeclarationArgument> parameters,
                                          @NotNull DefinitionArgument argument) {
        for (int index = 0; index < parameters.size(); index++) {
            if (parameters.get(index).getName().equals(argument.getName())) {
                return index;
            }
        }
        return -1;
    }
}

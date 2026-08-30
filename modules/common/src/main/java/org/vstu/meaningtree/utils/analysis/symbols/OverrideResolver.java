package org.vstu.meaningtree.utils.analysis.symbols;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectConstructorDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectDestructorDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.*;

/**
 * Заполняет связь «метод переопределяет метод предка» ({@link MethodDeclaration#getOverriddenFrom()})
 * после парсинга, для которого необходимы полностью построенные {@link MeaningTree} и лексическая
 * {@link ScopeTable}.
 *
 * <p>Это консервативная эвристика, а не полное связывание имён: она находит ближайшего предка
 * (по классу или интерфейсу, транзитивно), объявляющего метод с совпадающей сигнатурой, только
 * если этот предок присутствует в том же фрагменте кода. Если предок вне фрагмента или сигнатура
 * не совпала, поле остаётся {@code null} — это штатное состояние.</p>
 *
 * <p>Конструкторы, деструкторы и статические методы в связывание не включаются: у них нет
 * содержательного понятия переопределения в терминах MeaningTree.</p>
 */
public final class OverrideResolver {
    private final MeaningTree meaningTree;
    private final ScopeTable scopeTable;

    public OverrideResolver(@NotNull MeaningTree meaningTree, @NotNull ScopeTable scopeTable) {
        this.meaningTree = Objects.requireNonNull(meaningTree);
        this.scopeTable = Objects.requireNonNull(scopeTable);
    }

    /** Запускает разрешение переопределений методов для всего дерева. */
    public void resolve() {
        Map<UserType, ClassDefinition> classesInFragment = findClassDefinitionsByType();
        for (ClassDefinition classDefinition : classesInFragment.values()) {
            for (MethodDeclaration method : findResolvableMethods(classDefinition)) {
                MethodDeclaration ancestorMethod = findOverriddenAncestorMethod(
                        classDefinition, method, classesInFragment
                );
                if (ancestorMethod != null) {
                    method.setOverriddenFrom(ancestorMethod);
                }
            }
        }
    }

    private Map<UserType, ClassDefinition> findClassDefinitionsByType() {
        Map<UserType, ClassDefinition> result = new LinkedHashMap<>();
        for (NodeInfo nodeInfo : meaningTree) {
            if (nodeInfo.node() instanceof ClassDefinition classDefinition) {
                result.put(classDefinition.getDeclaration().getTypeNode(), classDefinition);
            }
        }
        return result;
    }

    private List<MethodDeclaration> findResolvableMethods(ClassDefinition classDefinition) {
        return classDefinition.getAllNodes().stream()
                .map(node -> switch (node) {
                    case MethodDeclaration declaration -> declaration;
                    case MethodDefinition definition -> definition.getDeclaration();
                    default -> null;
                })
                .filter(Objects::nonNull)
                .filter(this::isResolvable)
                .toList();
    }

    private boolean isResolvable(MethodDeclaration method) {
        return !(method instanceof ObjectConstructorDeclaration)
                && !(method instanceof ObjectDestructorDeclaration)
                && !method.getModifiers().contains(DeclarationModifier.STATIC);
    }

    private MethodDeclaration findOverriddenAncestorMethod(ClassDefinition owner,
                                                           MethodDeclaration method,
                                                           Map<UserType, ClassDefinition> classesInFragment) {
        Deque<UserType> queue = new ArrayDeque<>();
        Set<UserType> visited = new HashSet<>();
        // Класс не может быть предком самому себе — в корректном дереве такого не бывает, но
        // цикл в иерархии (испорченный/некорректно построенный AST) не должен заставить BFS
        // сравнить метод сам с собой.
        visited.add(owner.getDeclaration().getTypeNode());
        enqueueParents(owner.getDeclaration().getParents(), queue, visited);

        while (!queue.isEmpty()) {
            UserType ancestorType = queue.poll();
            ClassDefinition ancestor = resolveClassDefinition(ancestorType, classesInFragment);
            if (ancestor == null) {
                continue;
            }

            MethodDeclaration match = findMatchingMethod(ancestor, method);
            if (match != null) {
                return match;
            }

            enqueueParents(ancestor.getDeclaration().getParents(), queue, visited);
        }
        return null;
    }

    private void enqueueParents(List<Type> parents, Deque<UserType> queue, Set<UserType> visited) {
        for (Type parent : parents) {
            if (parent instanceof UserType userType && visited.add(userType)) {
                queue.add(userType);
            }
        }
    }

    private ClassDefinition resolveClassDefinition(UserType type, Map<UserType, ClassDefinition> classesInFragment) {
        ClassDefinition fromFragment = classesInFragment.get(type);
        if (fromFragment != null) {
            return fromFragment;
        }
        return scopeTable.findTypeDeclaration(type)
                .flatMap(scopeTable::findDefinition)
                .filter(ClassDefinition.class::isInstance)
                .map(ClassDefinition.class::cast)
                .orElse(null);
    }

    private MethodDeclaration findMatchingMethod(ClassDefinition ancestor, MethodDeclaration method) {
        for (MethodDeclaration candidate : findResolvableMethods(ancestor)) {
            if (signaturesMatch(method, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean signaturesMatch(MethodDeclaration method, MethodDeclaration candidate) {
        if (!method.getName().equals(candidate.getName())) {
            return false;
        }
        List<DeclarationArgument> methodArgs = method.getArguments();
        List<DeclarationArgument> candidateArgs = candidate.getArguments();
        if (methodArgs.size() != candidateArgs.size()) {
            return false;
        }
        for (int i = 0; i < methodArgs.size(); i++) {
            if (!argumentTypesMatch(methodArgs.get(i).getType(), candidateArgs.get(i).getType())) {
                return false;
            }
        }
        return true;
    }

    private boolean argumentTypesMatch(Type a, Type b) {
        return a instanceof UnknownType || b instanceof UnknownType || Objects.equals(a, b);
    }
}

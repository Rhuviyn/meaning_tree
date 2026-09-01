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
                // Присваивается всегда, в том числе пустым списком: если после правки дерева
                // совпадения больше нет, старая связь обязана исчезнуть, а не пережить проход.
                method.setOverriddenFrom(findOverriddenAncestorMethods(
                        classDefinition, method, classesInFragment
                ));
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

    /**
     * Методы предков, которые этот метод переопределяет.
     * <p>
     * Обход идёт по уровням иерархии, а не общей очередью: выигрывает ближайший предок, но если
     * на одном расстоянии сигнатуру объявляют несколько предков — метод реализует одинаковую
     * сигнатуру двух интерфейсов, или в C++ её объявляют две прямые базы, — возвращаются все.
     * Прежний обход отдавал первого встреченного, то есть выдавал за факт порядок объявления
     * родителей.
     *
     * @return пустой список, если совпадений нет
     */
    private List<MethodDeclaration> findOverriddenAncestorMethods(ClassDefinition owner,
                                                                  MethodDeclaration method,
                                                                  Map<UserType, ClassDefinition> classesInFragment) {
        Set<UserType> visited = new HashSet<>();
        // Класс не может быть предком самому себе — в корректном дереве такого не бывает, но
        // цикл в иерархии (испорченный/некорректно построенный AST) не должен заставить обход
        // сравнить метод сам с собой.
        visited.add(owner.getDeclaration().getTypeNode());
        List<UserType> level = nextLevel(owner.getDeclaration().getParents(), visited);

        while (!level.isEmpty()) {
            List<MethodDeclaration> matches = new ArrayList<>();
            List<Type> nextParents = new ArrayList<>();
            for (UserType ancestorType : level) {
                ClassDefinition ancestor = resolveClassDefinition(ancestorType, classesInFragment);
                if (ancestor == null) {
                    continue;
                }
                matches.addAll(findMatchingMethods(ancestor, method));
                nextParents.addAll(ancestor.getDeclaration().getParents());
            }
            if (!matches.isEmpty()) {
                return matches;
            }
            level = nextLevel(nextParents, visited);
        }
        return List.of();
    }

    private List<UserType> nextLevel(List<Type> parents, Set<UserType> visited) {
        List<UserType> level = new ArrayList<>();
        for (Type parent : parents) {
            if (parent instanceof UserType userType && visited.add(userType)) {
                level.add(userType);
            }
        }
        return level;
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

    /**
     * Одноимённые методы предка с подходящей сигнатурой.
     * <p>
     * Совпадение по невыведенному типу используется только как запасной вариант: если хоть один
     * кандидат совпал точно, приблизительные отбрасываются. Иначе перегруженное имя связывалось
     * бы с произвольной перегрузкой предка — {@link UnknownType} совпадает с любым типом, и при
     * нескольких перегрузках это не осторожность, а выбор наугад.
     */
    private List<MethodDeclaration> findMatchingMethods(ClassDefinition ancestor, MethodDeclaration method) {
        List<MethodDeclaration> exact = new ArrayList<>();
        List<MethodDeclaration> approximate = new ArrayList<>();
        for (MethodDeclaration candidate : findResolvableMethods(ancestor)) {
            switch (signatureMatch(method, candidate)) {
                case EXACT -> exact.add(candidate);
                case APPROXIMATE -> approximate.add(candidate);
                case NONE -> { }
            }
        }
        return exact.isEmpty() ? approximate : exact;
    }

    /** Насколько уверенно сигнатуры совпали. */
    private enum SignatureMatch { EXACT, APPROXIMATE, NONE }

    private SignatureMatch signatureMatch(MethodDeclaration method, MethodDeclaration candidate) {
        if (!method.getName().equals(candidate.getName())) {
            return SignatureMatch.NONE;
        }
        List<DeclarationArgument> methodArgs = method.getArguments();
        List<DeclarationArgument> candidateArgs = candidate.getArguments();
        if (methodArgs.size() != candidateArgs.size()) {
            return SignatureMatch.NONE;
        }
        boolean approximate = false;
        for (int i = 0; i < methodArgs.size(); i++) {
            Type a = methodArgs.get(i).getType();
            Type b = candidateArgs.get(i).getType();
            if (a instanceof UnknownType || b instanceof UnknownType) {
                approximate = true;
            } else if (!Objects.equals(a, b)) {
                return SignatureMatch.NONE;
            }
        }
        return approximate ? SignatureMatch.APPROXIMATE : SignatureMatch.EXACT;
    }
}

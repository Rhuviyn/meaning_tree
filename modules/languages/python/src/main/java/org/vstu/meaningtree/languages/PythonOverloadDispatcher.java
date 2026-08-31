package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.utils.Tab;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectConstructorDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectDestructorDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.types.TupleType;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;

import java.util.*;

/**
 * Вывод группы перегрузок одним Python-определением с разбором аргументов во время исполнения.
 *
 * <p>В Python нет перегрузок: второй {@code def} того же имени связывает имя заново. Поэтому
 * группа одноимённых определений, пришедшая из Java или C++, не может быть выведена как есть —
 * до вызова доживёт только последнее определение. Вместо этого выводится один
 * {@code def f(*args)}, который разбирает аргументы сам.</p>
 *
 * <p>Ветки различаются сначала числом аргументов и только потом типами. Порядок важен:
 * представления типов в Python грубее, чем в исходном языке ({@code int} и {@code long} — один
 * {@code int}), поэтому там, где перегрузки отличаются числом аргументов, проверять типы не
 * нужно вовсе, и группа выводится даже при неразличимых типах.</p>
 *
 * <p>Если же неразличимые типы встречаются при одинаковом числе аргументов, выбрать перегрузку
 * во время исполнения нельзя, и вывод прекращается {@link UnsupportedViewingException}. Выдать
 * код, который молча вызывает не ту перегрузку, хуже отказа: ошибка проявится не здесь.</p>
 */
final class PythonOverloadDispatcher {
    /** Имя параметра-кортежа, в который собираются все фактические аргументы. */
    private static final String ARGS = "args";

    private final PythonViewer viewer;

    PythonOverloadDispatcher(@NotNull PythonViewer viewer) {
        this.viewer = viewer;
    }

    /**
     * Какие узлы тела выводятся диспетчером, а какие подавляются.
     *
     * @param dispatchers id первого определения группы — все определения группы по порядку
     * @param suppressed  id определений, которые уже вошли в диспетчер и сами не выводятся
     */
    record Plan(@NotNull Map<Long, List<Definition>> dispatchers, @NotNull Set<Long> suppressed) {
        boolean isEmpty() {
            return dispatchers.isEmpty();
        }
    }

    /**
     * Ищет в теле группы одноимённых определений.
     * <p>
     * Группировка локальна для тела: этого достаточно, потому что перегрузки одного имени в
     * Java и C++ объявлены рядом — в одном классе или в одной единице трансляции, — а тело
     * класса и есть то, что рендерится одним вызовом.
     */
    /** Пустой план: ни одно определение не подавляется и диспетчеров не строится. */
    static Plan none() {
        return new Plan(Map.of(), Set.of());
    }

    static Plan plan(@NotNull List<Node> nodes) {
        Map<String, List<Definition>> byName = new LinkedHashMap<>();
        for (Node node : nodes) {
            if (node instanceof Definition definition && isOverloadable(definition)) {
                byName.computeIfAbsent(dispatchName(definition), key -> new ArrayList<>()).add(definition);
            }
        }

        Map<Long, List<Definition>> dispatchers = new LinkedHashMap<>();
        Set<Long> suppressed = new HashSet<>();
        for (List<Definition> group : byName.values()) {
            if (group.size() < 2) {
                continue;
            }
            dispatchers.put(group.getFirst().getId(), group);
            for (int index = 1; index < group.size(); index++) {
                suppressed.add(group.get(index).getId());
            }
        }
        return new Plan(dispatchers, suppressed);
    }

    /** Объявление определения; в группу попадают только callable-определения. */
    private static FunctionDeclaration declarationOf(Definition definition) {
        return (FunctionDeclaration) definition.getDeclaration();
    }

    private static boolean isOverloadable(Definition definition) {
        // Деструктор перегружаться не может, а его Python-имя (__del__) единственное.
        return definition instanceof FunctionDefinition
                && !(definition.getDeclaration() instanceof ObjectDestructorDeclaration);
    }

    /** Имя, под которым определение будет выведено в Python: конструктор всегда {@code __init__}. */
    private static String dispatchName(Definition definition) {
        return definition.getDeclaration() instanceof ObjectConstructorDeclaration
                ? "__init__"
                : declarationOf(definition).getName().toString();
    }

    /** Одна ветка диспетчера: конкретное число аргументов и связанная с ним перегрузка. */
    private record Branch(Definition definition,
                          List<DeclarationArgument> parameters,
                          int arity,
                          boolean unbounded,
                          List<String> typeChecks) {
        /** Может ли ветка сработать на том же числе аргументов, что и другая. */
        boolean overlaps(Branch other) {
            return (unbounded ? arity <= other.arity || other.unbounded : false)
                    || (other.unbounded ? other.arity <= arity : arity == other.arity);
        }
    }

    @NotNull
    String render(@NotNull List<Definition> group, @NotNull Tab tab) {
        List<Branch> branches = buildBranches(group);
        Map<Branch, List<String>> conditions = resolveConditions(branches);

        StringBuilder builder = new StringBuilder();
        builder.append(header(group, tab));

        Tab branchTab = tab.up();
        Tab bodyTab = branchTab.up();
        boolean first = true;
        for (Branch branch : branches) {
            builder.append(branchTab)
                    .append(first ? "if " : "elif ")
                    .append(String.join(" and ", conditions.get(branch)))
                    .append(":\n");
            first = false;

            for (String binding : bindings(branch, bodyTab)) {
                builder.append(binding).append("\n");
            }
            builder.append(viewer.toString(((FunctionDefinition) branch.definition()).getBody(), branchTab));
            builder.append("\n");
        }

        builder.append(branchTab)
                .append("raise TypeError(\"no overload matches the given arguments\")\n");
        return builder.toString();
    }

    /* ------------------------------------------------------------------
     |  Заголовок
     ------------------------------------------------------------------ */

    private String header(List<Definition> group, Tab tab) {
        FunctionDeclaration first = (FunctionDeclaration) group.getFirst().getDeclaration();

        boolean anyStatic = false;
        boolean anyInstance = false;
        for (Definition definition : group) {
            if (definition.getDeclaration().getModifiers().contains(DeclarationModifier.STATIC)) {
                anyStatic = true;
            } else {
                anyInstance = true;
            }
        }
        if (anyStatic && anyInstance) {
            throw new UnsupportedViewingException(
                    "Overloads of '%s' mix static and instance methods: a single Python function cannot be both"
                            .formatted(dispatchName(group.getFirst())));
        }

        StringBuilder builder = new StringBuilder();
        if (anyStatic && first instanceof MethodDeclaration) {
            builder.append("@staticmethod\n").append(tab);
        }

        builder.append("def ").append(dispatchName(group.getFirst())).append("(");
        if (first instanceof MethodDeclaration && !anyStatic) {
            builder.append("self, ");
        }
        builder.append("*").append(ARGS).append("):\n");
        return builder.toString();
    }

    /* ------------------------------------------------------------------
     |  Ветки
     ------------------------------------------------------------------ */

    private List<Branch> buildBranches(List<Definition> group) {
        List<Branch> branches = new ArrayList<>();
        for (Definition definition : group) {
            List<DeclarationArgument> parameters = declarationOf(definition).getArguments();
            int variadicIndex = variadicIndex(definition, parameters);

            if (variadicIndex >= 0) {
                branches.add(branch(definition, parameters, variadicIndex, true));
                continue;
            }

            // Параметр со значением по умолчанию даёт отдельное допустимое число аргументов:
            // ветка на каждое из них проще и точнее, чем одна ветка с диапазоном.
            int required = requiredCount(parameters);
            for (int arity = required; arity <= parameters.size(); arity++) {
                branches.add(branch(definition, parameters, arity, false));
            }
        }

        // Более специфичные ветки должны проверяться раньше: bool в Python — подкласс int,
        // поэтому isinstance(x, int) истинно и для True, и ветка int перехватила бы bool.
        branches.sort(Comparator.comparingInt(PythonOverloadDispatcher::specificityPenalty));
        return branches;
    }

    private Branch branch(Definition definition, List<DeclarationArgument> parameters, int arity, boolean unbounded) {
        List<String> checks = new ArrayList<>();
        for (int index = 0; index < arity; index++) {
            checks.add(pythonTypeName(parameters.get(index).getType()));
        }
        return new Branch(definition, parameters, arity, unbounded, checks);
    }

    private static int specificityPenalty(Branch branch) {
        int penalty = 0;
        for (String check : branch.typeChecks()) {
            if ("int".equals(check)) {
                penalty++;
            }
        }
        return penalty;
    }

    private int variadicIndex(Definition definition, List<DeclarationArgument> parameters) {
        int variadicIndex = -1;
        for (int index = 0; index < parameters.size(); index++) {
            DeclarationArgument parameter = parameters.get(index);
            if (parameter.isDictUnpacking()) {
                throw new UnsupportedViewingException(
                        "Overloaded '%s' takes keyword arguments: the dispatcher matches positional arguments only"
                                .formatted(dispatchName(definition)));
            }
            if (parameter.isListUnpacking()) {
                if (index != parameters.size() - 1) {
                    throw new UnsupportedViewingException(
                            "Overloaded '%s' has a variadic parameter that is not last".formatted(dispatchName(definition)));
                }
                variadicIndex = index;
            }
        }
        return variadicIndex;
    }

    private static int requiredCount(List<DeclarationArgument> parameters) {
        int required = 0;
        for (DeclarationArgument parameter : parameters) {
            if (!parameter.hasInitialExpression()) {
                required++;
            }
        }
        return required;
    }

    /* ------------------------------------------------------------------
     |  Условия
     ------------------------------------------------------------------ */

    /**
     * Строит условие каждой ветки.
     * <p>
     * Ветка, чьё число аргументов ни с кем не пересекается, проверяет только его: типы там ни на
     * что не влияют, а лишняя проверка {@code isinstance} отвергла бы допустимый вызов, если
     * тип не удалось спроецировать на Python.
     */
    private Map<Branch, List<String>> resolveConditions(List<Branch> branches) {
        Map<Branch, List<String>> conditions = new IdentityHashMap<>();
        for (Branch branch : branches) {
            List<Branch> colliding = branches.stream()
                    .filter(other -> other != branch && other.overlaps(branch))
                    .toList();

            List<String> condition = new ArrayList<>();
            condition.add("len(%s) %s %d".formatted(ARGS, branch.unbounded() ? ">=" : "==", branch.arity()));
            if (!colliding.isEmpty()) {
                requireDistinguishable(branch, colliding);
                for (int index = 0; index < branch.typeChecks().size(); index++) {
                    condition.add("isinstance(%s[%d], %s)".formatted(ARGS, index, branch.typeChecks().get(index)));
                }
            }
            conditions.put(branch, condition);
        }
        return conditions;
    }

    /**
     * Проверяет, что ветку можно отличить от пересекающихся с ней во время исполнения.
     * <p>
     * Отказ здесь — не осторожность, а единственный честный исход: если {@code f(int)} и
     * {@code f(long)} превращаются в одну и ту же проверку {@code isinstance(x, int)}, то любой
     * порядок веток выбирает перегрузку произвольно.
     */
    private void requireDistinguishable(Branch branch, List<Branch> colliding) {
        for (String check : branch.typeChecks()) {
            if (check == null) {
                throw new UnsupportedViewingException(
                        ("Overloads of '%s' take the same number of arguments and one of them has a type that "
                                + "cannot be checked at runtime").formatted(dispatchName(branch.definition())));
            }
        }
        for (Branch other : colliding) {
            if (branch.typeChecks().equals(other.typeChecks())) {
                throw new UnsupportedViewingException(
                        ("Overloads of '%s' are indistinguishable in Python: they take the same number of "
                                + "arguments and their parameter types map to the same Python types")
                                .formatted(dispatchName(branch.definition())));
            }
        }
    }

    /**
     * Имя Python-типа для {@code isinstance} либо {@code null}, если тип во время исполнения
     * не проверить. Проекция намеренно грубее, чем у аннотаций типов: {@code isinstance} не
     * принимает параметризованные обобщения, поэтому {@code list[int]} проверяется как
     * {@code list}, а различить {@code list[int]} и {@code list[str]} нельзя вовсе.
     */
    @Nullable
    private String pythonTypeName(Type type) {
        if (type == null || type instanceof UnknownType) {
            return null;
        }
        // Порядок важен: BooleanType проверяется раньше числовых типов.
        if (type instanceof BooleanType) {
            return "bool";
        }
        if (type instanceof IntType) {
            return "int";
        }
        if (type instanceof FloatType) {
            return "float";
        }
        if (type instanceof StringType || type instanceof CharacterType) {
            return "str";
        }
        if (type instanceof DictionaryType) {
            return "dict";
        }
        if (type instanceof SetType) {
            return "set";
        }
        if (type instanceof UnmodifiableListType || type instanceof TupleType) {
            return "tuple";
        }
        if (type instanceof ListType || type instanceof ArrayType) {
            return "list";
        }
        if (type instanceof UserType userType) {
            return userType.getName().toString();
        }
        return null;
    }

    /* ------------------------------------------------------------------
     |  Связывание имён
     ------------------------------------------------------------------ */

    /**
     * Возвращает исходным именам параметров их значения, чтобы тело перегрузки было выведено
     * без изменений.
     */
    private List<String> bindings(Branch branch, Tab tab) {
        List<String> bindings = new ArrayList<>();
        List<DeclarationArgument> parameters = branch.parameters();

        for (int index = 0; index < parameters.size(); index++) {
            DeclarationArgument parameter = parameters.get(index);
            String name = viewer.toString(parameter.getName());

            if (parameter.isListUnpacking()) {
                bindings.add(tab.concat("%s = %s[%d:]".formatted(name, ARGS, index)));
            } else if (index < branch.arity()) {
                bindings.add(tab.concat("%s = %s[%d]".formatted(name, ARGS, index)));
            } else {
                // Параметр не передан: ветка построена на число аргументов меньше полного,
                // значит здесь стоит значение по умолчанию.
                bindings.add(tab.concat("%s = %s".formatted(name, viewer.toString(parameter.getInitialExpression()))));
            }
        }
        return bindings;
    }
}

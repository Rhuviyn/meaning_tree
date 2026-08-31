package org.vstu.meaningtree.utils.analysis.symbols;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectConstructorDeclaration;
import org.vstu.meaningtree.nodes.declarations.ObjectDestructorDeclaration;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.utils.scopes.OverloadKind;
import org.vstu.meaningtree.utils.scopes.OverloadSemantics;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.*;

/**
 * Наполняет {@link ScopeTable} членами пользовательских типов и группами перегрузок.
 *
 * <p>Проход обходит дерево, а не полагается на регистрацию во время разбора, по трём причинам,
 * каждая из которых видна в коде парсеров: в Java владелец метода проставляется <i>после</i>
 * того, как тело класса собрано и его члены уже зарегистрированы; в C++ ни тело класса, ни
 * единица трансляции не строятся через {@code BodyConstructor}, поэтому свободные функции и
 * методы туда не попадают вовсе; в Java и Python члены класса регистрируются <i>до</i>
 * превращения в методы, а замена узла идёт через {@code CompoundStatement.substitute}, который
 * область видимости не трогает, — поэтому в таблице остаётся исходная {@code FunctionDeclaration}
 * без владельца, тогда как в дереве уже лежит {@code MethodDeclaration}. К моменту постобработки
 * дерево от этих расхождений свободно: владельцы проставлены, состав членов окончателен.</p>
 *
 * <p>Группа строится и для неперегруженного имени — из одной декларации. Так потребителю не
 * нужно различать «имя не перегружено» и «групп не строили»: первое — группа размера один,
 * второе — отсутствие группы.</p>
 */
public final class OverloadIndexer {
    private final MeaningTree meaningTree;
    private final ScopeTable scopeTable;
    private final OverloadSemantics semantics;

    /**
     * Ключ группировки: свободные функции — по лексической области и имени, члены — по
     * владельцу и имени.
     * <p>
     * У членов {@code scopeId} намеренно равен {@link #OWNER_SCOPED}: принадлежность методу
     * определяет владелец, а не место в тексте. Иначе объявление метода в классе и его
     * внеклассовое определение ({@code void A::f() {}} в C++) оказались бы в разных группах.
     * Реальная область видимости владельца запоминается отдельно, в {@code groupScopes}.
     */
    private record GroupKey(long scopeId, @Nullable UserType owner, SimpleIdentifier name, OverloadKind kind) {
    }

    /** Значение {@code GroupKey.scopeId} для членов: группировка идёт по владельцу. */
    private static final long OWNER_SCOPED = -1;

    public OverloadIndexer(@NotNull MeaningTree meaningTree,
                           @NotNull ScopeTable scopeTable,
                           @NotNull OverloadSemantics semantics) {
        this.meaningTree = Objects.requireNonNull(meaningTree);
        this.scopeTable = Objects.requireNonNull(scopeTable);
        this.semantics = Objects.requireNonNull(semantics);
    }

    public void index() {
        Map<GroupKey, List<FunctionDeclaration>> groups = new LinkedHashMap<>();
        Map<GroupKey, Long> groupScopes = new HashMap<>();
        Set<FunctionDeclaration> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        for (NodeInfo info : meaningTree) {
            if (!(info.node() instanceof FunctionDeclaration declaration) || !visited.add(declaration)) {
                continue;
            }
            // Деструктор перегружаться не может ни в одном поддерживаемом языке.
            if (declaration instanceof ObjectDestructorDeclaration) {
                continue;
            }

            GroupKey key = groupKeyOf(declaration, info);
            if (key == null) {
                continue;
            }
            if (key.owner() != null) {
                scopeTable.registerMember(key.owner(), declaration);
            }
            groupScopes.putIfAbsent(key, scopeIdOf(info));
            collect(groups.computeIfAbsent(key, ignored -> new ArrayList<>()), declaration);
        }

        groups.forEach((key, declarations) -> scopeTable.registerOverloadGroup(
                groupScopes.get(key), key.name(), key.kind(), key.owner(), declarations
        ));
    }

    /**
     * @return {@code null}, если декларацию не с чем группировать: метод без владельца
     *         сгруппировать не по чему, а приписывать его к лексической области было бы
     *         неверно — одноимённые методы разных классов оказались бы перегрузками
     */
    @Nullable
    private GroupKey groupKeyOf(@NotNull FunctionDeclaration declaration, @NotNull NodeInfo info) {
        if (declaration instanceof ObjectConstructorDeclaration constructor) {
            UserType owner = constructor.getOwner();
            return owner == null
                    ? null
                    : new GroupKey(OWNER_SCOPED, owner, constructor.getName(), OverloadKind.CONSTRUCTOR);
        }
        if (declaration instanceof MethodDeclaration method) {
            UserType owner = method.getOwner();
            return owner == null
                    ? null
                    : new GroupKey(OWNER_SCOPED, owner, method.getName(), OverloadKind.METHOD);
        }
        return new GroupKey(scopeIdOf(info), null, declaration.getName(), OverloadKind.FUNCTION);
    }

    /**
     * Добавляет декларацию в группу по правилам языка.
     * <p>
     * Там, где перегрузок нет, позднее объявление затеняет раннее, поэтому в группе всегда
     * остаётся ровно одна — последняя — декларация: два подряд идущих Python {@code def}
     * одного имени описывают одну живую функцию, а не две перегрузки.
     * <p>
     * Там, где перегрузки есть, одинаковые сигнатуры — это повторные упоминания одной
     * сущности (объявление в заголовке и определение в реализации у C++), а не разные
     * перегрузки. Канонической остаётся первая: для разрешения вызова важна сигнатура, а она
     * у них совпадает по определению.
     */
    private void collect(@NotNull List<FunctionDeclaration> group,
                         @NotNull FunctionDeclaration declaration) {
        if (!semantics.allowsOverloading()) {
            group.clear();
            group.add(declaration);
            return;
        }

        List<Type> signature = semantics.signatureOf(declaration);
        for (FunctionDeclaration existing : group) {
            if (semantics.signatureOf(existing).equals(signature)) {
                return;
            }
        }
        group.add(declaration);
    }

    /**
     * Лексическая область ближайшего охватывающего блока. Если охватывающего блока нет —
     * свободная функция единицы трансляции в C++, — считается объявленной в корневой области.
     */
    private long scopeIdOf(@NotNull NodeInfo info) {
        for (NodeInfo current = info; current != null; current = current.parent()) {
            if (current.node() instanceof CompoundStatement compound && compound.getScopeId().isPresent()) {
                return compound.getScopeId().getAsLong();
            }
        }
        return scopeTable.rootScopeId();
    }
}

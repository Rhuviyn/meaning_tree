package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

/**
 * Правило языка о том, где проходят границы лексических областей видимости.
 * <p>
 * Границы области — семантика языка, а не свойство дерева: один и тот же
 * {@link CompoundStatement} в Java открывает область, а в Python нет. Поэтому проход, строящий
 * {@link ScopeTable} по готовому дереву, обязан получать это правило снаружи, а не выводить его
 * из формы узлов.
 * <p>
 * Политика описывает те же границы, что и флаг {@code newScope} у {@code BodyConstructor} при
 * разборе; расхождение этих двух путей ловится тестами на паритет числа областей.
 */
@FunctionalInterface
public interface ScopePolicy {
    /**
     * Открывает ли тело собственную область видимости.
     *
     * @param body  тело, встреченное обходом
     * @param owner узел, которому это тело принадлежит, либо {@code null} для корня дерева
     */
    boolean opensScope(@NotNull CompoundStatement body, @Nullable Node owner);

    /**
     * Блочная видимость: собственную область открывает любой блок. Так устроены Java и C++ —
     * имя, объявленное внутри {@code if} или цикла, за его пределами не видно.
     */
    static ScopePolicy blockScoped() {
        return (body, owner) -> true;
    }

    /**
     * Видимость на уровне определений: область открывают только {@code def}, {@code class} и
     * подобные им определения, но не тела операторов. Так устроен Python — имя, присвоенное
     * внутри {@code if}, {@code for} или {@code try}, остаётся локальным именем функции или
     * модуля и видно после блока.
     */
    static ScopePolicy definitionScoped() {
        return (body, owner) -> owner instanceof Definition;
    }
}

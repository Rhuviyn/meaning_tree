package org.vstu.meaningtree.nodes.interfaces;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;

import java.util.List;

public interface Callable {
    /**
     * @return возвращает выражение, обозначающее имя вызываемой сущности (например, обращение к методу как полю объекта)
     */
    Expression getCallableName();
    List<Expression> getArguments();

    /**
     * Декларация, к которой относится вызов, если анализ определил её однозначно.
     * <p>
     * {@code null} означает «не определено», а не «не существует»: вызов может ссылаться на
     * сущность вне разбираемого фрагмента, а перегруженное имя — не давать единственного
     * подходящего кандидата. Оба случая штатные.
     *
     * @return декларация вызываемой сущности или {@code null}
     */
    @Nullable
    FunctionDeclaration getResolvedDeclaration();

    /**
     * Связывает вызов с декларацией. Вызывается проходом разрешения вызовов; сам узел эту
     * ссылку не выводит.
     */
    void setResolvedDeclaration(@Nullable FunctionDeclaration declaration);
}

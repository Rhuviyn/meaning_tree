package org.vstu.meaningtree.nodes.statements.exceptions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Конструкция обработки исключений: <code>try ... catch ... finally</code> в Java/C++
 * и <code>try ... except ... else ... finally</code> в Python.
 * <p>
 * Ветвь <code>elseBranch</code> выполняется, если тело <code>try</code> завершилось
 * без исключения (есть только в Python). Ветвь <code>finallyBranch</code> выполняется
 * в любом случае.
 */
public class ExceptionCatchStatement extends Statement {
    @TreeNode private Statement body;

    @TreeNode private List<CatchClause> catchClauses;

    @TreeNode
    @Nullable
    private Statement _elseBranch;

    @TreeNode
    @Nullable
    private Statement _finallyBranch;

    public ExceptionCatchStatement(
            @NotNull Statement body,
            @NotNull List<CatchClause> catchClauses,
            @Nullable Statement elseBranch,
            @Nullable Statement finallyBranch
    ) {
        this.body = body;
        this.catchClauses = new ArrayList<>(catchClauses);
        _elseBranch = elseBranch;
        _finallyBranch = finallyBranch;
    }

    public ExceptionCatchStatement(@NotNull Statement body, @NotNull List<CatchClause> catchClauses) {
        this(body, catchClauses, null, null);
    }

    public Statement getBody() {
        return body;
    }

    public List<CatchClause> getCatchClauses() {
        return catchClauses;
    }

    public void setCatchClauses(@NotNull List<CatchClause> catchClauses) {
        this.catchClauses = new ArrayList<>(catchClauses);
    }

    public Statement getElseBranch() {
        return Objects.requireNonNull(_elseBranch, "Exception catch statement does not have else branch");
    }

    public boolean hasElseBranch() {
        return _elseBranch != null;
    }

    /**
     * Ветвь {@code else} умеет разбирать только Python; в языках без такого синтаксиса её
     * снимает {@code TryElseLowerer} перед генерацией кода.
     */
    public void setElseBranch(@Nullable Statement elseBranch) {
        _elseBranch = elseBranch;
    }

    public Statement getFinallyBranch() {
        return Objects.requireNonNull(_finallyBranch, "Exception catch statement does not have finally branch");
    }

    public boolean hasFinallyBranch() {
        return _finallyBranch != null;
    }

    public void setFinallyBranch(@Nullable Statement finallyBranch) {
        _finallyBranch = finallyBranch;
    }

    public CompoundStatement makeCompoundBody() {
        body = makeCompound(body);
        return (CompoundStatement) body;
    }

    /**
     * Приводит тело и все ветви к составным операторам: языки с блочным синтаксисом
     * не могут отрендерить одиночный оператор в этих позициях.
     */
    public void makeCompoundBranches() {
        body = makeCompound(body);
        for (CatchClause clause : catchClauses) {
            clause.makeCompoundBody();
        }
        _elseBranch = _elseBranch == null ? null : makeCompound(_elseBranch);
        _finallyBranch = _finallyBranch == null ? null : makeCompound(_finallyBranch);
    }

    private static Statement makeCompound(@NotNull Statement statement) {
        return statement instanceof CompoundStatement ? statement : new CompoundStatement(statement);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExceptionCatchStatement nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(body, nodeInfos.body)
                && Objects.equals(catchClauses, nodeInfos.catchClauses)
                && Objects.equals(_elseBranch, nodeInfos._elseBranch)
                && Objects.equals(_finallyBranch, nodeInfos._finallyBranch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), body, catchClauses, _elseBranch, _finallyBranch);
    }

    @Override
    public ExceptionCatchStatement clone() {
        var clone = (ExceptionCatchStatement) super.clone();
        clone.body = body.clone();
        clone.catchClauses = new ArrayList<>(catchClauses.stream().map(CatchClause::clone).toList());
        clone._elseBranch = _elseBranch == null ? null : _elseBranch.clone();
        clone._finallyBranch = _finallyBranch == null ? null : _finallyBranch.clone();
        return clone;
    }
}

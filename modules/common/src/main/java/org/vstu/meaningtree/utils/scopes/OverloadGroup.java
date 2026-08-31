package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.types.UserType;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Одноимённые callable-сущности, между которыми выбирает разрешение вызова.
 * <p>
 * Группа существует и тогда, когда декларация всего одна: это позволяет потребителю задать
 * вопрос «какие ещё варианты есть у этого вызова» единообразно, не различая перегруженное имя
 * и обычное. Перегрузкой в содержательном смысле группа становится при {@link #isOverloaded()}.
 * <p>
 * Наследование в группу не входит: методы предка остаются в группе предка, а связь
 * «переопределяет» описывается отдельной моделью
 * ({@code MethodDeclaration.getOverriddenFrom()}).
 */
public final class OverloadGroup implements Serializable {
    private final long scopeId;

    @NotNull
    private final SimpleIdentifier name;

    @NotNull
    private final OverloadKind kind;

    @Nullable
    private final UserType owner;

    @NotNull
    private final List<FunctionDeclaration> declarations;

    OverloadGroup(long scopeId,
                  @NotNull SimpleIdentifier name,
                  @NotNull OverloadKind kind,
                  @Nullable UserType owner,
                  @NotNull List<FunctionDeclaration> declarations) {
        this.scopeId = scopeId;
        this.name = Objects.requireNonNull(name);
        this.kind = Objects.requireNonNull(kind);
        this.owner = owner;
        this.declarations = List.copyOf(declarations);
    }

    /** Лексическая область видимости группы; для методов и конструкторов — область владельца. */
    public long scopeId() {
        return scopeId;
    }

    @NotNull
    public SimpleIdentifier name() {
        return name;
    }

    @NotNull
    public OverloadKind kind() {
        return kind;
    }

    /** Владелец для методов и конструкторов; {@code null} для свободных функций. */
    @Nullable
    public UserType owner() {
        return owner;
    }

    /** По одной канонической декларации на каждую уникальную сигнатуру, в порядке объявления. */
    @NotNull
    public List<FunctionDeclaration> declarations() {
        return declarations;
    }

    /** Действительно ли имя перегружено, то есть несёт больше одной сигнатуры. */
    public boolean isOverloaded() {
        return declarations.size() > 1;
    }

    public boolean contains(@NotNull FunctionDeclaration declaration) {
        for (FunctionDeclaration candidate : declarations) {
            if (candidate == declaration) {
                return true;
            }
        }
        return false;
    }

    /** Типы параметров декларации — сигнатура в том виде, в каком её различает группа. */
    @NotNull
    public static List<Type> signatureOf(@NotNull FunctionDeclaration declaration) {
        return declaration.getArguments().stream().map(DeclarationArgument::getType).toList();
    }

    @Override
    public String toString() {
        String ownerPrefix = owner == null ? "" : owner + ".";
        return "%s%s/%d %s".formatted(ownerPrefix, name, declarations.size(), kind);
    }
}

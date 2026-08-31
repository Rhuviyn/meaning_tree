package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.UserType;

import java.io.Serializable;
import java.util.*;

/**
 * {@code TypeScope} управляет стеком областей видимости типов,
 * поддерживая операции входа и выхода из областей {@link ScopeTableElement}.
 */
public class ScopeTable implements Serializable {
    /**
     * Program-wide declarations and definitions.
     */
    @NotNull
    private final SymbolIndex symbols;

    /**
     * Program-wide type registry and user type hierarchy.
     */
    @NotNull
    private final TypeIndex types;

    /**
     * Program-wide imports.
     */
    @NotNull
    private final ImportIndex imports;

    @NotNull
    private final Map<Long, ScopeTableElement> scopes;

    /**
     * Члены пользовательских типов: владелец — имя — все одноимённые декларации.
     * <p>
     * Отдельно от лексических областей видимости потому, что метод виден не по вложенности
     * блоков, а по владельцу: два класса в одной области могут объявлять одноимённые методы,
     * и это не перегрузка друг друга.
     */
    @NotNull
    private final Map<UserType, Map<SimpleIdentifier, List<FunctionDeclaration>>> members;

    /** Группы перегрузок в порядке построения. */
    @NotNull
    private final List<OverloadGroup> overloadGroups;

    /**
     * Обратный индекс «декларация — её группа».
     * <p>
     * Ключи сравниваются по ссылке: {@code Node.equals} сравнивает по значению и без id,
     * поэтому две одинаковые по значению декларации разных классов схлопнулись бы в один ключ.
     */
    @NotNull
    private final Map<FunctionDeclaration, OverloadGroup> groupByDeclaration;

    private long nextScopeId;

    /**
     * Текущая область сущностей.
     */
    @NotNull
    private ScopeTableElement current;

    /**
     * Создаёт менеджер областей видимости с корневой областью.
     */
    public ScopeTable() {
        this.symbols = new SymbolIndex();
        this.types = new TypeIndex();
        this.imports = new ImportIndex();
        this.scopes = new LinkedHashMap<>();
        this.members = new LinkedHashMap<>();
        this.overloadGroups = new ArrayList<>();
        this.groupByDeclaration = new IdentityHashMap<>();
        this.nextScopeId = 1;
        this.current = createScope(null, null);
    }

    /**
     * Входит в новую область видимости.
     */
    public void enter() {
        enter(null);
    }

    /**
     * Входит в новую область видимости, связанную с AST-узлом.
     */
    public void enter(@Nullable Node owner) {
        current = createScope(current, owner);
    }

    /**
     * Входит в родительскую область.
     * Если родительской области нет, ничего не происходит.
     *
     * @throws IllegalStateException если текущая область корневая
     */
    public void leave() {
        leave(false);
    }

    /**
     * Входит в родительскую область.
     *
     * @param rootScopeMustExist Когда {@code true}, выбрасывает исключение
     *                           {@code IllegalStateException}, если родительской
     *                           области видимости не существует
     */
    public void leave(boolean rootScopeMustExist) {
        ScopeTableElement parent = current.getParent();
        if (parent != null) {
            current = parent;
            return;
        }

        if (rootScopeMustExist) {
            throw new IllegalStateException("Cannot leave root scope");
        }
    }

    public ScopeTableElement scope() {
        return current;
    }

    public Collection<ScopeTableElement> allScopes() {
        return List.copyOf(scopes.values());
    }

    public Optional<ScopeTableElement> findScope(long id) {
        return Optional.ofNullable(scopes.get(id));
    }

    public long currentScopeId() {
        return current.getId();
    }

    public ScopeTableElement restoreScope(long id, @Nullable Long parentId, @Nullable Node owner) {
        ScopeTableElement existing = scopes.get(id);
        if (existing != null) {
            existing.setOwner(owner);
            return existing;
        }

        ScopeTableElement parent = parentId == null ? null : scopes.get(parentId);
        ScopeTableElement restored = new ScopeTableElement(id, parent, owner);
        scopes.put(id, restored);
        nextScopeId = Math.max(nextScopeId, id + 1);
        return restored;
    }

    public void setCurrentScope(long id) {
        ScopeTableElement scope = scopes.get(id);
        if (scope == null) {
            throw new IllegalArgumentException("Unknown scope id: " + id);
        }
        current = scope;
    }

    public void setCurrentScopeOwner(@Nullable Node owner) {
        current.setOwner(owner);
    }

    public void registerVariable(@NotNull VariableDeclaration variableDeclaration) {
        current.registerVariable(variableDeclaration);
    }

    public void registerVariable(@NotNull SeparatedVariableDeclaration variableDeclaration) {
        current.registerVariable(variableDeclaration);
    }

    public void removeVariable(@NotNull SimpleIdentifier name) {
        current.removeVariable(name);
    }

    public boolean hasVariable(@NotNull SimpleIdentifier name) {
        return current.getVariableType(name) != null;
    }

    @Nullable
    public Type getVariableType(@NotNull SimpleIdentifier name) {
        return current.getVariableType(name);
    }

    public Optional<VariableDeclaration> getVariableDeclaration(@NotNull SimpleIdentifier name, @Nullable Type type) {
        return current.getVariableDeclaration(name, type);
    }

    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type, boolean createIfNotExists) {
        current.changeVariableType(name, type, createIfNotExists);
    }

    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type) {
        changeVariableType(name, type, true);
    }

    public void registerImport(@NotNull Import importDeclaration) {
        imports.registerImport(importDeclaration);
    }

    public void registerTypeDeclaration(@NotNull Type type, @NotNull Declaration declaration) {
        types.registerTypeDeclaration(type, declaration);
    }

    public Identifier registerType(@NotNull Identifier name, @NotNull Type type) {
        if (current.getParent() != null) {
            return current.registerType(name, type);
        }
        return types.registerType(name, type);
    }

    /**
     * Тип, который объявление вводит в программу. Есть только у объявлений пользовательских
     * типов: классов, структур и перечислений.
     */
    @Nullable
    static Type declaredTypeOf(@NotNull Declaration declaration) {
        return switch (declaration) {
            case ClassDeclaration cls -> cls.getTypeNode();
            case EnumDeclaration enumDeclaration -> enumDeclaration.getTypeNode();
            default -> null;
        };
    }

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        if (current.getParent() == null) {
            symbols.registerDeclaration(name, declaration);
            Type type = declaredTypeOf(declaration);
            if (type != null) {
                registerTypeDeclaration(type, declaration);
                registerType(name, type);
            }
        } else {
            current.registerDeclaration(name, declaration);
            // Иерархия предков — структурный факт о фрагменте, а не о видимости имён, поэтому
            // регистрируется независимо от глубины scope: вложенный класс тоже должен попасть
            // в TypeHierarchy, хотя его объявление остаётся видимым только локально.
            Type type = declaredTypeOf(declaration);
            if (type != null) {
                types.registerHierarchy(type, declaration);
            }
        }
    }

    public void registerDefinition(@NotNull SimpleIdentifier name, @NotNull Definition definition) {
        registerDeclaration(name, definition.getDeclaration());
        symbols.registerDefinition(definition.getDeclaration(), definition);
    }

    public void registerDefinition(@NotNull Declaration declaration, @NotNull Definition definition) {
        symbols.registerDefinition(declaration, definition);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name, @Nullable Class<? extends Declaration> clazz) {
        return findDeclaration(name, clazz, ScopeLookupMode.VISIBLE);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name,
                                                 @Nullable Class<? extends Declaration> clazz,
                                                 @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclaration(name, clazz);
            case GLOBAL -> symbols.findDeclaration(name, clazz);
            case VISIBLE -> current.findDeclaration(name, clazz)
                    .or(() -> symbols.findDeclaration(name, clazz));
        };
    }

    /**
     * Все одноимённые декларации: перегрузки одного имени в ближайшей области видимости,
     * где это имя объявлено. Пустой список означает, что имя не найдено.
     *
     * @see #findDeclaration(SimpleIdentifier, Class, ScopeLookupMode) одиночный вариант,
     *      возвращающий последнюю подходящую декларацию
     */
    public List<Declaration> findDeclarations(@NotNull SimpleIdentifier name,
                                              @Nullable Class<? extends Declaration> clazz) {
        return findDeclarations(name, clazz, ScopeLookupMode.VISIBLE);
    }

    public List<Declaration> findDeclarations(@NotNull SimpleIdentifier name,
                                              @Nullable Class<? extends Declaration> clazz,
                                              @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclarations(name, clazz);
            case GLOBAL -> symbols.findDeclarations(name, clazz);
            case VISIBLE -> {
                var local = current.findDeclarations(name, clazz);
                yield local.isEmpty() ? symbols.findDeclarations(name, clazz) : local;
            }
        };
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz) {
        return findDeclaration(clazz, ScopeLookupMode.VISIBLE);
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz,
                                             @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclaration(clazz);
            case GLOBAL -> symbols.findDeclaration(clazz);
            case VISIBLE -> {
                var localDeclarations = current.findDeclaration(clazz);
                yield localDeclarations.isEmpty() ? symbols.findDeclaration(clazz) : localDeclarations;
            }
        };
    }

    public Optional<Definition> findDefinition(@NotNull SimpleIdentifier name, @Nullable Class<? extends Declaration> declarationClass) {
        return findDefinition(name, declarationClass, ScopeLookupMode.VISIBLE);
    }

    public Optional<Definition> findDefinition(@NotNull SimpleIdentifier name,
                                               @Nullable Class<? extends Declaration> declarationClass,
                                               @NotNull ScopeLookupMode mode) {
        return findDeclaration(name, declarationClass, mode).flatMap(this::findDefinition);
    }

    public Optional<Definition> findDefinition(@NotNull Declaration declaration) {
        return symbols.findDefinition(declaration);
    }

    public List<Definition> findDefinition(@NotNull Class<? extends Definition> clazz) {
        return symbols.findDefinition(clazz);
    }

    public Optional<Type> findType(@NotNull Identifier name) {
        return findType(name, ScopeLookupMode.VISIBLE);
    }

    public Optional<Type> findType(@NotNull Identifier name, @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentType(name);
            case GLOBAL -> types.findType(name);
            case VISIBLE -> current.findType(name).or(() -> types.findType(name));
        };
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type) {
        return findTypeDeclaration(type, ScopeLookupMode.VISIBLE);
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type, @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentTypeDeclaration(type);
            case GLOBAL -> types.findTypeDeclaration(type);
            case VISIBLE -> current.findTypeDeclaration(type).or(() -> types.findTypeDeclaration(type));
        };
    }

    public TypeHierarchy typeHierarchy() {
        return types.hierarchy();
    }

    public Map<UserType, Set<UserType>> userTypeHierarchy() {
        return typeHierarchy().asMap();
    }

    public Set<UserType> directParents(@NotNull UserType type) {
        return typeHierarchy().directParents(type);
    }

    public Set<UserType> ancestors(@NotNull UserType type) {
        return typeHierarchy().ancestors(type);
    }

    public Set<UserType> descendants(@NotNull UserType type) {
        return typeHierarchy().descendants(type);
    }

    public boolean isSubtypeOf(@NotNull UserType possibleSubtype, @NotNull UserType possibleParent) {
        return typeHierarchy().isSubtypeOf(possibleSubtype, possibleParent);
    }

    public Set<UserType> userTypes() {
        return typeHierarchy().userTypes();
    }

    /**
     * Идентификатор корневой области видимости. Она создаётся в конструкторе и существует
     * всегда, поэтому это безопасная точка отсчёта для деклараций, у которых не нашлось
     * охватывающего блока.
     */
    public long rootScopeId() {
        return scopes.keySet().iterator().next();
    }

    /**
     * Регистрирует член пользовательского типа: метод, конструктор или иную callable-сущность,
     * принадлежащую владельцу.
     */
    public void registerMember(@NotNull UserType owner, @NotNull FunctionDeclaration member) {
        members.computeIfAbsent(owner, key -> new LinkedHashMap<>())
                .computeIfAbsent(member.getName(), key -> new ArrayList<>())
                .add(member);
    }

    /** Все одноимённые члены владельца в порядке регистрации. */
    public List<FunctionDeclaration> findMembers(@NotNull UserType owner, @NotNull SimpleIdentifier name) {
        return List.copyOf(members.getOrDefault(owner, Map.of()).getOrDefault(name, List.of()));
    }

    /** Все члены владельца в порядке регистрации. */
    public List<FunctionDeclaration> findMembers(@NotNull UserType owner) {
        return members.getOrDefault(owner, Map.of()).values().stream()
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Создаёт и запоминает группу перегрузок. Единственный способ получить
     * {@link OverloadGroup}: таблица остаётся единственным владельцем групп, поэтому обратный
     * индекс не может разойтись с их списком.
     */
    public OverloadGroup registerOverloadGroup(long scopeId,
                                               @NotNull SimpleIdentifier name,
                                               @NotNull OverloadKind kind,
                                               @Nullable UserType owner,
                                               @NotNull List<FunctionDeclaration> declarations) {
        OverloadGroup group = new OverloadGroup(scopeId, name, kind, owner, declarations);
        overloadGroups.add(group);
        for (FunctionDeclaration declaration : group.declarations()) {
            groupByDeclaration.put(declaration, group);
        }
        return group;
    }

    /**
     * Группа, в которую входит декларация.
     * <p>
     * Пусто означает, что группы не строились вовсе — например, при
     * {@code skipOptimizations}, когда конвейер анализа не выполняется, — а не что декларация
     * не перегружена: неперегруженное имя тоже образует группу из одного элемента.
     */
    public Optional<OverloadGroup> findOverloadGroup(@NotNull FunctionDeclaration declaration) {
        return Optional.ofNullable(groupByDeclaration.get(declaration));
    }

    /** Все построенные группы перегрузок. */
    public List<OverloadGroup> overloadGroups() {
        return List.copyOf(overloadGroups);
    }

    /** Группы, в которых имя действительно перегружено, то есть несёт больше одной сигнатуры. */
    public List<OverloadGroup> overloadedGroups() {
        return overloadGroups.stream().filter(OverloadGroup::isOverloaded).toList();
    }

    /** Глобальные декларации: имя — все одноимённые декларации в порядке регистрации. */
    public Map<Identifier, List<Declaration>> allDeclarations() {
        return symbols.allDeclarations();
    }

    public Map<Declaration, Definition> allDefinitions() {
        return symbols.allDefinitions();
    }

    public Map<Identifier, Type> allTypes() {
        return types.allTypes();
    }

    public Map<Type, Declaration> allTypeDeclarations() {
        return types.allTypeDeclarations();
    }

    public Set<Import> allImports() {
        return imports.allImports();
    }

    @Override
    public String toString() {
        return current.toString();
    }

    private ScopeTableElement createScope(@Nullable ScopeTableElement parent, @Nullable Node owner) {
        ScopeTableElement scope = new ScopeTableElement(nextScopeId++, parent, owner);
        scopes.put(scope.getId(), scope);
        if (owner instanceof CompoundStatement compoundStatement) {
            compoundStatement.bindScope(scope);
        }
        return scope;
    }
}

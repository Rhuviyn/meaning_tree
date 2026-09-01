package org.vstu.meaningtree.utils.analysis;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Строит {@link ScopeTable} с нуля отдельным проходом по уже готовому {@link MeaningTree} —
 * вместо попутного наполнения во время разбора ({@code BodyConstructor}/
 * {@code TranslatorContext.registerInScope}). Поэтому работает на любом дереве: только что
 * распарсенном, десериализованном из JSON или собранном программно/тестом.
 * <p>
 * Универсальное правило границы области видимости — {@code every CompoundStatement — scope} —
 * не зависит от языка и чинит пробел, который есть у попутной сборки сегодня: C++ строит тела
 * классов и единицу трансляции в обход {@code BodyConstructor} (см.
 * {@code CppParser.fromTranslationUnit}, class/struct/interface body), поэтому эти
 * {@code CompoundStatement} никогда не получают {@code scope}, и свободные функции/методы
 * попадают в {@code ScopeTable} только благодаря fallback на корневую область в
 * {@code OverloadIndexer}. Проход по готовому дереву применяет правило равномерно для всех
 * языков.
 * <p>
 * Диспетчеризация "как зарегистрировать узел по его типу" живёт в
 * {@link ScopeTable#register(Node)} и общая с попутным наполнением при разборе, поэтому два
 * пути регистрации не могут разойтись.
 * <p>
 * {@link MeaningTree#iterate()} отдаёт узлы в post-order (потомки раньше родителя — см.
 * {@code DFSNodeIterator}), а для входа/выхода из области видимости через
 * {@link ScopeTable#enter(Node)}/{@link ScopeTable#leave()} нужен обратный порядок: сначала
 * узел-владелец, потом содержимое. Поэтому вместо прямого использования этого порядка сначала
 * группируем узлы по родителю (относительный порядок детей одного родителя в post-order уже
 * верный), а затем идём от корня обычной рекурсией — это и даёт нужный pre-order.
 */
public final class ScopeTableBuilder {
    private ScopeTableBuilder() {
    }

    public static ScopeTable build(MeaningTree tree) {
        ScopeTable scope = new ScopeTable();
        Map<Long, List<NodeInfo>> childrenByParentId = groupByParent(tree.iterate());
        NodeInfo root = tree.getNodeById(tree.getRootNode().getId());
        visit(root, scope, childrenByParentId);
        return scope;
    }

    private static Map<Long, List<NodeInfo>> groupByParent(List<NodeInfo> flat) {
        Map<Long, List<NodeInfo>> childrenByParentId = new HashMap<>();
        for (NodeInfo info : flat) {
            NodeInfo parent = info.parent();
            if (parent == null) {
                continue;
            }
            childrenByParentId.computeIfAbsent(parent.id(), id -> new ArrayList<>()).add(info);
        }
        return childrenByParentId;
    }

    private static void visit(NodeInfo info, ScopeTable scope, Map<Long, List<NodeInfo>> childrenByParentId) {
        scope.register(info.node());

        boolean entered = info.node() instanceof CompoundStatement;
        if (entered) {
            scope.enter(info.node());
        }
        for (NodeInfo child : childrenByParentId.getOrDefault(info.id(), List.of())) {
            visit(child, scope, childrenByParentId);
        }
        if (entered) {
            scope.leave();
        }
    }

}

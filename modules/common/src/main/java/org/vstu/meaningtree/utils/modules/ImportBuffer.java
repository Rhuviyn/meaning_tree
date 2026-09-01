package org.vstu.meaningtree.utils.modules;

import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.modules.ImportAllFromModule;
import org.vstu.meaningtree.nodes.modules.ImportMembersFromModule;
import org.vstu.meaningtree.nodes.modules.ImportModule;
import org.vstu.meaningtree.nodes.modules.Include;
import org.vstu.meaningtree.utils.Label;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Импорты, отложенные до отрисовки шапки программы.
 * <p>
 * Сюда попадают и импорты, явно стоявшие в дереве ({@link #bufferTopLevelImports}), и те, о
 * необходимости которых становится известно только по ходу отрисовки ({@link #preserveImport}):
 * Python-структуре нужен {@code from dataclasses import dataclass}, C++-контейнеру — свой
 * заголовок. Тот, кто рисует шапку, забирает и то, и другое из одного места, с общей
 * дедупликацией по {@link #coversImport}.
 * <p>
 * Буфер живёт ровно столько же, сколько контекст трансляции, и меняется только внутри него;
 * это состояние одной отрисовки, а не свойство дерева.
 */
public final class ImportBuffer {
    private final List<Import> imports = new ArrayList<>();

    /**
     * Откладывает импорт, который понадобился рендереру по ходу отрисовки. Забирает отложенное
     * тот, кто отрисовывает шапку программы, — обычно через {@link #prependPreserved}.
     */
    public void preserveImport(Import importNode) {
        imports.add(importNode);
    }

    /**
     * Вынимает импорты верхнего уровня из {@code nodes} и откладывает их в тот же буфер, что и
     * {@link #preserveImport}.
     *
     * @param nodes список узлов одного уровня тела программы
     * @return тот же список без узлов-импортов верхнего уровня
     */
    public List<Node> bufferTopLevelImports(List<? extends Node> nodes) {
        List<Node> rest = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            if (node instanceof Import importNode) {
                preserveImport(importNode);
            } else {
                rest.add(node);
            }
        }
        return rest;
    }

    /**
     * Убирает из буфера все импорты, подошедшие под {@code matcher}. Сравнение — на усмотрение
     * вызывающего: например, по модулю (без учёта конкретных членов) или по языковому подтипу.
     *
     * @return true, если хотя бы один узел был убран
     */
    public boolean removeIf(Predicate<Import> matcher) {
        return imports.removeIf(matcher);
    }

    /**
     * Снимок буфера без изъятия — в отличие от {@link #flush}/{@link #flushMissing}, не
     * расходует содержимое. Пригодится для отладки или для проверки «а что там вообще отложено»
     * перед принятием решения (например, перед {@link #removeIf}).
     */
    public List<Import> peek() {
        return List.copyOf(imports);
    }

    /** Сколько импортов сейчас в буфере — без построения снимка, когда нужно только число. */
    public int size() {
        return imports.size();
    }

    public List<Import> flush() {
        var dump = List.copyOf(imports);
        imports.clear();
        return dump;
    }

    /**
     * Забирает отложенные импорты, которых еще нет среди {@code existingNodes}, попутно
     * отсеивая повторы внутри самой пачки: один и тот же импорт мог отложиться столько раз,
     * сколько в программе конструкций, которым он нужен.
     */
    public List<Import> flushMissing(Collection<? extends Node> existingNodes) {
        List<Import> missing = new ArrayList<>();
        for (Import preserved : flush()) {
            if (isImportCovered(preserved, missing) || isImportCovered(preserved, existingNodes)) {
                continue;
            }
            missing.add(preserved);
        }
        return missing;
    }

    /**
     * Дописывает перед готовым телом программы импорты, отложенные через
     * {@link #preserveImport} и еще отсутствующие в {@code existingNodes}.
     * <p>
     * Отрисовка импорта передается вызывающим: у языка может быть свой путь диспетчеризации
     * (в Python — с отступом), а импорт обязан пройти именно по нему, иначе он не попадет в
     * source map наравне с остальными узлами.
     *
     * @param body          уже отрисованное тело программы
     * @param existingNodes узлы этого тела: по ним проверяется, что импорта еще нет
     * @param linePrefix    отступ, с которого начинается строка на этом уровне
     * @param render        отрисовка одного импорта
     * @return тело с шапкой из недостающих импортов
     */
    public String prependPreserved(String body,
                                   Collection<? extends Node> existingNodes,
                                   String linePrefix,
                                   Function<Import, String> render) {
        List<Import> missing = flushMissing(existingNodes);
        if (missing.isEmpty()) {
            return body;
        }
        List<String> lines = new ArrayList<>();
        for (Import missingImport : missing) {
            lines.add(linePrefix + render.apply(missingImport));
        }
        lines.add(body);
        return String.join("\n", lines);
    }

    /**
     * Проверяет, покрыт ли {@code required} каким-либо импортом из {@code nodes}.
     */
    public static boolean isImportCovered(Import required, Collection<? extends Node> nodes) {
        return nodes.stream().anyMatch(node -> node instanceof Import existing && coversImport(existing, required));
    }

    /**
     * Делает ли импорт {@code existing} ненужным импорт {@code required}.
     * <p>
     * Это не эквивалентность (не {@code equals}), а одностороннее отношение "покрытия": например,
     * {@code from module import *} покрывает {@code from module import x}, но не наоборот, а
     * импорт всего модуля покрывает лишь точно такой же импорт всего модуля. Оба узла при этом
     * могут быть равны и через {@link Node#equals} (метка {@link Label#REMAPPED} — stealth, см.
     * {@link Node#remap}, — и на равенство не влияет), но для дедупликации нужна именно семантика
     * покрытия, а не совпадение содержимого.
     */
    public static boolean coversImport(Import existing, Import required) {
        // #include стоит особняком: он не именует модуль, а подключает файл, поэтому
        // покрывает только точно такое же подключение того же файла в той же форме
        if (existing instanceof Include || required instanceof Include) {
            return existing instanceof Include a && required instanceof Include b
                    && a.getIncludeType() == b.getIncludeType()
                    && a.getFileName().getUnescapedValue().equals(b.getFileName().getUnescapedValue());
        }
        if (!(existing instanceof ImportModule from) || !(required instanceof ImportModule needed)) {
            return false;
        }
        if (!from.getModuleName().internalRepresentation()
                .equals(needed.getModuleName().internalRepresentation())) {
            return false;
        }
        if (!(needed instanceof ImportMembersFromModule requiredMembers)) {
            // Импорт модуля целиком заменяется только таким же импортом модуля целиком
            return existing.getClass().equals(required.getClass());
        }
        if (existing instanceof ImportAllFromModule) {
            return true;
        }
        if (!(existing instanceof ImportMembersFromModule presentMembers)) {
            return false;
        }
        Set<String> present = presentMembers.getMembers().stream()
                .map(Identifier::internalRepresentation)
                .collect(Collectors.toSet());
        return requiredMembers.getMembers().stream()
                .map(Identifier::internalRepresentation)
                .allMatch(present::contains);
    }
}

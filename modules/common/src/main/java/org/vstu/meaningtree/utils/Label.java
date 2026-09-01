package org.vstu.meaningtree.utils;

import com.google.gson.JsonElement;
import org.vstu.meaningtree.exceptions.MeaningTreeConfigException;

import java.util.Objects;
import java.util.Set;

/**
 * Специальные метки для узла дерева
 */
public class Label {
    /**
     * Метка, которая использует атрибут и позволяет привязать к узлу любое значение для любых целей
     */
    public static final short VALUE = 0;

    /**
     * Указывает Viewer выводить пустую строку вместо этого узла
     */
    public static final short DUMMY = 1;

    /**
     * Пометка для пользовательского кода, указывает, что узел изменился после каких-то манипуляций (мутация).
     * Устанавливается пользовательским кодом по соглашениям, определенным в нем.
     * Может иметь атрибут в виде short кода, принятого пользователем для уточнения мутации
     */
    public static final short MUTATION_FLAG = 2;

    /**
     * Показывает, из какого языка создано дерево изначально. Содержит id из enum SupportedLanguage
     */
    public static final short ORIGIN = 3;

    /**
     * Показывает, какую байтовую позицию занимает узел в исходной строке. Содержит [offset, length] (массив int[])
     */
    public static final short BYTEPOS_ANNOTATED = 4;

    /**
     * Сигнализирует, что узел был преобразован в другой при viewing. Хранит оригинальный ast_id. Нужен для корректной генерации SourceMap
     */
    public static final short REMAPPED = 5;

    /**
     * Зарезервированный номер. Применяется в случае, если метка была не распознана
     */
    public static final short UNKNOWN = Short.MAX_VALUE;

    /**
     * Зарезервированный номер. Применяется, если владелец метки имеет ошибку
     */
    public static final short ERROR = Short.MIN_VALUE;


    /**
     * Пользователи библиотеки могут создавать собственные метки. Для этого выделен диапазон отрицательных id
     * Представлено максимально возможное значение пользовательской метки
     */
    public static final short USER_TAG_MAX_ID = -1;
    /**
     * Пользователи библиотеки могут создавать собственные метки. Для этого выделен диапазон отрицательных id
     * Представлено минимально возможное значение пользовательской метки
     */
    public static final short USER_TAG_MIN_ID = -32000;


    private short id;
    private Object attribute = null;
    private Class<?> attributeType = null;
    private final boolean stealth;

    public Label(short id, Object attribute) {
        this(id, attribute, false);
    }

    /**
     * @param stealth если true, метка не участвует в {@link Node#equals}: узлы, различающиеся
     *                только наличием такой метки, при сравнении содержимого считаются одинаковыми.
     *                Нужно для меток, которые описывают происхождение/служебное состояние узла, а
     *                не его содержимое — например, {@link #REMAPPED}.
     */
    public Label(short id, Object attribute, boolean stealth) {
        this.attribute = attribute;
        this.id = id;
        this.attributeType = attribute.getClass();
        typeFits(attribute);
        this.stealth = stealth;
    }

    public Label(short id) {
        this(id, false);
    }

    public Label(short id, boolean stealth) {
        this.id = id;
        this.stealth = stealth;
    }

    public short getId() {
        return id;
    }

    /**
     * Участвует ли метка в сравнении содержимого узлов.
     * <p>
     * {@link #REMAPPED} невидима всегда, независимо от того, каким флагом метка создана: её
     * смысл — «узел получен из другого узла при выводе», то есть происхождение, а не содержимое.
     * Благодаря этому JSON, записанный до появления флага, после чтения ведёт себя как прежде:
     * поля {@code stealth} в нём нет, но равенство узлов оно не ломает.
     */
    public boolean isStealth() {
        return stealth || id == REMAPPED;
    }

    private static final Set<Class<?>> ALLOWED_TYPES = Set.of(
            String.class,
            Number.class,
            Boolean.class,
            // Массивы объектов
            Number[].class,
            String[].class,
            Boolean[].class,
            // Массивы примитивов
            int[].class,
            long[].class,
            double[].class,
            float[].class,
            boolean[].class,
            JsonElement.class
    );

    private void typeFits(Object attr) {
        if (attr == null) return;

        Class<?> cls = attr.getClass();

        if (ALLOWED_TYPES.stream().noneMatch(t -> t.isAssignableFrom(cls))) {
            throw new MeaningTreeConfigException(
                    "Invalid label attribute type: " + cls.getName()
            );
        }
    }

    public Class<?> getAttributeType() {
        return attributeType;
    }

    public Object getAttribute() {
        return attribute;
    }

    public int attributeAsInt() {
        return ((Number) attribute).intValue();
    }

    public String attributeAsString() {
        return (String) attribute;
    }

    public String stringifyAttribute() {
        return attribute.toString();
    }

    public boolean attributeAsBoolean() {
        return (boolean) attribute;
    }

    public double attributeAsDouble() {
        return ((Number) attribute).doubleValue();
    }

    public long attributeAsLong() {
        return ((Number) attribute).longValue();
    }

    public long[] attributeAsLongArray() {
        return (long[]) attribute;
    }

    public int[] attributeAsIntArray() {
        return (int[]) attribute;
    }

    public <T> T attributeAs(Class<T> tClass) {
        return (T) attribute;
    }

    public boolean hasAttribute() {
        return attribute != null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Label)) return false;
        return ((Label)o).id == this.id;
    }

    /**
     * Хеш считается только по id, как и {@link #equals(Object)}: метка идентифицируется своим
     * номером, а атрибут — это её изменяемое содержимое. Если учитывать атрибут, две метки с
     * одинаковым id, но разными атрибутами окажутся равными с разными хешами, и множество меток
     * узла перестанет их различать.
     */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}

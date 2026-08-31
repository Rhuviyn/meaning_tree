package org.vstu.meaningtree.utils.scopes;

/** Вид callable-сущности, вокруг которой собрана {@link OverloadGroup}. */
public enum OverloadKind {
    /** Свободная функция: группируется по лексической области видимости и имени. */
    FUNCTION,
    /** Метод класса: группируется по владельцу и имени. */
    METHOD,
    /** Конструктор: группируется по владельцу, имя роли не играет. */
    CONSTRUCTOR
}

package org.vstu.meaningtree.utils.modules;

import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.Alias;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Наивный перевод имени модуля между «точечной» формой (Java/Python) и путём к файлу
 * (C/C++ {@code #include}).
 * <p>
 * Наивный — потому что делается без обращения к файловой системе: языки называют одну и ту же
 * сущность по-разному, и без резолва по проекту (см. {@code ImportResolver}) единственное, что
 * можно сделать честно, — механически заменить разделитель. Резолвер уточняет результат позже,
 * когда известен корень проекта.
 */
public final class ImportPathConverter {
    /**
     * Расширения, которые снимаются при переводе пути в точечное имя. Список закрытый
     * намеренно: слепое отсечение всего после последней точки испортило бы уже точечное имя
     * ({@code a.b.c} превратилось бы в {@code a.b}).
     */
    private static final Set<String> SOURCE_EXTENSIONS = Set.of(
            "h", "hpp", "hxx", "hh", "inc",
            "c", "cpp", "cxx", "cc",
            "java", "py", "pyi"
    );

    public static final String DEFAULT_HEADER_EXTENSION = ".h";

    private ImportPathConverter() {}

    /**
     * {@code a.b.c} → {@code a/b/c.h}: точечное имя модуля в путь заголовочного файла.
     */
    public static String dottedNameToHeaderPath(String dottedName) {
        return dottedNameToPath(dottedName).concat(DEFAULT_HEADER_EXTENSION);
    }

    /**
     * {@code a.b.c} → {@code a/b/c}: точечное имя модуля в путь без расширения.
     */
    public static String dottedNameToPath(String dottedName) {
        return dottedName.replace('.', '/');
    }

    /**
     * {@code a/b/c.h} → {@code a.b.c}: путь к файлу в точечное имя модуля.
     * <p>
     * Уже точечное имя ({@code a.b.c}, как его пишут существующие лоссовые выводы) проходит
     * через метод без изменений: расширение снимается только известное.
     */
    public static String filePathToDottedName(String filePath) {
        String normalized = filePath.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        normalized = stripKnownExtension(normalized);
        return normalized.replace('/', '.');
    }

    /**
     * Плоское точечное имя идентификатора: {@code a.b.c}.
     * <p>
     * Не идёт через рендерер языка намеренно: имя нужно анализу, а не выводу, и должно быть
     * одинаковым независимо от того, каким разделителем язык печатает области видимости.
     * Алиас разворачивается — искать файл надо по настоящему имени.
     */
    public static String dottedName(Identifier identifier) {
        return switch (identifier) {
            case Alias alias -> dottedName(alias.getRealName());
            case ScopedIdentifier scoped -> scoped.getScopeResolution().stream()
                    .map(ImportPathConverter::dottedName)
                    .collect(Collectors.joining("."));
            case QualifiedIdentifier qualified ->
                    dottedName(qualified.getScope()) + "." + dottedName(qualified.getMember());
            case SimpleIdentifier simple -> simple.getName();
            default -> identifier.toString();
        };
    }

    /**
     * Первый сегмент точечного имени: по нему опознаётся принадлежность библиотеке.
     */
    public static String topLevelSegment(String dottedName) {
        int dot = dottedName.indexOf('.');
        return dot < 0 ? dottedName : dottedName.substring(0, dot);
    }

    private static String stripKnownExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot <= 0 || dot < path.lastIndexOf('/')) {
            return path;
        }
        String extension = path.substring(dot + 1);
        return SOURCE_EXTENSIONS.contains(extension.toLowerCase()) ? path.substring(0, dot) : path;
    }
}

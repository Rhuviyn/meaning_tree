package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigParameters;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.ConfigValue;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;
import org.vstu.meaningtree.utils.tokens.TokenType;

import java.util.Map;

public class JavaTranslator extends LanguageTranslator {
    public static final int ID = 2;

    public JavaTranslator(Map<String, Object> rawConfig) {
        super(rawConfig);
        this.init(new JavaParser(this), new JavaViewer(this));
    }

    public JavaTranslator() {
        super();
        this.init(new JavaParser(this), new JavaViewer(this));
    }

    public JavaTranslator(Config config) {
        super(config);
        this.init(new JavaParser(this), new JavaViewer(this));
    }

    @Override
    public int getLanguageId() {
        return ID;
    }

    @Override
    public String getLanguageName() {
        return "java";
    }

    /**
     * Печатать ли классы стандартной библиотеки полным именем ({@code java.util.ArrayList})
     * вместо простого с {@code import}.
     * <p>
     * По умолчанию {@code true}: полное имя ни с чем не конфликтует и не требует шапки, что
     * важно для фрагмента кода, который вставляют куда-то ещё. При {@code false} вывод ближе к
     * тому, как Java пишут вручную, но тогда он обязан идти вместе со своими импортами — и
     * потому осмыслен только для целой единицы трансляции.
     */
    public static final String PREFER_QUALIFIED_REFERENCES = "preferQualifiedReferences";

    @Override
    protected Config extendConfigParameters() {
        var preferQualifiedReferences = ConfigParameters.registerIfNotExists(
                this, PREFER_QUALIFIED_REFERENCES, new ConfigValue(true), ConfigScope.VIEWER);
        return new Config(preferQualifiedReferences);
    }

    @Override
    public LanguageTokenizer getTokenizer() {
        return new JavaTokenizer(this);
    }

    @Override
    public String prepareCode(String code) {
        if (isExpressionMode()) {
            if (!code.endsWith(";")) {
                code += ";";
            }
            code = String.format("class Main { public static void main(String[] args) {%s} }", code);
        }

        return code;
    }

    @Override
    public TokenList prepareCode(TokenList list) {
        if (isExpressionMode()) {
            if (!list.getLast().type.equals(TokenType.SEPARATOR)) {
                list.add(new Token(";", TokenType.SEPARATOR));
            }

            TokenList final_ = getTokenizer().tokenize("class Main { public static void main(String[] args) {;%s} }", true);
            int marker = final_.indexOf(
                    final_.stream().filter((Token t) -> t.value.equals(";")).findFirst().orElse(null)
            );
            final_.remove(marker);
            final_.addAll(
                    marker,
                    list
            );
            return final_;
        }

        return list;
    }

    @Override
    public LanguageTranslator clone() {
        return new JavaTranslator(this.getConfig());
    }

    @Override
    public LanguageTranslator clone(Config config) {
        return new JavaTranslator(config);
    }

}

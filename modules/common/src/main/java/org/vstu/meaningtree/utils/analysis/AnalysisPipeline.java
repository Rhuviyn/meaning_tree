package org.vstu.meaningtree.utils.analysis;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEvaluator;
import org.vstu.meaningtree.utils.analysis.imports.ImportResolver;
import org.vstu.meaningtree.utils.analysis.loops.LoopIterationAnalyzer;
import org.vstu.meaningtree.utils.analysis.symbols.OverloadCallResolver;
import org.vstu.meaningtree.utils.analysis.symbols.OverloadIndexer;
import org.vstu.meaningtree.utils.analysis.symbols.OverrideResolver;
import org.vstu.meaningtree.utils.analysis.symbols.SymbolResolver;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionAnalyzer;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionReport;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Objects;
import java.util.Optional;

/**
 * Конвейер анализа как самостоятельная вызываемая единица.
 * <p>
 * Раньше конвейер был зашит в {@code LanguageParser}: единственная точка входа — только через
 * разбор исходника, единственный источник {@code ScopeTable} — контекст парсера. Здесь тот же
 * конвейер принимает {@link MeaningTree} и {@link ScopeTable} явными аргументами, поэтому его
 * можно запустить на дереве, собранном программно, десериализованном из JSON, или доработать
 * задним числом после правки дерева. {@code LanguageParser} — один из вызывающих, а не
 * единственный вход.
 * <p>
 * Языковые правила (перегрузки, преобразования типов, резолвинг импортов, контекст проекта)
 * конвейер берёт у {@link LanguageTranslator}, а не принимает по отдельности: транслятор — уже
 * существующая точка, где эти правила для языка собраны вместе, и он же знает, задан ли контекст
 * проекта. Вызывающему не нужно самому решать, звать ли резолвинг импортов отдельно — это часть
 * {@link #run()}.
 * <p>
 * Проходы образуют одну неделимую единицу работы с жёстким порядком: {@code OverrideResolver}
 * заполняет {@code overriddenFrom} и не зависит от остальных проходов, поэтому идёт первым, вне
 * жёсткой цепочки данных. Дальше {@code SymbolResolver} дописывает типы полей, найденных по
 * присваиваниям вида {@code self.x = ...}; полная таблица символов нужна
 * {@code OverloadIndexer}, который наполняет {@code ScopeTable} членами типов и группами
 * перегрузок; этими группами пользуется {@code OverloadCallResolver}, выбирая перегрузку для
 * места вызова, и {@code TypeConversionAnalyzer}, разбирающий сами преобразования. Оценки
 * {@code ExpressionValueEvaluator} нужны {@code LoopIterationAnalyzer}. Каждому следующему
 * нужен <b>полный</b> результат предыдущего по всему дереву, поэтому проходы нельзя ни
 * переставить, ни слить в один обход, ни включить частично — порядок здесь зависимость по
 * данным, а не настройка.
 * <p>
 * Резолвинг импортов стоит последним и особняком: это единственный проход с внешней
 * зависимостью — файловой системой, а не только данными дерева. Без контекста проекта
 * ({@code translator.hasSourceContext()}) или без резолвера для языка он просто не выполняется,
 * и узлы остаются без метаданных — штатное состояние для перевода одиночного файла.
 */
public final class AnalysisPipeline {
    private final MeaningTree tree;
    private final ScopeTable scope;
    private final LanguageTranslator translator;

    private TypeConversionReport typeConversionReport;

    public AnalysisPipeline(MeaningTree tree, ScopeTable scope, LanguageTranslator translator) {
        this.tree = Objects.requireNonNull(tree, "tree must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.translator = Objects.requireNonNull(translator, "translator must not be null");
    }

    /** Запускает проходы анализа в единственно допустимом порядке; см. class javadoc. */
    public AnalysisPipeline run() {
        new OverrideResolver(tree, scope).resolve();
        new SymbolResolver(tree, scope).resolve();
        new OverloadIndexer(tree, scope, translator.getOverloadSemantics()).index();
        TypeConversionAnalyzer typeConversionAnalyzer = new TypeConversionAnalyzer(translator.getTypeConversionSemantics());
        new OverloadCallResolver(tree, scope, typeConversionAnalyzer).resolveAll();
        typeConversionReport = typeConversionAnalyzer.analyze(tree, scope);
        ExpressionValueEvaluator evaluator = new ExpressionValueEvaluator(tree, scope);
        evaluator.analyze();
        new LoopIterationAnalyzer().analyze(tree, evaluator);
        resolveImports();
        return this;
    }

    private void resolveImports() {
        ImportResolver resolver = translator.getImportResolver();
        if (resolver == null || !translator.hasSourceContext()) {
            return;
        }
        resolver.resolve(
                tree,
                translator.getProjectRootPath().orElseThrow(),
                translator.getCurrentFileRelPath().orElseThrow()
        );
    }

    public Optional<TypeConversionReport> getTypeConversionReport() {
        return Optional.ofNullable(typeConversionReport);
    }
}

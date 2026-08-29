package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedConversionException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Матричное умножение (Python `@`) в C++ невыразимо: ни оператора, ни функции `matmul` в языке
 * нет. Раньше вьюер печатал вызов `matmul(a, b)`, который не собрался бы, — отказ честнее.
 */
class CppMatMulTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    @Test
    void matrixMultiplicationIsRefusedInsteadOfEmittingACallThatDoesNotExist() {
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree("c = a @ b");
        CppTranslator cpp = new CppTranslator(CONFIG);

        // Отказ приходит из pre-flight проверки, а не из рендерера: узел объявлен явно
        // неподдерживаемым, поэтому до отрисовки дело не доходит вовсе
        UnsupportedConversionException error =
                assertThrows(UnsupportedConversionException.class, () -> cpp.getCode(tree));
        assertTrue(error.getMessage().contains("MatMulOp"), error.getMessage());
        assertFalse(error.getMessage().contains("matmul("), error.getMessage());
    }

    @Test
    void supportAnalysisReportsMatrixMultiplicationBeforeRendering() {
        // Отказ обязан ловиться pre-flight проверкой, иначе о нём узнают только исключением
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree("c = a @ b");
        CppTranslator cpp = new CppTranslator(CONFIG);

        assertTrue(!cpp.analyzeSupport(tree).isSupported());
    }
}

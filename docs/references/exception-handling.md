# Обработка исключений в трёх языках

Описывает текущее поведение разбора и генерации для `ExceptionCatchStatement`,
`CatchClause` и `RaiseExceptionStatement`. Сами узлы описаны в
[node-types.md](node-types.md), JSON-форма — в [json-format.d.ts](json-format.d.ts).
Java-try-with-resources — это тот же `ExceptionCatchStatement` с непустым списком ресурсов,
см. [resource-context.md](resource-context.md).

## Что во что разбирается

| Язык | tree-sitter | Узел |
| --- | --- | --- |
| Java | `try_statement`, `catch_clause`, `finally_clause` | `ExceptionCatchStatement` + `CatchClause` |
| Java | `throw_statement` | `RaiseExceptionStatement` |
| Python | `try_statement`, `except_clause`, `else_clause`, `finally_clause` | `ExceptionCatchStatement` + `CatchClause` |
| Python | `raise_statement` | `RaiseExceptionStatement` |
| C++ | `try_statement`, `catch_clause` | `ExceptionCatchStatement` + `CatchClause` |
| C++ | `throw_statement` | `RaiseExceptionStatement` |

Формы, которые сводятся к одному и тому же `CatchClause`:

| Форма | `exception_types` | `name` |
| --- | --- | --- |
| Java `catch (E e)` | `[E]` | `e` |
| Java `catch (A \| B e)` | `[A, B]` | `e` |
| Python `except E as e:` | `[E]` | `e` |
| Python `except (A, B) as e:` | `[A, B]` | `e` |
| Python `except E:` | `[E]` | нет |
| Python `except:` | пусто | нет |
| C++ `catch (const E& e)` | `[E]` | `e` |
| C++ `catch (...)` | пусто | нет |

`const` и ссылка в C++ описывают способ передачи, а не тип исключения, поэтому в дерево
попадает тип без них; при выводе на C++ они дописываются обратно.

## Как узел выводится обратно

| Ситуация | Java | Python | C++ |
| --- | --- | --- | --- |
| несколько типов в ветви | `catch (A \| B e)` | `except (A, B) as e:` | ветвь размножается по одной на тип, тело копируется (`MultiCatchSplitter`) |
| ветвь без типов | `catch (Exception e)` | `except:` | `catch (...)` |
| ветвь без имени | подставляется `e` | имя не печатается | имя не печатается |
| ветвь `else` | флаг + `if` после конструкции (`TryElseLowerer`) | `else:` | флаг + `if` после конструкции |
| ветвь `finally` | `finally` | `finally:` | не поддерживается (`TryFinallyFeature`) |
| владение ресурсами | `try (R r = e)` | `with` внутри `try` | объявление и `delete` в теле `try` |
| возбуждение без значения | не поддерживается (`BareRaiseFeature`) | `raise` | `throw;` |

`TryElseLowerer` взводит флаг последним оператором тела `try` и проверяет его в `if` после
конструкции. Когда у той же конструкции есть ещё и `finally`, вся тройка заворачивается в
отдельный `try ... finally`: в Python `finally` выполняется после ветви `else`, а внешний
`finally` той же конструкции выполнился бы раньше.

## Область видимости переменной исключения

Переменная исключения — это имя на `CatchClause`, а не `VariableDeclaration`, поэтому в
`ScopeTable` регистрируется только её тип (`registerCatchVariable`), и только когда ветвь
перехватывает ровно один тип: у multi-catch и python-кортежа статический тип — надтип
перечисленных, вычислять который модель пока не умеет. Регистрация идёт в область,
охватывающую конструкцию, — как у переменной `for`-each, тело ветви её видит.

## Что не поддерживается

- `raise ... from ...` (Python): у узла нет поля причины, разбор отвергается
  `UnsupportedParsingException`.
- `except*` и группы исключений (Python): разбор отвергается.
- `throws` в сигнатуре (Java), `noexcept` (C++): это свойство декларации, а не данного узла.
- Python `raise E("x")` разбирается как обычный вызов, а не создание объекта, поэтому в Java
  выводится `throw E("x")` без `new`. Это общее ограничение разбора Python, а не этой
  конструкции: `x = E()` ведёт себя так же.
- Python `except:` и `except Exception:` в одной конструкции дают в Java две одинаковые ветви
  `catch (Exception e)`, что Java не компилирует.

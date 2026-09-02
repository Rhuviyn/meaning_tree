# Владение ресурсами в трёх языках

Описывает текущее поведение разбора и генерации для `ResourceContextStatement` и поля
`resourceDeclarations` у `ExceptionCatchStatement`. Сами узлы описаны в
[node-types.md](node-types.md), JSON-форма — в [json-format.d.ts](json-format.d.ts),
обработка исключений — в [exception-handling.md](exception-handling.md).

## Два узла на одну конструкцию

Java-`try (...)` может нести и ресурсы, и ветви `catch`/`finally` — в tree-sitter это один
узел `try_with_resources_statement`. Поэтому:

| Конструкция | Узел |
| --- | --- |
| `try (R r = e) { }` без ветвей, `with e as r:` | `ResourceContextStatement` |
| `try (R r = e) { } catch ...` / `... finally ...` | `ExceptionCatchStatement` с непустым `resourceDeclarations` |

Разделение проходит по наличию ветвей, а не по языку: java-`try (r) { }` без `catch` даёт
`ResourceContextStatement`, как и python-`with`.

## Что во что разбирается

| Язык | tree-sitter | Узел |
| --- | --- | --- |
| Java | `try_with_resources_statement`, `resource_specification`, `resource` | `ResourceContextStatement` или `ExceptionCatchStatement` |
| Python | `with_statement`, `with_clause`, `with_item`, `as_pattern` | `ResourceContextStatement` |
| C++ | — | конструкции нет, обратный разбор не делается |

Формы одного ресурса:

| Форма | Узел ресурса |
| --- | --- |
| Java `R r = e` | `VariableDeclaration(R, r, e)` |
| Java `existing` | выражение `existing` |
| Python `e as r` | `VariableDeclaration(UnknownType, r, e)` |
| Python `e` | выражение `e` |

Имена ресурсов регистрируются в области видимости до разбора тела: узел-объявление лежит в
заголовке конструкции, а обычный путь наполнения области (`BodyConstructor`) видит только узлы
тела — так же, как переменная `catch`.

## Как узел выводится обратно

| Ситуация | Java | Python | C++ |
| --- | --- | --- | --- |
| владение без ветвей | `try (R r = e) { }` | `with e as r:` | блок с объявлением и `delete` |
| владение с `catch`/`finally` | `try (R r = e) { } catch ...` | `with` внутри `try` (`ResourceContextLowerer.nest`) | объявления и `delete` вносятся в тело `try` |
| ресурс без имени | `try (e)` | `with e:` | оператор-выражение, освобождения нет |
| несколько ресурсов | `try (a; b)` | `with a, b:` | объявления по порядку, `delete` в обратном |

В C++ владение ресурсами разворачивается `ResourceContextLowerer.flatten` в плоский блок:
объявления, тело, `delete` каждого именованного ресурса в обратном порядке захвата. У
`try` объявления вносятся внутрь его тела, а не перед конструкцией, потому что в Java ошибку
захвата ресурса ловят те же ветви, что и ошибки тела. Обратно C++ в этот узел не разбирается:
получившийся блок неотличим от обычного кода.

Тип ресурса, пришедшего из Python, неизвестен (`UnknownType`), поэтому в Java он выводится как
`var` при наличии инициализатора и как `Object` без него. Java требует от ресурса
`AutoCloseable`, так что `Object` не скомпилируется — это общее ограничение `UnknownType`, а не
данной конструкции.

## Что не поддерживается

- Разбор C++ обратно в `ResourceContextStatement`: плоский блок с `delete` неотличим от
  обычного кода.
- `async with` (Python): признака асинхронности у узла нет, разбор отвергается
  `UnsupportedParsingException`.
- Распаковка кортежа в `as`-цели (`with a() as (x, y)`): имя ресурса — один
  `SimpleIdentifier`, набора имён узел не хранит, разбор отвергается.
- `delete` в C++ применяется к любому именованному ресурсу, в том числе не-указателю: модель
  не различает владение указателем и значением.

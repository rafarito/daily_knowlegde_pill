# Diagramas de Classes: daily-knowledge

> Complementa o [documento de arquitetura](arquitetura.md). Todas as classes ficam no pacote
> `dev.rafael.dailyknowledge`, em `src/main/java`. Os diagramas refletem o código atual.

**Convenções usadas nos diagramas**

| Notação | Significado |
|---|---|
| `<<interface>>` / `<<record>>` / `<<enumeration>>` / `<<utility>>` | Tipo do elemento (`utility` = classe `final` só com métodos estáticos) |
| `+` / `-` / `~` (ou sem símbolo) | público / privado / visibilidade de pacote |
| `$` no fim do membro | membro estático |
| `..\|>` | implementa interface |
| `--\|>` | herança |
| `*--` | composição (o todo é dono das partes) |
| `-->` | associação (mantém referência) |
| `..>` | dependência (usa, cria ou lança) |

## Sumário

1. [Visão geral das dependências](#1-visão-geral-das-dependências)
2. [Núcleo: orquestração do fluxo diário](#2-núcleo-orquestração-do-fluxo-diário)
3. [Porta de pesquisa: Researcher e AgyClient](#3-porta-de-pesquisa-researcher-e-agyclient)
4. [Verificação e normalização de URLs](#4-verificação-e-normalização-de-urls)
5. [Persistência: histórico e commit](#5-persistência-histórico-e-commit)
6. [Apresentação e entrega](#6-apresentação-e-entrega)
7. [Modelo de dados (records imutáveis)](#7-modelo-de-dados-records-imutáveis)
8. [Composição: o que o Main monta](#8-composição-o-que-o-main-monta)
9. [Dublês de teste](#9-dublês-de-teste)

---

## 1. Visão geral das dependências

Todas as classes, mostrando só quem depende de quem. As camadas foram agrupadas para leitura, mas não existem como pacotes Java separados.

```mermaid
flowchart TB
    subgraph ENTRADA["Entrada / composição"]
        Main
    end
    subgraph NUCLEO["Núcleo (regras do fluxo)"]
        DailyRunner
        PromptBuilder
        Themes
        MessageFormatter
    end
    subgraph PORTAS["Portas"]
        Researcher{{"Researcher"}}
        Sender{{"Sender"}}
    end
    subgraph ADAPTADORES["Adaptadores / infraestrutura"]
        AgyClient
        TelegramClient
        Verifier
        SeenStore
        GitCommitter
    end
    subgraph MODELO["Modelo e utilitários"]
        Discovery
        Verification
        SeenEntry
        Config
        UrlNormalizer
        Json
        Log
    end

    Main --> DailyRunner
    Main --> AgyClient
    Main --> TelegramClient
    Main --> Verifier
    Main --> SeenStore
    Main --> GitCommitter
    Main --> Config
    Main --> MessageFormatter

    DailyRunner --> Researcher
    DailyRunner --> Sender
    DailyRunner --> Verifier
    DailyRunner --> SeenStore
    DailyRunner --> PromptBuilder
    DailyRunner --> MessageFormatter

    AgyClient -.->|"implementa"| Researcher
    TelegramClient -.->|"implementa"| Sender

    PromptBuilder --> Themes
    MessageFormatter --> PromptBuilder
    MessageFormatter --> Themes
    MessageFormatter --> UrlNormalizer
    Verifier --> UrlNormalizer
    SeenStore --> UrlNormalizer
    AgyClient --> Config
    AgyClient --> Json
    SeenStore --> Json
    TelegramClient --> Json
    Verifier --> Json
```

**O que observar**
- O **`DailyRunner` não depende de nenhum adaptador concreto de I/O externo caro** (agy, Telegram). Ele conhece só as portas `Researcher` e `Sender`. A exceção é o `Verifier` (ver dívida T-2 na arquitetura).
- O `Main` é o **único** ponto que conhece as implementações concretas (*composition root*).
- `UrlNormalizer`, `Json`, `Themes` e `MessageFormatter` são folhas sem estado, fáceis de testar isoladamente.

---

## 2. Núcleo: orquestração do fluxo diário

```mermaid
classDiagram
    direction LR

    class DailyRunner {
        -LocalDate date
        -int maxAttempts
        -SeenStore store
        -PromptBuilder promptBuilder
        -Researcher researcher
        -Verifier verifier
        -Sender sender
        -boolean persist
        -Supplier~ZonedDateTime~ clock
        +DailyRunner(LocalDate, int, SeenStore, PromptBuilder, Researcher, Verifier, Sender, boolean, Supplier)
        +run() Optional~Discovery~
        -save() void
        -now() String
    }

    class Researcher {
        <<interface>>
        +research(String prompt, int attempt) Discovery
    }

    class Sender {
        <<interface>>
        +send(String html) void
    }

    class Verifier {
        +verify(String url) Verification
    }

    class SeenStore {
        +all() List~SeenEntry~
        +contains(String url) boolean
        +add(SeenEntry entry) void
        +save() void
    }

    class PromptBuilder {
        +build(LocalDate date, List~SeenEntry~ seen) String
    }

    class MessageFormatter {
        <<utility>>
        +daily(LocalDate, Discovery, Verification)$ String
    }

    class Discovery {
        <<record>>
    }
    class Verification {
        <<record>>
    }
    class SeenEntry {
        <<record>>
    }

    DailyRunner --> Researcher : pesquisa
    DailyRunner --> Verifier : valida URL
    DailyRunner --> SeenStore : consulta e grava
    DailyRunner --> PromptBuilder : monta prompt
    DailyRunner --> Sender : entrega
    DailyRunner ..> MessageFormatter : formata
    DailyRunner ..> SeenEntry : cria daily/rejected
    Researcher ..> Discovery : produz
    Verifier ..> Verification : produz
```

**Decisões de design refletidas aqui**
- **Injeção por construtor** de todas as dependências, inclusive o relógio (`Supplier<ZonedDateTime>`). Assim, `sent_at` é determinístico nos testes.
- A flag `persist` (falsa no `--dry-run`) faz o `DailyRunner` executar o fluxo inteiro sem gravar o histórico.
- `run()` devolve `Optional<Discovery>`, e não `boolean`. O `Main` usa o nome do projeto na mensagem de commit.
- Exceções: `ResearchException` é **capturada** dentro do loop (conta como tentativa perdida). Uma `IOException` do `Sender` ou do `save()` **sobe** para o `Main`, porque sem envio ou sem gravação não faz sentido continuar tentando.

---

## 3. Porta de pesquisa: Researcher e AgyClient

```mermaid
classDiagram
    direction LR

    class Researcher {
        <<interface>>
        +research(String prompt, int attempt) Discovery
    }

    class ResearchException {
        +ResearchException(String message)
        +ResearchException(String message, Throwable cause)
    }

    class Exception

    class AgyClient {
        -Config cfg
        -String schema
        -LocalDate date
        +AgyClient(Config cfg, String schema, LocalDate date)
        +research(String prompt, int attempt) Discovery
        ~parse(String stdout)$ Discovery
    }

    class Config {
        <<record>>
        +String agyBin
        +String agyModel
        +String agyTimeout
        +agyTimeoutDuration() Duration
        +workDir() Path
        +logsDir() Path
    }

    class Discovery {
        <<record>>
        +validate() void
    }

    class Json {
        <<utility>>
        +ObjectMapper MAPPER$
    }

    class ProcessBuilder {
        <<JDK>>
    }

    AgyClient ..|> Researcher
    ResearchException --|> Exception
    Researcher ..> ResearchException : lança
    AgyClient --> Config
    AgyClient ..> ProcessBuilder : spawn agy
    AgyClient ..> Json : lê stdout
    AgyClient ..> Discovery : treeToValue + validate
```

**Responsabilidades do `AgyClient`**

| Etapa | Implementação |
|---|---|
| Montar o comando | `agy -p <prompt> --model … --sandbox --dangerously-skip-permissions --output-format json --json-schema <schema> --print-timeout …` |
| Isolar | `directory(work/)`, stdin `/dev/null` |
| Capturar | stdout em `logs/agy-<data>-attemptN.out.json` e stderr em `.err.txt` (evita deadlock de pipe e preserva evidência) |
| Limitar | `waitFor(print-timeout + 1 min)`; se estourar, `destroyForcibly()` |
| Interpretar | `parse()`: `status == SUCCESS` → `structured_output` → `Discovery` → `validate()` |
| Falhar | Qualquer problema vira `ResearchException`, com a mensagem pronta para o log |

`parse()` é estático e tem visibilidade de pacote: foi isolado do I/O para poder ser testado diretamente com strings.

---

## 4. Verificação e normalização de URLs

```mermaid
classDiagram
    direction LR

    class Verifier {
        -String UA$
        -HttpClient http
        -String githubApiBase
        +Verifier()
        ~Verifier(HttpClient http, String githubApiBase)
        +verify(String url) Verification
        ~fromGithubApi(String ownerRepo) Optional~Verification~
        ~isReachable(String url) boolean
        -status(URI uri, String method) int
    }

    class Verification {
        <<record>>
        +boolean found
        +String canonicalUrl
        +Integer stars
        +String license
        +String pushedAt
        +String failureReason
        +notFound(String reason)$ Verification
        +reachable(String url)$ Verification
    }

    class UrlNormalizer {
        <<utility>>
        +normalize(String url)$ String
        +githubRepo(String url)$ Optional~String~
        +host(String url)$ String
    }

    class HttpClient {
        <<JDK>>
    }

    Verifier --> HttpClient : redirecionamentos NORMAL
    Verifier ..> UrlNormalizer : githubRepo()
    Verifier ..> Verification : cria
```

**Lógica do `verify(url)`**

```mermaid
flowchart LR
    A["verify(url)"] --> B{"githubRepo(url) presente?"}
    B -->|"sim"| C["fromGithubApi(owner/repo)"]
    C -->|"200"| OK["Verification(found, html_url, stars, spdx, pushed_at)"]
    C -->|"404"| NF["Verification.notFound"]
    C -->|"vazio: 403, 5xx, rede"| D["isReachable(url)"]
    B -->|"não"| D
    D -->|"HEAD menor que 400 ou GET menor que 400"| R["Verification.reachable(url)"]
    D -->|"falhou"| NF2["Verification.notFound"]
```

**Decisões de design refletidas aqui**
- `fromGithubApi` devolve `Optional.empty()` quando a resposta **não é conclusiva**, o que é diferente de "não existe". Assim, um rate limit não vira uma rejeição permanente.
- O construtor com visibilidade de pacote `Verifier(HttpClient, String)` permite apontar para outro host em testes.
- `license` vem como `null` quando o GitHub responde `NOASSERTION`. Quem decide o fallback para a licença do modelo é o `MessageFormatter`, e não o `Verifier`.

---

## 5. Persistência: histórico e commit

```mermaid
classDiagram
    direction LR

    class SeenStore {
        -Path file
        -List~SeenEntry~ entries
        -boolean dirty
        ~SeenStore(Path file, List~SeenEntry~ entries)
        +load(Path file)$ SeenStore
        +all() List~SeenEntry~
        +contains(String url) boolean
        +add(SeenEntry entry) void
        +isDirty() boolean
        +save() void
        +sentInWeekEnding(LocalDate today) List~SeenEntry~
    }

    class FileFormat {
        <<record>>
        +List~SeenEntry~ projects
    }

    class SeenEntry {
        <<record>>
        +String name
        +String url
        +String tagline
        +String sentAt
        +String source
        +Boolean themeMatch
        +String reason
        +String SEED$
        +String DAILY$
        +String REJECTED$
        +daily(String, String, String, String, boolean)$ SeenEntry
        +rejected(String, String, String, String)$ SeenEntry
    }

    class GitCommitter {
        <<utility>>
        +commitSeen(Path projectDir, String message)$ void
        -run(Path dir, List~String~ cmd)$ void
    }

    class Json {
        <<utility>>
        +ObjectMapper MAPPER$
    }

    class UrlNormalizer {
        <<utility>>
    }

    SeenStore *-- "0..*" SeenEntry : entries
    SeenStore ..> FileFormat : serializa/desserializa
    FileFormat o-- "0..*" SeenEntry
    SeenStore ..> Json
    SeenStore ..> UrlNormalizer : contains() compara normalizado
    GitCommitter ..> SeenStore : commit de data/seen.json (via arquivo)
```

**Decisões de design refletidas aqui**
- `FileFormat` é um record **interno** que fixa o formato `{"projects": [...]}` no disco. Com isso, campos de nível superior (versão, metadados) podem ser adicionados depois sem quebrar quem já lê o arquivo.
- `save()` grava em `seen.json.tmp` e faz `Files.move(..., ATOMIC_MOVE)`.
- A flag `dirty` deixa o `Main` decidir se há algo para commitar, sem comparar arquivos.
- `contains()` normaliza **os dois lados** na hora da comparação. Assim, entradas antigas gravadas sem normalização continuam funcionando.
- `SeenEntry` usa `@JsonInclude(NON_NULL)`, então campos ausentes não poluem o JSON (o seed tem só `name`, `url` e `source`).
- `GitCommitter` não conhece o `SeenStore` em código: a ligação entre os dois é o **arquivo** (conector de dados compartilhados, C6).

---

## 6. Apresentação e entrega

```mermaid
classDiagram
    direction LR

    class MessageFormatter {
        <<utility>>
        +int TELEGRAM_LIMIT$
        -int MIN_FIELD$
        +daily(LocalDate date, Discovery d, Verification v)$ String
        +weekly(LocalDate today, List~SeenEntry~ week)$ String
        -renderDaily(LocalDate, Discovery, Verification, Map, boolean, boolean)$ String
        -longest(Map texts)$ Field
        ~sourceLinks(List~String~ sources)$ String
        ~formatStars(int stars)$ String
        ~esc(String s)$ String
        -attr(String s)$ String
    }

    class Field {
        <<enumeration>>
        WHAT
        CONTEXT
        ORIGIN
        WHY
    }

    class Sender {
        <<interface>>
        +send(String html) void
    }

    class TelegramClient {
        -HttpClient http
        -String token
        -String chatId
        +TelegramClient(String token, String chatId)
        +send(String html) void
    }

    class DryRunSender {
        <<lambda>>
        +send(String html) void
    }

    class PromptBuilder {
        <<utility>>
        ~weekdayName(LocalDate)$ String
    }

    class Themes {
        <<utility>>
        +forDay(DayOfWeek day)$ String
    }

    MessageFormatter *-- Field
    MessageFormatter ..> Themes : tema no cabeçalho
    MessageFormatter ..> PromptBuilder : nome do dia
    MessageFormatter ..> UrlNormalizer : host das fontes
    TelegramClient ..|> Sender
    DryRunSender ..|> Sender
```

**Decisões de design refletidas aqui**
- `MessageFormatter` só tem **funções puras**: a mesma entrada gera sempre a mesma saída, sem I/O. Por isso os testes comparam strings exatas.
- O `enum Field` lista **apenas** os campos que podem ser encurtados. Título, link, tagline e `try_it` não estão nele de propósito.
- Ordem de degradação (D18): encurtar o maior `Field`, depois remover as fontes, depois as alternativas.
- O `TelegramClient` falha no **construtor** se o token ou o chat ID estiverem ausentes. O erro aparece antes da pesquisa, sem gastar cota do agy.
- O `DryRunSender` não é uma classe: é uma lambda no `Main` (`html -> System.out.println(...)`). Como `Sender` tem um único método, funciona como interface funcional.

---

## 7. Modelo de dados (records imutáveis)

```mermaid
classDiagram
    direction TB

    class Discovery {
        <<record>>
        +String name
        +String url
        +String website
        +String category
        +Boolean themeMatch
        +String license
        +String language
        +String tagline
        +String whatItDoes
        +String contextAndNeed
        +String origin
        +String whyInteresting
        +String tryIt
        +List~String~ alternatives
        +List~String~ sources
        +validate() void
        +isThemeMatch() boolean
    }

    class Verification {
        <<record>>
        +boolean found
        +String canonicalUrl
        +Integer stars
        +String license
        +String pushedAt
        +String failureReason
    }

    class SeenEntry {
        <<record>>
        +String name
        +String url
        +String tagline
        +String sentAt
        +String source
        +Boolean themeMatch
        +String reason
    }

    class Config {
        <<record>>
        +Path projectDir
        +String telegramBotToken
        +String telegramChatId
        +String agyBin
        +String agyModel
        +String agyTimeout
        +int maxAttempts
        +load(Path projectDir)$ Config
        ~parseEnv(String content)$ Map
        +agyTimeoutDuration() Duration
        +seenFile() Path
        +workDir() Path
        +logsDir() Path
        +toString() String
    }

    Discovery ..> SeenEntry : name, tagline, themeMatch
    Verification ..> SeenEntry : canonicalUrl vira url
```

**Por que records**
- **Imutabilidade:** um `Discovery` que veio do LLM não pode ser alterado no meio do fluxo. O enriquecimento vem num objeto **separado** (`Verification`), e a combinação dos dois só acontece no `MessageFormatter` e na criação do `SeenEntry`. Assim, fica sempre claro o que veio do modelo e o que veio do GitHub.
- **Construtor compacto como fronteira de saneamento:** `Discovery` troca listas nulas por `List.of()` e strings vazias ou `"null"` por `null` em `website`/`origin`. O resto do código não precisa tratar essas variações.
- **Mapeamento direto:** com o `ObjectMapper` em `SNAKE_CASE`, `whatItDoes` ↔ `what_it_does` sem nenhuma anotação.
- **Tipos `Boolean`/`Integer` em vez de primitivos** onde "ausente" é diferente de "falso/zero": `themeMatch` ausente é violação de contrato; `stars` ausente significa "não é GitHub".
- **`Config.toString()` sobrescrito:** o `toString` gerado automaticamente imprimiria o token.

---

## 8. Composição: o que o Main monta

```mermaid
classDiagram
    direction LR

    class Main {
        <<utility>>
        +ZoneId ZONE$
        +main(String[] args)$ void
        ~projectDir()$ Path
    }

    class Config
    class Log {
        <<utility>>
        +init(Path logsDir, ZonedDateTime now)$ void
        +info(String)$ void
        +warn(String)$ void
        +error(String)$ void
    }
    class SeenStore
    class TelegramClient
    class DryRunSender
    class DailyRunner
    class AgyClient
    class Verifier
    class PromptBuilder
    class MessageFormatter
    class GitCommitter

    Main ..> Config : load(projectDir)
    Main ..> Log : init
    Main ..> SeenStore : load(seen.json)
    Main ..> TelegramClient : cria (modo normal)
    Main ..> DryRunSender : cria (--dry-run)
    Main ..> AgyClient : cria com schema do classpath
    Main ..> Verifier : cria
    Main ..> PromptBuilder : fromResource()
    Main ..> DailyRunner : cria e injeta tudo, run()
    Main ..> MessageFormatter : weekly() aos domingos ou --weekly-only
    Main ..> GitCommitter : commitSeen() se store.isDirty()
    Log ..> Main : usa ZONE (dívida T-1)
    SeenStore ..> Main : usa ZONE (dívida T-1)
```

**Sequência de montagem no `main()`**
1. Interpreta as flags (`--dry-run`, `--weekly-only`).
2. `projectDir()`: pai de `target/` se rodando do jar; senão, o diretório atual.
3. `Config.load` → `Log.init` → `SeenStore.load`.
4. Escolhe o `Sender`: lambda de stdout ou `TelegramClient`.
5. Se não for `--weekly-only`, monta o `DailyRunner` com `AgyClient`, `Verifier`, `PromptBuilder` e relógio, e executa.
6. Se for domingo ou `--weekly-only`, envia `MessageFormatter.weekly(...)`.
7. Se `store.isDirty()` e não for dry-run, chama `GitCommitter.commitSeen(...)`.
8. `System.exit(0 | 1)`.

As setas `Log ..> Main` e `SeenStore ..> Main` aparecem **de propósito**. São dependências de baixo para cima, registradas como dívida T-1 no documento de arquitetura.

---

## 9. Dublês de teste

Os testes (`src/test/java`) usam as portas e os construtores com visibilidade de pacote para rodar **sem rede e sem o agy real**:

```mermaid
classDiagram
    direction LR

    class Researcher {
        <<interface>>
        +research(String prompt, int attempt) Discovery
    }
    class Verifier {
        +verify(String url) Verification
    }
    class Sender {
        <<interface>>
        +send(String html) void
    }

    class ScriptedResearcher {
        <<test>>
        ~Deque~Object~ answers
        ~List~String~ prompts
        +research(String prompt, int attempt) Discovery
    }
    class FakeVerifier {
        <<test>>
        ~Map results
        +verify(String url) Verification
    }
    class ListSender {
        <<test>>
        +send(String html) void
    }
    class FakeAgyScript {
        <<test>>
        +grava_pwd_e_argv()
        +imprime_json_enlatado()
        +exit_n()
    }
    class AgyClient

    ScriptedResearcher ..|> Researcher
    FakeVerifier --|> Verifier
    ListSender ..|> Sender
    AgyClient ..> FakeAgyScript : AGY_BIN aponta para o script
```

| Dublê | Usado em | O que permite verificar |
|---|---|---|
| `ScriptedResearcher` | `DailyRunnerTest` | Sequência de respostas (sucesso, falha, duplicado); os prompts recebidos em cada tentativa, provando que o texto não muda e que o rejeitado entra no `SEEN_LIST` |
| `FakeVerifier` | `DailyRunnerTest` | 404, renomeação via URL canônica, sem HTTP |
| `sent::add` | `DailyRunnerTest` | O que seria enviado e quantas vezes |
| Script `fake-agy.sh` | `AgyClientTest` | Flags exatas passadas ao agy, diretório `work/`, exit codes, saída sem `structured_output`, contrato violado |

O `FakeVerifier` **estende** a classe concreta, enquanto os outros dois implementam interfaces. É a assimetria descrita na dívida T-2.

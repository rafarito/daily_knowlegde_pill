# daily-knowledge

Boletim diário de descobertas de software, entregue no Telegram.

Todo dia às 08:00, um job Java chama o Antigravity CLI (`agy`) em modo headless com o prompt de
[`src/main/resources/prompt.md`](src/main/resources/prompt.md). O job valida o projeto sugerido
(API do GitHub ou HEAD/GET), evita repetições usando [`data/seen.json`](data/seen.json) e envia o
resultado para o Telegram. Aos domingos, envia também um resumo da semana.

## Requisitos

- Java 25+ e Maven 3.9+
- `agy` instalado e já autenticado por uma sessão interativa (`agy` uma vez no terminal)
- `cronie` (ou outro cron) ativo

## 1. Bot do Telegram

1. No Telegram, abra uma conversa com **@BotFather**, envie `/newbot`, escolha nome e username e copie o **token**.
2. Abra uma conversa com o seu bot e mande qualquer mensagem (por exemplo, `/start`).
3. Descubra o chat ID:
   ```bash
   curl -s "https://api.telegram.org/bot<TOKEN>/getUpdates"
   ```
   O valor está em `result[0].message.chat.id`.

## 2. Configuração

```bash
cp .env.example .env   # preencha TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID e GITHUB_TOKEN
```

| Variável | Padrão | Descrição |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | — | token do bot (obrigatório, exceto no `--dry-run`) |
| `TELEGRAM_CHAT_ID` | — | chat que recebe as mensagens |
| `GITHUB_TOKEN` | — | PAT do GitHub (sem scopes) para evitar rate limit de IP (recomendado) |
| `AGY_BIN` | `/usr/bin/agy` | caminho do CLI |
| `AGY_MODEL` | `gemini-3.1-pro-high` | modelo (`agy models` lista os disponíveis) |
| `AGY_TIMEOUT` | `10m` | tempo máximo por tentativa (`s`, `m`, `h`) |
| `MAX_ATTEMPTS` | `2` | tentativas por dia |

## 3. Build e uso

```bash
mvn package                                   # roda os testes e gera target/daily-knowledge.jar
java -jar target/daily-knowledge.jar --dry-run      # imprime a mensagem, não envia e não grava o histórico
java -jar target/daily-knowledge.jar                # execução normal
java -jar target/daily-knowledge.jar --weekly-only  # só o resumo semanal
```

O jar descobre a raiz do projeto pela própria localização (`target/..`), então pode ser chamado de qualquer diretório.

## 4. Agendamento (crontab)

Acrescente esta linha com `crontab -e`, sem apagar as entradas existentes:

```bash
0 8 * * * /usr/bin/java -jar /caminho/para/o/projeto/target/daily-knowledge.jar >> /caminho/para/o/projeto/logs/cron.log 2>&1
```

O cron só dispara se o PC estiver ligado às 08:00.

## Como funciona

1. O `{{DATE}}`, `{{WEEKDAY}}`, `{{THEME}}` (foco por dia da semana) e `{{SEEN_LIST}}` são preenchidos. O restante do prompt não é alterado.
2. O `agy -p` roda em `work/` (diretório vazio), com `--sandbox --dangerously-skip-permissions`, `--output-format json` e `--json-schema` ([schema](src/main/resources/discovery.schema.json)). O job lê só o campo `structured_output`.
3. **Verificação:** URLs do GitHub passam pela API pública, que segue renomeações e devolve a URL canônica, as estrelas, a licença e o último push. Outras URLs passam por HEAD/GET.
4. **Deduplicação:** a URL do modelo e a URL canônica são comparadas com o `seen.json`.
   - Se o projeto for repetido, o job faz uma nova tentativa.
   - Se a URL não existir, o candidato é gravado como `rejected`, não volta mais e já entra no `{{SEEN_LIST}}` da tentativa seguinte.
5. Quando o Telegram confirma o envio, o projeto é gravado no `seen.json` e é feito um commit automático no git local.
6. Aos domingos, depois da descoberta, sai o resumo com os projetos dos últimos 7 dias. Ele é enviado mesmo que a descoberta falhe.

Falhas não geram alerta: ficam em `logs/AAAA-MM-DD.log`, e o processo termina com código 1. A saída bruta de cada tentativa do agy fica em `logs/agy-*.out.json` e `logs/agy-*.err.txt`.

| Dia | Foco |
|---|---|
| Seg | Bibliotecas e SDKs (qualquer linguagem) |
| Ter | Ferramentas CLI |
| Qua | Self-hosted (alternativas a SaaS) |
| Qui | Dados, documentos e conversão (estilo Pandoc) |
| Sex | IA e automação local (estilo Handy) |
| Sáb | Apps desktop e produtividade |
| Dom | Livre / curiosidade / clássicos esquecidos |

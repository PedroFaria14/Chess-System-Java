# Guia do Chess Studio

## Objetivo

Jogar xadrez contra o Stockfish ou frente a frente, com duas pessoas no mesmo dispositivo. O Java valida os lances; o Angular apresenta o tabuleiro e organiza o histórico. Não é necessário cadastrar uma conta.

## Tecnologias

| Parte | Tecnologia |
| --- | --- |
| Motor | Java 21, classes originais de peças e tabuleiro |
| API | Spring Boot 3.5, Bean Validation, Spring Data JPA |
| Persistência | H2 em arquivo para início rápido; PostgreSQL 17 no Docker |
| Migrações | Flyway |
| Frontend | Angular 22, TypeScript 6, Signals, componentes standalone |
| Verificação | JUnit 5, MockMvc, Playwright desktop/mobile |
| Execução empacotada | Docker Compose e Nginx |

## Primeiro uso no Windows

Instale Java 21 e Node.js 24.15 ou superior da linha 24. Confira `java -version` e `node --version`.

No primeiro terminal, na pasta `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

No segundo terminal, na pasta `frontend`:

```powershell
npm ci
npm start
```

O primeiro build precisa de internet para baixar dependências. Depois, abra **http://127.0.0.1:4200/play**.

O Maven global não é necessário. Linux/macOS usam `bash mvnw` no lugar de `.\mvnw.cmd`.

## Como jogar

### Contra o computador

No Windows x64, execute `.\scripts\install-stockfish.ps1` a partir de `backend` antes de iniciar a API. Ele instala o Stockfish 19 oficial e verifica o checksum do download. A instalação local já foi realizada nesta máquina.

Em Linux, use o pacote `stockfish` da distribuição (a descoberta também procura `/usr/games/stockfish`). Em macOS, instale o Stockfish e configure o caminho se necessário. Para um executável em outro local, defina:

```powershell
$env:STOCKFISH_PATH = 'C:\caminho\stockfish.exe'
.\mvnw.cmd spring-boot:run
```

No Docker, o pacote Stockfish da distribuição Ubuntu é instalado na imagem, sem configuração adicional; a versão pode diferir da instalação Windows.

Escolha **Contra o computador**, preencha seu nome e selecione cor e dificuldade. Brancas começam; se escolher pretas, o computador abre a partida e o tabuleiro fica orientado para você.

| Dificuldade | Skill Level | Tempo de análise por lance |
| --- | --- | --- |
| Fácil | 0 | 200 ms |
| Médio | 8 | 500 ms |
| Difícil | 16 | 1.000 ms |

Esses níveis são ajustes do motor, não classificações Elo garantidas. Mesmo o fácil pode desafiar iniciantes. O backend limita o motor a uma thread e 16 MB de hash por análise, com até duas análises simultâneas e timeout de 8 segundos incluindo inicialização.

Enquanto aparece **Stockfish está pensando…**, a interface bloqueia jogadas humanas. Se o motor falhar, a última posição permanece salva; use **Tentar jogada do computador novamente**. Reabrir a partida também retoma o turno pendente.

O modo contra computador não oferece empate por acordo. Você pode desistir ou reivindicar empate quando a regra permitir no seu turno; o motor também reivindica empates elegíveis no turno dele.

### Com outra pessoa

1. Informe os nomes das brancas e das pretas e clique em **Começar partida**.
2. Clique em uma peça do jogador da vez; os destinos legais aparecem no tabuleiro.
3. Clique no destino. No computador, também é possível arrastar a peça.
4. Na promoção, escolha dama, torre, bispo ou cavalo.
5. Cada lance é salvo no servidor antes de a interface confirmar a nova posição.

### Teclado

Use Tab até o tabuleiro. As setas percorrem as casas, Enter seleciona a peça e confirma o destino, e Escape limpa a seleção. Os diálogos usam o comportamento nativo de foco e Escape do navegador.

### Histórico e revisão

**Minhas partidas** permite continuar partidas e revisar as encerradas. Os botões abaixo dos lances percorrem as posições. Durante a revisão, a posição histórica não aceita jogadas; use **Voltar à posição atual** para continuar.

Nova partida mantém a anterior no histórico. **Atualizar** recupera o estado mais recente se outra aba também estiver aberta.

### Encerramento

- Xeque-mate, afogamento e material insuficiente são reconhecidos pelo motor.
- Cinco repetições da posição ou 75 lances de cada lado sem captura/movimento de peão encerram automaticamente, com prioridade para xeque-mate.
- A partir da terceira repetição ou de 50 lances de cada lado sem captura/movimento de peão, o jogador pode reivindicar empate na posição atual.
- **Empate** pede confirmação de acordo entre os dois jogadores locais.
- **Desistir** concede a vitória ao adversário do jogador da vez, após confirmação.

A interface não implementa a reivindicação antecipada de empate pela declaração de um lance ainda não executado, nem arbitragem de torneio para todas as posições mortas possíveis. O detector de material insuficiente cobre reis isolados, rei e um bispo/cavalo contra rei e posições apenas com bispos em casas da mesma cor.

### Exportação

**PGN** baixa a partida completa com nomes, data, resultado e notação algébrica. **FEN** baixa a posição visualizada, inclusive quando estiver em revisão. Importação de arquivos não está implementada.

### Preferências

Escolha tabuleiro verde musgo ou madeira; ative/desative dicas e som. Essas preferências ficam no `localStorage`. Bloquear o armazenamento não impede uma partida, mas impede a conservação dessas preferências.

## Onde ficam os dados

- Sem Docker: banco em `backend/data/` quando o backend é iniciado dessa pasta.
- Com Docker: volume `chess-data` do PostgreSQL.
- Navegador: cookie HttpOnly `chess-owner` identifica seu espaço local e permite retomar as partidas após reiniciar o servidor.

Limpar cookies ou trocar de navegador perde o acesso ao histórico anterior. O cookie é uma credencial local; esta versão não tem contas, recuperação de acesso ou sincronização entre dispositivos.

## Execução com Docker

1. Inicie o Docker Desktop/Engine.
2. Copie `.env.example` para `.env` e defina sua senha de desenvolvimento.
3. Na raiz, execute:

```sh
docker compose up -d --build --wait
```

Abra **http://localhost:8088**. Apenas o frontend publica porta no host; o backend e o banco são acessados na rede interna.

```sh
docker compose ps
docker compose logs -f backend
docker compose down
```

`down` preserva o volume do banco. Não remova esse volume se quiser manter suas partidas.

## Variáveis de ambiente

| Variável | Uso | Padrão sem Docker |
| --- | --- | --- |
| `SERVER_ADDRESS` | Endereço em que a API escuta | `127.0.0.1` |
| `PORT` | Porta da API | `8080` |
| `DATABASE_URL` | URL JDBC | H2 em `./data/chess` |
| `DATABASE_USER` | Usuário do banco | `sa` |
| `DATABASE_PASSWORD` | Senha do banco | Vazia no H2 local |
| `ALLOWED_ORIGINS` | Origens permitidas para operações do navegador | URLs locais em `application.yml` |
| `POSTGRES_PASSWORD` | Senha do PostgreSQL no Compose | Obrigatória em `.env` |
| `TEST_DATABASE_URL` | Banco dos testes de integração da API | H2 em memória |
| `STOCKFISH_PATH` | Executável do motor, sem argumentos adicionais | Descoberta em `tools/stockfish`, PATH ou `/usr/games/stockfish` |

Exemplo PowerShell para usar um PostgreSQL já disponível:

```powershell
$env:DATABASE_URL = 'jdbc:postgresql://localhost:5432/chess'
$env:DATABASE_USER = 'chess'
$env:DATABASE_PASSWORD = '<sua-senha-local>'
.\mvnw.cmd spring-boot:run
```

O `.env` da raiz é lido pelo Docker Compose. A execução direta com Maven usa as variáveis do processo; não lê esse arquivo automaticamente.

## Testes

Dentro de `backend`:

```powershell
.\mvnw.cmd verify

# Após instalar o motor, incluir o teste contra Stockfish real
.\mvnw.cmd verify -Pstockfish
```

Dentro de `frontend`:

```powershell
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
```

O Playwright inicia os servidores necessários, usando o JAR gerado em `backend/target`. Instale o Stockfish antes de executar a suíte completa, pois os cenários contra o computador usam o motor real. Se já houver servidores nas portas 8080/4200, ele os reutiliza no ambiente local. Os cenários criam suas próprias partidas; use servidores de desenvolvimento.

Relatório visual: `frontend/playwright-report/index.html`. Screenshots: `frontend/test-results/`. A CI executa testes da API contra PostgreSQL e também verifica o stack Docker.

## Problemas comuns

- **Não conecta ao servidor:** inicie o backend e teste `/actuator/health`.
- **Porta ocupada:** encerre a instância anterior. Se alterar a porta da API, atualize `frontend/proxy.conf.json`.
- **Erro de origem:** use a URL local documentada ou ajuste `ALLOWED_ORIGINS` para a URL real.
- **Partida mudou:** atualize a partida. Uma aba com estado antigo não pode sobrescrever uma jogada salva.
- **Docker não conecta:** abra o Docker Desktop e aguarde o Engine iniciar.

## Evolução planejada

1. Salas online com convite e reconexão.
2. Relógio controlado pelo servidor nas partidas online.
3. Contas, estatísticas e problemas de treinamento.

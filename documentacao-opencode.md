# Arquitetura e manutenção — Chess Studio

## Estrutura

```text
backend/
  src/main/java/
    boardgame/              Tabuleiro e posições genéricas originais
    chess/                  Estado e regras de uma partida
    chess_pieces/           Movimento de cada peça
    application/            Interface de terminal preservada
    dev/chess/
      domain/               Fachada de jogo, SAN e PGN
      application/          Casos de uso e transações
      infrastructure/
        engine/             Processo Stockfish, protocolo UCI e timeout
        persistence/        Entidade JPA e repository
        web/                Controller, validação e erros HTTP
  src/main/resources/
    db/migration/           Migrações Flyway
frontend/src/app/
  core/                     Contratos, API, store e preferências
  shared/                   Tabuleiro e peças SVG originais
  play/                     Partida local
  history/                  Histórico paginado
  settings/                 Preferências
frontend/e2e/               Testes reais de navegador
```

## Fluxo principal

```text
Clique/teclado/arraste no BoardComponent
  → GameStore.select → GameApi.legal → GET /legal-moves
  → GameStore.moveTo → escolha de promoção, quando necessária
  → GameApi.move → GameController → GameService
  → reconstrução de ChessGame a partir dos lances persistidos
  → ChessMatch valida e aplica o lance
  → persistência transacional com @Version
  → resposta State → Signals atualizam tabuleiro, histórico e resultado
```

O domínio não depende de Spring, JPA, HTTP ou Angular. As classes originais conservam seus packages para facilitar o estudo e comparação com o projeto inicial. A aplicação é um único backend, organizado por responsabilidades.

## Motor de xadrez

`ChessMatch.possibleMoves` retorna movimentos **legais**, após simular cada candidato e verificar a segurança do rei. O mapa de ataques é separado do mapa de movimentos: peões atacam diagonais e reis não atacam através de roque.

`makeMove` e `undoMove` são internos e precisam manter tabuleiro, contadores, capturas e en passant reversíveis. Não use chamadas a `possibleMoves` para detectar ataques de peões ou reis.

A promoção é um argumento da jogada e é aplicada antes de calcular xeque, mate ou empate. A vez é avançada inclusive ao encerrar; use `result`, e não `currentPlayer`, para saber quem venceu.

Na chave de repetição, a casa de en passant só conta se houver uma captura en passant legal. FEN de exportação mantém a casa-alvo padrão após avanço duplo, mesmo se não houver captura disponível.

O detector de material insuficiente é conservador e não é uma solução geral de todas as posições mortas de regulamento. Reivindicações de 50 lances/tripla repetição são sobre a posição já alcançada.

## Persistência

A migração V2 acrescenta `mode`, `human_color` e `difficulty`. Partidas existentes recebem `LOCAL`, `WHITE` e `MEDIUM`, mantendo o comportamento anterior.

`games` mantém ID, proprietário local, nomes, timestamps UTC, versão, resultado, motivo, contagem e a lista JSON de comandos de movimento. Há índice por proprietário e atualização.

Ao carregar uma partida, a sequência é reproduzida desde a posição inicial. Isso reconstitui direitos de roque, oportunidade de en passant, contadores e repetição. Encerramentos manuais são reaplicados depois da reconstrução.

Esta estratégia privilegia simplicidade e correção para partidas locais. O custo cresce com o histórico; para partidas extensas em grande escala, introduza snapshots versionados com compatibilidade de motor. Evoluções das regras devem considerar partidas antigas antes de alterar o replay.

`@Version` protege atualizações concorrentes no banco. Além disso, cada comando HTTP exige a versão vista pelo cliente. Uma requisição repetida com a versão anterior retorna 409, sem duplicar o lance.

## Contratos HTTP

Todas as rotas de partida estão em `/api/games`. Swagger e OpenAPI são gerados pelo Springdoc; o documento efetivo está em `/v3/api-docs`.

| Método | Rota | Entrada / retorno |
| --- | --- | --- |
| POST | `/api/games` | `{whiteName, blackName}` → 201 + State + Location |
| GET | `/api/games?page=0` | Página de 12 resumos, ordenada por atualização |
| GET | `/api/games/{id}` | Estado completo |
| GET | `/api/games/{id}/legal-moves?from=e2` | Lista de casas de destino |
| POST | `/api/games/{id}/moves` | `{version, from, to, promotion}` → novo State |
| POST | `/api/games/{id}/computer-move` | `{version}` → jogada do motor, apenas no turno dele |
| POST | `/api/games/{id}/resignation` | `{version}` → derrota do jogador da vez |
| POST | `/api/games/{id}/draw-agreement` | `{version}` → empate por acordo |
| POST | `/api/games/{id}/draw-claim` | `{version}` → empate se elegível |
| GET | `/api/games/{id}/pgn` | Arquivo `application/x-chess-pgn` |

Casas: `[a-h][1-8]`. Promoção: `Q`, `R`, `B` ou `N`, obrigatória no contrato; envie `Q` quando não houver promoção. Nomes: não vazios, até 40 caracteres, sem caracteres de controle.

State inclui `id`, `version`, nomes, `result`, `termination`, `currentPlayer`, `check`, `canClaimDraw`, `fen`, `initialFen`, `board`, `captured`, `moves` e timestamps.

Criação aceita também `mode` (`LOCAL`/`COMPUTER`), `humanColor` (`WHITE`/`BLACK`) e `difficulty` (`EASY`/`MEDIUM`/`HARD`). São opcionais para compatibilidade; os padrões são LOCAL/WHITE/MEDIUM. Esses campos voltam no estado e nos resumos. O nome do lado do computador é definido no servidor como Stockfish.

`GET /api/engine` informa `{available, name, message}` sem expor o caminho do executável. Falhas do motor usam Problem Details HTTP 503.

## Integração Stockfish

`ChessEngine` é a porta da aplicação; `StockfishEngine` a implementa via UCI em um processo separado, sem shell. O executável vem da configuração `STOCKFISH_PATH`, da instalação portátil ou do sistema. Não aceite caminhos de executável em requests.

`ChessGame.uciPosition()` gera `position startpos moves ...` a partir do histórico completo, incluindo sufixo de promoção apenas quando houve promoção. Isso mantém o contexto de repetição e lances reversíveis na análise do motor.

O adapter realiza handshake `uci`/`uciok`, configura limites, aguarda `isready`/`readyok` e só então solicita `go movetime`. Ele limita análises simultâneas a duas, usa uma thread de CPU e 16 MB de hash por processo e encerra o processo em sucesso, falha, interrupção ou timeout. A disponibilidade é consultada com cache curto de 15 segundos.

O service verifica dono, versão, modo e cor antes de chamar o motor. A jogada UCI retornada passa novamente pelo motor Java antes do save transacional. Erros nunca substituem a posição persistida. O computador reivindica empates elegíveis no próprio turno; o humano não pode mover peças do computador pela API.

`GameStore.playComputer()` é chamado após criar, retomar ou salvar uma jogada humana. É uma requisição separada, para que o lance humano já esteja salvo antes da análise. Se houver falha, não há loop automático de retries: a interface apresenta uma ação explícita de nova tentativa.

Testes de protocolo usam um subprocesso Java UCI controlado para provar timeout e encerramento, sem depender da instalação do Stockfish. O perfil Maven `stockfish` executa também `StockfishIT` contra o motor real; a CI instala esse motor antes dos testes e do Playwright.

Erros usam Problem Details com `status`, `title` e `detail`: 400 para contrato inválido, 404 para partida ausente/estrangeira, 409 para conflito de versão, 422 para jogada ilegal e 500 com mensagem sanitizada para falha inesperada. Falhas internas são registradas no servidor.

## Acesso local e navegador

O cookie HttpOnly, SameSite=Strict `chess-owner` é uma credencial aleatória de acesso ao espaço daquele navegador. Todas as consultas e alterações filtram por proprietário. Não se trata de login de conta, e perder o cookie implica perder o acesso local.

As mutações exigem JSON. O filtro rejeita Origin fora da allowlist. Não há CORS aberto. No desenvolvimento, o proxy Angular mantém a mesma origem; no Compose, o Nginx encaminha `/api/` ao backend. A API não deve ser apresentada como plataforma pública multiusuário sem uma etapa de autenticação e políticas operacionais.

Nginx adiciona CSP, proteção de frame e headers básicos. A configuração empacotada é HTTP local. Em uma publicação HTTPS, configure o proxy de confiança e cookies seguros de acordo com o terminador TLS.

## Frontend

Rotas `/play`, `/history` e `/settings` são carregadas sob demanda. `GameStore` guarda o estado recebido do Java; o tabuleiro deriva a visualização de FEN e **não decide legalidade**.

Seleções descartam respostas antigas por contador de requisições. Após falha ou timeout em uma mutação, a store consulta o estado para reconciliar uma jogada que possa ter sido salva. A revisão é somente visual e bloqueia jogadas/encerramentos até voltar ao presente.

`BoardComponent` oferece foco móvel entre casas com setas, Enter nativo dos botões e nomes acessíveis. Cliques são a alternativa touch ao drag-and-drop. Os diálogos são nativos para contenção e restauração do foco.

`Preferences` salva tema, dicas e áudio em localStorage, com tolerância a bloqueio de storage. `chess-last-game` apenas guarda o ID da partida selecionada; o servidor ainda valida seu proprietário.

Os estilos e tokens estão em `frontend/src/styles.css`. O SVG de cada peça é original, em `shared/piece.component.ts`, sem download de assets remotos. A aplicação respeita `prefers-reduced-motion`.

## Verificação e execução

- Backend: `.\mvnw.cmd verify` a partir de `backend` ou `bash mvnw verify` em Linux/macOS.
- Frontend: `npm ci` e `npm run build` a partir de `frontend`.
- Navegador: `npx playwright install chromium` e `npm run test:e2e`, após gerar o JAR.
- Compose: `docker compose --env-file .env.example config --quiet` para validar a configuração com valores de exemplo.
- CI: testes da API contra PostgreSQL, build Angular, auditoria de dependências de runtime, Playwright, build Docker e smoke test.

A CI só valida e empacota; não faz deploy ou publicação automática de imagens.

## Orientações de manutenção

1. Preserve Java como autoridade das regras; não replique o motor no Angular.
2. Para mudar um contrato, atualize DTOs Java, `core/models.ts`, o client e os testes.
3. Altere schema com uma nova migração Flyway, sem editar migrações já aplicadas em ambientes existentes.
4. Teste movimentos especiais e a reversibilidade das simulações quando alterar o motor.
5. Ao evoluir a persistência, mantenha a reconstrução de en passant, roque e repetição.
6. Nunca registre cookie de acesso ou credenciais nos logs.
7. Preserve a LICENSE original e não versione `data/`, artefatos de build, `.env` ou dependências instaladas.

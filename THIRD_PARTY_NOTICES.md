# Componentes de terceiros

## Stockfish

- Projeto: https://stockfishchess.org/
- Repositório e código-fonte: https://github.com/official-stockfish/Stockfish
- Licença: GNU General Public License, versão 3 (GPLv3).
- Versão do instalador Windows: [Stockfish 19, tag sf_19](https://github.com/official-stockfish/Stockfish/releases/tag/sf_19).
- A licença e o código-fonte incluídos no arquivo oficial são preservados pelo instalador em `backend/tools/stockfish/`.

O backend se comunica com o executável separado através do protocolo UCI. O binário não faz parte dos arquivos versionados deste projeto.

A imagem Docker instala o pacote `stockfish` do Ubuntu. Os avisos do pacote ficam em `/usr/share/doc/stockfish/` na imagem; o código-fonte correspondente pode ser obtido pelos repositórios de fontes da distribuição. Ao redistribuir uma imagem ou executável, preserve os avisos e disponibilize o código-fonte correspondente conforme a licença do Stockfish.

A licença original deste repositório permanece no arquivo `LICENSE`.

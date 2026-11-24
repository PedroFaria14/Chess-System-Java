param([string]$Destination = (Join-Path $PSScriptRoot '..\tools\stockfish'))
$ErrorActionPreference = 'Stop'
# Official release, pinned and checked before extraction. Keep the included GPL/source files.
$url = 'https://github.com/official-stockfish/Stockfish/releases/download/sf_19/stockfish-windows-x86-64-universal.zip'
$expected = '3c8bf1f9ea66a09350a40df4f632288285ac206d99f33ab5842c408fc30b48a7'
$backend = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path -LiteralPath $backend)) { throw 'Pasta backend não encontrada.' }
if ($env:PROCESSOR_ARCHITECTURE -ne 'AMD64') { throw 'Este instalador é para Windows x64. Use STOCKFISH_PATH para outra arquitetura.' }
$tools = Join-Path $backend 'tools'
if (-not (Test-Path -LiteralPath $tools)) { New-Item -ItemType Directory -Path $tools | Out-Null }
if (-not (Test-Path -LiteralPath $Destination)) { New-Item -ItemType Directory -Path $Destination | Out-Null }
$archive = Join-Path $Destination 'stockfish-download.zip'
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $archive
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
    throw 'O checksum do download não confere. Nenhum executável foi instalado.'
}
Expand-Archive -LiteralPath $archive -DestinationPath $Destination -Force
Remove-Item -LiteralPath $archive
'Stockfish 19 instalado em backend/tools/stockfish. Reinicie o backend para jogar.'

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TV="$ROOT/CineHUB-TV/src/main/assets/cinehub/dados"
mkdir -p "$TV"
rm -rf "$TV/conteudo"
cp -a "$ROOT/../../../../CineHUB_WEB/dados/conteudo" "$TV/conteudo"
cp "$ROOT/../../../../CineHUB_WEB/dados/listas.json" "$TV/listas.json"
cp "$ROOT/../../../../CineHUB_WEB/dados/canais.js" "$TV/canais.js"
cp "$ROOT/../../../../CineHUB_WEB/dados/canais-m3u8.js" "$TV/canais-m3u8.js"

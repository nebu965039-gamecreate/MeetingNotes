#!/usr/bin/env bash
# 各画面のスクショを撮って docs/design/screens/ に保存する。
# アプリ内の遷移は自動化できないため「手動で画面を出す → Enter → 撮影」の対話式。
#
# 使い方:
#   1) 実機/エミュレータを1台だけ接続
#   2) bash docs/design/capture-screens.sh
#   3) プロンプトに従い、アプリで目的の画面を出してから Enter
#      ファイル名の候補が順番に出る(そのまま Enter で採用 / 別名を入力 / s でスキップ / q で終了)
#
# 撮ったものは docs/design/screens/<name>.png。Figma にドラッグ&ドロップして参照に。

set -u

ADB="${ADB:-/c/Users/zhong/AppData/Local/Android/Sdk/platform-tools/adb.exe}"
OUT_DIR="$(cd "$(dirname "$0")" && pwd)/screens"
mkdir -p "$OUT_DIR"

if [ ! -x "$ADB" ] && ! command -v "$ADB" >/dev/null 2>&1; then
  echo "adb が見つかりません。ADB=... で明示するか PATH を通してください。" >&2
  exit 1
fi

DEVCOUNT=$("$ADB" devices | grep -cE '\sdevice$')
if [ "$DEVCOUNT" -ne 1 ]; then
  echo "接続台数が $DEVCOUNT 台です。1台だけにしてください。" >&2
  "$ADB" devices >&2
  exit 1
fi

# 画面サイズ・密度(Figma のフレームサイズ決めに使う)
echo "=== 端末情報 ==="
SIZE=$("$ADB" shell wm size | tr -d '\r')
DENS=$("$ADB" shell wm density | tr -d '\r')
echo "$SIZE"
echo "$DENS"
echo "$SIZE / $DENS  (取得: $(date '+%Y-%m-%d %H:%M'))" > "$OUT_DIR/_device-info.txt"
echo "  → dp 換算: 物理px ÷ (density/160)。例: density 420 なら ÷2.625"
echo

# 撮りたい画面の推奨リスト(順番の目安)。実際の遷移は手動。
SCREENS=(
  "home"
  "home-with-draft"
  "client-list"
  "client-list-add-dialog"
  "client-detail"
  "client-detail-todo-list"
  "client-detail-search"
  "client-info"
  "client-edit"
  "briefing"
  "recording-mode-picker"
  "recording-countdown"
  "recording-active"
  "recording-editing"
  "recording-transcribing"
  "result-summarizing"
  "result-done"
  "meeting-detail"
  "meeting-detail-export-dialog"
  "meeting-detail-phase-picker"
  "meeting-detail-followup-dialog"
  "schedule"
  "schedule-day-selected"
  "schedule-add-dialog"
  "notifications"
  "followup-list-todo"
  "followup-list-done"
  "settings"
  "settings-theme-dialog"
  "help"
  "help-diagnostics"
)

shoot() {
  local name="$1"
  local path="$OUT_DIR/$name.png"
  "$ADB" exec-out screencap -p > "$path" 2>/dev/null
  if [ -s "$path" ]; then
    echo "  saved: docs/design/screens/$name.png"
  else
    echo "  FAILED: $name" >&2
    rm -f "$path"
  fi
}

echo "=== 撮影開始 ==="
echo "各行で: [Enter]=この名前で撮影 / 文字入力=別名で撮影 / s=スキップ / q=終了"
echo
for s in "${SCREENS[@]}"; do
  printf '画面「%s」を出してください > ' "$s"
  read -r ans
  case "$ans" in
    q|Q) echo "終了します。"; break ;;
    s|S) echo "  skip"; continue ;;
    "")  shoot "$s" ;;
    *)   shoot "$ans" ;;
  esac
done

echo
echo "完了。$OUT_DIR に保存しました。"
echo "追加で撮りたい画面があれば、もう一度実行するか手動で:"
echo "  \"$ADB\" exec-out screencap -p > docs/design/screens/<name>.png"

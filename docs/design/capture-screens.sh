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

# adb 系コマンドは stdin を奪うことがあるので必ず </dev/null を付ける(対話入力を守る)
DEVCOUNT=$("$ADB" devices </dev/null | grep -cE '\sdevice$')
if [ "$DEVCOUNT" -ne 1 ]; then
  echo "接続台数が $DEVCOUNT 台です。1台だけにしてください。" >&2
  "$ADB" devices </dev/null >&2
  exit 1
fi

# 画面サイズ・密度(Figma のフレームサイズ決めに使う)
echo "=== 端末情報 ==="
SIZE=$("$ADB" shell wm size </dev/null | tr -d '\r')
DENS=$("$ADB" shell wm density </dev/null | tr -d '\r')
echo "$SIZE"
echo "$DENS"
echo "$SIZE / $DENS  (取得: $(date '+%Y-%m-%d %H:%M'))" > "$OUT_DIR/_device-info.txt"
echo "  → dp 換算: 物理px ÷ (density/160)。例: density 420 なら ÷2.625"
echo

# 撮りたい画面の推奨リスト(順番の目安)。実際の遷移は手動。
# 形式: "保存ファイル名|この画面の説明"
SCREENS=(
  "ホーム|アプリ起動直後の画面"
  "ホーム_下書きあり|録音を途中でやめた後のホーム(上部に「未完了の商談メモがあります」カード)"
  "クライアント一覧|「クライアント一覧」タイルを開いた画面"
  "クライアント一覧_追加ダイアログ|クライアント一覧で「＋」FABを押し「クライアントを追加」ダイアログを出した状態"
  "商談アーカイブ|クライアントを1つ開いた画面(商談の一覧)"
  "商談アーカイブ_ToDoリスト|上記でToDoが1件以上あり「ToDoリスト」セクションが見える状態"
  "商談アーカイブ_検索中|アーカイブで検索アイコンを押し、キーワードを入れて結果が出た状態"
  "クライアント情報|⋮メニュー →「クライアント情報」(閲覧専用画面)"
  "クライアント情報_編集|クライアント情報画面の編集アイコン →「クライアント情報を編集」"
  "前回のおさらい|2回目以降の録音前に出る「前回のおさらい」画面"
  "録音_モード選択|録音開始時の「この商談は？」ダイアログ(対面/リモート会議)"
  "録音_カウントダウン|録音開始直後の 3・2・1 カウントダウン"
  "録音中|録音中の画面(経過時間・音声レベル・ライブ文字起こし)"
  "録音_編集画面|録音停止後の文字起こし編集画面(文字数カウンタつき)"
  "録音_文字起こし中|リモート会議モードで停止直後の「文字起こし中...」画面"
  "要約結果_要約中|要約結果画面で「決定事項を抽出中...」等の段階メッセージが出ている状態"
  "要約結果_完成|要約が完成し、サマリー〜次回打ち合わせが表示された状態"
  "商談詳細|保存済みの商談を開いた詳細画面"
  "商談詳細_エクスポートダイアログ|商談詳細で「エクスポート」を押しダイアログを出した状態"
  "商談詳細_フェーズ選択|商談詳細でフェーズタグをタップし「商談フェーズ」ダイアログを出した状態"
  "商談詳細_フォローアップ下書き|商談詳細の「フォローアップの下書き」ダイアログ"
  "予定表|「予定表」タイルを開いた画面(月カレンダー + 一覧)"
  "予定表_日付選択|カレンダーで日付をタップし、その日の予定/ToDoに絞り込んだ状態"
  "予定表_予定追加ダイアログ|予定表の「＋」FAB →「予定を追加」ダイアログ"
  "通知|「通知」タイルを開いた画面(予定リスト + 通知履歴)"
  "ToDo一覧_ToDoタブ|「すべて表示」で開くToDo全件ページの「ToDo」タブ"
  "ToDo一覧_完了タブ|同じ画面の「完了」タブ"
  "設定|ホームの歯車アイコンから開く設定画面"
  "設定_テーマ選択ダイアログ|設定の「背景(テーマ)」→ ライト/ダーク/システムの選択ダイアログ"
  "ヘルプ|ホームの「?」アイコンから開くヘルプ画面"
  "ヘルプ_診断情報|ヘルプ画面を一番下までスクロールした「診断情報」カード"
)

shoot() {
  local name="$1"
  local path="$OUT_DIR/$name.png"
  "$ADB" exec-out screencap -p </dev/null > "$path"
  local bytes
  bytes=$(wc -c < "$path" 2>/dev/null | tr -d ' ')
  if [ "${bytes:-0}" -gt 1000 ]; then
    echo "  OK: docs/design/screens/$name.png (${bytes} bytes)"
  else
    echo "  失敗: $name — 画像が空です。'$ADB devices' で 'device' と出るか確認してください。" >&2
    rm -f "$path"
  fi
}

echo "=== 撮影開始 ==="
echo "スマホで説明どおりの画面を出してから: [Enter]=撮影 / s=スキップ / q=終了"
echo "(スマホ側は無反応で正常。PC に png が保存されます)"
echo
for entry in "${SCREENS[@]}"; do
  name="${entry%%|*}"      # "|" の前 = ファイル名
  desc="${entry#*|}"       # "|" の後 = 説明
  echo
  echo "▼ ${name}"
  echo "   ${desc}"
  printf '   この画面を出して Enter (s=スキップ / q=終了) > '
  read -r ans || { echo; echo "入力終了。"; break; }
  case "$ans" in
    q|Q) echo "終了します。"; break ;;
    s|S) echo "   → スキップ"; continue ;;
    *)   shoot "$name" ;;
  esac
done

echo
echo "完了。$OUT_DIR に保存しました。"
echo "追加で撮りたい画面があれば、もう一度実行するか手動で:"
echo "  \"$ADB\" exec-out screencap -p > docs/design/screens/<name>.png"

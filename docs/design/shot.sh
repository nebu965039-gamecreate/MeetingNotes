# スクショを1枚ずつ撮るための最小ヘルパー。
#
# 使い方(Git Bash):
#   cd /c/projects/meetingnotes
#   source docs/design/shot.sh        # ← 1回だけ。"." でも可: . docs/design/shot.sh
#   # スマホ/エミュレータで撮りたい画面を表示してから:
#   shot home
#   shot client-list
#   shot meeting-detail
#   ...
#
# 保存先: docs/design/screens/<名前>.png (git 管理外)
# adb のパスが違う場合は source する前に: export ADB=/path/to/adb.exe

ADB="${ADB:-/c/Users/zhong/AppData/Local/Android/Sdk/platform-tools/adb.exe}"

# 端末が見えているか先に確認する
adb-check() {
  if [ ! -e "$ADB" ] && ! command -v "$ADB" >/dev/null 2>&1; then
    echo "adb が見つかりません: $ADB" >&2
    echo "  → export ADB=/c/Users/zhong/AppData/Local/Android/Sdk/platform-tools/adb.exe を先に実行" >&2
    return 1
  fi
  "$ADB" devices
}

shot() {
  local name="$1"
  if [ -z "$name" ]; then
    echo "使い方: shot <名前>   例) shot home" >&2
    return 1
  fi
  local dir
  dir="$(git -C "$(pwd)" rev-parse --show-toplevel 2>/dev/null)/docs/design/screens"
  [ -d "$(dirname "$dir")" ] || dir="docs/design/screens"
  mkdir -p "$dir"

  "$ADB" exec-out screencap -p > "$dir/$name.png"
  local bytes
  bytes=$(wc -c < "$dir/$name.png" 2>/dev/null | tr -d ' ')
  if [ "${bytes:-0}" -gt 1000 ]; then
    echo "OK   $dir/$name.png  (${bytes} bytes)"
  else
    echo "失敗: 画像が空か極小です。以下を確認してください:" >&2
    echo "  1) '$ADB devices' に 'device' と出ているか(unauthorized なら端末側でUSBデバッグを許可)" >&2
    echo "  2) 端末が1台だけ接続されているか" >&2
    rm -f "$dir/$name.png"
    return 1
  fi
}

echo "shot() を読み込みました。まず 'adb-check' で端末を確認 → 'shot <名前>' で撮影。"

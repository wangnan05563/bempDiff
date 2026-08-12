#!/usr/bin/env bash
# 编译 javafx_ui（含 core 源，因 UI 直接引用 core 类），验证 App.java 前端分支改动可编译。
set -u

ROOT="D:/code/otherProjects/18_comparePakage"
JAVAC="$ROOT/prototype/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/javac.exe"
CFR="$ROOT/prototype/cfr.jar"
CORE_SRC="$ROOT/prototype/java_core/src"
UI_SRC="$ROOT/prototype/javafx_ui/src"
TC="$ROOT/prototype/toolchain"
OUT="$ROOT/prototype/javafx_ui/out"

MODULES="javafx.controls,javafx.fxml,org.kordamp.bootstrapfx.core,org.kordamp.ikonli.core,org.kordamp.ikonli.javafx,org.kordamp.ikonli.bootstrapicons"
MP="$TC"
ADD="$MODULES"

rm -rf "$OUT"
mkdir -p "$OUT"

SRC_FILES=$(find "$CORE_SRC" "$UI_SRC" -name '*.java')
"$JAVAC" -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8 \
  --module-path "$MP" --add-modules "$ADD" \
  -classpath "$CFR" -encoding UTF-8 -Xlint:-unchecked -d "$OUT" $SRC_FILES
echo "ui exit: $?"

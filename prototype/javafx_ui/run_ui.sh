#!/usr/bin/env bash
# 从源码运行 JavaFX UI（需 JDK21 + JavaFX；本目录已带 toolchain 下的 JDK21 与 javafx jars）
# 用法：bash run_ui.sh   （Windows 用 Git Bash）
set -u
cd "$(dirname "$0")/.."

JAVA="toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/java"
MP="toolchain/javafx-base-21-win.jar;toolchain/javafx-controls-21-win.jar;toolchain/javafx-fxml-21-win.jar;toolchain/javafx-graphics-21-win.jar;toolchain/bootstrapfx-core-0.4.0.jar;toolchain/ikonli-core-12.3.1.jar;toolchain/ikonli-javafx-12.3.1.jar;toolchain/ikonli-bootstrapicons-pack-12.3.1.jar"
ADD="javafx.controls,javafx.fxml,org.kordamp.bootstrapfx.core,org.kordamp.ikonli.core,org.kordamp.ikonli.javafx,org.kordamp.ikonli.bootstrapicons"

if [ ! -d javafx_ui/out ]; then
  echo "未编译，请先运行 scripts/build-ui.ps1 或 scripts/构建打包.bat"
  exit 1
fi

"$JAVA" --module-path "$MP" --add-modules "$ADD" -Dfile.encoding=UTF-8 -cp "javafx_ui/out;cfr.jar" com.bempdiff.ui.App

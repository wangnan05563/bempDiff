#!/usr/bin/env bash
# 编译 java_core/src + java_core/test 并跑 TestRunner 全量（含 FrontendTest）。
set -u

ROOT="D:/code/otherProjects/18_comparePakage"
JAVAC="$ROOT/bempdiff/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/javac.exe"
JAVA="$ROOT/bempdiff/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/bin/java.exe"
CFR="$ROOT/bempdiff/cfr.jar"
CORE_SRC="$ROOT/bempdiff/java_core/src"
TEST_SRC="$ROOT/bempdiff/java_core/test"
CORE_OUT="$ROOT/bempdiff/java_core/out"
TEST_OUT="$ROOT/bempdiff/java_core/test_out"

rm -rf "$CORE_OUT" "$TEST_OUT"
mkdir -p "$CORE_OUT" "$TEST_OUT"

echo "==> 编译 core src"
CORE_FILES=$(find "$CORE_SRC" -name '*.java')
"$JAVAC" -cp "$CFR" -encoding UTF-8 -Xlint:-unchecked -d "$CORE_OUT" $CORE_FILES
CORE_RC=$?
echo "core exit: $CORE_RC"
if [ "$CORE_RC" -ne 0 ]; then echo "core 编译失败"; exit 1; fi

echo "==> 编译 test"
TEST_FILES=$(find "$TEST_SRC" -name '*.java')
"$JAVAC" -cp "$CORE_OUT;$CFR" -encoding UTF-8 -Xlint:-unchecked -d "$TEST_OUT" $TEST_FILES
TEST_RC=$?
echo "test exit: $TEST_RC"
if [ "$TEST_RC" -ne 0 ]; then echo "test 编译失败"; exit 1; fi

echo "==> 运行 TestRunner"
"$JAVA" \
  -cp "$TEST_OUT;$CORE_OUT;$CFR" \
  -Dfile.encoding=UTF-8 \
  com.bempdiff.test.TestRunner \
  com.bempdiff.test.ParseTest \
  com.bempdiff.test.DiffTest \
  com.bempdiff.test.DecompileTest \
  com.bempdiff.test.AiTest \
  com.bempdiff.test.ReportTest \
  com.bempdiff.test.ExportTest \
  com.bempdiff.test.ModelTest \
  com.bempdiff.test.SsrfTest \
  com.bempdiff.test.FrontendTest \
  com.bempdiff.test.FolderTest \
  com.bempdiff.test.FolderDiffTest \
  com.bempdiff.test.ProfileTest \
  com.bempdiff.test.VendorConfigTest \
  com.bempdiff.test.ServerConfigTest \
  com.bempdiff.test.ArchiveDiffTest \
  com.bempdiff.test.ArchiveChildrenTest \
  com.bempdiff.test.ProjectContextTest \
  com.bempdiff.test.ContextPromptTest \
  com.bempdiff.test.ContextAiTest
echo "testrunner exit: $?"

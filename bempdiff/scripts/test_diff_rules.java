// BempDiff P0-② 后端单测：忽略不重要差异（DiffRules + LineDiff）。
// 直接 import 真实 com.bempdiff.diff.* 类，覆盖空白/注释/正则忽略、非法正则安全、
// 真实变更不被掩盖、diff 输出显示原始行等。
// 运行（或见同目录 test_p0_backend.ps1）：
//   javac -cp "java_core/src;dist_input/app/cfr.jar" -d _testcls scripts/test_diff_rules.java
//   java  -cp "_testcls;java_core/src;dist_input/app/cfr.jar" TestDiffRules
import com.bempdiff.diff.DiffRules;
import com.bempdiff.diff.LineDiff;

import java.util.Arrays;

class TestDiffRules {
    static int pass = 0, fail = 0;
    static void check(String name, boolean cond) {
        if (cond) pass++;
        else { fail++; System.out.println("  FAIL: " + name); }
    }

    public static void main(String[] args) {
        // 默认零回归
        DiffRules def = DiffRules.DEFAULT;
        check("默认不忽略任何项", !def.isIgnoreWhitespace() && !def.isIgnoreComments() && def.getIgnoreRegex() == null);
        check("默认 normalize 逐字符原样", def.normalize("  int  x  =  1;  ").equals("  int  x  =  1;  "));

        // 空白忽略
        DiffRules ws = DiffRules.of(true, false, null);
        check("空白移除(所有空白)", ws.normalize("  int  x  =  1;  ").equals("intx=1;"));
        check("空白忽略使两行匹配(含有无空白)", ws.normalize("a = 1;").equals(ws.normalize("a=1;")));

        // 注释忽略（仅整行）
        DiffRules cm = DiffRules.of(false, true, null);
        check("整行注释剥离", cm.normalize("// hello world").equals(""));
        check("行内注释保留", cm.normalize("int x = 1; // c").equals("int x = 1; // c"));
        check("注释忽略使两行匹配(整行注释)", cm.normalize("// old comment").equals(cm.normalize("// new comment")));

        // 正则忽略
        DiffRules rx = DiffRules.of(false, false, "TODO");
        check("正则移除命中", rx.normalize("a TODO b").equals("a  b"));
        check("正则不匹配原样", rx.normalize("a b").equals("a b"));

        // 非法正则安全（不崩溃，退化为默认）
        DiffRules bad = DiffRules.of(false, false, "[unclosed");
        check("非法正则退化为默认(不崩溃)", bad.normalize("x [unclosed y").equals("x [unclosed y"));

        // 真实变更不被掩盖（关键）
        DiffRules all = DiffRules.of(true, true, null);
        check("仅空白差被忽略→相同", all.normalize("int a=1;").equals(all.normalize("int a = 1;")));
        check("实质变更不被掩盖", !all.normalize("int a=1;").equals(all.normalize("int b=2;")));

        // 经 LineDiff 产出：仅空白差的两文件 diff 应为空（无 +/-）
        String a = "class A {\n  int x = 1;\n}", b = "class A {\n  int x=1;\n}";
        String diff = LineDiff.unified(Arrays.asList(a.split("\n")), Arrays.asList(b.split("\n")), all);
        check("仅空白差异→无 +/- 行", !diff.contains("\n- ") && !diff.contains("\n+ "));

        // 输出显示原始行（normalize 只用于匹配，不进输出）
        String diff2 = LineDiff.unified(Arrays.asList("int a=1;"), Arrays.asList("int b=2;"), all);
        check("diff 输出含原始行(非归一化)", diff2.contains("int a=1;") && diff2.contains("int b=2;"));

        System.out.println("P0-② DiffRules: PASS=" + pass + " FAIL=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}

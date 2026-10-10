package com.bempdiff.diff;

import com.bempdiff.model.DecompiledUnit;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javax.lang.model.element.Modifier;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * FR-007 API 兼容性语义分级（静态判定，AI 辅助行为级）：
 * 从反编译源码（DecompiledUnit.oldSource/newSource）提取 public/protected
 * 方法与字段签名，按类对齐输出三级变更：
 *   - REMOVED_API：移除公开方法/字段/类 → 二进制不兼容（醒目标注）
 *   - SIGNATURE_CHANGED：同名方法参数/返回签名变化 → 二进制不兼容
 *   - ADDED_API：新增公开 API → 兼容（信息性）
 * 行为变更（源码级不兼容但签名不变）由 AI 两阶段分析覆盖（CategoryConclusion 链路），
 * 本类仅产出摘要供 AI 上下文注入（FR-006 AST 基建共享）。
 *
 * <p>技术选型沿用 AstNormalizer：JDK 内置 javac 解析（零依赖），解析失败静默跳过
 * 该类（反编译源码极少非法；跳过不阻塞比对主流程）。</p>
 */
public final class ApiCompat {

    private ApiCompat() {
    }

    /** 变更级别：移除公开 API / 签名变化（均二进制不兼容）、新增公开 API（兼容）。 */
    public enum Level { REMOVED_API, SIGNATURE_CHANGED, ADDED_API }

    /** 单条变更：级别 + 所属类 + API 签名。 */
    public static final class Item {
        public final Level level;
        public final String cls;
        public final String api;

        Item(Level level, String cls, String api) {
            this.level = level;
            this.cls = cls;
            this.api = api;
        }
    }

    /** 分级结果：按类分组条目 + 计数 + 报告/提示词渲染。 */
    public static final class Result {
        public final List<Item> items = new ArrayList<>();
        public int removed, sigChanged, added;

        public boolean isEmpty() {
            return items.isEmpty();
        }

        /** 报告章节（Markdown）：不兼容项用 sev-breaking 徽章醒目标注（复用既有 CSS 语义）。 */
        public String toMarkdownSection() {
            if (items.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("### API 兼容性分级（静态）\n\n");
            sb.append("| 级别 | 类 | API |\n|---|---|---|\n");
            for (Item it : items) {
                String badge = switch (it.level) {
                    case REMOVED_API -> "**<span class=\"sev-breaking\">移除 API</span>**";
                    case SIGNATURE_CHANGED -> "**<span class=\"sev-high\">签名变更</span>**";
                    case ADDED_API -> "新增 API";
                };
                sb.append("| ").append(badge)
                  .append(" | `").append(it.cls).append("`")
                  .append(" | `").append(it.api).append("` |\n");
            }
            sb.append("\n> 分级口径：移除/签名变更=二进制不兼容（升级风险）；新增=兼容。")
              .append("行为级变更由两阶段 AI 分析覆盖。\n\n");
            return sb.toString();
        }

        /** 提示词段（进入 AI 阶段A 上下文，验收②：静态分级结论作为 AI 判定证据）。 */
        public String toPromptSection() {
            if (items.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            sb.append("【API 兼容性静态分级】（供评估二进制兼容风险时引用）\n");
            for (Item it : items) {
                sb.append("- ").append(it.level).append(" ").append(it.cls).append("#").append(it.api).append("\n");
            }
            sb.append("\n");
            return sb.toString();
        }
    }

    /**
     * 对反编译候选集做 API 分级：每个 unit 取双侧源码提取签名集对比。
     * 仅单侧可解析的 unit 跳过（无法静态判定）；行为变更不在本方法范围（AI 链路覆盖）。
     */
    public static Result analyze(Map<String, DecompiledUnit> units) {
        Result res = new Result();
        if (units == null || units.isEmpty()) return res;
        for (DecompiledUnit u : units.values()) {
            if (u == null || !u.isOk()) continue;
            String oldSrc = u.getOldSource();
            String newSrc = u.getNewSource();
            if (oldSrc == null && newSrc == null) continue;
            Map<String, Set<String>> oldApis = (oldSrc != null) ? extractApis(oldSrc) : null;
            Map<String, Set<String>> newApis = (newSrc != null) ? extractApis(newSrc) : null;
            if (oldApis == null && newApis == null) continue;
            if (oldApis == null) {
                // 仅新侧可解析 → 视为整类新增
                for (Map.Entry<String, Set<String>> e : newApis.entrySet()) {
                    for (String api : e.getValue()) res.items.add(new Item(Level.ADDED_API, e.getKey(), api));
                    res.added += e.getValue().size();
                }
                continue;
            }
            if (newApis == null) {
                // 仅旧侧可解析 → 视为整类移除（二进制不兼容）
                for (Map.Entry<String, Set<String>> e : oldApis.entrySet()) {
                    for (String api : e.getValue()) res.items.add(new Item(Level.REMOVED_API, e.getKey(), api));
                    res.removed += e.getValue().size();
                }
                continue;
            }
            diffClass(u.getKey(), oldApis, newApis, res);
        }
        return res;
    }

    /** 类级签名集对比：先求移除/新增，再把「同名方法」配对升级为 SIGNATURE_CHANGED（更准确的语义）。 */
    private static void diffClass(String key, Map<String, Set<String>> oldApis, Map<String, Set<String>> newApis, Result res) {
        Set<String> classes = new LinkedHashSet<>(oldApis.keySet());
        classes.addAll(newApis.keySet());
        for (String cls : classes) {
            Set<String> o = oldApis.getOrDefault(cls, Set.of());
            Set<String> n = newApis.getOrDefault(cls, Set.of());
            Set<String> removed = new LinkedHashSet<>(o);
            removed.removeAll(n);
            Set<String> added = new LinkedHashSet<>(n);
            added.removeAll(o);
            // 同名方法跨版本参数变化：removed/added 里形如 name(...):T 的配对 → SIGNATURE_CHANGED
            Map<String, String> removedByName = apiByName(removed);
            Map<String, String> addedByName = apiByName(added);
            List<String> sigChanged = new ArrayList<>();
            for (Map.Entry<String, String> e : removedByName.entrySet()) {
                String addedSig = addedByName.get(e.getKey());
                if (addedSig != null) {
                    sigChanged.add(e.getValue() + " → " + addedSig);
                    added.remove(addedSig);
                    removed.remove(e.getValue());
                }
            }
            for (String s : removed) {
                res.items.add(new Item(Level.REMOVED_API, cls, s));
                res.removed++;
            }
            for (String s : sigChanged) {
                res.items.add(new Item(Level.SIGNATURE_CHANGED, cls, s));
                res.sigChanged++;
            }
            for (String s : added) {
                res.items.add(new Item(Level.ADDED_API, cls, s));
                res.added++;
            }
        }
    }

    /** 从签名集提取「方法名 → 签名」映射（字段签名 name:Type 无括号，不参与配对）。 */
    private static Map<String, String> apiByName(Set<String> sigs) {
        Map<String, String> m = new TreeMap<>();
        for (String s : sigs) {
            int lp = s.indexOf('(');
            int rp = s.indexOf(')');
            if (lp > 0 && rp > lp) m.putIfAbsent(s.substring(0, lp), s);
        }
        return m;
    }

    /**
     * 提取公开 API 签名：javac 解析 + TreeScanner 遍历全部类（含嵌套），
     * 收集 public/protected 修饰的方法（含参数与返回类型）与字段。
     * 解析失败/无声明 → null（调用方跳过该侧）。
     */
    static Map<String, Set<String>> extractApis(String src) {
        try {
            JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
            JavaFileObject fo = new SimpleJavaFileObject(
                    URI.create("mem://bempdiff/ApiExtract.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncoding) {
                    return src;
                }
            };
            JavacTask task = (JavacTask) jc.getTask(null, null, diags,
                    List.of("-proc:none"), null, List.of(fo));
            Iterable<? extends CompilationUnitTree> units = task.parse();
            for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                if (d.getKind() == Diagnostic.Kind.ERROR) return null;
            }
            Map<String, Set<String>> out = new LinkedHashMap<>();
            for (CompilationUnitTree cu : units) {
                cu.accept(new TreeScanner<Void, String>() {
                    @Override
                    public Void visitClass(ClassTree ct, String outer) {
                        String simple = ct.getSimpleName().toString();
                        String fq = (outer == null || outer.isEmpty()) ? simple : outer + "." + simple;
                        for (Tree m : ct.getMembers()) {
                            if (m instanceof MethodTree mt) {
                                if (!isApi(mt.getModifiers())) continue;
                                StringBuilder sig = new StringBuilder(mt.getName().toString()).append('(');
                                List<String> ps = new ArrayList<>();
                                for (VariableTree p : mt.getParameters()) ps.add(p.getType().toString());
                                sig.append(String.join(",", ps)).append("):")
                                   .append(mt.getReturnType() == null ? "void" : mt.getReturnType().toString());
                                out.computeIfAbsent(fq, k -> new LinkedHashSet<>()).add(sig.toString());
                            } else if (m instanceof VariableTree vt) {
                                if (!isApi(vt.getModifiers())) continue;
                                out.computeIfAbsent(fq, k -> new LinkedHashSet<>())
                                   .add(vt.getName().toString() + ":" + vt.getType().toString());
                            }
                        }
                        // super 下钻嵌套类（fq 作前缀），成员级节点不触发 visitClass
                        return super.visitClass(ct, fq);
                    }
                }, null);
            }
            return out.isEmpty() ? null : out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** API 判定：public 或 protected（包私有/私有不算对外契约）。 */
    private static boolean isApi(ModifiersTree mods) {
        Set<Modifier> f = mods.getFlags();
        return f.contains(Modifier.PUBLIC) || f.contains(Modifier.PROTECTED);
    }
}

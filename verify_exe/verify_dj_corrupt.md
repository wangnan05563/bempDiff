# 差异分析报告（Java 端口 / 真实包比对）

- 老包：`corrupt_v1.war`（版本 1）
- 新包：`corrupt_v2.war`（版本 2）

## 一、差异统计
- 新增 **0** · 删除 **0** · 修改 **3** · 未变 1
- 业务/类级变更(非jar)：0 · jar 级变更：3

### 按文件类型分布
| 文件类型 | 新增 | 删除 | 修改 |
|---|---|---|---|
| 依赖 JAR | 0 | 0 | 3 |
| **合计** | **0** | **0** | **3** |

## 二、差异文件树（全量清单）
- `修改 ~` WEB-INF/lib/bad.jar
- `修改 ~` WEB-INF/lib/libA.jar
- `修改 ~` WEB-INF/lib/libB.jar

## 三、反编译源码级差异（Top-3 修改/新增/删除 Java 类）
> 共 0 个类已反编译（本处仅展示前 Top-K 条完整 diff）。

## 四、文本类文件内容差异（Top-3 修改/新增/删除 配置文件/JSP/JS/HTML/CSS）
- 无文本类文件（配置文件/JSP/前端源码）变动。

## 五、差异依赖 JAR 内部源码对比（Top-3 内部 class 反编译源码级 diff）
> 共 3 个差异 JAR（新增 0 / 修改 3 / 删除 0）；其内部 class 合计：新增 0 · 删除 0 · 修改 4 · 未变 0。

### WEB-INF/lib/bad.jar  [分析失败]
- ⚠️ 该 JAR 读取/枚举失败，已跳过（不影响其余 JAR 与报告其余章节）：枚举 lib jar 内部 class 失败: zip END header not found

### WEB-INF/lib/libA.jar  [修改 ~]
- 内部 class：新增 **0** · 删除 **0** · 修改 **2** · 未变 0

#### org/difflib/A1.class  [修改 ~]
- 反编译引擎：cfr(in-process)
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.difflib;
  
  public class A1 {
      public int x() {
-         return 1;
+         return 100;
      }
  }
  

```

#### org/difflib/A2.class  [修改 ~]
- 反编译引擎：cfr(in-process)
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.difflib;
  
  public class A2 {
      public int y() {
-         return 2;
+         return 200;
      }
  }
  

```

### WEB-INF/lib/libB.jar  [修改 ~]
- 内部 class：新增 **0** · 删除 **0** · 修改 **2** · 未变 0

#### org/difflib/B1.class  [修改 ~]
- 反编译引擎：cfr(in-process)
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.difflib;
  
  public class B1 {
      public int p() {
-         return 3;
+         return 300;
      }
  }
  

```

> 另有 1 个超出全局 Top-K 未展开源码；可在 GUI 双击该 JAR 查看全部内部 class 的逐项反编译对比。

## 六、破坏性变更清单（删除类/删除前端资源）
- 无删除类。

## 七、审计摘要（基础）
- 比对时间：2026-08-13 01:04:03
- 老包标识：1
- 新包标识：2
- AI 分析：未接入，本报告不含 AI 章节。

# 差异分析报告（Java 端口 / 真实包比对）

- 老包：`demo_v1.war`（版本 1）
- 新包：`demo_v2.war`（版本 2）

## 一、差异统计
- 新增 **2** · 删除 **0** · 修改 **9** · 未变 3
- 业务/类级变更(非jar)：10 · jar 级变更：1

### 按文件类型分布
| 文件类型 | 新增 | 删除 | 修改 |
|---|---|---|---|
| JSP 页面/标签 | 1 | 0 | 2 |
| Java 类 | 0 | 0 | 1 |
| 依赖 JAR | 0 | 0 | 1 |
| 前端 CSS | 0 | 0 | 1 |
| 前端 JS | 0 | 0 | 1 |
| 配置文件(XML/Properties) | 1 | 0 | 2 |
| 静态资源(图片/字体) | 0 | 0 | 1 |
| **合计** | **2** | **0** | **9** |

## 二、差异文件树（全量清单）
- `修改 ~` WEB-INF/classes/com/example/Service.class
- `修改 ~` WEB-INF/classes/config.properties
- `修改 ~` WEB-INF/lib/diff-lib.jar
- `修改 ~` WEB-INF/web.xml
- `修改 ~` index.jsp
- `修改 ~` static/app.js
- `修改 ~` static/logo.png
- `修改 ~` static/style.css
- `修改 ~` tags/common.tag
- `新增 +` WEB-INF/classes/new.properties
- `新增 +` new.jsp

## 三、反编译源码级差异（Top-12 修改/新增/删除 Java 类）
> 共 1 个类已反编译（本处仅展示前 Top-K 条完整 diff）。

### WEB-INF/classes/com/example/Service.class
- 反编译引擎：cfr(in-process)
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package com.example;
  
  public class Service {
      public int add(int n, int n2) {
-         return n + n2;
+         return n + n2 + 1;
      }
  }
  

```

## 四、文本类文件内容差异（Top-12 修改/新增/删除 配置文件/JSP/JS/HTML/CSS）
> 共 8 个文本类文件已做内容级逐行 diff（本处仅展示前 Top-K 条完整 diff）。

### WEB-INF/classes/config.properties
- 处理引擎：text-normalize
```diff
  app.name=demo
- app.version=1.0
- feature.flag=false
- timeout=30
+ app.version=2.0
+ feature.flag=true
+ timeout=60
  

```

### WEB-INF/web.xml
- 处理引擎：text-normalize
```diff
  <?xml version="1.0" encoding="UTF-8"?>
  <web-app>
    <servlet>
      <servlet-name>demo</servlet-name>
      <servlet-class>com.example.DemoServlet</servlet-class>
+     <load-on-startup>1</load-on-startup>
    </servlet>
+   <filter>
+     <filter-name>auth</filter-name>
+   </filter>
    <welcome-file-list>
      <welcome-file>index.jsp</welcome-file>
+     <welcome-file>home.html</welcome-file>
    </welcome-file-list>
  </web-app>
  

```

### index.jsp
- 处理引擎：jsp-normalize
```diff
  
  <%@ page contentType="text/html;charset=UTF-8" %>
  
  <html>
  <body>
- <h1>Demo v1</h1>
- <p>hello</p>
+ <h1>Demo v2</h1>
+ <p>hello world</p>
+ <p>new line</p>
  </body>
  </html>
  
  

```

### static/app.js
- 处理引擎：js-beautify
```diff
- function add(a,b){return a+b;}function sub(a,b){return a-b;}
+ function add(a,b){return a+b;}
+ function sub(a,b){return a-b;}
+ function mul(a,b){return a*b;}

```

### static/style.css
- 处理引擎：css-beautify
```diff
- body{color:red;margin:0;padding:0;}
+ body{color:blue;margin:0;padding:8px;font-size:14px;}

```

### tags/common.tag
- 处理引擎：jsp-normalize
```diff
  
  <%@ tag pageEncoding="UTF-8" %>
  
- <div class="box">v1</div>
+ <div class="box">v2 updated</div>
+ <span>extra</span>
  
  

```

### WEB-INF/classes/new.properties
- 处理引擎：text-normalize
```diff
// [新增文件] 老包无此文件
new.key=value
new.flag=true

```

### new.jsp
- 处理引擎：jsp-normalize
```diff
// [新增文件] 老包无此文件

<%@ page contentType="text/html;charset=UTF-8" %>

<html>
<body>new page</body>
</html>


```

## 五、差异依赖 JAR 内部源码对比（Top-12 内部 class 反编译源码级 diff）
> 共 1 个差异 JAR（新增 0 / 修改 1 / 删除 0）；其内部 class 合计：新增 1 · 删除 0 · 修改 1 · 未变 0。

### WEB-INF/lib/diff-lib.jar  [修改 ~]
- 内部 class：新增 **1** · 删除 **0** · 修改 **1** · 未变 0

#### org/difflib/Extra.class  [新增 +]
- 反编译引擎：cfr(in-process)
```diff
// [新增类] 老包无此文件
+ /*
+  * Decompiled with CFR 0.152.
+  */
+ package org.difflib;
+ 
+ public class Extra {
+     public String tag() {
+         return "v2-extra";
+     }
+ }
+ 

```

#### org/difflib/Helper.class  [修改 ~]
- 反编译引擎：cfr(in-process)
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package org.difflib;
  
  public class Helper {
      public String greet() {
-         return "hello-v1";
+         return "hello-v2-changed";
      }
  
      public int n() {
-         return 1;
+         return 2;
      }
  }
  

```

## 六、破坏性变更清单（删除类/删除前端资源）
- 无删除类。

## 七、审计摘要（基础）
- 比对时间：2026-08-13 00:29:46
- 老包标识：1
- 新包标识：2
- AI 分析：未接入，本报告不含 AI 章节。

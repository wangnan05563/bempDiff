# 差异分析报告（Java 端口 / 真实包比对）

- 老包：`demo_v1.war`（版本 1）
- 新包：`demo_v2.war`（版本 2）

## 一、差异统计
- 新增 **2** · 删除 **0** · 修改 **9** · 未变 2
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
> 共 1 个类已反编译（本处仅展示前 Top-K 条，且每条仅列变更行：附原始/修改后行号与变更类型——修改/新增/删除，不展示整个文件）。

### WEB-INF/classes/com/example/Service.class
- 反编译引擎：cfr(in-process)
```diff
- [修改] 老 L8 → 新 L8：
-            return n + n2;
+            return n + n2 + 1;
```

## 四、文本类文件内容差异（Top-12 修改/新增/删除 配置文件/JSP/JS/HTML/CSS）
> 共 8 个文本类文件已做内容级逐行 diff（本处仅展示前 Top-K 条，且每条仅列变更行：附原始/修改后行号与变更类型——修改/新增/删除，不展示整个文件）。

### WEB-INF/classes/config.properties
- 处理引擎：text-normalize
```diff
- [修改] 老 L2-4 → 新 L2-4：
-    app.version=1.0
-    feature.flag=false
-    timeout=30
+    app.version=2.0
+    feature.flag=true
+    timeout=60
```

### WEB-INF/web.xml
- 处理引擎：text-normalize
```diff
- [新增] 新 L6：
+        <load-on-startup>1</load-on-startup>

- [新增] 新 L8-10：
+      <filter>
+        <filter-name>auth</filter-name>
+      </filter>

- [新增] 新 L13：
+        <welcome-file>home.html</welcome-file>
```

### index.jsp
- 处理引擎：jsp-normalize
```diff
- [修改] 老 L6-7 → 新 L6-8：
-    <h1>Demo v1</h1>
-    <p>hello</p>
+    <h1>Demo v2</h1>
+    <p>hello world</p>
+    <p>new line</p>
```

### static/app.js
- 处理引擎：js-beautify
```diff
- [修改] 老 L1 → 新 L1-3：
-    function add(a,b){return a+b;}function sub(a,b){return a-b;}
+    function add(a,b){return a+b;}
+    function sub(a,b){return a-b;}
+    function mul(a,b){return a*b;}
```

### static/style.css
- 处理引擎：css-beautify
```diff
- [修改] 老 L1 → 新 L1：
-    body{color:red;margin:0;padding:0;}
+    body{color:blue;margin:0;padding:8px;font-size:14px;}
```

### tags/common.tag
- 处理引擎：jsp-normalize
```diff
- [修改] 老 L4 → 新 L4-5：
-    <div class="box">v1</div>
+    <div class="box">v2 updated</div>
+    <span>extra</span>
```

### WEB-INF/classes/new.properties
- 处理引擎：text-normalize
```diff
- [新增] 新 L1-4：
+    // [新增文件] 老包无此文件
+    new.key=value
+    new.flag=true
+
```

### new.jsp
- 处理引擎：jsp-normalize
```diff
- [新增] 新 L1-9：
+    // [新增文件] 老包无此文件
+    
+    <%@ page contentType="text/html;charset=UTF-8" %>
+    
+    <html>
+    <body>new page</body>
+    </html>
+    
+
```

## 五、差异依赖 JAR 内部源码对比（Top-12 内部 class 反编译源码级 diff）
> 共 1 个差异 JAR（新增 0 / 修改 1 / 删除 0）；其内部 class 合计：新增 1 · 删除 0 · 修改 1 · 未变 0。

### WEB-INF/lib/diff-lib.jar  [修改 ~]
- 内部 class：新增 **1** · 删除 **0** · 修改 **1** · 未变 0

#### org/difflib/Extra.class  [新增 +]
- 反编译引擎：cfr(in-process)
```diff
- [新增] 新 L2-12：
+    /*
+     * Decompiled with CFR 0.152.
+     */
+    package org.difflib;
+    
+    public class Extra {
+        public String tag() {
+            return "v2-extra";
+        }
+    }
+
```

#### org/difflib/Helper.class  [修改 ~]
- 反编译引擎：cfr(in-process)
```diff
- [修改] 老 L8 → 新 L8：
-            return "hello-v1";
+            return "hello-v2-changed";

- [修改] 老 L12 → 新 L12：
-            return 1;
+            return 2;
```

## 六、破坏性变更清单（删除类/删除前端资源）
- 无删除类。

## 七、AI 智能分析（两阶段 / 项目级上下文增强）
- **本次分析聚焦**：破坏性变更专项

### 项目级上下文（分析依据）
- 构建系统：多项目仓库（28 个项目）
- 模块：adapter、banks、banks/ext-fxbank、banks/ext-hlsecurity、banks/ext-hnnxbank、banks/ext-huisbank、banks/ext-huzbank、banks/ext-hxbank、banks/ext-jinzbank、banks/ext-nmgbank、banks/ext-qinnbank、banks/ext-sample、banks/ext-shaoxbank、banks/ext-tianjbank、banks/ext-xxbank、banks/ext-xxbank/xxbank-adapter-deploy、banks/ext-xxbank/xxbank-served-deploy、banks/ext-yibbank、bom、deploy、deploy/all-boot-deploy、deploy/all-war-deploy、deploy/join-bbep、deploy/join-bbsp4、deploy/join-bbsp4/joinsp4-model2、deploy/join-bbsp4/joinsp4-model6、framework、frontend
- 核心依赖：（无）
- 配置文件：（无）
- 架构简述：## 项目级上下文（递归扫描，共 28 个项目，根：D:\code\QJ\BEMP5.0DEV）

### 项目 1/28：Maven（adapter）
- 模块：api、client-api、as、adapter-bbsp4、adapter-stdsp4、adapter-stdsp4-conf、adapter-bbep
- 核心依赖：bemp-version-bom、bemp-adapter、findbugs-maven-plugin、maven-source-plugin、sonar-maven-plugin、maven-surefire-plugin、jacoco-maven-plugin
- 入口/主类：（无）
- 配置文件：as/src/test/resources/application.properties
- 技术栈：Maven
- 约定：Java 包根：com.hundsun.bemp.adapter（公共 4 级 / 最深 7 级）、持久层命名 *Mapper/*Dao（4 个）、服务层命名 *Service（19 个）、接口/控制层命名 *Controller/*Resource（7 个）
- 简述：多模块工程（Maven），模块含 api、client-api、as、adapter-bbsp4、adapter-stdsp4、adapter-stdsp4-conf、adapter-bbep；约定：Java 包根：com.hundsun.bemp.adapter（公共 4 级 / 最深 7 级）；持久层命名 *Mapper/*Dao（4 个）；服务层命名 *Service（19 个）；接口/控制层命名 *Controller/*Resource（7 个）。

### 项目 2/28：Maven（banks）
- 模块：ext-huisbank、ext-fxbank、ext-hnnxbank、ext-yibbank、ext-nmgbank、ext-hxbank、ext-tianjbank、ext-qinnbank、ext-jinzbank、ext-hlsecurity、ext-shaoxbank、ext-huzbank
- 核心依赖：bemp-banks
- 入口/主类：ext-fxbank/fxbank-served-deploy/src/test/java/com/hundsun/com/bemp/FxBempDevApplicationStarter.java、ext-hlsecurity/hlsecurity-served-deploy/src/main/java/com/hundsun/bemp/BempDevApplicationStarter.java、ext-hnnxbank/hnnxbank-adapter-deploy/src/main/java/com/hundsun/bemp/BempAdapterAppStarter.java、ext-hnnxbank/hnnxbank-adapter-deploy/src/test/java/com/hundsun/bemp/BempAdapterAppStarter.java、ext-hnnxbank/hnnxbank-served-deploy/src/main/java/com/hundsun/bemp/BempServedAppStarter.java、ext-hnnxbank/hnnxbank-served-deploy/src/test/java/com/hundsun/bemp/BempServedAppStarter.java、ext-huisbank/huisbank-adapter-boot-deploy/src/main/java/com/hundsun/bemp/BempAdapterAppStarter.java、ext-huisbank/huisbank-cpesmq-boot-deploy/src/main/java/com/hundsun/bemp/BempCpesmqAppStarter.java、ext-huisbank/huisbank-served-boot-deploy/src/main/java/com/hundsun/bemp/BempServedAppStarter.java、ext-huzbank/huzbank-adapter-deploy/src/main/java/com/hundsun/bemp/AdapterDevApplicationStarter.java、ext-huzbank/huzbank-served-deploy/src/main/java/com/hundsun/bemp/BempDevApplicationStarter.java、ext-hxbank/hxbank-adapter-deploy/src/test/java/com/hundsun/bemp/HxBankAdapterApplicationStarter.java、ext-hxbank/hxbank-served-deploy/src/main/java/com/hundsun/bemp/BempServedAppStarter.java、ext-hxbank/hxbank-served-deploy/src/test/java/com/hundsun/bemp/HxServerdApplicationStarter.java、ext-jinzbank/jinzbank-adapter-boot-deploy/src/main/java/com/hundsun/bemp/BempAdapterAppStarter.java、ext-jinzbank/jinzbank-cpesmq-boot-deploy/src/main/java/com/hundsun/bemp/BempCpesmqAppStarter.java、ext-jinzbank/jinzbank-manage-boot-deploy/src/main/java/com/hundsun/bemp/BempManageAppStarter.java、ext-jinzbank/jinzbank-served-boot-deploy/src/main/java/com/hundsun/bemp/BempServedAppStarter.java、ext-jinzbank/jinzbank-task-boot-deploy/src/main/java/com/hundsun/bemp/BempTaskAppStarter.java、ext-nmgbank/nmgbank-adapter-boot-deploy/src/main/java/com/hundsun/bemp/BempAdapterAppStarter.java
- 配置文件：ext-fxbank/fxbank-adapter-deploy/src/main/resources/application.properties、ext-fxbank/fxbank-cpesmq-deploy/src/main/resources/application.properties、ext-fxbank/fxbank-served-deploy/src/main/resources/application.properties、ext-fxbank/fxbank-served-deploy/src/test/resources/application.properties、ext-hlsecurity/hlsecurity-adapter-deploy/src/main/resources/application.properties、ext-hlsecurity/hlsecurity-served-deploy/src/main/resources/application.properties、ext-hnnxbank/hnnxbank-adapter-deploy/src/main/resources/application.properties、ext-hnnxbank/hnnxbank-cpesmq-deploy/src/main/resources/application.properties、ext-hnnxbank/hnnxbank-served-deploy/src/main/resources/application.properties、ext-huisbank/huisbank-adapter-boot-deploy/src/main/resources/application.properties、ext-huisbank/huisbank-cpesmq-boot-deploy/src/main/resources/application.properties、ext-huisbank/huisbank-served-boot-deploy/src/main/resources/application.properties、ext-huzbank/huzbank-adapter-deploy/src/main/resources/application.properties、ext-huzbank/huzbank-cpesmq-deploy/src/main/resources/application.properties、ext-huzbank/huzbank-served-deploy/src/main/resources/application.properties、ext-hxbank/hxbank-adapter-deploy/src/main/resources/application.properties、ext-hxbank/hxbank-cpesmq-deploy/src/main/resources/application.properties、ext-hxbank/hxbank-served-deploy/src/main/resources/application.properties、ext-jinzbank/jinzbank-adapter-boot-deploy/src/main/resources/application.properties、ext-jinzbank/jinzbank-cpesmq-boot-deploy/src/main/resources/application.properties
- 技术栈：Maven
- 约定：Java 包根：com.hundsun.bemp（公共 3 级 / 最深 6 级）、持久层命名 *Mapper/*Dao（1 个）、服务层命名 *Service（3 个）、接口/控制层命名 *Controller/*Resource（7 个）
- 简述：多模块工程（Maven），模块含 ext-huisbank、ext-fxbank、ext-hnnxbank、ext-yibbank、ext-nmgbank、ext-hxbank、ext-tianjbank、ext-qinnbank、ext-jinzbank、ext-hlsecurity、ext-shaoxbank、ext-huzbank；入口/主类：ext-fxbank/fxbank-served-deploy/src/test/java/com/hundsun/com/bemp/FxBempDevApplicationStarter.java、ext-hlsecurity/hlsecurity-served-deploy/src/main/java/com/hundsun/bemp/BempDevApplicationStarter.java、ext-hnnxbank/hnnxbank-adapter-deploy/src/main/java/com/hundsun/bemp/BempAdapterAppStarter.java、ext-hnnxbank/hnnxbank-adapter-deploy/src/test/java/com/hundsun/bemp/BempAdapterAppStarter.java、ext-hnnxbank/hnnxbank-served-deploy/src/main/java/com/hundsun/bemp/BempServedAppStarter.java、ext-hnnxbank/hnnxbank-served-deploy/src/test/java/com/hundsun/bemp/BempServedAppStarter.java、ext-huisbank/huisbank-adapter-boot-deploy/src/main/java/com/hundsun/bemp/BempAdapterAppStarter.java、ext-huisbank/huisbank-cpesmq-boot-deploy/src/main/java/com/hundsun/bemp/BempCpesmqAppStarter.java、ext-huisbank/huisbank-served-boot-deploy/src/main/java/com/hundsun/bemp/BempServedAppStarter.java、ext-huzbank/huzbank-adapter-deploy/src/main/java/com/hundsun/bemp/AdapterDevApplicationStarter.java、ext-huzbank/huzbank-served-deploy/src/main/java/com/hundsun/bemp/BempDevApplicationStarter.java、ext-hxbank/hxbank-adapter-deploy/src/test/java/com/hundsun/bemp/HxBankAdapterApplicationStarter.java、ext-hxbank/hxbank-served-deploy/s
…（上下文超长截断，剩余项目省略）

### 阶段A 概览
- 整体风险：**LOW**
- 影响范围：主要影响前端页面展示逻辑（index.jsp、tags/common.tag、new.jsp）和配置参数（config.properties、new.properties），以及部署配置（web.xml）中的新增过滤器和欢迎页设置
- **项目上下文影响**：基于多模块Maven架构和统一的包命名约定，所有配置变更均在模块化配置文件中进行，未影响公共接口定义。模块间依赖关系保持稳定，新增文件未破坏现有模块边界，因此未发现破坏性变更。
- 测试主题：验证新增过滤器(auth)的拦截规则是否影响现有接口调用；检查配置文件(new.properties)中新增参数是否被其他模块引用；确认Service类方法返回值变更是否影响依赖该方法的上游调用方；测试前端页面(new.jsp)是否能正常加载并显示；验证web.xml中load-on-startup配置是否改变Servlet加载顺序；检查CSS样式变更是否导致前端界面布局异常；确认JSP页面新增元素是否破坏现有模板引擎渲染逻辑；测试所有模块的版本号更新是否影响依赖管理
- 每文件初评风险：
  - `WEB-INF/classes/com/example/Service.class`：**LOW** — 服务类方法返回值变更属于功能增强，未改变方法签名和参数类型
  - `WEB-INF/classes/config.properties`：**LOW** — 配置参数更新属于非功能性变更，未影响接口定义
  - `WEB-INF/web.xml`：**LOW** — 部署配置新增过滤器和欢迎页，属于非破坏性扩展
  - `index.jsp`：**LOW** — 页面内容更新未改变结构和功能逻辑
  - `static/app.js`：**LOW** — 新增函数属于功能扩展，未修改现有函数签名
  - `static/style.css`：**LOW** — 样式属性变更属于前端视觉调整，不影响功能
  - `tags/common.tag`：**LOW** — JSP标签内容更新未改变标签定义和用法
  - `WEB-INF/classes/new.properties`：**LOW** — 新增配置文件未影响现有配置体系
  - `new.jsp`：**LOW** — 新增页面文件未改变现有页面结构和路由

### 破坏性变更专项结论
- 未识别到破坏性变更点。

- **兼容性结论**：无破坏性变更，所有修改均保持向后兼容性。需特别关注web.xml新增的<filter>配置可能对现有请求处理流程产生影响，以及new.jsp是否被正确集成到前端路由体系中
### 优化改造建议（结合项目上下文）
[

### 阶段B 逐文件深读
#### WEB-INF/classes/com/example/Service.class
- 风险：**MEDIUM**
- 意图：修改计算逻辑，增加固定值1到返回结果
- 影响：可能影响所有调用该方法的下游模块，导致数值计算结果偏差
- **项目上下文影响**：{
- 测试要点：
  - 验证所有调用Service类方法的模块是否处理了返回值变化
  - 检查依赖该服务的业务逻辑是否因数值变化产生异常
  - 确认接口契约是否保持一致（如返回值类型和范围）
  - 验证配置文件中相关参数是否需要调整以适配新逻辑
  - 运行集成测试确保上下游模块协作正常

#### WEB-INF/classes/config.properties
- 风险：**MEDIUM**
- 意图：更新应用版本号、功能标志和超时设置
- 影响：{
- **项目上下文影响**：{
- 测试要点：
  - 验证所有子模块在新配置下的功能开关逻辑（feature.flag=true）是否按预期执行
  - 检查超时参数(timeout=60)是否影响核心业务流程的时序行为
  - 确认版本号(app.version=2.0)变更是否导致依赖版本校验的模块出现兼容性问题
  - 测试配置文件加载顺序是否覆盖其他配置源（如BOM管理的版本号）
  - 验证配置变更是否影响第三方服务对接的时序约束

#### WEB-INF/web.xml
- 风险：**HIGH**
- 意图：新增Web应用配置项，用于定义Filter和欢迎页面
- 影响：{
- **项目上下文影响**：{
- 测试要点：
  - 验证新增的auth Filter是否能正确加载并生效
  - 检查welcome-file配置是否能正确访问home.html页面
  - 测试应用启动时是否出现Filter缺失导致的错误
  - 确认其他模块调用该Web服务时是否受影响
  - 验证Servlet加载顺序是否导致初始化异常

#### index.jsp
- 风险：**LOW**
- 意图：页面内容更新
- 影响：{
- 测试要点：
  - 验证页面标题是否正确显示为'Demo v2'
  - 检查新增的'new line'段落是否正常渲染
  - 确认页面布局在修改后未出现错位或样式异常
  - 测试浏览器兼容性（尤其关注新增内容的显示）

#### static/app.js
- 风险：**LOW**
- 意图：新增数学运算函数 mul，同时保留原有 add 和 sub 函数的定义
- 影响：{
- **项目上下文影响**：{
- 测试要点：
  - 验证 mul 函数的运算逻辑（如 2*3 应返回 6）
  - 检查原有 add/sub 函数调用场景是否仍正常工作
  - 测试依赖该文件的页面在运算场景下的显示结果
  - 确认函数未引入命名冲突（如是否覆盖其他模块同名函数）

#### static/style.css
- 风险：**MEDIUM**
- 意图：调整基础页面样式，修改body文字颜色、填充间距及字体大小
- 影响：{
- **项目上下文影响**：{
- 测试要点：
  - 验证页面整体布局是否因padding:8px产生错位（如L1-2的padding变化可能影响元素间距）
  - 检查文字颜色是否从红色正确变为蓝色（L1-2的color属性修改）
  - 确认字体大小14px是否在所有文本元素上生效（新增属性需覆盖原有样式）
  - 测试不同分辨率下样式响应性是否保持一致
  - 验证是否存在样式覆盖冲突（如其他CSS文件是否定义了body的padding或font-size）

#### tags/common.tag
- 风险：**LOW**
- 意图：UI内容更新
- 影响：{
- **项目上下文影响**：{
- 测试要点：
  - 验证使用 common.tag 的页面是否正确显示更新后的文本内容
  - 检查新增 <span>extra</span> 是否导致布局错位或样式冲突
  - 确认标签文件未引入新的依赖或配置要求
  - 确保标签文件的修改不会影响其他模块的标签调用兼容性

#### WEB-INF/classes/new.properties
- 风险：**LOW**
- 意图：新增配置项
- 影响：{
- **项目上下文影响**：{
- 测试要点：
  - 验证新增配置项是否被模块代码正确读取（如通过@Value或Environment对象）
  - 检查模块启动日志中是否加载了new.properties文件
  - 确认无其他模块依赖该配置项（通过依赖分析工具定位引用关系）
  - 测试模块在不同环境下的配置加载优先级

#### new.jsp
- 风险：**LOW**
- 意图：新增一个基础JSP页面用于展示新内容
- 影响：{
- **项目上下文影响**：{
- 测试要点：
  - 验证JSP文件路径是否在spring.mvc.view.prefix和spring.mvc.view.suffix配置范围内
  - 检查是否存在对应请求路径的Controller方法（如/new或特定REST端点）
  - 确认JSTL标签库是否已正确引入（如<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>）
  - 测试页面在浏览器中的可访问性（需确保Web模块已打包并部署）


## 八、审计摘要（基础）
- 比对时间：2026-08-23 15:15:11
- 老包标识：1
- 新包标识：2
- AI 分析：已接入（含项目级上下文增强）

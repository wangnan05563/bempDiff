# 差异分析报告（Java 端口 / 真实包比对）

- 老包：`old.war`（版本 1.6.1）
- 新包：`new.war`（版本 1.6.2）

## 一、差异统计
- 新增 **1** · 删除 **1** · 修改 **6** · 未变 0
- 业务/类级变更(非jar)：8 · jar 级变更：0

## 二、差异文件树（全量清单）
- `修改 ~` META-INF/MANIFEST.MF
- `修改 ~` WEB-INF/classes/com/demo/Demo.class
- `修改 ~` css/style.css
- `修改 ~` static/app.js
- `修改 ~` static/app.min.js
- `修改 ~` templates/page.html
- `新增 +` static/new-feature.js
- `删除 -` static/old-lib.js

## 三、反编译源码级差异（Top-20 修改/新增/删除 Java 类）
> 共 1 个类已反编译（本处仅展示前 Top-K 条完整 diff）。

### WEB-INF/classes/com/demo/Demo.class
- 反编译引擎：cfr(in-process)
```diff
  /*
   * Decompiled with CFR 0.152.
   */
  package com.demo;
  
  class Demo {
-     private String name = "legacy";
+     private String name = "current";
  
      Demo() {
      }
  
      public String sayHello(String string) {
-         if (string == null || string.isEmpty()) {
+         if (string == null || string.trim().isEmpty()) {
              return "Hello, guest";
          }
-         return "Hello, " + string;
+         return "Hello, " + string.trim();
      }
  
      public int calc(int n, int n2) {
-         return n + n2;
+         return n * n2;
      }
+ 
+     public boolean isAdmin(String string) {
+         return "admin".equalsIgnoreCase(string);
+     }
  }
  

```

## 四、前端资源代码差异（Top-20 修改/新增/删除 JS/HTML/CSS）
> 共 6 个前端文件已做美化后内容 diff（本处仅展示前 Top-K 条完整 diff）。

### css/style.css
- 处理引擎：css-beautify
```diff
- body{margin:0;padding:0;background:#fff;}a{color:#00f;}
+ body{margin:0;padding:8px;background:#fafafa;}a{color:#0a0;}

```

### static/app.js
- 处理引擎：js-beautify
```diff
  function validateForm(form) {
    var name = form.name.value;
    if (name.length === 0) {
-     return false;
+     alert('name required'); return false;
    }
    return true;
  }
  

```

### static/app.min.js
- 处理引擎：js-beautify
```diff
  function init(){
    var a=1;
    var b=2;
    var total=a+b;
    function calc(x){
-     return x*2+total;
+     return x*3+total;
      
    }function render(){
-     document.getElementById('app').innerHTML='old';
+     document.getElementById('app').innerHTML='new-v2';
      
    }var api=new Object();
    api.get=function(){
      return fetch('/api/x');
      
    };
    function bootstrap(){
      init();
      render();
      calc(10);
      
    }window.onload=bootstrap;
    function init(){
      var a=1;
      var b=2;
      var total=a+b;
      function calc(x){
-       return x*2+total;
+       return x*3+total;
        
      }function render(){
-       document.getElementById('app').innerHTML='old';
+       document.getElementById('app').innerHTML='new-v2';
        
      }var api=new Object();
      api.get=function(){
        return fetch('/api/x');
        
      };
      function bootstrap(){
        init();
        render();
        calc(10);
        
      }window.onload=bootstrap;
      function init(){
        var a=1;
        var b=2;
        var total=a+b;
        function calc(x){
-         return x*2+total;
+         return x*3+total;
          
        }function render(){
-         document.getElementById('app').innerHTML='old';
+         document.getElementById('app').innerHTML='new-v2';
          
        }var api=new Object();
        api.get=function(){
          return fetch('/api/x');
          
        };
        function bootstrap(){
          init();
          render();
          calc(10);
          
        }window.onload=bootstrap;
        

```

### templates/page.html
- 处理引擎：html-beautify
```diff
  <div>
  <span>hello</span>
- <p>old content</p>
+ <p>new content v2</p>
  </div>
  <ul>
  <li>item1</li>
+ <li>item2</li>
  </ul>

```

### static/new-feature.js
- 处理引擎：js-beautify
```diff
// [新增文件] 老包无此文件
export function newFeature(){return 'added in v2';}
```

### static/old-lib.js
- 处理引擎：js-beautify
```diff
// [删除文件] 新包无此文件（前端资源移除，需确认引用方）
function deprecated(){return 'gone';}
```

## 五、破坏性变更清单（删除类/删除前端资源）
- `static/old-lib.js`（潜在对外 API / 行为移除，需重点回归）

## 六、审计摘要（基础）
- 比对时间：2026-08-12 01:08:13
- 老包标识：1.6.1
- 新包标识：1.6.2
- AI 分析：未接入（core.ai 待补），本报告不含 AI 章节。

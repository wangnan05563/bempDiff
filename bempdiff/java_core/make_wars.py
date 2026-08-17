#!/usr/bin/env python3
"""构造两个演示 war：old.war / new.war，含压缩/可读/变更/新增/删除的前端 JS/HTML/CSS，
以及一个变更的 .class（L1），用于端到端验证前端源码对比与分析链路。
同时写入 MANIFEST.MF，使 Implementation-Version 分别为 1.6.1 / 1.6.2。"""
import os, sys, zipfile

OUT = sys.argv[1] if len(sys.argv) > 1 else "."

def w(name, version, entries):
    p = os.path.join(OUT, name)
    with zipfile.ZipFile(p, "w", zipfile.ZIP_DEFLATED) as z:
        manifest = (
            "Manifest-Version: 1.0\r\n"
            "Implementation-Version: {ver}\r\n"
            "Built-By: bempdiff-e2e\r\n\r\n"
        ).format(ver=version)
        z.writestr("META-INF/MANIFEST.MF", manifest)
        for k, v in entries.items():
            z.writestr(k, v)
    print("wrote", p, "version", version)

# 压缩单行 JS（old）：超长单行，直接行级 diff 无意义
MIN_OLD = ("function init(){var a=1;var b=2;var total=a+b;"
           "function calc(x){return x*2+total;}function render(){document.getElementById('app').innerHTML='old';}"
           "var api=new Object();api.get=function(){return fetch('/api/x');};"
           "function bootstrap(){init();render();calc(10);}window.onload=bootstrap;") * 3
MIN_NEW = MIN_OLD.replace("'old'", "'new-v2'").replace("return x*2+total;", "return x*3+total;")

# 可读多行 JS（old）
READ_OLD = (
    "function validateForm(form) {\n"
    "  var name = form.name.value;\n"
    "  if (name.length === 0) {\n"
    "    return false;\n"
    "  }\n"
    "  return true;\n"
    "}\n"
)
READ_NEW = READ_OLD.replace("return false;", "alert('name required'); return false;")

# HTML
HTML_OLD = "<div><span>hello</span><p>old content</p></div><ul><li>item1</li></ul>"
HTML_NEW  = "<div><span>hello</span><p>new content v2</p></div><ul><li>item1</li><li>item2</li></ul>"

# CSS
CSS_OLD = "body{margin:0;padding:0;background:#fff;}a{color:#00f;}"
CSS_NEW  = "body{margin:0;padding:8px;background:#fafafa;}a{color:#0a0;}"

# 被删除的 JS（old 有，new 无）
OLD_LIB = "function deprecated(){return 'gone';}"

# 新增的 JS（new 有，old 无）
NEW_FEATURE = "export function newFeature(){return 'added in v2';}"

def read_class(stem):
    dp = os.path.join(OUT, stem)
    if os.path.exists(dp):
        with open(dp, "rb") as f:
            return f.read()
    return None

demo_old = read_class("Demo.class.old")
demo_new = read_class("Demo.class.new")

OLD = {
    "WEB-INF/classes/com/demo/Demo.class": demo_old,
    "static/app.min.js": MIN_OLD,
    "static/app.js": READ_OLD,
    "templates/page.html": HTML_OLD,
    "css/style.css": CSS_OLD,
    "static/old-lib.js": OLD_LIB,
}
NEW = {
    "WEB-INF/classes/com/demo/Demo.class": demo_new,
    "static/app.min.js": MIN_NEW,
    "static/app.js": READ_NEW,
    "templates/page.html": HTML_NEW,
    "css/style.css": CSS_NEW,
    "static/new-feature.js": NEW_FEATURE,
}
w("old.war", "1.6.1", OLD)
w("new.war", "1.6.2", NEW)

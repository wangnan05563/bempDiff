package com.bempdiff.model;

/** 文件分类（FR2.3）。对应 prototype: classify。
 *  JS/HTML/CSS 为"前端源码文本"，纳入内容 diff 与 AI 分析（FR4.4 增强）；
 *  JSP(.jsp/.jspx/.tag/.tagx) 为服务端页面/标签文件，同样纳入内容级逐行 diff（本需求扩展）；
 *  CONFIG(.xml/.properties/.yml/.json/.tld/.xhtml/.vm/.ftl 等) 为文本配置/描述符，纳入内容 diff；
 *  其余 STATIC（图片/字体/二进制）仅比对 sha256 与大小（FR4.8）；OTHER 为未识别兜底。 */
public enum FileClass {
    CLASS,
    JAR,
    CONFIG,
    STATIC,
    /** 前端 JavaScript 源码（.js）。压缩后通常单行，需美化后再做内容 diff。 */
    JS,
    /** 前端 HTML 模板（.html/.htm）。 */
    HTML,
    /** 前端 CSS 样式（.css）。 */
    CSS,
    /** 服务端 JSP 页面 / 标签文件（.jsp/.jspx/.tag/.tagx）。需求扩展：纳入内容级逐行 diff。 */
    JSP,
    /**
     * 归档压缩包（.zip / .war / .ear / .tar / .tar.gz / .tgz）。
     * 这类文件是二进制，按字节解码再"美化"会得到一堆乱码并长时间跑 JS 状态机，
     * 与其在 {@link com.bempdiff.diff.FrontendTextDiff} 上做无效文本 diff，
     * 不如直接把它们当作"目录"处理：对两个归档做条目清单（name/size/crc）展示，
     * 差异按行级呈现。这样点击一个 4.5 MB zip 即时返回可见结果，不再卡在反编译 spinner。
     * {@link com.bempdiff.model.FileClass#JAR} 仍表示库 jar（war 内的 lib jar 等），与 ARCHIVE 平级。
     */
    ARCHIVE,
    OTHER;

    /** 是否为"前端源码文本"——需要内容 diff 与 AI 分析（区别于图片/字体等二进制 STATIC）。 */
    public boolean isFrontendText() {
        return this == JS || this == HTML || this == CSS;
    }

    /** 是否为"可内容 diff 的文本资源"——CONFIG/JSP/前端源码均纳入内容级逐行比对（区别于二进制 STATIC）。 */
    public boolean isTextDiffable() {
        return this == CONFIG || this == JSP || this == JS || this == HTML || this == CSS;
    }

    /** 报告/统计用的中文归类标签（用于按文件类型汇总 新增/删除/修改）。 */
    public String categoryLabel() {
        switch (this) {
            case CLASS: return "Java 类";
            case JAR:   return "依赖 JAR";
            case CONFIG:return "配置文件(XML/Properties)";
            case JSP:   return "JSP 页面/标签";
            case JS:    return "前端 JS";
            case HTML:  return "前端 HTML";
            case CSS:   return "前端 CSS";
            case STATIC:return "静态资源(图片/字体)";
            case ARCHIVE:return "归档压缩包(zip/war/ear/tar)";
            default:    return "其他";
        }
    }
}

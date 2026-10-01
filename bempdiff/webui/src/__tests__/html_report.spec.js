// R11 HTML 报告导出（二期 T01476）——buildHtmlReport 纯函数验收。
// 覆盖：自包含文档结构、自定义标题/副标题/落款/风险口径注入与转义、
//       Markdown 管线转换（标题/表格/代码）、默认值、防注入。
import { describe, it, expect } from 'vitest'
import { buildHtmlReport, defaultReportMeta } from '../lib/html_report'

const MD = '# 整体风险：**高**\n\n| 文件 | 状态 |\n| --- | --- |\n| A.java | 修改 |\n\n```java\nint x = 1;\n```'

describe('R11 buildHtmlReport', () => {
  it('自包含文档：doctype/charset/内联样式齐全', () => {
    const h = buildHtmlReport({ md: MD })
    expect(h.startsWith('<!DOCTYPE html>')).toBe(true)
    expect(h).toContain('charset="utf-8"')
    expect(h).toContain('<style>')
    expect(h).not.toMatch(/<link\s/) // 无外部样式依赖
    expect(h).not.toMatch(/<script/) // 无脚本（纯静态报告）
  })

  it('Markdown 管线：标题/表格/代码块转换 + 风险着色与预览一致', () => {
    const h = buildHtmlReport({ md: MD })
    expect(h).toContain('<h1>')
    expect(h).toContain('<table')
    expect(h).toContain('<pre>')
    expect(h).toContain('rp-report-body')
  })

  it('自定义模板：标题/副标题/落款/风险口径全部注入', () => {
    const h = buildHtmlReport({
      md: MD,
      title: '升级审计报告',
      subtitle: '任务 j-123',
      author: '质量组',
      riskNote: '风险等级按主版本变更数量与安全项加权评估'
    })
    expect(h).toContain('<title>升级审计报告</title>')
    expect(h).toContain('>升级审计报告</h1>')
    expect(h).toContain('任务 j-123')
    expect(h).toContain('落款：质量组')
    expect(h).toContain('风险口径说明')
    expect(h).toContain('加权评估')
  })

  it('模板字段 HTML 转义（防注入）', () => {
    const h = buildHtmlReport({
      md: MD,
      title: '<script>alert(1)</script>',
      author: '<img src=x onerror=alert(2)>',
      riskNote: '"><b>inject'
    })
    expect(h).not.toContain('<script>alert')
    expect(h).not.toContain('<img src=x')
    expect(h).toContain('&lt;script&gt;')
    expect(h).toContain('&lt;b&gt;inject')
  })

  it('默认值：无标题用默认标题，无落款/口径不渲染对应块', () => {
    const h = buildHtmlReport({ md: MD })
    expect(h).toContain('BempDiff 差异分析报告')
    expect(h).not.toContain('落款：')
    expect(h).not.toContain('风险口径说明</b>')
    expect(h).toMatch(/生成时间：/)
  })

  it('defaultReportMeta：默认标题 + jobId 副标题', () => {
    const m = defaultReportMeta({ job: { jobId: 'j-9' } })
    expect(m.title).toBe('BempDiff 差异分析报告')
    expect(m.subtitle).toBe('任务 j-9')
    expect(m.author).toBe('')
  })
})

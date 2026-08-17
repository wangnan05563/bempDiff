// AI 风险结论严重性档位匹配（P0-④）。
// 后端 AI 章节的 risk 是自由文本（高/中/低/严重/致命…），前端用此法在不改后端的前提下上色。
// 匹配规则：strong 文本命中档位正则 + 其上下文（自身及所有祖先 li/p 文本）含“风险”。
// 配色：breaking 红 / high 橙 / med 黄 / low 绿 / none 灰（对标 japi-compliance-checker）。

export const RISK_TERMS = [
  { re: /(致命|严重|critical|breaking|破坏性|fatal)/i, cls: 'sev-breaking' },
  { re: /(高|high|较大|major)/i, cls: 'sev-high' },
  { re: /(中|med|medium|一般|moderate)/i, cls: 'sev-med' },
  { re: /(低|low|较小|轻微|minor)/i, cls: 'sev-low' },
  { re: /(无|none|没有|未|na)/i, cls: 'sev-none' },
]

// 判定一个 strong（风险值）应上的严重性 class。
//   strongText: <strong> 的文本内容（已解码，安全）
//   ctxText:    自身 + 所有祖先 <li>/<p> 文本的拼接
// 返回 class 名（如 'sev-high'）或 null（不上色）。
export function matchSev(strongText, ctxText) {
  if (!/风险/.test(ctxText)) return null
  const hit = RISK_TERMS.find(t => t.re.test(strongText))
  return hit ? hit.cls : null
}

// 仅按风险文本本身匹配档位 class（用于报告顶部「总体风险结论」汇总条，无上下文要求）。
export function sevClassFromText(text) {
  if (!text) return null
  const hit = RISK_TERMS.find(t => t.re.test(text))
  return hit ? hit.cls : null
}

// 对渲染后的报告 HTML 做风险着色（浏览器侧）。
// 仅给「处在风险上下文中的 <strong>」加上 sev-* class；其余不动，确保不破坏原结构与安全转义。
export function colorizeReport(htmlString) {
  if (typeof document === 'undefined' || !htmlString) return htmlString
  try {
    const doc = new DOMParser().parseFromString(htmlString, 'text/html')
    doc.querySelectorAll('strong').forEach(el => {
      const ctx = []
      let p = el.parentElement
      while (p && p !== doc.body) {
        if (p.tagName === 'LI' || p.tagName === 'P') ctx.push(p.textContent || '')
        p = p.parentElement
      }
      const cls = matchSev(el.textContent || '', ctx.join(' '))
      if (cls) el.classList.add(cls)
    })
    return doc.body.innerHTML
  } catch (_) {
    return htmlString
  }
}

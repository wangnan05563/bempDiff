import { describe, it, expect } from 'vitest'
import { tokenizeLine, langOf, TOKEN_TYPES } from '../lib/syntax_highlight'

// 便捷断言：返回 token 类型序列（用于快速比对结构）
const seq = (line, lang) => tokenizeLine(line, lang).map(t => t.type)

describe('syntax_highlight tokenizeLine', () => {
  it('JS：关键字/标识符/函数调用/字符串/数字/注释', () => {
    expect(seq('const x = "hi";', 'js')).toEqual(['keyword', 'ws', 'ident', 'ws', 'op', 'ws', 'string', 'op'])
    expect(seq('function foo(a) {', 'js')).toEqual(['keyword', 'ws', 'func', 'op', 'ident', 'op', 'ws', 'op'])
    expect(seq('bar(1);', 'js')).toEqual(['func', 'op', 'number', 'op']) // ');' 连续运算符合并为单个 op
    expect(seq('// todo', 'js')).toEqual(['comment'])
    expect(seq('x = 42', 'js')).toEqual(['ident', 'ws', 'op', 'ws', 'number'])
  })

  it('Java：class/类型名首字母大写识别', () => {
    expect(seq('public class Foo {', 'java')).toEqual(['keyword', 'ws', 'keyword', 'ws', 'type', 'ws', 'op'])
  })

  it('Python：def 函数与 # 行注释', () => {
    expect(seq('def main():', 'python')).toEqual(['keyword', 'ws', 'func', 'op']) // '():' 连续运算符合并为单个 op
    expect(seq('# comment', 'python')).toEqual(['comment'])
    expect(seq('if __name__:', 'python')).toEqual(['keyword', 'ws', 'ident', 'op'])
  })

  it('HTML：标签/属性/引号值/注释', () => {
    expect(seq('<div class="x">', 'html')).toEqual(['tag', 'ws', 'attr', 'op', 'string', 'op'])
    expect(seq('<!-- note -->', 'html')).toEqual(['comment'])
    expect(seq('</div>', 'html')).toEqual(['tag', 'op'])
  })

  it('CSS：属性名/颜色值/注释', () => {
    expect(seq('color: #ff0000;', 'css')).toEqual(['attr', 'op', 'ws', 'number', 'op'])
    expect(seq('/* c */', 'css')).toEqual(['comment'])
    expect(seq('margin: 0 auto;', 'css')).toEqual(['attr', 'op', 'ws', 'number', 'ws', 'ident', 'op'])
  })

  it('Markdown：标题/链接/行内代码/分隔线', () => {
    expect(seq('# Title', 'markdown')).toEqual(['op', 'ws', 'heading'])
    expect(seq('[text](url)', 'markdown')).toEqual(['link', 'string'])
    expect(seq('`code` here', 'markdown')).toEqual(['inlinecode', 'ws', 'plain'])
    expect(seq('---', 'markdown')).toEqual(['op'])
  })

  it('plain：整行单段，不做任何分词', () => {
    expect(seq('anything goes here', 'plain')).toEqual(['plain'])
  })

  it('token 合并：相邻 ws/plain 合并为一个 token', () => {
    const toks = tokenizeLine('a   b', 'js')
    expect(toks.filter(t => t.type === 'ws')).toHaveLength(1)
    expect(toks.find(t => t.type === 'ws').text).toBe('   ')
  })

  it('字符串转义：引号内转义不提前结束', () => {
    expect(seq('s = "a\\"b";', 'js')).toEqual(['ident', 'ws', 'op', 'ws', 'string', 'op'])
  })

  it('输出 token 类型均在 TOKEN_TYPES 表内', () => {
    for (const lang of ['js', 'java', 'python', 'html', 'css', 'markdown', 'plain']) {
      for (const t of tokenizeLine('if (x) { return "y"; } // c', lang)) {
        expect(TOKEN_TYPES).toContain(t.type)
      }
    }
  })
})

describe('syntax_highlight langOf', () => {
  it('扩展名优先（覆盖后端未细分的 .md/.py/.ts）', () => {
    expect(langOf('a/b/c.py', 'OTHER')).toBe('python')
    expect(langOf('a/b/c.ts', 'OTHER')).toBe('ts')
    expect(langOf('README.md', 'CONFIG')).toBe('markdown')
    expect(langOf('static/app.js', 'JS')).toBe('js')
    expect(langOf('index.html', 'HTML')).toBe('html')
    expect(langOf('style.css', 'CSS')).toBe('css')
  })

  it('FileClass 兜底', () => {
    expect(langOf('', 'CLASS')).toBe('java')
    expect(langOf('', 'JS')).toBe('js')
    expect(langOf('', 'JSP')).toBe('jsp')
    expect(langOf('', 'CONFIG')).toBe('plain')
    expect(langOf('', 'OTHER')).toBe('plain')
  })

  it('大小写不敏感', () => {
    expect(langOf('A/B/README.MD', 'OTHER')).toBe('markdown')
    expect(langOf('A/B/App.TSX', 'OTHER')).toBe('ts')
  })
})

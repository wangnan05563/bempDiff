// 忽略规则引擎（右键菜单「忽略」）测试：精确/前缀/文件名/扩展名 匹配、添加去重、删除。
import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  isIgnored, matchRule, addRule, removeRule, defaultRuleFor, basenameOf
} from '../lib/ignore'

describe('matchRule 单规则匹配', () => {
  const key = 'WEB-INF/lib/commons-lang.jar'
  it('exact：精确匹配', () => {
    expect(matchRule(key, { type: 'exact', value: 'WEB-INF/lib/commons-lang.jar' })).toBe(true)
    expect(matchRule(key, { type: 'exact', value: 'WEB-INF/lib/other.jar' })).toBe(false)
  })
  it('prefix：前缀匹配（忽略整目录）', () => {
    expect(matchRule(key, { type: 'prefix', value: 'WEB-INF/lib/' })).toBe(true)
    expect(matchRule(key, { type: 'prefix', value: 'WEB-INF/' })).toBe(true)
    expect(matchRule(key, { type: 'prefix', value: 'META-INF/' })).toBe(false)
  })
  it('name：文件名（基名）匹配', () => {
    expect(matchRule(key, { type: 'name', value: 'commons-lang.jar' })).toBe(true)
    expect(matchRule('sub/deep/commons-lang.jar', { type: 'name', value: 'commons-lang.jar' })).toBe(true)
    expect(matchRule(key, { type: 'name', value: 'lang.jar' })).toBe(false)
  })
  it('ext：扩展名匹配（大小写不敏感）', () => {
    expect(matchRule('a.LOG', { type: 'ext', value: '.log' })).toBe(true)
    expect(matchRule(key, { type: 'ext', value: '.jar' })).toBe(true)
    expect(matchRule(key, { type: 'ext', value: '.xml' })).toBe(false)
  })
  it('空值规则不匹配', () => {
    expect(matchRule(key, { type: 'name', value: '' })).toBe(false)
    expect(matchRule(key, null)).toBe(false)
  })
})

describe('isIgnored 组合判定', () => {
  it('任一规则命中即忽略', () => {
    const rules = [{ type: 'name', value: 'Thumbs.db' }, { type: 'ext', value: '.tmp' }]
    expect(isIgnored('a/Thumbs.db', rules)).toBe(true)
    expect(isIgnored('x.tmp', rules)).toBe(true)
    expect(isIgnored('x.java', rules)).toBe(false)
  })
  it('空规则/空数组不忽略', () => {
    expect(isIgnored('a.txt', [])).toBe(false)
    expect(isIgnored('a.txt', null)).toBe(false)
  })
})

describe('addRule / removeRule', () => {
  it('添加：同类型同值去重', () => {
    let rules = []
    rules = addRule(rules, 'name', 'Thumbs.db')
    rules = addRule(rules, 'name', 'Thumbs.db')
    rules = addRule(rules, 'name', 'Other.db')
    expect(rules.length).toBe(2)
  })
  it('添加：空值忽略', () => {
    expect(addRule([], 'name', '  ').length).toBe(0)
  })
  it('删除：按下标', () => {
    const rules = [{ type: 'name', value: 'a' }, { type: 'ext', value: '.tmp' }]
    expect(removeRule(rules, 0).length).toBe(1)
    expect(removeRule(rules, 0)[0].type).toBe('ext')
    expect(removeRule(rules, 9).length).toBe(2) // 越界幂等
  })
})

describe('defaultRuleFor 默认规则值', () => {
  it('文件名：取基名', () => {
    expect(defaultRuleFor('sub/deep/a.log', 'name')).toBe('a.log')
  })
  it('扩展名：取最后一个点的后缀', () => {
    expect(defaultRuleFor('a.log', 'ext')).toBe('.log')
    expect(defaultRuleFor('noext', 'ext')).toBe('')
  })
  it('目录前缀：取父目录', () => {
    expect(defaultRuleFor('WEB-INF/lib/x.jar', 'prefix')).toBe('WEB-INF/lib/')
    expect(defaultRuleFor('dir/', 'prefix')).toBe('dir/')
  })
  it('basenameOf：目录 key 去尾斜杠', () => {
    expect(basenameOf('WEB-INF/lib/')).toBe('lib')
  })
})

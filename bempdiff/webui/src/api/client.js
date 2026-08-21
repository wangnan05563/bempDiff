// BempDiff 后端 `server` 子命令的极简 fetch 封装。
// 后端绑定 127.0.0.1，同源（dev 经 vite proxy，生产经 Tauri sidecar 同源托管）免 CORS。
const BASE = ''

async function req(method, path, body, opts = {}) {
  const init = { method, headers: {} }
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  if (opts.signal) init.signal = opts.signal
  let res
  try {
    res = await fetch(BASE + path, init)
  } catch (e) {
    if (e && e.name === 'AbortError') throw e // 透传取消，交由调用方静默忽略
    throw new Error('无法连接后端服务（请确认 server 已启动）：' + e.message)
  }
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const j = await res.json()
      msg = j.message || j.error || msg
    } catch (_) { /* 非 JSON 错误体 */ }
    throw new Error(msg)
  }
  const ct = res.headers.get('content-type') || ''
  if (opts.blob) return await res.blob()
  if (opts.text || !ct.includes('application/json')) return await res.text()
  return await res.json()
}

export const api = {
  // body: { leftType:'package'|'folder', leftPath, rightPath, options? }
  compare(payload) { return req('POST', '/api/session/compare', payload) },
  // -> { jobId, status, tree }
  jobStatus(id) { return req('GET', `/api/job/${encodeURIComponent(id)}/status`) },
  // -> { oldSrc, newSrc, diffText, engine, ok }
  // opts: { signal } 用于切换 tab 时取消在途请求
  decompile(jobId, key, opts = {}) {
    return req('GET', `/api/entry/decompile?jobId=${encodeURIComponent(jobId)}&key=${encodeURIComponent(key)}`, undefined, opts)
  },
  // 归档展开：返回内部条目列表，跨旧/新算 ADDED/DELETED/MODIFIED/UNCHANGED。
  // key 可为顶层归档 key 或复合键 outer!/innerArchive（支持递归展开嵌套归档）。
  entryChildren(jobId, key) {
    return req('GET', `/api/entry/children?jobId=${encodeURIComponent(jobId)}&key=${encodeURIComponent(key)}`)
  },
  // 自动递归解包：一次性返回完整嵌套差异树（嵌套归档节点带 children，受后端深度/节点护栏约束）。
  // 每层节点含 status（ADDED/DELETED/MODIFIED/UNCHANGED），供"点开即自动展开全部嵌套层"使用。
  entryRecursive(jobId, key) {
    return req('GET', `/api/entry/recursive?jobId=${encodeURIComponent(jobId)}&key=${encodeURIComponent(key)}`)
  },
  // ai:boolean -> markdown 文本；category/prompt 可选（AI 分析项，透传给后端 buildFocus，使不同分析项报告内容可区分）；
  // projDir 可选：项目级上下文目录（后端未传时回退到服务端配置，双保险）
  report(id, ai, category, prompt, projDir) {
    const body = { ai }
    if (category) body.category = category
    if (prompt) body.prompt = prompt
    if (projDir) body.projectDir = projDir
    return req('POST', `/api/job/${encodeURIComponent(id)}/report`, body, { text: true })
  },
  // -> blob (zip)
  exportZip(id) { return req('POST', `/api/job/${encodeURIComponent(id)}/export`, {}, { blob: true }) },
  // -> { items:[{key,risk(HIGH/MEDIUM/LOW),category,reason}], coverage, totalChanged }
  classify(id, projDir) {
    return req('POST', `/api/job/${encodeURIComponent(id)}/ai-classify`, projDir ? { projectDir: projDir } : {})
  },
  // 差异树右键菜单磁盘操作（仅文件夹对比模式）：{ jobId, op:'info'|'delete'|'rename'|'copy', key, newName? }
  // -> { ok, code, message, side, status, info? }
  fileOps(payload) {
    return req('POST', '/api/file', payload)
  },
  // -> { report, analyze, classify, threshold }  AI token 预估（成本闸门，P0 #5）。
  // category/prompt 可选：与 report/ai-analyze 口径一致，使预估按分析项区分（后端 handleAiEstimate 读取 POST body）。
  aiEstimate(id, category, prompt) {
    const body = {}
    if (category) body.category = category
    if (prompt) body.prompt = prompt
    return req('POST', `/api/job/${encodeURIComponent(id)}/ai-estimate`, body)
  },
  // -> config json（GET 不回显 apiKey，含 hasApiKey）
  getConfig() { return req('GET', '/api/config') },
  // cfg: 部分/完整 config
  putConfig(cfg) { return req('PUT', '/api/config', cfg) },
  // -> { ok, rootPath, projectCount, scannedAt, cacheFile, fromCache, javaFileCount, projects[], message }
  // 查询/刷新「上下文目录」递归项目索引（refresh=1 强制重扫）
  contextStatus(dir, refresh) {
    return req('GET', `/api/ai/context?dir=${encodeURIComponent(dir || '')}&refresh=${refresh ? 1 : 0}`)
  },
  // body: { provider, baseUrl, apiKey, model, httpProxy?, httpsProxy? }
  testAi(payload) { return req('POST', '/api/ai/test', payload) },
  // -> { ok, models: string[], lastError, message } 按 API Base URL + Key 自动拉取可用模型
  fetchModels(payload) { return req('POST', '/api/ai/models', payload) },
  /**
   * AI 分析流式端点（SSE）。消费 /api/job/{id}/ai-analyze 的 event:/data: 流，
   * 分发 onThinking/onAnswer/onDone/onError。返回一个 AbortController 供「停止」调用。
   * handlers: { onThinking(data:{phase,message}), onAnswer(data:{text}), onDone(data), onError(data:{message}) }
   */
  analyzeStream(id, handlers = {}, body) {
    const url = BASE + `/api/job/${encodeURIComponent(id)}/ai-analyze`
    const controller = new AbortController()
    // onDone 恰好触发一次：done 事件 / 流结束 EOF / abort 三者先到者胜，避免双触发（评审 P1 #6）
    let finished = false
    const finish = (data) => {
      if (finished) return
      finished = true
      handlers.onDone && handlers.onDone(data)
    }
    ;(async () => {
      let res
      try {
        res = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: body !== undefined ? JSON.stringify(body) : '{}',
          signal: controller.signal
        })
      } catch (e) {
        if (e.name === 'AbortError') { finish({ ok: true, aborted: true }); return }
        handlers.onError && handlers.onError({ message: '无法连接后端服务：' + e.message }); return
      }
      if (!res.ok) {
        handlers.onError && handlers.onError({ message: `${res.status} ${res.statusText}` }); return
      }
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      try {
        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''
          let curEvent = ''
          for (const line of lines) {
            if (line.startsWith('event: ')) {
              curEvent = line.slice(7).trim()
            } else if (line.startsWith('data: ')) {
              const dataStr = line.slice(6)
              let data
              try { data = JSON.parse(dataStr) } catch (_) { continue }
              dispatchSse(curEvent, data, handlers)
              // done/error 为终态事件：置 finished，后续 EOF/abort 不再重复回调
              if (curEvent === 'done' || curEvent === 'error') finished = true
              curEvent = ''
            }
          }
        }
      } catch (e) {
        if (e.name === 'AbortError') { finish({ ok: true, aborted: true }); return }
        handlers.onError && handlers.onError({ message: e.message }); return
      }
      finish({ ok: true })
    })()
    return controller
  }
}

// SSE 事件分发（thinking / answer / done / error）
function dispatchSse(event, data, h) {
  switch (event) {
    case 'thinking': h.onThinking && h.onThinking(data); break
    case 'answer': h.onAnswer && h.onAnswer(data); break
    case 'done': h.onDone && h.onDone(data); break
    case 'error': h.onError && h.onError(data); break
  }
}

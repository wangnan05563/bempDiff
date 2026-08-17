// BempDiff 后端 `server` 子命令的极简 fetch 封装。
// 后端绑定 127.0.0.1，同源（dev 经 vite proxy，生产经 Tauri sidecar 同源托管）免 CORS。
const BASE = ''

async function req(method, path, body, opts = {}) {
  const init = { method, headers: {} }
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  let res
  try {
    res = await fetch(BASE + path, init)
  } catch (e) {
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
  decompile(jobId, key) {
    return req('GET', `/api/entry/decompile?jobId=${encodeURIComponent(jobId)}&key=${encodeURIComponent(key)}`)
  },
  // ai:boolean -> markdown 文本
  report(id, ai) { return req('POST', `/api/job/${encodeURIComponent(id)}/report`, { ai }, { text: true }) },
  // -> blob (zip)
  exportZip(id) { return req('POST', `/api/job/${encodeURIComponent(id)}/export`, {}, { blob: true }) },
  // -> config json（GET 不回显 apiKey，含 hasApiKey）
  getConfig() { return req('GET', '/api/config') },
  // cfg: 部分/完整 config
  putConfig(cfg) { return req('PUT', '/api/config', cfg) },
  // body: { provider, baseUrl, apiKey, model, httpProxy?, httpsProxy? }
  testAi(payload) { return req('POST', '/api/ai/test', payload) }
}

/**
 * AI 连接/调用失败的结构化归因（二期 R8 / T01469）。
 *
 * 设计要点：后端/网络层报错文案形态多样（HTTP 状态码、fetch TypeError、厂商 JSON error），
 * 此处按可验证特征归为四类，并给出**可执行**的建议动作；归因不出错时落「其他」兜底，
 * 永不虚构（与「不伪造」原则一致）。
 */

const PATTERNS = [
  {
    type: 'auth',
    test: (m) => /\b401\b|\b403\b|unauthorized|forbidden|invalid[ _-]?api[ _-]?key|api key|authentication|鉴权|密钥/i.test(m),
    reason: '鉴权失败（API Key 缺失/错误/无权限）',
    hint: '检查配置中心的 API Key 是否正确、是否属于所选厂商；企业账号确认 Key 未过期且对该模型有权限。'
  },
  {
    type: 'model',
    test: (m) => /\b404\b|model[_ -]?not[_ -]?found|does not exist|decommissioned|模型不存在|无可用模型/i.test(m),
    reason: '模型名称无效或不可用',
    hint: '核对模型 ID 拼写（如 gpt-4o）；确认该 Key/厂商提供此模型，或点「拉取模型列表」选择可用项。'
  },
  {
    type: 'network',
    test: (m) => /failed to fetch|networkerror|econnrefused|etimedout|timeout|enotfound|getaddrinfo|proxy|connection|网络|超时|代理|连接/i.test(m),
    reason: '网络不可达（地址错误/断网/代理未生效）',
    hint: '核对 BaseURL 与端口；需要代理时在配置中心填写 HTTP/HTTPS 代理；本地模型确认服务已启动。'
  },
  {
    type: 'rate',
    test: (m) => /\b429\b|rate[ _-]?limit|quota|insufficient|余额|配额|限速/i.test(m),
    reason: '限速或配额不足',
    hint: '稍后重试；检查厂商控制台的配额/余额；必要时更换模型或降低 Top-K。'
  }
]

/**
 * 归因：返回 { type, reason, hint }；无法归因时 type='other'，reason 保留原始摘要。
 * @param {string} message 原始错误文案（可含 HTTP 状态码/厂商 JSON 片段）
 */
export function classifyAiError(message) {
  const m = String(message || '')
  for (const p of PATTERNS) {
    if (p.test(m)) return { type: p.type, reason: p.reason, hint: p.hint }
  }
  const brief = m.length > 80 ? m.slice(0, 80) + '…' : m
  return { type: 'other', reason: brief || '未知错误', hint: '查看详细报错内容；确认厂商服务状态页是否正常。' }
}

/** 组装面向用户的友好文案：结构化原因 + 建议动作（toast / 弹窗共用）。 */
export function friendlyAiError(message) {
  const c = classifyAiError(message)
  return `失败原因：${c.reason}。建议：${c.hint}`
}

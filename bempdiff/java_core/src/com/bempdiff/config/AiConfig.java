package com.bempdiff.config;

/**
 * AI 服务配置（§5.9 / FR9.4 / T11）。全部 UI 入口，对应详细设计 §4 AiConfig。
 * 密钥仅存"是否已配置"标记，真实密钥由配置中心加密落盘（量产版）；此处仅放明文占位供离线回放。
 */
public final class AiConfig {
    private String provider = "openai";       // openai / azure / ollama / qwen / custom
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";               // 量产版加密存储
    private String model = "gpt-4o";
    private boolean enabled = false;          // 未配置=false，基础比对仍可用
    private int stageAFileSampleLines = 80;   // 阶段A 单文件 diff 摘要截断行数
    private int stageATopK = 30;              // 阶段A 概览纳入文件上限
    private int stageBTopK = 15;              // 阶段B 深读完整 diff 上限（FR7.2 默认 15）
    private double costGateWarnTokens = 8000; // 成本闸门：预估超阈值弹确认
    /**
     * 单次请求最大输入 token（护栏，默认 120000，为 128K 上下文窗口留余量）。
     * 与「成本闸门」是两个维度：成本闸门只做费用确认、确认后照发；
     * 本护栏在发送前拦截「注定被模型拒收」的超大 prompt，避免必然的 HTTP 400 与无谓的超大请求体传输。
     */
    private int maxPromptTokens = 120_000;
    /**
     * 单次请求最大输出 token（模型输出护栏，默认 8192）。
     * 对应请求体 max_tokens；0 表示不显式设置，交服务端默认。
     * 此前请求体不携带 max_tokens，输出长度听任服务端默认值（部分兼容服务默认仅数百~2k），
     * 长报告（尤其阶段B 逐文件测试要点位于字段末尾）会被硬切、尾部丢失——故新增此显式上限。
     */
    private int maxOutputTokens = 8192;
    private String httpProxy = "";            // 企业网访问 LLM 的 HTTP 代理（FR9.11）
    private String httpsProxy = "";           // HTTPS 代理（FR9.11）
    private boolean blockPrivateEndpoints = false; // 严格 SSRF：额外拒绝回环/私网（本地 Ollama 需关）

    public String getProvider() { return provider; }
    public void setProvider(String v) { if (v != null) this.provider = v; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = (v == null) ? "" : v.trim(); }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { this.apiKey = (v == null) ? "" : v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = (v == null) ? "" : v.trim(); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public int getStageAFileSampleLines() { return stageAFileSampleLines; }
    public void setStageAFileSampleLines(int v) { this.stageAFileSampleLines = v; }
    public int getStageATopK() { return stageATopK; }
    public void setStageATopK(int v) { this.stageATopK = v; }
    public int getStageBTopK() { return stageBTopK; }
    public void setStageBTopK(int v) { this.stageBTopK = v; }
    public double getCostGateWarnTokens() { return costGateWarnTokens; }
    public void setCostGateWarnTokens(double v) { this.costGateWarnTokens = v; }
    public int getMaxPromptTokens() { return maxPromptTokens; }
    public void setMaxPromptTokens(int v) { this.maxPromptTokens = v; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int v) { this.maxOutputTokens = v; }
    public String getHttpProxy() { return httpProxy; }
    public void setHttpProxy(String v) { this.httpProxy = v == null ? "" : v; }
    public String getHttpsProxy() { return httpsProxy; }
    public void setHttpsProxy(String v) { this.httpsProxy = v == null ? "" : v; }
    public boolean isBlockPrivateEndpoints() { return blockPrivateEndpoints; }
    public void setBlockPrivateEndpoints(boolean v) { this.blockPrivateEndpoints = v; }
}

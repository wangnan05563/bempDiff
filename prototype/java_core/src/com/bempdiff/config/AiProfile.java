package com.bempdiff.config;

/**
 * 一个已保存的命名 LLM 配置（BYOK 多配置）。
 *
 * <p>仅保存"连接三元组" + 厂商标识：provider / baseUrl / apiKey / model。
 * 分析类参数（stageBTopK、成本闸门、代理、SSRF 开关）属于全局设置，不按配置隔离，
 * 以免切换配置时改变分析行为（与 Karpathy 的 AiUserConfig 字段范围一致）。
 */
public final class AiProfile {

    private String name;       // 用户给该配置起的名字（唯一键）
    private String provider;   // 落到 AiConfig.provider，可为预设 key 或 custom
    private String baseUrl;
    private String apiKey;
    private String model;

    public AiProfile() {}

    public AiProfile(String name, String provider, String baseUrl, String apiKey, String model) {
        this.name = name;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getProvider() { return provider; }
    public void setProvider(String v) { this.provider = v; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String v) { this.apiKey = v; }
    public String getModel() { return model; }
    public void setModel(String v) { this.model = v; }

    @Override
    public String toString() {
        return name; // ComboBox 展示用
    }
}

package com.bempdiff.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LLM 厂商预设（UI 参考 Karpathy 项目 llm-presets.json 的 {@code LlmPreset} 模型）。
 *
 * <p>每个预设携带一套 OpenAI 兼容协议的默认连接参数（baseUrl / model）以及
 * 官方「获取 API Key」页面链接（apiKeyUrl），供配置页一键切换与跳转。
 *
 * <p>国产模型统一走 OpenAI 兼容协议，故仅通过 baseUrl + apiKey + model 三个字段区分，
 * 无需为每家单独实现客户端（与 Karpathy 的 OpenAICompatibleAdapter 思路一致）。
 */
public final class LlmPreset {

    private final String key;          // 内部标识，如 openai / deepseek
    private final String label;        // 展示名，如 OpenAI / DeepSeek
    private final String provider;     // 落到 AiConfig.provider
    private final String baseUrl;      // 默认 API 基地址
    private final String model;        // 默认模型
    private final String apiKeyUrl;    // 官方获取 API Key 页面
    private final boolean local;       // 是否本地服务（如 Ollama，默认无需 Key）

    public LlmPreset(String key, String label, String provider, String baseUrl,
                     String model, String apiKeyUrl, boolean local) {
        this.key = key;
        this.label = label;
        this.provider = provider;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKeyUrl = apiKeyUrl;
        this.local = local;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String getProvider() { return provider; }
    public String getBaseUrl() { return baseUrl; }
    public String getModel() { return model; }
    public String getApiKeyUrl() { return apiKeyUrl; }
    public boolean isLocal() { return local; }

    /** 内置厂商预设列表（顺序即下拉展示顺序）。 */
    public static List<LlmPreset> builtinPresets() {
        return new ArrayList<>(Arrays.asList(
            new LlmPreset("openai",   "OpenAI (GPT)",       "openai",
                "https://api.openai.com/v1", "gpt-4o",
                "https://platform.openai.com/api-keys", false),
            new LlmPreset("azure",    "Azure OpenAI",       "azure",
                "https://<resource>.openai.azure.com", "gpt-4o",
                "https://portal.azure.com/#view/Microsoft_Azure_ProjectOrchestra/OpenAIKeysBlade", false),
            new LlmPreset("ollama",   "Ollama (本地)",       "ollama",
                "http://localhost:11434/v1", "qwen2.5:7b",
                "https://ollama.com", true),
            new LlmPreset("deepseek", "DeepSeek",           "deepseek",
                "https://api.deepseek.com", "deepseek-chat",
                "https://platform.deepseek.com/api_keys", false),
            new LlmPreset("qwen",     "通义千问 (阿里云百炼)", "qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
                "https://dashscope.console.aliyun.com/apiKey", false),
            new LlmPreset("glm",      "智谱 GLM",            "glm",
                "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus",
                "https://open.bigmodel.cn/usercenter/apikeys", false),
            new LlmPreset("moonshot", "Moonshot (Kimi)",    "moonshot",
                "https://api.moonshot.cn/v1", "moonshot-v1-8k",
                "https://platform.moonshot.cn/console/api-keys", false),
            new LlmPreset("doubao",   "豆包 (火山方舟)",       "doubao",
                "https://ark.cn-beijing.volces.com/api/v3", "doubao-pro-4.0-241128",
                "https://console.volcengine.com/ark", false)
        ));
    }

    /** 按 key 查找内置预设（找不到返回 null）。 */
    public static LlmPreset byKey(String key) {
        if (key == null) return null;
        for (LlmPreset p : builtinPresets()) {
            if (p.key.equals(key)) return p;
        }
        return null;
    }

    @Override
    public String toString() {
        return label; // ComboBox 展示用
    }
}

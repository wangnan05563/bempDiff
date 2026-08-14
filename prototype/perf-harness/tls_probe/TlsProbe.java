package com.bempdiff.perf;

import com.bempdiff.ai.HttpAiAnalyzer;
import com.bempdiff.config.AiConfig;

/**
 * 临时 TLS 探针：复现用户报告的"AI 连接测试失败"问题。
 * 模拟 dist/BempDiff/BempDiff.exe 的启动方式（工具链 java + app.jar），
 * 调 HttpAiAnalyzer.testConnection 并输出结果。
 * 用 -Djavax.net.debug=ssl,handshake 抓握手日志。
 */
public final class TlsProbe {
    public static void main(String[] args) {
        String baseUrl = args.length > 0 ? args[0] : "https://api.deepseek.com";
        String apiKey  = args.length > 1 ? args[1] : "sk-probe-dummy-key-for-diagnosis";
        String model   = args.length > 2 ? args[2] : "deepseek-chat";

        AiConfig cfg = new AiConfig();
        cfg.setProvider("deepseek");
        cfg.setBaseUrl(baseUrl);
        cfg.setApiKey(apiKey);
        cfg.setModel(model);
        cfg.setEnabled(true);
        cfg.setBlockPrivateEndpoints(false);

        System.out.println("[TlsProbe] === start ===");
        System.out.println("[TlsProbe] baseUrl = " + baseUrl);
        System.out.println("[TlsProbe] model   = " + model);
        System.out.println("[TlsProbe] java.runtime = " + System.getProperty("java.runtime.name")
                + " " + System.getProperty("java.version"));
        System.out.println("[TlsProbe] java.home    = " + System.getProperty("java.home"));
        System.out.println("[TlsProbe] https.protocols (sys prop) = " + System.getProperty("https.protocols"));
        System.out.println("[TlsProbe] jpackage.runtime = " + System.getProperty("jpackage.runtime"));

        // 直接调 static-init 后的 HttpsURLConnection 全局默认工厂
        try {
            javax.net.ssl.SSLSocketFactory def =
                    javax.net.ssl.HttpsURLConnection.getDefaultSSLSocketFactory();
            System.out.println("[TlsProbe] HttpsURLConnection.getDefaultSSLSocketFactory().getClass() = "
                    + (def == null ? "null" : def.getClass().getName()));
        } catch (Exception e) {
            System.out.println("[TlsProbe] getDefaultSSLSocketFactory err: " + e);
        }

        HttpAiAnalyzer a = new HttpAiAnalyzer(cfg);
        long t0 = System.currentTimeMillis();
        boolean ok;
        try {
            ok = a.testConnection(cfg);
        } catch (Throwable t) {
            System.out.println("[TlsProbe] testConnection THREW: " + t);
            t.printStackTrace(System.out);
            return;
        }
        long ms = System.currentTimeMillis() - t0;
        System.out.println("[TlsProbe] testConnection result = " + ok + " (" + ms + " ms)");
        System.out.println("[TlsProbe] lastError = " + a.getLastError());
        System.out.println("[TlsProbe] === end ===");
    }
}

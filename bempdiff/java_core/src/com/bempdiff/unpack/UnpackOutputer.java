package com.bempdiff.unpack;

import com.bempdiff.server.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 解包状态报告（.json）与错误日志（.log）落盘（M-B 期望输出）。
 * JSON 复用 Json 写入器；错误日志用纯 ASCII + CRLF，避免 cmd/GBK 乱码（工程约定）。
 * 写文件前创建目录、单侧失败绝不中断主流程（对齐 openLog 容错约定）。
 */
public final class UnpackOutputer {

    private UnpackOutputer() {
    }

    /**
     * 把两侧解包报告写为 unpack-&lt;jobId&gt;.json 与 unpack-&lt;jobId&gt;.log。
     *
     * @param reports [0]=旧侧 [1]=新侧；可能含 null（未开启解包的一侧）。
     * @param logsDir 落盘目录（如 {user.home}/.bempdiff/logs）。
     */
    public static void write(String jobId, UnpackReport[] reports, Path logsDir) {
        if (reports == null) return;
        try {
            Files.createDirectories(logsDir);
            writeJson(jobId, reports, logsDir);
            writeLog(jobId, reports, logsDir);
        } catch (Exception ex) {
            // 容错：落盘失败仅打印，绝不影响比对与后续 AI（对齐 openLog 容错约定）。
            System.err.println("[UnpackOutputer] 解包报告落盘失败 jobId=" + jobId + " : " + ex);
        }
    }

    private static void writeJson(String jobId, UnpackReport[] reports, Path logsDir) throws Exception {
        List<Object> list = new ArrayList<>();
        for (UnpackReport r : reports) {
            list.add(r == null ? null : Json.parseObject(r.toJson()));
        }
        Path f = logsDir.resolve("unpack-" + jobId + ".json");
        Files.write(f, Json.write(list).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeLog(String jobId, UnpackReport[] reports, Path logsDir) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (UnpackReport r : reports) {
            if (r == null) continue;
            synchronized (r.getErrors()) {
                for (UnpackError e : r.getErrors()) {
                    sb.append("[ERROR] kind=").append(r.getKind())
                            .append(" key=").append(e.key)
                            .append(" stage=").append(e.stage)
                            .append(" msg=").append(ascii(e.message))
                            .append("\r\n");
                }
            }
        }
        Path f = logsDir.resolve("unpack-" + jobId + ".log");
        Files.write(f, sb.toString().getBytes(StandardCharsets.US_ASCII));
    }

    /** 错误消息转纯 ASCII：仅保留可打印 ASCII，避免非拉丁字符造成 cmd/GBK 乱码。 */
    private static String ascii(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c < 127) sb.append(c);
        }
        return sb.toString();
    }
}
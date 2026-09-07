package com.bempdiff.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 差异树右键菜单的磁盘文件操作（仅文件夹对比模式有物理路径可用）。
 *
 * <p>纯逻辑类，不依赖 HTTP/服务端：{@code BempServer} 只负责解析请求参数并把本类的
 * {@link OpResult} 翻译为 HTTP 响应，因此可用 JUnit 直接测试全部正常路径与边界。
 *
 * <p>安全约束（与 FolderParser.sanitizeKey 同源）：
 * <ul>
 *   <li>key 必须是相对路径：拒绝绝对路径、拒绝含 {@code ..} 的路径穿越（Zip-Slip 同类）；</li>
 *   <li>归一化后必须仍位于 root 之内（越界一律拒绝）；</li>
 *   <li>目录 key 约定以 '/' 结尾（FileClass.FOLDER 条目），文件 key 无尾斜杠。</li>
 * </ul>
 */
public final class FileOps {

    private FileOps() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /** 操作结果错误码与提示（多处重复，提取为常量规避 S1192）。 */
    private static final String CODE_NOT_FOUND = "NOT_FOUND";
    private static final String CODE_INVALID_PATH = "INVALID_PATH";
    private static final String CODE_IO_ERROR = "IO_ERROR";
    private static final String MSG_NOT_EXIST = "不存在: ";

    /** 操作结果：ok=false 时 code 供前端映射提示文案（NOT_FOUND/EXISTS/INVALID_PATH/IO_ERROR 等）；info 仅供「属性」操作携带。 */
    public record OpResult(boolean ok, String code, String message, FileInfo info) {
        public OpResult(boolean ok, String code, String message) {
            this(ok, code, message, null);
        }

        public static OpResult ok(String message) {
            return new OpResult(true, "OK", message, null);
        }

        public static OpResult okWithInfo(FileInfo info) {
            return new OpResult(true, "OK", "ok", info);
        }
    }

    /** 文件/目录属性快照（「属性」菜单项的数据源）。 */
    public record FileInfo(String key, String path, boolean dir, long size, long lastModified, String sha256) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", key);
            m.put("path", path);
            m.put("dir", dir);
            m.put("size", size);
            m.put("lastModified", lastModified);
            m.put("sha256", sha256);
            return m;
        }
    }

    /**
     * 校验 key 并将之安全映射到 root 内的物理路径。
     *
     * @throws InvalidPathException 越界/非法 key（作为受控业务异常抛出，由调用方转为 OpResult）
     */
    public static Path resolveWithin(Path root, String key) throws InvalidPathException {
        if (root == null || key == null || key.isEmpty()) throw new InvalidPathException("key 为空");
        if (key.startsWith("/") || key.startsWith("\\")) throw new InvalidPathException("拒绝绝对路径: " + key);
        if (key.contains("..")) throw new InvalidPathException("拒绝路径穿越: " + key);
        String k = key.endsWith("/") ? key.substring(0, key.length() - 1) : key; // 目录 key 去尾斜杠
        if (k.isEmpty()) throw new InvalidPathException("拒绝根目录自身");
        Path base = root.toAbsolutePath().normalize();
        Path p = base.resolve(k.replace('/', java.io.File.separatorChar)).normalize();
        if (!p.startsWith(base)) throw new InvalidPathException("路径越界: " + key);
        return p;
    }

    /** 属性：路径/类型/大小/修改时间/SHA-256（目录 sha 恒为 null）。不存在返回 code=NOT_FOUND。 */
    public static OpResult info(Path root, String key) {
        try {
            Path p = resolveWithin(root, key);
            if (!Files.exists(p)) return new OpResult(false, CODE_NOT_FOUND, MSG_NOT_EXIST + key);
            boolean dir = Files.isDirectory(p);
            long size = dir ? 0 : Files.size(p);
            long mtime = Files.getLastModifiedTime(p).toMillis();
            String sha = dir ? null : sha256(p);
            return OpResult.okWithInfo(new FileInfo(key, p.toString(), dir, size, mtime, sha));
        } catch (InvalidPathException e) {
            return new OpResult(false, CODE_INVALID_PATH, e.getMessage());
        } catch (IOException e) {
            return new OpResult(false, CODE_IO_ERROR, "读取属性失败: " + e.getMessage());
        }
    }

    /**
     * 删除条目：文件直接删；目录递归删除（含其下全部子项）。仅允许删除 root 内的对象。
     * 越界/不存在返回受控错误，不做任何删除。
     */
    public static OpResult deleteEntry(Path root, String key) {
        try {
            Path p = resolveWithin(root, key);
            if (!Files.exists(p)) return new OpResult(false, CODE_NOT_FOUND, MSG_NOT_EXIST + key);
            if (Files.isDirectory(p)) {
                try (Stream<Path> s = Files.walk(p)) {
                    // 先删子后删父：按路径深度倒序
                    s.sorted((a, b) -> b.compareTo(a)).forEach(x -> {
                        try {
                            Files.deleteIfExists(x);
                        } catch (IOException e) {
                            throw new DeleteFailure(e);
                        }
                    });
                }
            } else {
                Files.deleteIfExists(p);
            }
            return OpResult.ok("已删除: " + key);
        } catch (InvalidPathException e) {
            return new OpResult(false, CODE_INVALID_PATH, e.getMessage());
        } catch (DeleteFailure | IOException e) {
            Throwable c = (e instanceof DeleteFailure) ? e.getCause() : e;
            return new OpResult(false, CODE_IO_ERROR, "删除失败: " + (c != null ? c.getMessage() : e.getMessage()));
        }
    }

    /** 重命名条目（同目录内）：newName 必须是纯文件名（不含路径分隔符与 '..'）。 */
    public static OpResult renameEntry(Path root, String key, String newName) {
        if (newName == null || newName.isEmpty()) return new OpResult(false, CODE_INVALID_PATH, "新名称不能为空");
        if (newName.indexOf('/') >= 0 || newName.indexOf('\\') >= 0 || newName.contains("..")) {
            return new OpResult(false, CODE_INVALID_PATH, "新名称必须是纯文件名（不含路径分隔符）");
        }
        try {
            Path p = resolveWithin(root, key);
            if (!Files.exists(p)) return new OpResult(false, CODE_NOT_FOUND, MSG_NOT_EXIST + key);
            Path target = p.resolveSibling(newName);
            if (Files.exists(target)) return new OpResult(false, "EXISTS", "目标已存在: " + newName);
            Files.move(p, target);
            return OpResult.ok("已重命名: " + key + " → " + newName);
        } catch (InvalidPathException e) {
            return new OpResult(false, CODE_INVALID_PATH, e.getMessage());
        } catch (IOException e) {
            return new OpResult(false, CODE_IO_ERROR, "重命名失败: " + e.getMessage());
        }
    }

    /**
     * 把条目从一侧复制到另一侧（方向 direction = "l2r" | "r2l"）。
     * 源不存在或目标已存在时拒绝（返回受控错误，避免误覆盖用户数据）。
     */
    public static OpResult copyAcross(Path srcRoot, Path dstRoot, String key, String direction) {
        if (!"l2r".equals(direction) && !"r2l".equals(direction)) {
            return new OpResult(false, CODE_INVALID_PATH, "未知复制方向: " + direction);
        }
        try {
            Path src = resolveWithin(srcRoot, key);
            Path dst = resolveWithin(dstRoot, key);
            if (!Files.exists(src)) return new OpResult(false, CODE_NOT_FOUND, "源不存在: " + key);
            if (Files.exists(dst)) return new OpResult(false, "EXISTS", "目标侧已存在同名条目: " + key);
            if (Files.isDirectory(src)) {
                copyDir(src, dst);
            } else {
                if (dst.getParent() != null) Files.createDirectories(dst.getParent());
                Files.copy(src, dst);
            }
            return OpResult.ok("已复制: " + key + "（" + ("l2r".equals(direction) ? "左 → 右" : "右 → 左") + "）");
        } catch (InvalidPathException e) {
            return new OpResult(false, CODE_INVALID_PATH, e.getMessage());
        } catch (IOException e) {
            return new OpResult(false, CODE_IO_ERROR, "复制失败: " + e.getMessage());
        }
    }

    private static void copyDir(Path src, Path dst) throws IOException {
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path rel = src.relativize(p);
                Path t = dst.resolve(rel.toString().replace(java.io.File.separatorChar, '/'));
                if (Files.isDirectory(p)) {
                    Files.createDirectories(t);
                } else {
                    if (t.getParent() != null) Files.createDirectories(t.getParent());
                    Files.copy(p, t);
                }
            }
        }
    }

    /** 计算文件 SHA-256（与 PackageParser.sha256 同实现；目录不适用返回 null）。 */
    public static String sha256(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 不可用", e);
        }
        byte[] buf = new byte[8192];
        try (java.io.InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** 内部异常：目录递归删除中途失败（包装后统一转 IO_ERROR）。 */
    private static final class DeleteFailure extends RuntimeException {
        DeleteFailure(IOException cause) {
            super(cause);
        }
    }

    /** 受控业务异常：key 非法/越界。 */
    public static final class InvalidPathException extends Exception {
        InvalidPathException(String message) {
            super(message);
        }
    }
}

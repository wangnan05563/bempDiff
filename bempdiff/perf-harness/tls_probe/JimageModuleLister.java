package com.bempdiff.perf;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.stream.Stream;

/**
 * 诊断工具：列出 jpackage runtime/lib/modules 里的所有模块名，
 * 确认是否含 jdk.crypto.ec（TLSv1.3/EC cipher suites 必需）。
 * 用法：java -cp <jrt-fs.jar> -Dfile.encoding=UTF-8 com.bempdiff.perf.JimageModuleLister <runtime-dir>
 *   例：java -cp bempdiff/toolchain/zulu21.52.15-ca-jdk21.0.12-win_x64/lib/jrt-fs.jar
 *        -Dfile.encoding=UTF-8 com.bempdiff.perf.JimageModuleLister dist/BempDiff/runtime
 */
public final class JimageModuleLister {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("usage: JimageModuleLister <runtime-dir>");
            return;
        }
        String runtimeDir = args[0];
        // jrt URL: jrt:/<path-prefix>  对应 runtime/lib/modules 的模块索引
        URI jrtUri = URI.create("jrt:/");
        try (var fs = FileSystems.newFileSystem(jrtUri, Collections.singletonMap("java.home", runtimeDir))) {
            Path modulesRoot = fs.getPath("/modules");
            try (Stream<Path> stream = Files.list(modulesRoot)) {
                stream.filter(Files::isDirectory)
                      .map(Path::getFileName)
                      .map(Object::toString)
                      .sorted()
                      .forEach(name -> System.out.println(name));
            }
        }
    }
}
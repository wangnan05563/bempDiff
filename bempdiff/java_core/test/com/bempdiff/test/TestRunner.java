package com.bempdiff.test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 极简测试运行器：扫描传入的测试类，执行所有 `public static void testXxx()` 方法，
 * 统计通过/失败并以退出码返回（0=全绿）。用法：
 *   java -cp <core>;<tests> com.bempdiff.test.TestRunner \
 *       com.bempdiff.test.ParseTest com.bempdiff.test.DiffTest ...
 */
public final class TestRunner {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("用法: TestRunner <测试类全限定名>...");
            System.exit(2);
        }
        int total = 0, failed = 0;
        List<String> failures = new ArrayList<>();
        long start = System.currentTimeMillis();

        for (String className : args) {
            Class<?> clazz = Class.forName(className);
            List<Method> tests = new ArrayList<>();
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().startsWith("test") && m.getParameterCount() == 0
                        && m.getReturnType() == void.class
                        && java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                    tests.add(m);
                }
            }
            tests.sort((a, b) -> a.getName().compareTo(b.getName()));
            String simple = clazz.getSimpleName();
            System.out.println("------------------------------------------------------------");
            System.out.println("[套件] " + simple + "  (" + tests.size() + " 用例)");
            // 优先实例化（JUnit 风格实例方法），静态方法亦可
            Object instance = null;
            try {
                instance = clazz.getDeclaredConstructor().newInstance();
            } catch (Throwable ignored) {
                instance = null; // 纯静态测试类
            }
            for (Method m : tests) {
                total++;
                String name = simple + "#" + m.getName();
                try {
                    if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                        m.invoke(null);
                    } else {
                        if (instance == null) throw new IllegalStateException("无实例且方法非静态");
                        m.invoke(instance);
                    }
                    System.out.println("  ✓ " + m.getName());
                } catch (java.lang.reflect.InvocationTargetException e) {
                    failed++;
                    String reason = e.getCause() == null ? e.getMessage() : e.getCause().toString();
                    failures.add(name + " -> " + reason);
                    System.out.println("  ✗ " + m.getName() + "  原因: " + reason);
                } catch (Throwable t) {
                    failed++;
                    failures.add(name + " -> " + t);
                    System.out.println("  ✗ " + m.getName() + "  原因: " + t);
                }
            }
        }

        long ms = System.currentTimeMillis() - start;
        System.out.println("------------------------------------------------------------");
        System.out.println("总计: " + total + "  通过: " + (total - failed) + "  失败: " + failed
                + "  耗时: " + ms + "ms");
        if (failed > 0) {
            System.out.println("失败明细:");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
        System.out.println("全部通过 ✓");
        System.exit(0);
    }
}

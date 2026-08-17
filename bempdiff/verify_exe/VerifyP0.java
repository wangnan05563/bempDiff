import com.bempdiff.decompile.Decompiler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * P0-1 运行时验证（针对已打好的 exe runtime）：
 *  1) 确认 inProcessCfrAvailable=true（cfr 类在运行时 classpath / app.jar 内）
 *  2) 用真实 class 字节走 decompileOne，确认产出 CFR 真源码（非 javap 降级）
 */
public class VerifyP0 {
    public static void main(String[] a) throws Exception {
        Decompiler d = new Decompiler(null, null);

        Field f = Decompiler.class.getDeclaredField("inProcessCfrAvailable");
        f.setAccessible(true);
        boolean avail = (boolean) f.get(d);
        System.out.println("inProcessCfrAvailable=" + avail);

        Method m = Decompiler.class.getDeclaredMethod("decompileOne", byte[].class);
        m.setAccessible(true);

        // 取 app.jar 内一个真实 .class 做反编译样本
        java.io.InputStream is = Decompiler.class.getResourceAsStream(
                "/com/bempdiff/parse/PackageParser.class");
        if (is == null) { System.out.println("SAMPLE_CLASS_MISSING"); return; }
        byte[] bytes = is.readAllBytes();
        System.out.println("SAMPLE_CLASS_BYTES=" + bytes.length);

        String src = (String) m.invoke(d, (Object) bytes);
        System.out.println("SRC_LEN=" + src.length());
        System.out.println("HAS_PUBLIC_CLASS=" + src.contains("public class"));
        System.out.println("HAS_METHOD_BRACE=" + src.contains("{"));
        System.out.println("IS_DEGRADED=" + src.startsWith("// [降级]"));
        System.out.println("ENGINE_USED=" + (avail && !src.startsWith("// [降级]")
                ? "cfr(in-process)" : "degraded/javap"));
        int end = Math.min(400, src.length());
        System.out.println("SAMPLE>>>" + src.substring(0, end));
    }
}

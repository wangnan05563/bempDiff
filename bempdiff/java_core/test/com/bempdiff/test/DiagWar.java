package com.bempdiff.test;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;
import com.bempdiff.unpack.NestedUnpacker;
import com.bempdiff.unpack.UnpackOptions;
import com.bempdiff.unpack.UnpackReport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 诊断：真实 M059/M061 顶层 zip，提高解包容量/深度上限，强制展开到 war 层，核对 3 层版本容器配对。 */
public final class DiagWar {
    public static void main(String[] args) throws Exception {
        // 路径参数化：-Dbempdiff.oldZip= -Dbempdiff.newZip= 可覆盖；缺省回退本机样例。依赖外部大包，不纳入 TestRunner。
        String oldZ = System.getProperty("bempdiff.oldZip",
                "E:\\testC\\BEMP5.0V202301-02-036M059(20260703-1104).zip");
        String newZ = System.getProperty("bempdiff.newZip",
                "E:\\testC\\BEMP5.0V202301-02-036M061(20260707-1135).zip");

        ParseConfig pc = new ParseConfig();
        PackageParser pp = new PackageParser();
        PackageSnapshot os = pp.parse(Paths.get(oldZ), pc, false);
        PackageSnapshot ns = pp.parse(Paths.get(newZ), pc, false);
        System.out.println("parsed old=" + os.getEntries().size() + " new=" + ns.getEntries().size());

        UnpackOptions uo = new UnpackOptions();
        uo.threadPoolSize = 4;
        uo.perItemTimeoutMs = 60_000;         // 深层 webapp WAR 单根需较久，给足预算以验证确定性
        uo.maxDepth = 12;                 // 足够到达 顶层/webzip/war/jar/class
        uo.totalBytesCap = 8L * 1024 * 1024 * 1024; // 8GB，避免被上限提前截断
        PackageSnapshot fo = new NestedUnpacker(uo, Files.createTempDirectory("do-")).flatten(os, new UnpackReport("package"), null);
        PackageSnapshot fn = new NestedUnpacker(uo, Files.createTempDirectory("dn-")).flatten(ns, new UnpackReport("package"), null);
        System.out.println("flattened old=" + fo.getEntries().size() + " new=" + fn.getEntries().size());
        System.out.println("OLD含.war键数=" + fo.getEntries().keySet().stream().filter(k -> k.contains(".war")).count());
        System.out.println("NEW含.war键数=" + fn.getEntries().keySet().stream().filter(k -> k.contains(".war")).count());

        DiffResult dr = new DiffEngine().compute(fo, fn);
        System.out.println("DEL=" + dr.get(DiffStatus.DELETED).size() + " ADD=" + dr.get(DiffStatus.ADDED).size()
                + " MOD=" + dr.get(DiffStatus.MODIFIED).size() + " UNCH=" + dr.get(DiffStatus.UNCHANGED).size());
        System.out.println("---- 含 web / .war 的 DEL ----");
        for (String k : dr.get(DiffStatus.DELETED)) if (k.contains(".war") || k.contains("web")) System.out.println("DEL " + k);
        System.out.println("---- 含 web / .war 的 ADD ----");
        for (String k : dr.get(DiffStatus.ADDED)) if (k.contains(".war") || k.contains("web")) System.out.println("ADD " + k);
        System.out.println("---- 含 web 的 MOD ----");
        for (String k : dr.get(DiffStatus.MODIFIED)) if (k.contains(".war") || k.contains("web")) System.out.println("MOD " + k);
    }
}
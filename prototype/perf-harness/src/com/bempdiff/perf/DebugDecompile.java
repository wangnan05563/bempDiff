package com.bempdiff.perf;

import com.bempdiff.config.ParseConfig;
import com.bempdiff.decompile.Decompiler;
import com.bempdiff.diff.DiffEngine;
import com.bempdiff.diff.DiffResult;
import com.bempdiff.diff.DiffStatus;
import com.bempdiff.model.LogicalEntry;
import com.bempdiff.model.PackageSnapshot;
import com.bempdiff.parse.PackageParser;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class DebugDecompile {
    public static void main(String[] args) throws Exception {
        Path old = Paths.get(args[0]);
        Path nw = Paths.get(args[1]);
        PackageParser p = new PackageParser();
        PackageSnapshot o = p.parse(old, new ParseConfig(), true);
        PackageSnapshot n = p.parse(nw, new ParseConfig(), true);
        DiffResult r = new DiffEngine().compute(o, n);
        List<String> cands = DiffEngine.collectL1ClassCandidates(r, o, n);
        System.out.println("candidates=" + cands.size());
        String k = cands.get(0);
        LogicalEntry oe = o.getEntries().get(k);
        LogicalEntry ne = n.getEntries().get(k);
        System.out.println("key=" + k);
        System.out.println("oe.src=" + oe.getSrc() + " nested=" + oe.getSrc().isNested());
        byte[] ob = new PackageParser().readEntryBytes(o, oe);
        byte[] nb = new PackageParser().readEntryBytes(n, ne);
        System.out.println("oldBytes=" + ob.length + " newBytes=" + nb.length);
        System.out.println("oldMagic=" + String.format("%02x%02x%02x%02x", ob[0], ob[1], ob[2], ob[3]));
        String jh = System.getenv("JAVA_HOME");
        String javaBin = Paths.get(jh, "bin", "java.exe").toString();
        Decompiler dec = new Decompiler(Paths.get("prototype/cfr.jar").toAbsolutePath(), javaBin);
        var u = dec.decompile(o, n, oe, ne, k);
        System.out.println("ok=" + u.isOk() + " engine=" + u.getEngine());
        System.out.println("err=" + u.getError());
        System.out.println("diffHead=" + (u.getDiffText() == null ? "null" : u.getDiffText().substring(0, Math.min(200, u.getDiffText().length()))));
        // 同时也直接抽取字节跑 CFR 看 stderr
        System.out.println("--- done ---");
    }
}

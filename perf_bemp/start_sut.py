"""启动 bempdiff 后端 SUT（生产交付 jar），写 pid 文件供定向关停。
仅启动，不采样、不杀进程；退出码非 0 表示启动失败。"""
import subprocess, sys, time, urllib.request

APP = r"D:/code/otherProjects/18_comparePakage/bempdiff/dist_input"
JAVA = APP + "/jre/bin/java.exe"
CP = APP + "/app/bempdiff.jar;" + APP + "/app/cfr.jar;" + APP + "/app/lib/*"
WEBROOT = "D:/code/otherProjects/18_comparePakage/bempdiff/dist_input/webui"
PORT = 18799
PIDFILE = "perf_bemp/sut.pid"

cmd = [JAVA, "-cp", CP, "com.bempdiff.Main", "server", "--port", str(PORT), "--webroot", WEBROOT]
proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
open(PIDFILE, "w").write(str(proc.pid))
print("SUT started pid=" + str(proc.pid))

# 健康探针：最长 60s 轮询非 5xx
health = "http://127.0.0.1:%d/api/config" % PORT
ok = False
for _ in range(60):
    try:
        with urllib.request.urlopen(health, timeout=3) as r:
            if 200 <= r.status < 500:
                ok = True
                break
    except Exception:
        pass
    time.sleep(1)
print("health " + str(ok))
sys.exit(0 if ok else 1)
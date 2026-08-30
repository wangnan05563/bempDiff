//! BempDiff Tauri 桌面壳（路径 B）。
//!
//! 职责：**进程管理器 + 原生窗口 + 原生对话框**，不含任何业务/反编译逻辑。
//! 后端仍是 `java_core` 的 `server` 子命令（JDK 自带 com.sun.net.httpserver），
//! 以「内嵌 JRE + bempdiff.jar」作为本地资源随包分发；Rust 负责：
//!   1. 找到随包 JRE 与 jar（生产=资源目录；开发=CARGO_MANIFEST_DIR/../dist_input）；
//!   2. 拉起 `java -cp bempdiff.jar:cfr.jar com.bempdiff.Main server --webroot <webui> --port <p>`；
//!   3. 等 HTTP 端口就绪（TCP 探活）后，创建 WebView 窗口指向 http://127.0.0.1:<p>；
//!   4. 应用退出时杀掉 Java 子进程，避免孤儿。
//!
//! 前端 SPA 由该 Java 服务同源托管（base='./'），`/api` 同域免 CORS；
//! 文件/文件夹选择走 Tauri 原生对话框（`@tauri-apps/plugin-dialog`）直接拿到绝对路径，无需上传。

use std::io::Write;
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::{Emitter, Manager, WebviewUrl, WebviewWindowBuilder};

/// 受管状态：保存 Java 后端子进程，便于退出时回收。
struct AppState {
    java: Mutex<Option<Child>>,
}

/// 拼接 classpath（Windows 用 `;`，与其他平台 `:`）。本应用仅发布 Windows，直接用 `;`。
/// 除 bempdiff.jar/cfr.jar 外，还追加随包分发的三方 `app/lib/*`（Apache POI 等，解析旧 .xls 用）；
/// `dir/*` 是 Java 支持的目录通配，lib_dir（开发态缺 lib 时返回 None）不参与拼接。
fn classpath(bempdiff_jar: &Path, cfr_jar: &Path, lib_dir: Option<&Path>) -> String {
    let mut cp = format!("{};{}", bempdiff_jar.display(), cfr_jar.display());
    if let Some(lib) = lib_dir {
        cp.push(';');
        // 用通配 `dir/*` 而非枚举单 jar：发布侧 lib 数量可能随 POI 版本变化，通配免维护且全量加载
        cp.push_str(&format!("{}*", lib.display()));
    }
    cp
}

/// 探测一组基准目录，返回首个存在 `rel` 的绝对路径。
/// 基准顺序：
///   ① exe 自身所在目录（NSIS 安装后资源与 exe 同目录，最可靠）；
///   ② Tauri 资源目录；
///   ③ CARGO_MANIFEST_DIR/../dist_input（开发态，由 build_tauri_app.ps1 预先 assemble）；
///   ④ 当前工作目录。
fn resolve_asset(app: &tauri::AppHandle, rel: &str) -> Option<PathBuf> {
    let mut bases: Vec<PathBuf> = Vec::new();
    if let Ok(exe) = std::env::current_exe() {
        if let Some(p) = exe.parent() {
            bases.push(p.to_path_buf());
        }
    }
    if let Ok(r) = app.path().resource_dir() {
        bases.push(r);
    }
    if let Ok(man) = std::env::var("CARGO_MANIFEST_DIR") {
        let p = PathBuf::from(&man).join("..").join("dist_input");
        if let Ok(c) = p.canonicalize() {
            bases.push(c);
        } else {
            bases.push(p);
        }
    }
    if let Ok(cwd) = std::env::current_dir() {
        bases.push(cwd);
    }
    for base in &bases {
        let cand = base.join(rel);
        rust_log(&format!("resolve_asset trying: {}", cand.display()));
        if cand.exists() {
            rust_log(&format!("resolve_asset found: {}", cand.display()));
            return Some(cand);
        }
    }
    rust_log(&format!("resolve_asset NOT FOUND for: {rel}"));
    None
}

/// 选一个空闲端口（TOCTOU 极小，失败由后端重试覆盖）。
fn pick_free_port() -> u16 {
    TcpListener::bind("127.0.0.1:0")
        .ok()
        .and_then(|l| l.local_addr().ok())
        .map(|a| a.port())
        .unwrap_or(18765)
}

/// TCP 探活：后端 HttpServer.start() 返回即已监听，连上即说明就绪。
/// `progress` 在等待期间周期回调（0~100），供启动图展示「等待后端就绪」的真实等待时长，
/// 避免进度条在最长 30s 的阻塞期完全静止（也对应 splash.html 的 waiting 阶段）。
fn wait_for_server<F: FnMut(u8)>(port: u16, timeout: Duration, mut progress: F) -> bool {
    let addr = format!("127.0.0.1:{port}");
    let start = Instant::now();
    let mut ticks = 0u8;
    loop {
        if let Ok(_) = TcpStream::connect_timeout(
            &addr.parse().unwrap(),
            Duration::from_millis(300),
        ) {
            return true;
        }
        if start.elapsed() > timeout {
            return false;
        }
        ticks += 1;
        // 每 ~0.2s 推进 1 点，从 76 缓涨到 90 后停住，等待真实就绪事件推进到 100。
        progress((76 + ticks.min(14)).min(90));
        std::thread::sleep(Duration::from_millis(200));
    }
}

/// 向启动图（splash 窗口）广播真实启动进度。
/// stage 与 splash.html 的 STAGES 映射一致（resolving/spawning/waiting/ready/error）；
/// percent 为 0~100 整数，前端据此设置进度条宽度。
fn splash(app: &tauri::AppHandle, stage: &str, percent: u8) {
    // 无窗口失败无伤大雅：进度丢失只影响观感，不阻塞启动主流程。
    let _ = app.emit(
        "splash-progress",
        serde_json::json!({ "stage": stage, "percent": percent }),
    );
}

/// 仅负责拉起 Java 后端并等就绪（**不创建窗口**）。窗口由 run() 在 setup 主线程创建。
fn launch_java(app: &tauri::AppHandle, port: u16) -> Result<(), Box<dyn std::error::Error>> {
    // 阶段①：定位随包 JRE / jar / webroot（通常为毫秒级，给 10% 底子即可）。
    splash(app, "resolving", 10);
    let java_exe = resolve_asset(app, "jre/bin/java.exe")
        .or_else(|| {
            // 开发态未 jlink 时，退而求其次用系统 JAVA_HOME / PATH 的 java
            if let Ok(h) = std::env::var("JAVA_HOME") {
                let c = PathBuf::from(h).join("bin").join("java.exe");
                if c.exists() {
                    rust_log(&format!("java.exe fallback via JAVA_HOME: {}", c.display()));
                    return Some(c);
                }
            }
            rust_log("java.exe fallback to PATH 'java'");
            None
        })
        .unwrap_or_else(|| PathBuf::from("java"));

    rust_log(&format!("java_exe resolved to: {}", java_exe.display()));
    rust_log(&format!("java_exe exists: {}", java_exe.exists()));

    let bempdiff_jar = resolve_asset(app, "app/bempdiff.jar")
        .ok_or("未找到 bempdiff.jar（生产应随包在 resources/app，开发应在 dist_input/app）")?;
    let cfr_jar = resolve_asset(app, "app/cfr.jar")
        .ok_or("未找到 cfr.jar（反编译引擎，必须与 bempdiff.jar 同目录）")?;
    let webroot = resolve_asset(app, "webui")
        .ok_or("未找到前端 webui 目录（应随包在 resources/webui 或开发态 dist_input/webui）")?;
    // 三方依赖目录（POI 等），随 resources 打包为 app/lib；开发态缺该目录则 classpath 不追加。
    let lib_dir = resolve_asset(app, "app/lib");

    let cp = classpath(&bempdiff_jar, &cfr_jar, lib_dir.as_deref());
    // 阶段②：资源就绪，即将拉起 Java 子进程。
    splash(app, "spawning", 45);
    rust_log(&format!(
        "启动后端：{} -Xmx 2g -cp {} com.bempdiff.Main server --webroot {} --port {}",
        java_exe.display(),
        cp,
        webroot.display(),
        port
    ));

    // 把 Java 的 stdout/stderr 落到临时日志，便于后端起不来时排查；
    // 同时避免 `Stdio::piped()` 无人读取导致 Java 写满管道被阻塞。
    let java_log = std::env::temp_dir().join("bempdiff-java.log");
    let out = std::fs::OpenOptions::new().create(true).append(true).open(&java_log).ok();
    let err = std::fs::OpenOptions::new().create(true).append(true).open(&java_log).ok();

    let child = Command::new(&java_exe)
        .args([
            "-Xmx",
            "2g",
            "-cp",
            &classpath(&bempdiff_jar, &cfr_jar, lib_dir.as_deref()),
            "com.bempdiff.Main",
            "server",
            "--webroot",
            &webroot.display().to_string(),
            "--port",
            &port.to_string(),
        ])
        .stdout(out.map_or(Stdio::null(), Stdio::from))
        .stderr(err.map_or(Stdio::null(), Stdio::from))
        .spawn()?;

    // 保存子进程到受管状态
    if let Some(state) = app.try_state::<AppState>() {
        if let Ok(mut g) = state.java.lock() {
            *g = Some(child);
        }
    }

    // 阶段③：子进程已拉起，进入等待就绪阶段（wait_for_server 内部周期推进 76→90）。
    splash(app, "waiting", 75);
    if wait_for_server(port, Duration::from_secs(30), |p| {
        splash(app, "waiting", p)
    }) {
        rust_log(&format!(
            "后端已就绪（端口 {}），Java 日志：{}",
            port,
            java_log.display()
        ));
        Ok(())
    } else {
        Err(format!(
            "后端 30s 内未就绪（端口 {} 未监听），请检查 JRE 与 bempdiff.jar；Java 日志：{}",
            port,
            java_log.display()
        )
        .into())
    }
}

/// 把关键启动/错误信息落到 %TEMP%/bempdiff-rust.log。
/// GUI 子系统程序没有控制台，Rust panic / 窗口创建失败等信息会直接丢失，靠它定位闪退。
fn rust_log(msg: &str) {
    let log_path = std::env::temp_dir().join("bempdiff-rust.log");
    if let Ok(mut f) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path)
    {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        let _ = std::writeln!(f, "[{now}] {msg}");
        let _ = f.flush();
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    // GUI 子系统程序没有控制台，Rust panic / 窗口创建失败等信息会直接丢失。
    // 先装 panic 钩子把崩溃原因落盘，便于在无界面闪退时定位。
    std::panic::set_hook(Box::new(|info| {
        let mut s = String::from("PANIC");
        if let Some(m) = info.payload().downcast_ref::<&str>() {
            s.push_str(&format!(": {m}"));
        } else if let Some(m) = info.payload().downcast_ref::<String>() {
            s.push_str(&format!(": {m}"));
        }
        if let Some(loc) = info.location() {
            s.push_str(&format!(" @ {}:{}", loc.file(), loc.line()));
        }
        rust_log(&s);
    }));

    rust_log("run() entered");

    // 顺带记录 WebView2 运行时是否存在（窗口创建失败的常见根因）。
    for p in [
        r"C:\Program Files (x86)\Microsoft\EdgeWebView\Application\WebView2Loader.dll",
        r"C:\Program Files\Microsoft\EdgeWebView\Application\WebView2Loader.dll",
    ] {
        rust_log(&format!(
            "WebView2Loader.dll [{}] = {}",
            p,
            Path::new(p).exists()
        ));
    }

    let builder = tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState { java: Mutex::new(None) })
        .setup(|app| {
            rust_log("setup entered");
            // 主窗口必须在**主线程（setup 内）**创建：
            // ① 避免从后台线程建 WebView2 窗口在 Windows 上直接失败/无窗口（闪退）；
            // ② 事件循环就绪前也不应建窗口。
            let port = pick_free_port();
            rust_log(&format!("picked port {port}"));

            // —— 生产态：先显示本地启动图（splash，不依赖后端秒显），主窗口隐藏待后端就绪后再亮出。
            //    开发态：无 splash，直接显示指向 vite dev server 的主窗口（后端手动启动）。
            #[cfg(not(debug_assertions))]
            {
                // —— 窗口尺寸适配：主窗口默认 1366x800，若主屏工作区更小则收缩，
                //    避免窗口比屏幕大而被系统摆到屏外（低分辨率屏上用户会看不到窗口）。
                //    work_area() 返回物理像素，需除以 scale_factor 换成与 inner_size 一致的逻辑尺寸。
                let (mut win_w, mut win_h) = (1366.0_f64, 800.0_f64);
                if let Ok(Some(m)) = app.primary_monitor() {
                    let sf = m.scale_factor();
                    let wa = m.work_area();
                    let avail_w = wa.size.width as f64 / sf;
                    let avail_h = wa.size.height as f64 / sf;
                    if avail_w > 0.0 && avail_h > 0.0 {
                        win_w = win_w.min(avail_w);
                        win_h = win_h.min(avail_h);
                    }
                }
                rust_log(&format!("main window size adapted to {win_w}x{win_h}"));

                // 启动图窗口：加载被嵌入二进制的 dist_input/webui/splash.html
                // （withGlobalTauri=true 注入 window.__TAURI__，可订阅 splash-progress 事件）。
                // 无边框 + 置顶，营造轻量品牌启动画面。
                match WebviewWindowBuilder::new(app, "splash", WebviewUrl::App("splash.html".into()))
                    .title("BempDiff — 启动中")
                    .inner_size(440.0, 520.0)
                    .resizable(false)
                    .decorations(false)
                    .always_on_top(true)
                    .build()
                {
                    Ok(_) => rust_log("splash window created OK"),
                    Err(e) => rust_log(&format!("FAILED to create splash window: {e:?}")),
                }

                // 主窗口先建好但隐藏；后端就绪后由后台线程 show()（避免启动空窗，也就绪即可用）。
                match WebviewWindowBuilder::new(
                    app,
                    "main",
                    WebviewUrl::External(
                        format!("http://127.0.0.1:{port}/")
                            .parse::<url::Url>()
                            .expect("invalid window url"),
                    ),
                )
                .title("BempDiff — 差异化对比工具")
                .inner_size(win_w, win_h)
                .min_inner_size(1024.0, 640.0)
                .resizable(true)
                .visible(false)
                .build()
                {
                    Ok(win) => {
                        // 创建后立即居中（隐藏窗口也可设置位置），确保 show() 时位于主屏可见区域
                        if let Err(e) = win.center() {
                            rust_log(&format!("main window center failed: {e:?}"));
                        }
                        // 居中按外框计算，边框/阴影会使外框左上角略越出工作区（负数），再钳回 (0,0) 保证完全可见
                        if let Ok(pos) = win.outer_position() {
                            let nx = pos.x.max(0);
                            let ny = pos.y.max(0);
                            if nx != pos.x || ny != pos.y {
                                let _ = win.set_position(tauri::PhysicalPosition::new(nx, ny));
                                rust_log(&format!("clamped window outer from ({},{}) to ({},{})", pos.x, pos.y, nx, ny));
                            }
                        }
                        rust_log("main window created (hidden) OK")
                    }
                    Err(e) => rust_log(&format!("FAILED to create main window: {e:?}")),
                }

                // 在后台线程拉起随包 JRE + bempdiff.jar，并按真实阶段广播进度；就绪后切窗。
                let handle = app.handle().clone();
                std::thread::spawn(move || {
                    let result = launch_java(&handle, port);
                    // 终结态：无论成败都推进到 100，关闭 splash、亮出主窗口（失败时由前端连接自愈兜底）。
                    splash(&handle, "ready", 100);
                    let h = handle.clone();
                    let _ = handle.run_on_main_thread(move || {
                        if let Some(spl) = h.get_webview_window("splash") {
                            let _ = spl.destroy();
                        }
                        if let Some(main) = h.get_webview_window("main") {
                            let _ = main.show();
                        }
                    });
                    if let Err(e) = result {
                        rust_log(&format!("启动后端失败：{e}"));
                    }
                });
            }
            #[cfg(debug_assertions)]
            {
                let window_url = "http://localhost:5173/".to_string();
                match WebviewWindowBuilder::new(
                    app,
                    "main",
                    WebviewUrl::External(window_url.parse::<url::Url>().expect("invalid window url")),
                )
                .title("BempDiff — 差异化对比工具")
                .inner_size(1366.0, 800.0)
                .min_inner_size(1024.0, 640.0)
                .resizable(true)
                .build()
                {
                    Ok(_) => rust_log("main window created OK"),
                    // 窗口创建失败（常见为 WebView2 不可用）：记录原因后继续，
                    // 进程以无窗口态运行，便于从日志定位根因。
                    Err(e) => rust_log(&format!("FAILED to create main window: {e:?}")),
                }
            }

            rust_log("setup done");
            Ok(())
        });

    rust_log("builder configured, calling build()");
    let app = match builder.build(tauri::generate_context!()) {
        Ok(a) => a,
        Err(e) => {
            rust_log(&format!("FAILED builder.build(): {e:?}"));
            return;
        }
    };
    rust_log("builder.build() OK, entering run()");

    app.run(|app, event| {
        if let tauri::RunEvent::ExitRequested { .. } = event {
            if let Some(state) = app.try_state::<AppState>() {
                if let Ok(mut g) = state.java.lock() {
                    if let Some(mut child) = g.take() {
                        let _ = child.kill();
                        let _ = child.wait();
                    }
                }
            }
        }
    });
    rust_log("run() exited");
}

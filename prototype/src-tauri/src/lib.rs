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

use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::time::{Duration, Instant};

use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};

/// 受管状态：保存 Java 后端子进程，便于退出时回收。
struct AppState {
    java: Mutex<Option<Child>>,
}

/// 拼接 classpath（Windows 用 `;`，与其他平台 `:`）。本应用仅发布 Windows，直接用 `;`。
fn classpath(bempdiff_jar: &Path, cfr_jar: &Path) -> String {
    format!("{};{}", bempdiff_jar.display(), cfr_jar.display())
}

/// 探测一组基准目录，返回首个存在 `rel` 的绝对路径。
/// 基准顺序：① Tauri 资源目录（生产安装态）② CARGO_MANIFEST_DIR/../dist_input（开发态，
/// 由 build_tauri_app.ps1 预先 assemble）③ 当前工作目录。
fn resolve_asset(app: &tauri::AppHandle, rel: &str) -> Option<PathBuf> {
    let mut bases: Vec<PathBuf> = Vec::new();
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
    for base in bases {
        let cand = base.join(rel);
        if cand.exists() {
            return Some(cand);
        }
    }
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
fn wait_for_server(port: u16, timeout: Duration) -> bool {
    let addr = format!("127.0.0.1:{port}");
    let start = Instant::now();
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
        std::thread::sleep(Duration::from_millis(200));
    }
}

/// 启动 Java 后端并等就绪，成功后建窗口指向它。
fn launch_and_serve(app: &tauri::AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    let java_exe = resolve_asset(app, "jre/bin/java.exe")
        .or_else(|| {
            // 开发态未 jlink 时，退而求其次用系统 JAVA_HOME / PATH 的 java
            if let Ok(h) = std::env::var("JAVA_HOME") {
                let c = PathBuf::from(h).join("bin").join("java.exe");
                if c.exists() {
                    return Some(c);
                }
            }
            None
        })
        .unwrap_or_else(|| PathBuf::from("java"));

    let bempdiff_jar = resolve_asset(app, "app/bempdiff.jar")
        .ok_or("未找到 bempdiff.jar（生产应随包在 resources/app，开发应在 dist_input/app）")?;
    let cfr_jar = resolve_asset(app, "app/cfr.jar")
        .ok_or("未找到 cfr.jar（反编译引擎，必须与 bempdiff.jar 同目录）")?;
    let webroot = resolve_asset(app, "webui")
        .ok_or("未找到前端 webui 目录（应随包在 resources/webui 或开发态 dist_input/webui）")?;

    let port = pick_free_port();
    println!("[BempDiff] 启动后端：{} --webroot {} --port {}", java_exe.display(), webroot.display(), port);

    let child = Command::new(&java_exe)
        .args([
            "-cp",
            &classpath(&bempdiff_jar, &cfr_jar),
            "com.bempdiff.Main",
            "server",
            "--webroot",
            &webroot.display().to_string(),
            "--port",
            &port.to_string(),
        ])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()?;

    // 保存子进程到受管状态
    if let Some(state) = app.try_state::<AppState>() {
        if let Ok(mut g) = state.java.lock() {
            *g = Some(child);
        }
    }

    if !wait_for_server(port, Duration::from_secs(30)) {
        return Err("后端 30s 内未就绪（端口未监听），请检查 JRE 与 bempdiff.jar".into());
    }

    let url = format!("http://127.0.0.1:{port}/");
    println!("[BempDiff] 后端就绪，打开窗口：{}", url);

    let _win = WebviewWindowBuilder::new(app, "main", WebviewUrl::External(url.parse::<url::Url>().unwrap()))
        .title("BempDiff — 票据系统 WAR/JAR 差异比对与智能分析")
        .inner_size(1366.0, 800.0)
        .min_inner_size(1024.0, 640.0)
        .resizable(true)
        .build(app)?;

    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(AppState { java: Mutex::new(None) })
        .setup(|app| {
            // 开发态（`tauri dev`，debug）：沿用浏览器开发工作流——
            // 由 Tauri 打开 devUrl（vite @5173），后端由用户手动 `java ... server --port 18765` 启动，
            // vite 已配置 /api 代理到 18765。不在此拉起内置 JRE，避免与开发态冲突。
            // 生产态（release）：拉起随包 JRE + bempdiff.jar，窗口指向内嵌服务。
            #[cfg(not(debug_assertions))]
            {
                let handle = app.handle().clone();
                std::thread::spawn(move || {
                    if let Err(e) = launch_and_serve(&handle) {
                        eprintln!("[BempDiff] 启动后端失败：{e}");
                    }
                });
            }
            #[cfg(debug_assertions)]
            {
                let _ = app;
            }
            Ok(())
        })
        .run(|app, event| {
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
}

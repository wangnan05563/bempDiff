# BempDiff

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20macOS-blue)](../../releases)
[![Release](https://img.shields.io/github/v/release/wangnan05563/bempDiff.svg)](../../releases/latest)

![BempDiff Social Preview](bempdiff/assets/social-preview-1280x640.png)

A desktop **package diff & AI analysis** tool for Java delivery artifacts: **war / jar / zip / folders**. Drop in two builds, get a tree-structured diff, decompiled source-level changes, and AI-powered risk analysis — all without installing Java separately.

> **Why BempDiff?** Production-vs-release package comparison is still mostly manual. BempDiff turns it into a repeatable workflow: recursive unpacking, structured diffing, CFR decompilation, and optional LLM analysis with cost gating and desensitization.

---

## Highlights

- **Layered diff view**: `L0` package manifest → `L1` internal business classes → `L2` third-party jars. Noise is folded by default.
- **Recursive unpacking**: nested `war → jar → zip` unpacked to atomic entries. Small files stay in memory; a global 256 MB soft cap controls disk usage.
- **Source-level decompilation**: CFR primary, `javap` fallback. GBK-tolerant decoding. Shows only changed lines + line ranges.
- **Frontend asset diff**: JS / HTML / CSS text diff with pretty-print normalization.
- **Folder comparison**: compare two directories by name, type, size, mtime, and SHA-256.
- **BCompare-style UX**: tree view with collapse / expand all, path history (back/forward/up), drag-to-compare, file/directory unified browse dialog.
- **Two-stage AI analysis**: whole-package overview + top-K file deep-read. Cost gating, financial-grade desensitization, offline project-context enhancement, and graceful degradation when AI is unreachable.
- **Export suite**: Markdown report, changed-class asset zip, decompiled source bundle, download manager for large packages.
- **Cross-platform installers**: Windows x64/arm64, Linux arm64, macOS arm64 — packaged via electron-builder (NSIS) with a trimmed JRE bundled, so no local Java is required.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Desktop shell | Electron |
| Frontend | Vue 3 + Bootstrap 5 + Vite |
| Diff engine / AI sidecar | Java 21 (local server on port `18765`) |
| Decompilation | CFR + javap fallback |
| AI provider | OpenAI-compatible APIs (OpenAI, Azure, Ollama, etc.) |

---

## Installation

Download the latest installer from [Releases](../../releases):

- Windows: `BempDiff-<version>-setup.exe`
- Windows arm64: `BempDiff-<version>-arm64-setup.exe`
- macOS arm64: `BempDiff-<version>-arm64.dmg`
- Linux arm64: `BempDiff-<version>-arm64.AppImage`

The installer embeds a trimmed JRE. No separate Java installation is needed.

---

## Quick Start

### GUI

1. Launch BempDiff.
2. Browse or drag the **old build** (production) and **new build** (release candidate) into the inputs.
3. Click **Start Compare**.
4. Select a changed item in the left tree to see the source diff in the center pane.
5. Open **Settings → AI** and enter your provider Base URL + API Key if you want AI analysis.
6. Use **Smart Analysis** to generate a risk overview and per-file deep analysis.

### Headless / CI

When invoked with a subcommand, `BempDiff.exe` runs headlessly and exits:

```bash
BempDiff.exe compare  <old> <new> [--expand-all]
BempDiff.exe report   <old> <new> [--top-k N] [--report out.md]
BempDiff.exe export   <old> <new> [--out outdir]
BempDiff.exe folderdiff <leftDir> <rightDir> [--report <md>]
BempDiff.exe ai       <old> <new> [--apikey KEY] [--baseurl URL] [--project <dir>]
```

You can also invoke the Java sidecar directly:

```bash
java -cp dist_input/classes com.bempdiff.Main <subcommand> ...
```

---

## Configuration

Settings are stored in `~/.bempdiff/bempdiff-web.properties` (server side) and are editable from **Settings** in the app:

- **Compare**: internal package prefixes, ignore patterns, nested unpack depth/threads, whitespace/comment normalization.
- **AI**: provider, Base URL, model, API Key (masked; not persisted by default), proxy, cost gate threshold.
- **UI**: theme toggle, diff tree filters.

---

## Architecture

```text
┌─────────────────────────────────────────────┐
│  Electron shell (dev-shell/)                │
│  └── Vue 3 frontend (bempdiff/webui/)        │
├─────────────────────────────────────────────┤
│  Java 21 sidecar (java_core/)               │
│  ├── diff engine                            │
│  ├── CFR decompiler                         │
│  └── AI analysis pipeline                   │
└─────────────────────────────────────────────┘
```

The sidecar runs as a local HTTP server on `127.0.0.1:18765`. The Electron renderer communicates with it via REST/Web APIs.

---

## Development

```bash
cd bempdiff/webui
npm install
npm run dev          # Vite dev server
npm run test       # Vitest unit tests
npm run build      # production bundle

# Run the desktop shell
cd ..
tooling/scripts/启动桌面壳.bat
```

Java tests use the TestRunner harness and require `cfr.jar` on the classpath:

```bash
javac -cp "dist_input/classes;toolchain/lib/*" -d out_test test/...
java -cp "out_test;toolchain/lib/*;dist_input/classes;cfr.jar" com.bempdiff.test.TestRunner com.bempdiff.test.XxxTest
```

---

## Security & Privacy

- **API Key** is not persisted to disk by default.
- **AI analysis** applies built-in desensitization rules for financial and personal data.
- **Cost gate** estimates token usage before analysis and blocks requests above the configured threshold.
- Use `--apikey ""` to force the mock AI path and keep everything local.

---

## Roadmap

See [`docs/PRD-优化需求全景-基于GitHub同款调研.md`](docs/PRD-优化需求全景-基于GitHub同款调研.md) for the full product optimization PRD. Short term priorities:

1. Character-level inline diff highlighting.
2. Collapse unchanged regions in large files.
3. Dark / light / system theme support.
4. CLI/CI mode for CI gate usage.
5. Syntax-aware diff with tree-sitter Java AST alignment.

---

## Contributing

Pull requests and issues are welcome. Please keep commit messages scoped and avoid unrelated WIP. For architecture history, see the long-term memory in `.workbuddy/memory/MEMORY.md`.

---

## License

[MIT](LICENSE)

---

## Acknowledgments

- [CFR](https://www.benf.org/other/cfr/) — Java decompiler
- [Electron](https://www.electronjs.org/), [Vue 3](https://vuejs.org/), [Bootstrap 5](https://getbootstrap.com/)

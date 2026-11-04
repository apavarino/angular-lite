# Angular Lite

Angular and TypeScript highlighting, completion, diagnostics and navigation for
IntelliJ IDEA, without requiring the paid JavaScript or Angular plugins.

Current plugin version: **0.1.0**.

## Prerequisites

- IntelliJ IDEA with **TextMate Bundles** enabled. The plugin declares platform
  build 253 or newer; the tested IDE is **2026.2.4 (262.11879.100)**.
- [LSP4IJ 0.21.0](https://plugins.jetbrains.com/plugin/23257-lsp4ij), installed
  separately from Settings > Plugins > Marketplace.
- An Angular project with its npm dependencies already installed, including
  Angular and TypeScript, and a valid `tsconfig.json`.
- Internet access on first use to download the language tooling.

The plugin prepares Node.js 24.22.2 and language servers under
`~/.angular-lite-idea`. You do not need a separate Node installation to run the
plugin. It does not install your application's dependencies or change its PATH.
Yarn Plug'n'Play and dependencies unavailable through standard Node resolution
are not supported.

No separate Java installation is required: the plugin runs inside IntelliJ IDEA's
[bundled JetBrains Runtime](https://www.jetbrains.com/help/idea/installation-guide.html).
Its bytecode targets Java 21, but the IDE may require a newer runtime (Java 25
for the tested IDEA 2026.2.4). Keep the runtime bundled with your IDE.

## Installation

1. Install LSP4IJ and enable TextMate Bundles as described above.
2. Download **angular-lite-0.1.0.zip** from the
   [Releases page](https://github.com/apavarino/angular-lite/releases).
3. Open **Settings > Plugins > gear icon > Install Plugin from Disk** and select
   the ZIP without extracting it. On macOS, use IntelliJ IDEA > Settings.
4. Restart IntelliJ IDEA, open your Angular project, then open an HTML or
   TypeScript file. Wait for the tooling preparation notification to finish.
5. Without the JavaScript plugin, associate `.ts` files with **TextMate** under
   **Settings > Editor > File Types**. Keep `.html` associated with HTML.

Plugin settings are under **Languages & Frameworks > Angular Lite**.
For an update that retains version 0.1.0, reinstall the ZIP and restart the IDE
so existing language-server processes use the new code.

Before a tagged release exists, a manually triggered successful
[Build plugin workflow](https://github.com/apavarino/angular-lite/actions/workflows/release.yml)
provides an artifact at the bottom of its run page. Extract that artifact to
obtain the installable plugin ZIP. GitHub sign-in is required for workflow
artifact downloads; tagged releases provide a direct ZIP download.

## Features

- Interpolations `{{ ... }}`, property bindings `[value]`, events `(click)` and
  two-way bindings `[(ngModel)]`.
- Nested control-flow blocks: `@if`, `@else`, `@for`, `@empty`, `@switch`, `@case`,
  `@default`, `@defer`, `@placeholder`, `@loading`, `@error` and `@let`.
- Legacy structural directives such as `*ngIf`, `*ngFor`, `*ngSwitchCase` and
  `*ngSwitchDefault`, including aliases and local variables. Import CommonModule
  or the relevant directives in your application where required.
- Automatic local template suggestions when typing `@` or `*` in external HTML
  templates. Block suggestions require Angular 17+; `@let` requires Angular 18+.
- Angular language-server completion, diagnostics and definition navigation.
  Use **Go to Declaration**: **Cmd+B** on macOS or **Ctrl+B** on Windows/Linux
  with the default keymap, or invoke the action from the menu.
- Component tag navigation and resolved expression highlighting, including
  bindings with whitespace around `=`.
- Multiple Angular dependency roots, with separate servers and routing to the
  closest root. Detection is limited to 8 directory levels and 10,000 folders.

Inline TypeScript templates use the grammar and Angular server, but local `@`/`*`
snippet insertion is currently limited to external HTML templates. Supported
Angular syntax still depends on the Angular version used by your project.

The language service matches the project's exact Angular version. When no
language-server package exists for that patch version, setup selects a stable
server in the same major/minor branch while retaining the exact language service.
Restart the servers or reopen the project after changing Angular versions.

### Non-strict templates

The editor launches Angular's language server with `--forceStrictTemplates`.
This enables analysis inside embedded views even when the project has
`strictTemplates: false` and omits `fullTemplateTypeCheck`. It restores definition
navigation and resolved colors inside nested control flow in that configuration.

**The project's tsconfig and build behavior are not changed.** The editor may
report additional strict template diagnostics compared with the project build.
The settings panel identifies this revision as **NAV5**, still version **0.1.0**.

## Compatibility and validation

IDE integration was tested on **Windows with IntelliJ IDEA 2026.2.4**, using an
isolated profile with `JavaScript`, `AngularJS` and
`com.intellij.modules.ultimate` disabled. The unified IDEA distribution was used
to test operation without the paid features.

Checks covered real Go to Declaration actions, resolved editor colors, nested
blocks, legacy directives, automatic completion and dynamic disable/re-enable.
Language-server tests covered Angular **16.2.12**, **21.0.0**, **22.0.7** and
**22.2.4**, including the relaxed template configuration described above.
Recorded results are in [tests/validation-nav5-2026.2.4.txt](tests/validation-nav5-2026.2.4.txt).

Tooling selection targets macOS Intel/Apple Silicon, Windows x64/ARM64 and Linux
x64/ARM64. **Actual IDE operation on macOS has not yet been tested.** This plugin
does not promise feature parity with IntelliJ's commercial Angular tooling.

## Build from source

Build prerequisites: **Python 3**, **JDK 25** for the IDEA 2026.2.4 API, an extracted
IDEA distribution, and the extracted LSP4IJ 0.21.0 ZIP at `deps/lsp4ij` (its JARs
must be in `deps/lsp4ij/lib`). Java compilation targets Java 21 bytecode.

```sh
python build.py --idea /path/to/idea
```

On macOS, pass the application's `Contents` directory. Alternatively, set
`ANGULAR_LITE_IDEA` to the IDE directory. The result is
`dist/angular-lite-0.1.0.zip`; IDEA and LSP4IJ binaries are not bundled.

## Troubleshooting

Check setup notifications and the indicated `installation.log` when preparation
fails. Language servers appear under **View > Tool Windows > Language Servers**
(or **LSP Consoles**, depending on LSP4IJ). Logs can include local paths and source
snippets, so review them before sharing. If bindings inside blocks lack navigation,
reinstall the current ZIP and restart the IDE to activate the NAV5 server options.

## License

Angular Lite is [MIT licensed](LICENSE). Third-party grammar notices are included
in [licenses](licenses), with upstream revisions in
[textmate/provenance.json](textmate/provenance.json). To regenerate the grammars,
run `python fetch-grammars.py` followed by `python adapt-grammars.py`.

This is an independent project and is not affiliated with JetBrains or Google.

# macOS setup (from scratch)

This project is a **Java 21** application built with **Maven**. These steps assume a standard Mac account (admin password when prompted).

> **You do not need full Xcode** from the App Store (~10–15 GB). Use **Git alone** (~50 MB) or Apple’s **Command Line Tools** (~1–2 GB, compact package).

## 1. Git (pick one option)

### Option A — Git only (lightest, ~50 MB)

Best if you want the smallest download to clone the repo.

1. Download from https://git-scm.com/download/mac  
2. Run the installer from the `.dmg`.  
3. Open a **new** Terminal and verify:

```bash
git --version
```

### Option B — Command Line Tools (compact, ~1–2 GB)

Installs Git **and** tools Homebrew typically needs. **Recommended** if you will use Homebrew below.

```bash
xcode-select --install
```

In the dialog, install **“Command Line Tools”** — **not** full Xcode from the App Store.

### Option C — Git via Homebrew (after step 2)

```bash
brew install git
git --version
```

## 2. Homebrew (package manager)

If you skipped option B above, the Homebrew installer may prompt for **Command Line Tools** (compact package — accept; still not full Xcode).

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Follow the on-screen instructions. At the end, Homebrew may print two lines to add to your shell config (Apple Silicon vs Intel paths differ). **Run those commands** so `brew` works in new terminals.

Verify:

```bash
brew --version
```

## 3. JDK 21 and Maven

```bash
brew install openjdk@21 maven
```

Register the JDK so macOS tools can find it (Homebrew prints similar `sudo ln -sfn` instructions after install; run them if suggested):

```bash
sudo ln -sfn "$(brew --prefix openjdk@21)/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

Point your shell at Java 21 for this session:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
```

Add those two `export` lines to `~/.zshrc` (default shell on recent macOS) so every new terminal uses Java 21.

Verify:

```bash
java -version   # should report 21.x
mvn -version    # should report Maven 3.x using Java 21
```

## 4. VS Code for Java

1. Open VS Code.
2. Open the **Extensions** view (`Cmd+Shift+X`).
3. Install **Extension Pack for Java** (publisher: Microsoft, id `vscjava.vscode-java-pack`). It includes the language support, Maven, debugger, and test runner.
4. **File → Open Folder…** and select the **Paper2** project root (the folder that contains `pom.xml`).
5. When prompted, allow the workspace JDK to be **21**. If needed: `Cmd+Shift+P` → **Java: Configure Java Runtime** → select JDK 21.

## 5. Build and run the project

In Terminal, from the project root:

```bash
cd /path/to/Paper2
mvn -q compile
```

Run the batch experiment driver (reads JSON under the configured input folder and writes `*_solution.json` and `*_metrics.json` under `files/output/`):

```bash
mvn -q exec:java
```

Or from VS Code: open a Java file → use **Run** above `main` in `ExperimentRunner`, or create a `launch.json` that runs `com.paper2.runner.ExperimentRunner`.

**No extra repo config is required** (there is no mandatory `.vscode` folder). If the integrated terminal fails, use **Terminal.app** for all commands — see [setup-from-zero.md §8](setup-from-zero.md#8-opcional-editor--vs-code-ou-cursor).

## 6. Troubleshooting

| Issue | What to try |
|--------|----------------|
| `git: command not found` | Install Git (step 1, option A or B). |
| Homebrew asks for Command Line Tools | Expected. Run `xcode-select --install` (compact package, not full Xcode). |
| `java: command not found` or wrong version | Ensure `JAVA_HOME` and `PATH` include JDK 21 (see step 3). Open a **new** terminal after editing `~/.zshrc`. |
| `mvn: command not found` | Run `brew install maven` and ensure Homebrew’s `bin` is on your `PATH` (follow “Next steps” after `brew` install). |
| Lombok errors in VS Code | Install **Extension Pack for Java**; run **Java: Clean Java Language Server Workspace** from the command palette, then reload. |
| VS Code terminal won't open | Broken `~/.zshrc` is common after adding `JAVA_HOME`; run `zsh -n ~/.zshrc`. Use Terminal.app meanwhile. Details: [setup-from-zero.md §8](setup-from-zero.md#8-opcional-editor--vs-code-ou-cursor). |
| `release version 21 not supported` | Your `java -version` is not 21; fix `JAVA_HOME` as above. |

## Requirements summary

| Tool | Version |
|------|---------|
| macOS | Recent enough for Homebrew (see [Homebrew requirements](https://docs.brew.sh/Installation)) |
| Git | Any recent version (standalone installer or Command Line Tools — **not** full Xcode) |
| JDK | **21** (matches `maven.compiler.release` in `pom.xml`) |
| Maven | 3.9+ (via Homebrew is fine) |
| VS Code | Current; plus **Extension Pack for Java** (optional) |

See also: [setup-from-zero.md](setup-from-zero.md) for clone commands and Linux steps.

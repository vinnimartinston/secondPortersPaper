# macOS setup (from scratch)

This project is a **Java 21** application built with **Maven**. These steps assume you only have **Visual Studio Code** installed and a standard Mac account (admin password when prompted).

## 1. Apple developer tools (required for Git, compilers, and Homebrew)

1. Open **Terminal** (Spotlight: type `Terminal`).
2. Run:

```bash
xcode-select --install
```

3. Complete the dialog (Agree → Install). This can take several minutes.

## 2. Homebrew (package manager)

1. Install from the official script (see [brew.sh](https://brew.sh)):

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

2. Follow the on-screen instructions. At the end, Homebrew may print two lines to add to your shell config (Apple Silicon vs Intel paths differ). **Run those commands** so `brew` works in new terminals.

3. Verify:

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

## 4. Automated setup script (optional)

From the project root:

```bash
chmod +x scripts/setup-macos.sh
./scripts/setup-macos.sh
```

The script checks prerequisites, installs `openjdk@21` and `maven` via Homebrew when possible, and prints suggested `JAVA_HOME` / `PATH` lines for `~/.zshrc`. If Homebrew is missing, it prints the official install command instead of running it (the installer needs your interaction).

## 5. VS Code for Java

1. Open VS Code.
2. Open the **Extensions** view (`Cmd+Shift+X`).
3. Install **Extension Pack for Java** (publisher: Microsoft, id `vscjava.vscode-java-pack`). It includes the language support, Maven, debugger, and test runner.
4. **File → Open Folder…** and select the **Paper2** project root (the folder that contains `pom.xml`).
5. When prompted, allow the workspace JDK to be **21**. If needed: `Cmd+Shift+P` → **Java: Configure Java Runtime** → select JDK 21.

## 6. Build and run the project

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

## 7. Troubleshooting

| Issue | What to try |
|--------|----------------|
| `java: command not found` or wrong version | Ensure `JAVA_HOME` and `PATH` include JDK 21 (see step 3). Open a **new** terminal after editing `~/.zshrc`. |
| `mvn: command not found` | Run `brew install maven` and ensure Homebrew’s `bin` is on your `PATH` (follow “Next steps” after `brew` install). |
| Lombok errors in VS Code | Install **Extension Pack for Java**; run **Java: Clean Java Language Server Workspace** from the command palette, then reload. |
| `release version 21 not supported` | Your `java -version` is not 21; fix `JAVA_HOME` as above. |

## Requirements summary

| Tool | Version |
|------|---------|
| macOS | Recent enough for Homebrew (see [Homebrew requirements](https://docs.brew.sh/Installation)) |
| JDK | **21** (matches `maven.compiler.release` in `pom.xml`) |
| Maven | 3.9+ (via Homebrew is fine) |
| VS Code | Current; plus **Extension Pack for Java** |

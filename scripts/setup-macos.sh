#!/usr/bin/env bash
# Paper2 — macOS bootstrap: Homebrew JDK 21 + Maven.
# Run from anywhere; uses the script's directory to find the project root.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "==> Paper2 macOS setup"
echo "    Project root: ${PROJECT_ROOT}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "This script is for macOS only." >&2
  exit 1
fi

if ! xcode-select -p &>/dev/null; then
  echo "" >&2
  echo "Xcode Command Line Tools are not installed." >&2
  echo "Run:  xcode-select --install" >&2
  echo "Then re-run this script." >&2
  exit 1
fi

if ! command -v brew &>/dev/null; then
  echo "" >&2
  echo "Homebrew is not installed or not on PATH." >&2
  echo "Install it (interactive; needs your password):" >&2
  echo '  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"' >&2
  echo "" >&2
  echo "After install, add brew to PATH (the installer prints the exact lines for your Mac)." >&2
  exit 1
fi

echo "==> brew install openjdk@21 maven"
brew install openjdk@21 maven

JDK_PREFIX="$(brew --prefix openjdk@21)"
JAVA_HOME_CANDIDATE=""
for candidate in "${JDK_PREFIX}/libexec/openjdk.jdk/Contents/Home" "${JDK_PREFIX}"; do
  if [[ -x "${candidate}/bin/java" ]]; then
    JAVA_HOME_CANDIDATE="${candidate}"
    break
  fi
done
if [[ -z "${JAVA_HOME_CANDIDATE}" ]]; then
  echo "Could not find java under ${JDK_PREFIX}" >&2
  exit 1
fi

echo ""
echo "==> Suggested environment (add to ~/.zshrc if not already there):"
echo ""
echo "export JAVA_HOME=\"${JAVA_HOME_CANDIDATE}\""
echo "export PATH=\"\${JAVA_HOME}/bin:\${PATH}\""
echo ""
echo "Optional: register the JDK for /usr/libexec/java_home (may prompt for sudo):"
echo "  sudo ln -sfn \"${JDK_PREFIX}/libexec/openjdk.jdk\" /Library/Java/JavaVirtualMachines/openjdk-21.jdk"
echo ""

export JAVA_HOME="${JAVA_HOME_CANDIDATE}"
export PATH="${JAVA_HOME}/bin:${PATH}"

echo "==> Using JAVA_HOME=${JAVA_HOME}"
java -version
echo ""
mvn -version
echo ""

echo "==> Compile project"
cd "${PROJECT_ROOT}"
mvn -q compile
echo "Build OK."
echo ""
echo "Run experiments:  cd \"${PROJECT_ROOT}\" && mvn -q exec:java"
echo "Full guide:       docs/setup-macos.md"

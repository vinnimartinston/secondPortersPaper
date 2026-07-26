# Setup do zero — Paper2

Guia passo a passo para clonar o repositório, instalar dependências e rodar o projeto em um computador novo.

**O que este projeto precisa:**

| Ferramenta | Versão |
|------------|--------|
| Git | qualquer versão recente |
| JDK | **21** |
| Apache Maven | **3.9+** |
| Python (opcional, dashboard Streamlit) | **3.10+** |

> **macOS:** você **não precisa** instalar o **Xcode completo** (App Store, ~10–15 GB). Basta Git sozinho ou as **Command Line Tools** (pacote compacto da Apple, ~1–2 GB).

---

## 1. Instalar Git

### macOS

Escolha **uma** opção:

#### Opção A — Só Git (mais leve, ~50 MB)

Ideal se você quer apenas clonar o repositório com o mínimo de download.

1. Baixe o instalador em: https://git-scm.com/download/mac  
2. Abra o `.dmg`, execute o instalador e conclua o assistente.  
3. Feche e abra um **novo** Terminal.

```bash
git --version
```

#### Opção B — Command Line Tools (compacta, ~1–2 GB)

Instala Git **e** ferramentas que o **Homebrew** costuma exigir (recomendada se você for seguir o passo 3 com `brew`).

```bash
xcode-select --install
```

Na janela que abrir, instale **“Command Line Tools”** — **não** o Xcode completo da App Store.

#### Opção C — Git via Homebrew (depois do passo 3)

Se você já instalou o Homebrew:

```bash
brew install git
git --version
```

> **Não instale** o Xcode completo só por causa deste projeto. Ele não é necessário para Java/Maven.

### Linux (Debian/Ubuntu)

```bash
sudo apt update
sudo apt install -y git
```

### Verificar

```bash
git --version
```

---

## 2. Clonar o repositório

Escolha uma pasta onde você guarda projetos (ex.: `~/Projects`) e clone.

### Opção A — HTTPS (mais simples; pede login do GitHub na primeira vez)

```bash
mkdir -p ~/Projects
cd ~/Projects
git clone https://github.com/vinnimartinston/secondPortersPaper.git Paper2
cd Paper2
```

### Opção B — SSH (requer chave SSH configurada no GitHub)

```bash
mkdir -p ~/Projects
cd ~/Projects
git clone git@github.com:vinnimartinston/secondPortersPaper.git Paper2
cd Paper2
```

### Conferir que está na branch correta

```bash
git status
git branch
```

Deve mostrar `main` e working tree limpo (ou apenas arquivos locais que você ainda não commitou).

### Atualizar depois (quando já tiver clonado)

```bash
cd ~/Projects/Paper2
git pull origin main
```

---

## 3. Instalar JDK 21 e Maven

### macOS (recomendado: Homebrew)

**3.1 — Instalar Homebrew** (se ainda não tiver):

Se ainda não fez o passo 1 opção B, o instalador do Homebrew pode pedir as **Command Line Tools** (pacote compacto — aceite; ainda **não** é o Xcode completo).

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

No final, o instalador pode pedir para adicionar o `brew` ao PATH. Execute os comandos que ele imprimir (variam entre Mac Intel e Apple Silicon).

Verifique:

```bash
brew --version
```

**3.2 — Instalar Java 21 e Maven:**

```bash
brew install openjdk@21 maven
```

**3.3 — Registrar o JDK no macOS:**

```bash
sudo ln -sfn "$(brew --prefix openjdk@21)/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

**3.4 — Configurar o shell (zsh) para usar Java 21 sempre:**

```bash
echo 'export JAVA_HOME="$(/usr/libexec/java_home -v 21)"' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

### Linux (Debian/Ubuntu)

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven
```

Configure o Java 21 como padrão (se houver várias versões):

```bash
sudo update-alternatives --config java
```

Escolha a opção do **OpenJDK 21**.

---

## 4. Verificar Java e Maven

Na raiz do projeto (pasta que contém `pom.xml`):

```bash
cd ~/Projects/Paper2
java -version
mvn -version
```

**Esperado:**

- `java -version` → linha com **21.x**
- `mvn -version` → Maven **3.9+** usando **Java 21**

Se aparecer `release version 21 not supported`, o Maven está usando um JDK antigo. Corrija o `JAVA_HOME` (macOS: repita o passo 3.4).

---

## 5. Compilar e rodar os testes

Ainda na raiz do projeto:

```bash
cd ~/Projects/Paper2
mvn -q compile
mvn -q test
```

O Maven baixa dependências automaticamente na primeira execução (Jackson, Lombok, JUnit, etc.). Não é necessário instalar nada manualmente além do JDK e do Maven.

---

## 6. Rodar os experimentos (backend Java)

### Estrutura de arquivos

| Pasta | Conteúdo |
|-------|----------|
| `files/input/` | Instâncias do problema (`*.json`) |
| `files/experiments/` | Configurações de experimento (`*.json`) |
| `files/output/` | Saídas geradas (`*_solution.json`, `*_metrics.json`) |

### Configuração (opcional)

Edite `src/main/java/com/paper2/runner/ExperimentRunnerConfig.java` para escolher quais instâncias/experimentos rodar e o paralelismo. Por padrão, roda os experimentos `infinity` e `default`.

### Executar

```bash
cd ~/Projects/Paper2
mvn -q exec:java
```

Classe principal: `com.paper2.runner.ExperimentRunner`.

Saídas em:

```text
files/output/<nome-do-experimento>/<instancia>_solution.json
files/output/<nome-do-experimento>/<instancia>_metrics.json
```

---

## 7. (Opcional) Dashboard Streamlit

Só necessário se quiser visualizar as soluções em gráfico Gantt.

```bash
cd ~/Projects/Paper2
python3 -m venv .venv
source .venv/bin/activate
pip install -r streamlit/requirements.txt
streamlit run streamlit/app.py
```

Abra o URL que o Streamlit imprimir no terminal (geralmente `http://localhost:8501`).

Para desativar o ambiente virtual depois:

```bash
deactivate
```

---

## 8. (Opcional) Editor — VS Code ou Cursor

1. Instale **Extension Pack for Java** (Microsoft).
2. Abra a pasta raiz do projeto (`Paper2`, onde está o `pom.xml`).
3. Confirme que o workspace usa **JDK 21** (`Cmd+Shift+P` → **Java: Configure Java Runtime**).

Para rodar pelo editor: abra `ExperimentRunner.java` e use **Run** acima do método `main`.

**Este projeto não exige configuração extra** (não há pasta `.vscode` obrigatória no repositório). Git, Java, Maven e `mvn exec:java` funcionam no **Terminal do macOS** normalmente.

### Terminal do VS Code / Cursor não abre

Se `Terminal → New Terminal` (`` Ctrl+` ``) não funcionar, o problema é do editor ou do shell — **não** do Paper2.

**1. Teste o Terminal do macOS primeiro**

Abra **Terminal.app** (Spotlight → `Terminal`) e rode:

```bash
echo ok
zsh --version
cd ~/Projects/Paper2
mvn -q compile
```

Se falhar aqui, corrija o shell antes do VS Code (passo 2). Se funcionar, use o Terminal.app para todo o setup; o projeto roda igual.

**2. Erro comum: `~/.zshrc` quebrado**

Linhas mal coladas no passo 3.4 (JAVA_HOME) podem fazer o shell **abrir e fechar na hora**. No Terminal.app:

```bash
zsh -n ~/.zshrc
```

Se aparecer erro de sintaxe, edite e corrija:

```bash
nano ~/.zshrc
```

As linhas corretas são:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
export PATH="$JAVA_HOME/bin:$PATH"
```

Depois:

```bash
source ~/.zshrc
```

**3. Definir o shell padrão no VS Code**

`Cmd+Shift+P` → **Terminal: Select Default Profile** → escolha **zsh**.

Ou em **Settings** (`Cmd+,`), busque `terminal default profile osx` e defina **zsh**.

**4. Forçar caminho do shell (se ainda falhar)**

`Cmd+Shift+P` → **Preferences: Open User Settings (JSON)** e adicione:

```json
{
  "terminal.integrated.defaultProfile.osx": "zsh",
  "terminal.integrated.profiles.osx": {
    "zsh": {
      "path": "/bin/zsh",
      "args": ["-l"]
    }
  }
}
```

Salve, **Reload Window** (`Cmd+Shift+P` → **Developer: Reload Window**) e tente o terminal de novo.

**5. Outras causas**

| Sintoma | O que fazer |
|---------|-------------|
| Terminal abre e fecha em 1 s | Quase sempre `~/.zshrc` com erro (passo 2). |
| “Launching the shell… Failed” | Confirme `/bin/zsh` existe: `ls -l /bin/zsh`. |
| VS Code pede permissão / não abre nada | **System Settings → Privacy & Security** — permita VS Code se solicitado; reinicie o VS Code. |
| Só no VS Code, Terminal.app OK | Passos 3–4; ou reinstale VS Code de [code.visualstudio.com](https://code.visualstudio.com/). |

**Alternativa:** ignore o terminal integrado e use só o **Terminal.app** para `git`, `mvn` e `streamlit`. Não há passo extra no Paper2 por causa disso.

---

## 9. Resumo rápido (já com tudo instalado)

```bash
cd ~/Projects/Paper2
git pull origin main
mvn -q compile
mvn -q test
mvn -q exec:java
```

---

## 10. Problemas comuns

| Erro | O que fazer |
|------|-------------|
| `git: command not found` | Instale Git (passo 1, opção A ou B). |
| Homebrew pede “Command Line Tools” | Normal. Instale via `xcode-select --install` (pacote compacto, não o Xcode completo). |
| `Permission denied (publickey)` no clone SSH | Use HTTPS (opção A) ou configure chave SSH no GitHub. |
| `java: command not found` | Instale JDK 21 e configure `JAVA_HOME` (passo 3). |
| `release version 21 not supported` | `java -version` não é 21; ajuste `JAVA_HOME`. |
| `mvn: command not found` | Instale Maven (`brew install maven` ou `apt install maven`). |
| Erros de Lombok no editor | Instale **Extension Pack for Java**; **Java: Clean Java Language Server Workspace** → reload. |
| Terminal do VS Code não abre | Veja seção 8 acima; use Terminal.app enquanto corrige. Causa frequente: `~/.zshrc` inválido. |
| Clone OK mas pasta vazia | Verifique se clonou na branch `main`: `git checkout main`. |

---

## 11. Documentação adicional

| Arquivo | Conteúdo |
|---------|----------|
| [README.md](../README.md) | Visão geral do projeto |
| [setup-macos.md](setup-macos.md) | Detalhes extras para macOS |
| [docs/README.md](README.md) | Índice da documentação JSON |
| [input-json.md](input-json.md) | Formato das instâncias de entrada |
| [experiment-json.md](experiment-json.md) | Formato dos experimentos |

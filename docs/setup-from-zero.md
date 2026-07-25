# Setup do zero — Paper2

Guia passo a passo para clonar o repositório, instalar dependências e rodar o projeto em um computador novo.

**O que este projeto precisa:**

| Ferramenta | Versão |
|------------|--------|
| Git | qualquer versão recente |
| JDK | **21** |
| Apache Maven | **3.9+** |
| Python (opcional, dashboard Streamlit) | **3.10+** |

---

## 1. Instalar Git

### macOS

Abra o **Terminal** e instale as ferramentas de linha de comando da Apple (inclui Git):

```bash
xcode-select --install
```

Siga o assistente na tela (Concordar → Instalar).

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
| `git: command not found` | Instale Git (passo 1). |
| `Permission denied (publickey)` no clone SSH | Use HTTPS (opção A) ou configure chave SSH no GitHub. |
| `java: command not found` | Instale JDK 21 e configure `JAVA_HOME` (passo 3). |
| `release version 21 not supported` | `java -version` não é 21; ajuste `JAVA_HOME`. |
| `mvn: command not found` | Instale Maven (`brew install maven` ou `apt install maven`). |
| Erros de Lombok no editor | Instale **Extension Pack for Java**; **Java: Clean Java Language Server Workspace** → reload. |
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

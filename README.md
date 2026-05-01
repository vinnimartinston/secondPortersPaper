# Paper2

Hospital portering simulation: Java/Maven backend, optional Streamlit viewer for solution JSON.

## Documentation index

| Location | Contents |
|----------|-----------|
| **[docs/README.md](docs/README.md)** | Index of all technical docs in `docs/`. |
| **[docs/input-json.md](docs/input-json.md)** | Problem instance JSON (`InputDto`). |
| **[docs/experiment-json.md](docs/experiment-json.md)** | Experiment batch configuration JSON. |
| **[docs/output-solution-json.md](docs/output-solution-json.md)** | Solution export (`*_solution.json`). |
| **[docs/output-metrics-json.md](docs/output-metrics-json.md)** | Metrics export (`*_metrics.json`). |

**Worked examples** (same shapes as production outputs):

- [`files/examples/example_input.json`](files/examples/example_input.json)
- [`files/examples/example_experiment.json`](files/examples/example_experiment.json)
- [`files/examples/example_solution.json`](files/examples/example_solution.json)
- [`files/examples/example_metrics.json`](files/examples/example_metrics.json)

Agent-oriented Java rules: [`docs/AGENTS.md`](docs/AGENTS.md) and [`docs/guidelines.md`](docs/guidelines.md).

---

## Prerequisites

- **JDK 21** (see `maven.compiler.release` in [`pom.xml`](pom.xml))
- **Apache Maven** 3.9+
- (Optional dashboard) **Python 3.10+** with `pip`

Clone and enter the repository:

```bash
git clone <repository-url>
cd Paper2
```

Build and tests:

```bash
mvn -q compile
mvn -q test
```

---

## Running batch experiments (`ExperimentRunner`)

### 1. Instance JSON files

Place instance files under **`files/input/`** (flat directory). Names must end with `.json`.

### 2. Experiment JSON files

Define scenarios under **`files/experiments/`** (e.g. [`files/experiments/default.json`](files/experiments/default.json)).

See **[docs/experiment-json.md](docs/experiment-json.md)** for fields (`penalty`, optional wheelchair overrides via `overWriteInitialWheelchairInventory` + `depots[]`, etc.).

### 3. Configure `ExperimentRunnerConfig`

Edit [`src/main/java/com/paper2/runner/ExperimentRunnerConfig.java`](src/main/java/com/paper2/runner/ExperimentRunnerConfig.java):

| Static field | Role |
|--------------|------|
| `instances` | Instance stems or file names (`.json` added if missing). **Empty list** ⇒ run **every** `*.json` in `files/input/`. |
| `experiments` | Experiment file names under `files/experiments/`. **Empty list** ⇒ run **every** `*.json` there (sorted). |
| `parallelism` | `ForkJoinPool` parallelism; if `null` or ≤ 0, CPU count is used. |

Paths are fixed in [`ExperimentManager`](src/main/java/com/paper2/runner/ExperimentManager.java): input root `files/input`, experiments root `files/experiments`.

### 4. Run

From the repo root:

```bash
mvn -q exec:java
```

Equivalent main class: `com.paper2.runner.ExperimentRunner`.

**Outputs:** `files/output/<experimentStem>/<instanceStem>_solution.json` and `_metrics.json`.

Console: cyan progress `Running Experiment XX/YY (ZZ%)`, green completion `Experiment <experiment> + <instance> - ok`, plus write paths.

---

## Running Streamlit

The dashboard reads **`files/output/*/`** and plots Gantt-style schedules from `*_solution.json`.

From the repo root:

```bash
python3 -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r streamlit/requirements.txt
streamlit run streamlit/app.py
```

Implementation: [`streamlit/app.py`](streamlit/app.py).

---

## Interpreting JSON artifacts

Use the docs above for full tables. Short guide:

### Input (`example_input.json`)

Defines **patients** (requests, priorities, locations, times, transport modes), **porters** (`amountOfPorters`), **depots** with initial wheelchair counts, and **`timeMatrix.graph`** travel times. Details: **[docs/input-json.md](docs/input-json.md)**.

### Experiment (`example_experiment.json`)

Controls **penalty** for depot violations and optionally **overwrites** depot inventories when `overWriteInitialWheelchairInventory` is `true`. Details: **[docs/experiment-json.md](docs/experiment-json.md)**.

### Solution (`example_solution.json`)

Final routes: **`objectiveFunction`** (objective, horizon, tardiness sums, depot penalty term), **`finalSchedules`** (per porter, ordered patients with clock fields), **`depotInventory`**. Details: **[docs/output-solution-json.md](docs/output-solution-json.md)**.

### Metrics (`example_metrics.json`)

Aggregates tardiness, response times, idle/setup, porter mix, depot summaries, wall-clock runtime. Details: **[docs/output-metrics-json.md](docs/output-metrics-json.md)**.


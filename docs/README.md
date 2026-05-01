# Paper2 documentation

This folder contains reference material for JSON formats and tooling.

| Document | Purpose |
|----------|---------|
| [input-json.md](input-json.md) | Problem instance JSON (`InputDto`): patients, depots, travel times. |
| [experiment-json.md](experiment-json.md) | Experiment configuration JSON consumed by `ExperimentRunner`. |
| [output-solution-json.md](output-solution-json.md) | Serialized solution (`*_solution.json`). |
| [output-metrics-json.md](output-metrics-json.md) | Aggregated run metrics (`*_metrics.json`). |

**Example artifacts** (trimmed or full runs) live under [`files/examples/`](../files/examples/):

- [`example_input.json`](../files/examples/example_input.json)
- [`example_experiment.json`](../files/examples/example_experiment.json)
- [`example_solution.json`](../files/examples/example_solution.json)
- [`example_metrics.json`](../files/examples/example_metrics.json)

Java coding guidelines for this repository are under [AGENTS.md](../AGENTS.md) and [guidelines.md](guidelines.md).

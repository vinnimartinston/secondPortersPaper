# Experiment JSON format

Experiment files configure batch runs executed by [`ExperimentRunner`](../src/main/java/com/paper2/runner/ExperimentRunner.java). They live under **`files/experiments/`** (e.g. [`files/experiments/default.json`](../files/experiments/default.json)).

**Small illustrative example:** [`files/examples/example_experiment.json`](../files/examples/example_experiment.json).

Jackson binds JSON into [`ExperimentRunner`’s internal `ExperimentFileConfig`](../src/main/java/com/paper2/runner/ExperimentRunner.java) (private static class).

## Fields

| Field | Type | Description |
|--------|------|-------------|
| `penalty` | int (optional) | Coefficient for **wheelchair depot inventory** violations (passed to [`Solution.setDefaultDepotInventoryViolationPenaltyCoefficient`](../src/main/java/com/paper2/domain/Solution.java)). If omitted or non-positive, the runner uses default `100_000`. |
| `amountOfDepots` | int (optional) | Parsed from JSON but **not** used by the current runner to choose input folders (instances are read from `files/input/`). Reserved for future use or external tooling. |
| `overWriteInitialWheelchairInventory` | boolean (optional) | If `true`, the runner replaces each [`DepotDto`](../src/main/java/com/paper2/dto/DepotDto.java) `initialWheelchairInventory` with values from `depots[]` below **when** both the flag is true and at least one valid `{id, initialWheelchairInventory}` patch exists. |
| `depots` | array (optional) | Sparse patches: objects with `id` and `initialWheelchairInventory`. Only ids present here override the instance JSON when overwrite is enabled. |

Unknown JSON properties are ignored by Jackson unless configured otherwise.

## Which experiments run

[`ExperimentRunnerConfig`](../src/main/java/com/paper2/runner/ExperimentRunnerConfig.java) lists experiment **file names** in `experiments`. If that list is **empty**, every `*.json` under `files/experiments/` is executed (sorted).

## Outputs

Each experiment file stem (without `.json`) becomes a subdirectory under **`files/output/`** containing `*_solution.json` and `*_metrics.json` per instance.

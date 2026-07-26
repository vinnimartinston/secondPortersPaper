# Metrics JSON file format (`*_metrics.json`)

Aggregated metrics computed by [SolutionMetricsCalculator](../src/main/java/com/paper2/metrics/SolutionMetricsCalculator.java) from final schedules and input. Example: [example_metrics.json](../files/examples/example_metrics.json).

Main mapping: [ExperimentMetricsDto](../src/main/java/com/paper2/dto/metrics/ExperimentMetricsDto.java).

## Regenerating metrics without re-simulating

[MetricsRegenerator](../src/main/java/com/paper2/runner/MetricsRegenerator.java) rebuilds every `*_metrics.json` under `files/output/` from the matching `*_solution.json` and `files/input/{stem}.json`. It preserves `simulatorWallTimeSeconds` from the existing metrics file when present.

```bash
mvn -q exec:java -Dexec.mainClass=com.paper2.runner.MetricsRegenerator
```

Optional output root: `mvn -q exec:java -Dexec.mainClass=com.paper2.runner.MetricsRegenerator -Dexec.args="files/output"`.

## Top-level fields

| Field | Type | Description |
|--------|------|-------------|
| `instanceName` | string | Copied from `InputDto.name`. |
| `amountOfPorters` | int | Number of porters declared in the input. |
| `transportedPatientCount` | int | Total **real** patients (non-dummy) across all final routes. |
| `simulatorWallTimeSeconds` | double | Wall-clock time spent in `Simulator.start` for this instance. |

## `summary`

| Field | Description |
|--------|-------------|
| `scheduleDurationSeconds` | Elapsed seconds from schedule start (08:00) to last patient end; equals `setupIdle.horizonSeconds`. |
| `scheduleDurationClock` | Same span as duration `HH:MM:SS` (not a time-of-day). |
| `objectiveFunction` | Nested objective snapshot on the committed final plan (see below). |

### `summary.objectiveFunction`

| Field | Description |
|--------|-------------|
| `value` | `tardinessTerm.value + depotPenaltyTerm.value` (clamped to `int`). |
| `tardinessTerm` | Weighted tardiness term and per-priority breakdown. |
| `depotPenaltyTerm` | Wheelchair depot violation penalty term. |

### `summary.objectiveFunction.tardinessTerm`

| Field | Description |
|--------|-------------|
| `value` | Σ (tardiness in seconds × priority weight) on final schedules. |
| `sumUnweightedTardinessSeconds` | Σ raw tardiness in seconds on final schedules. |
| `byPriority` | Per priority: `priority`, `value` (= `sumUnweightedTardinessSeconds × coefficient`), `coefficient` (priority weight), `sumUnweightedTardinessSeconds`. |

### `summary.objectiveFunction.depotPenaltyTerm`

| Field | Description |
|--------|-------------|
| `value` | `totalWheelchairViolationSecondsBelowZero × coefficient`. |
| `coefficient` | Experiment penalty coefficient (`penalty` in experiment JSON). |
| `totalWheelchairViolationSecondsBelowZero` | Seconds with negative wheelchair stock (all depots, evaluation horizon). |

## `tardiness`

| Field | Description |
|--------|-------------|
| `countTardinessZero` | Patients with zero tardiness. |
| `countTardinessPositiveUpTo1800s` | Tardiness in (0, 1800] seconds. |
| `countTardinessAbove1800s` | Tardiness &gt; 1800 seconds. |
| `byPriority` | List of objects per priority level: `priority`, `patientCount`, `sumUnweightedTardinessMinutes`, `avgUnweightedTardinessMinutes`, `tardyPatientCount`, `tardyPatientShare`. |
| `meanUnweightedTardinessMinutesAllPatients` | Global mean raw tardiness in minutes per patient. |

## `response`

| Field | Description |
|--------|-------------|
| `byPriority` | Per priority: `patientCount`, `avgResponseMinutes` (definition aligned with C++: `end - transportTime - timeAsked`, mean minutes). |
| `meanResponseMinutesAllPatients` | Global mean in minutes. |

## `setupIdle`

| Field | Description |
|--------|-------------|
| `sumTravelSetupSeconds` | Sum of travel (setup-to-origin) times for real patients. |
| `avgTravelSetupMinutesPerPatient` | Mean per patient (minutes). |
| `sumIdleSeconds` | Total idle reconstructed by replaying routes with the graph (see code). |
| `sumIdleClock` | `sumIdleSeconds` as duration `HH:MM:SS`. |
| `avgIdleMinutesPerPorter` | Mean idle per porter (minutes). |
| `idleTimeShareOfHorizon` | Mean idle per porter / horizon (minutes), analogous to idle percentage in C++. |
| `horizonSeconds` | Elapsed seconds from schedule start (08:00) to last patient end (same as `summary.scheduleDurationSeconds`). |
| `idleNote` | `null` if idle was computed; message if `Solution` has no `Graph`. |

## `porterEffort`

Per-porter metrics on **bed share** and consecutive sequences:

| Field | Description |
|--------|-------------|
| `effortRateMean` | Mean of “bed / real patients” fractions per porter. |
| `maxBedShareAcrossPorters` | Maximum of that fraction. |
| `minBedShareAcrossPorters` | Minimum. |
| `byPorter` | List: `porterId`, `bedPatientShare`, `maxConsecutiveBeds`, `medianConsecutiveBedStreakMetric`. |

## `wheelchairDepot`

Wheelchair / depot KPIs from [WheelchairDepotMetricsCalculator](../src/main/java/com/paper2/metrics/WheelchairDepotMetricsCalculator.java), aligned with legacy `WC_indicator` / `print_chairs_infos` in `printer.cpp`:

| Field | Description |
|--------|-------------|
| `maxWheelchairsUsedAtSameTime` | Peak concurrent wheelchairs checked out of depots (`WC_max`). |
| `timeShareUsingMaxWheelchairs` | Fraction of horizon seconds at that peak (`WC_max_usage`). |
| `depotMinBalance` | Per depot (sorted by id): worst deficit vs. zero baseline (`-min(balanceTracker)`). |
| `totalPickUp` | Total pick-up events at depots. |
| `totalDropOff` | Total drop-off events at depots. |
| `avgWalkingToDepotMinutes` | Mean travel/setup time per depot visit (minutes). |

## `scheduleTimeAggregates`

Aggregates from [PorterScheduleRouteMetrics](../src/main/java/com/paper2/metrics/PorterScheduleRouteMetrics.java) per final route:

| Field / area | Description |
|----------------|-------------|
| `sumTravel`, `sumTransport`, `sumIdle`, `sumDuration` | Objects with `seconds` (long) and `clock` (human-readable string). |
| `meanDurationActiveSeconds` / `meanDurationActiveClock` | Mean route duration only among porters with ≥1 real patient. |
| `maxDurationSeconds` / `maxDurationClock` | Largest route duration among porters. |
| `minDurationActiveSeconds` / `minDurationActiveClock` | Smallest duration among **active** routes; `-1` / `null` if none active. |
| `durationSpreadSeconds` / `durationSpreadClock` | `max - min` among active routes. |
| `activePorterCount` | Porters with at least one real patient. |
| `idlePorterCount` | Porters with no real patients on the final route. |
| `fleetTravelShare`, `fleetTransportShare`, `fleetIdleShare` | Shares of summed durations (travel/transport/idle vs total `duration`). |
| `schedulesAllChecksTrueCount` | How many porters pass all three route consistency checks. |
| `scheduleCount` | Number of entries in `finalSchedules`. |
| `scheduleValidationPassRate` | `schedulesAllChecksTrueCount / scheduleCount`. |
| `byPorter` | Per `porterId`: seconds and clocks for travel/transport/idle/duration, `realPatientCount`, `allValidationsTrue`. |

### Per-porter validation (`allValidationsTrue`)

`true` when simultaneously: last patient end matches shift end; interval `(lastEnd - dummyEnd)` equals the duration used; and `travel + transport + idle` sums to duration. Otherwise the serializer may also log a warning.

## Minimal example (structure)

```json
{
  "instanceName": "instance.txt",
  "amountOfPorters": 16,
  "transportedPatientCount": 50,
  "simulatorWallTimeSeconds": 1.7,
  "summary": {
    "scheduleDurationSeconds": 3642,
    "scheduleDurationClock": "01:00:42",
    "objectiveFunction": {
      "value": 20447,
      "tardinessTerm": {
        "value": 162336.0,
        "sumUnweightedTardinessSeconds": 10110.0,
        "byPriority": []
      },
      "depotPenaltyTerm": {
        "value": 0,
        "coefficient": 100000,
        "totalWheelchairViolationSecondsBelowZero": 0
      }
    }
  },
  "tardiness": { "countTardinessZero": 0, "countTardinessPositiveUpTo1800s": 0, "countTardinessAbove1800s": 0, "byPriority": [], "meanUnweightedTardinessMinutesAllPatients": 0 },
  "response": { "byPriority": [], "meanResponseMinutesAllPatients": 0 },
  "setupIdle": {
    "sumTravelSetupSeconds": 0,
    "avgTravelSetupMinutesPerPatient": 0,
    "sumIdleSeconds": 0,
    "sumIdleClock": "00:00:00",
    "avgIdleMinutesPerPorter": 0,
    "idleTimeShareOfHorizon": 0,
    "horizonSeconds": 0,
    "idleNote": null
  },
  "porterEffort": { "effortRateMean": 0, "maxBedShareAcrossPorters": 0, "minBedShareAcrossPorters": 0, "byPorter": [] },
  "wheelchairDepot": { "maxWheelchairsUsedAtSameTime": 0, "timeShareUsingMaxWheelchairs": 0.0, "depotMinBalance": [], "totalPickUp": 0.0, "totalDropOff": 0.0, "avgWalkingToDepotMinutes": 0.0 },
  "scheduleTimeAggregates": {
    "sumTravel": { "seconds": 0, "clock": "0:00:00" },
    "sumTransport": { "seconds": 0, "clock": "0:00:00" },
    "sumIdle": { "seconds": 0, "clock": "0:00:00" },
    "sumDuration": { "seconds": 0, "clock": "0:00:00" },
    "byPorter": []
  }
}
```

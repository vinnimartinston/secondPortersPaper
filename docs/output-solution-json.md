# Solution JSON file format (`*_solution.json`)

Export of the final solution after simulation, produced by [SolutionSerializer](../src/main/java/com/paper2/serialization/SolutionSerializer.java). Example: [example_solution.json](../files/examples/example_solution.json).

## Root document

| Field | Type | Description |
|--------|------|-------------|
| `simulatorClock` | string | Simulation clock at export time, `HH:MM:SS` format. |
| `objectiveFunction` | object | **Exclusive** objective-function snapshot: scalar value, evaluation horizon, penalty coefficients, tardiness sum, depot penalty term (see below). |
| `finalSchedules` | array | One entry per porter with summary and list of assigned real patients. |
| `depotInventory` | object | Per-depot wheelchair inventory detail (`depots` array only); comes **after** `finalSchedules` in the file. |

Java mapping: [SolutionResultDto](../src/main/java/com/paper2/dto/solution/SolutionResultDto.java).

## Object `objectiveFunction`

All scalar FO-related fields used to audit [Solution.getObjectiveValue()](../src/main/java/com/paper2/domain/Solution.java) and the unweighted-tardiness / depot-penalty model:

| Field | Type | Description |
|--------|------|-------------|
| `objectiveValue` | int | Stored objective on the solution. |
| `evaluationWindowStartSeconds` | int | Start of evaluation window (seconds since midnight). |
| `evaluationWindowStartClock` | string | Same as `HH:MM:SS`. |
| `evaluationWindowEndExclusiveSeconds` | int | Half-open horizon end (exclusive). |
| `evaluationHorizonLastIncludedSecondClock` | string | Clock for the last second included in the violation integral. |
| `depotInventoryViolationPenaltyCoefficient` | int | Multiplier for warehouse violation seconds. |
| `totalWheelchairViolationSecondsBelowZero` | long | Total violation-integral seconds (all depots). |
| `depotPenaltyTerm` | long | `totalWheelchairViolationSecondsBelowZero × coefficient`. |
| `unweightedTardinessSumSeconds` | long | Sum of unweighted tardiness on working schedules (non-dummy). |

DTO: [SolutionObjectiveFunctionDto](../src/main/java/com/paper2/dto/solution/SolutionObjectiveFunctionDto.java).

## Object `depotInventory`

| Field | Type | Description |
|--------|------|-------------|
| `depots` | array | One entry per depot: id, location, inventory, violation seconds, optional timeline summary. |

DTO: [SolutionDepotInventoryDto](../src/main/java/com/paper2/dto/solution/SolutionDepotInventoryDto.java).

## Object `finalSchedules[]`

| Field | Type | Description |
|--------|------|-------------|
| `porterId` | int | Porter identifier (0-based, aligned with internal solution lists). |
| `scheduleSummary` | object | Route aggregates; see below. |
| `patients` | array | **Real patients only** (dummy omitted), ordered by service sequence. |

### `scheduleSummary.patient`

Counts derived from real patients on the final route:

| Field | Type | Description |
|--------|------|-------------|
| `transportedPatientCount` | int | Number of real patients transported on this route. |
| `hospitalBedCount` | int | How many of those requests use mode `"Hospital Bed"` (case-insensitive match to the JSON label). |
| `patientsByPriority` | object | Map priority → count (string keys in JSON, e.g. `"1"`, `"2"`). |

### `scheduleSummary.time`

Aggregated route times (duration or clock strings `HH:MM:SS`):

| Field | Description |
|--------|-------------|
| `start` | Route start in the summary (dummy or default shift start, e.g. 08:00:00). |
| `end` | End of the porter’s **work shift** in the solution (not necessarily the last patient’s `end` if the model fixes shift end). |
| `duration` | Active route duration used in metrics (`lastRealEnd - scheduleStart`), duration format. |
| `transportTime` | Sum of transport durations for real patients. |
| `travelTime` | Sum of travel times to each real patient’s pickup origin. |
| `idleTime` | `duration - transportTime - travelTime` (may appear signed in signed-duration format if applicable). |
| `totalTimeWorkedSeconds` | Long: final simulator clock (seconds) minus route start seconds (same basis as `start`: dummy start or default shift). |
| `totalTimeWorkedClock` | Same span as `HH:MM:SS` (mirrors `totalTimeWorkedSeconds`). |

These values align with [PorterScheduleRouteMetrics](../src/main/java/com/paper2/metrics/PorterScheduleRouteMetrics.java) for travel/transport/idle/duration; `totalTimeWorked*` uses the final [Solution](../src/main/java/com/paper2/domain/Solution.java) simulator clock.

## Object `patients[]` (within each porter)

Each element is a snapshot of a real request in service order:

| Field | Type | Description |
|--------|------|-------------|
| `id` | int | Patient id (from input). |
| `sequence` | int | 1-based position on the route (after removing dummy). |
| `time` | object | See table below. |
| `transportMode` | object | `type`, `keepsTheEquipmentAtDestination`, `hasTheEquipmentAtOrigin` (mirror of input / policy). |
| `priority` | object | `priority`, `weight`. |
| `location` | object | `origin`, `destination` (node indices). |

### `patients[].time`

**All subfields are `HH:MM:SS` strings** (clock or duration depending on meaning):

| Field | Typical meaning |
|--------|-----------------|
| `timeAsked` | Request instant. |
| `travelTime` | Duration traveling to the service origin. |
| `start` | Service start. |
| `transportTime` | Patient transport duration. |
| `end` | Service end. |
| `dueDate` | Due time. |
| `lateness` | Tardiness (duration `max(0, end - due)` or domain equivalent). |
| `responseTime` | Duration `start - timeAsked` (time until the porter starts after the request). |

## Differences from input

- Input uses **integer seconds** in `time`; the solution uses **strings** for human readability and C++-style reporting compatibility.
- Dummy patient `id: 0` does **not** appear in `patients[]`; it only affects `scheduleSummary.time.start` / route metrics indirectly.

## Minimal example (structure)

```json
{
  "simulatorClock": "23:59:59",
  "objectiveFunction": {
    "objectiveValue": 20447,
    "evaluationWindowStartSeconds": 28800,
    "evaluationWindowStartClock": "08:00:00",
    "evaluationWindowEndExclusiveSeconds": 32400,
    "evaluationHorizonLastIncludedSecondClock": "08:59:59",
    "depotInventoryViolationPenaltyCoefficient": 100000,
    "totalWheelchairViolationSecondsBelowZero": 0,
    "depotPenaltyTerm": 0,
    "unweightedTardinessSumSeconds": 1200
  },
  "finalSchedules": [
    {
      "porterId": 0,
      "scheduleSummary": {
        "patient": {
          "transportedPatientCount": 1,
          "hospitalBedCount": 0,
          "patientsByPriority": { "3": 1 }
        },
        "time": {
          "start": "08:00:00",
          "end": "08:30:00",
          "duration": "00:30:00",
          "transportTime": "00:05:00",
          "travelTime": "00:10:00",
          "idleTime": "00:15:00",
          "totalTimeWorkedSeconds": 3600,
          "totalTimeWorkedClock": "01:00:00"
        }
      },
      "patients": [
        {
          "id": 4,
          "sequence": 1,
          "time": {
            "timeAsked": "08:00:00",
            "travelTime": "00:05:00",
            "start": "08:05:00",
            "transportTime": "00:06:00",
            "end": "08:11:00",
            "dueDate": "08:21:00",
            "lateness": "00:00:00",
            "responseTime": "00:05:00"
          },
          "transportMode": {
            "type": "Wheelchair",
            "keepsTheEquipmentAtDestination": false,
            "hasTheEquipmentAtOrigin": false
          },
          "priority": { "priority": 3, "weight": 18 },
          "location": { "origin": 19, "destination": 22 }
        }
      ]
    }
  ],
  "depotInventory": {
    "depots": []
  }
}
```

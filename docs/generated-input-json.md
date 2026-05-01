# Input JSON file format (`generated_input.json`)

Overview of the JSON consumed by `ExperimentRunner` / `Simulator` via Jackson into `InputDto`. The [generated_input.json](../generated_input.json) file in the repository is a full example (large instance).

## Root document

| Field | Type | Description |
|--------|------|-------------|
| `name` | string | Instance identifier (usually the source file name, e.g. `800N1SC3DEP40RT80CH1REP.txt`). |
| `amountOfPorters` | int | Number of porters in the model. |
| `patients` | array | Transport requests; see below. |
| `depots` | array | Wheelchair depots (location and initial inventory). |
| `timeMatrix` | object | Travel time matrix between nodes; see below. |

Java mapping: [InputDto](../src/main/java/com/paper2/dto/InputDto.java).

## Object `patients[]`

Each element maps to [PatientDto](../src/main/java/com/paper2/dto/PatientDto.java).

| Field | Type | Description |
|--------|------|-------------|
| `id` | int | Request identifier. Value `0` with priority `0` and zero times is used as a **dummy patient** (route placeholder); all others are real requests. |
| `priority` | object | `priority` (level) and `weight` (weight in the objective / weighted tardiness). |
| `location` | object | `origin` and `destination`: node indices in the graph (0-based, aligned with `timeMatrix.graph`). |
| `time` | object | Times in **seconds since midnight** (integer). See [TimeDto](../src/main/java/com/paper2/dto/TimeDto.java). |
| `time.timeAsked` | int | Request time. |
| `time.dueDate` | int | Due date for tardiness. |
| `transportMode` | object | Transport mode and equipment flags. See [TransportModeDto](../src/main/java/com/paper2/dto/TransportModeDto.java). |
| `transportMode.type` | string | Exact domain text, e.g. `"Wheelchair"` or `"Hospital Bed"` (see [TransportModeKind](../src/main/java/com/paper2/domain/TransportModeKind.java)). |
| `transportMode.keepsTheEquipmentAtDestination` | boolean | Equipment stays at destination. |
| `transportMode.hasTheEquipmentAtOrigin` | boolean | Equipment already at origin. |

### Time units

All numeric instants in `time` are **seconds since 00:00:00**. Example: `28800` = 08:00:00.

## Object `depots[]`

Each depot has `id`, `location` (`origin`/`destination` at the same node index), and `initialWheelchairInventory` (wheelchairs available at the start of the evaluation window for the soft inventory model). Maps to [DepotDto](../src/main/java/com/paper2/dto/DepotDto.java).

## Object `timeMatrix`

| Field | Type | Description |
|--------|------|-------------|
| `graph` | array of arrays | Square matrix `graph[i][j]`: travel time **in seconds** from node `i` to node `j`. Values are non-negative integers; typical instances use a symmetric matrix (equal outbound/inbound times). |

Mapping: [TimeTravelDto](../src/main/java/com/paper2/dto/TimeTravelDto.java).

## Minimal example (structure)

```json
{
  "name": "instance.txt",
  "amountOfPorters": 2,
  "patients": [
    {
      "id": 0,
      "priority": { "priority": 0, "weight": 0 },
      "location": { "origin": 0, "destination": 0 },
      "time": { "timeAsked": 0, "dueDate": 0 },
      "transportMode": {
        "type": "Hospital Bed",
        "keepsTheEquipmentAtDestination": true,
        "hasTheEquipmentAtOrigin": true
      }
    },
    {
      "id": 1,
      "priority": { "priority": 3, "weight": 18 },
      "location": { "origin": 14, "destination": 23 },
      "time": { "timeAsked": 28800, "dueDate": 30000 },
      "transportMode": {
        "type": "Wheelchair",
        "keepsTheEquipmentAtDestination": false,
        "hasTheEquipmentAtOrigin": false
      }
    }
  ],
  "depots": [],
  "timeMatrix": { "graph": [[0, 60], [60, 0]] }
}
```

# Input JSON format (instance / `InputDto`)

An **input** file describes one problem instance: patients, porters (stretchers), depots with initial wheelchair stock, and travel times between graph nodes. The Java simulation loads it via [`InputDto`](../src/main/java/com/paper2/dto/InputDto.java).

**Example:** [`files/examples/example_input.json`](../files/examples/example_input.json) (large file; same structure as production inputs under `files/input/`).

## Top-level fields

| Field | Type | Description |
|--------|------|-------------|
| `name` | string | Label for the instance (often the original scenario file name, e.g. `800N1SC3DEP40RT80CH1REP.txt`). |
| `amountOfPorters` | int | Number of porters (“stretchers” in legacy text files); used to size the solution schedules. |
| `patients` | array | One object per logical row (including id `0` as a **dummy** patient used internally; id `1..amountOfPorters` may use fixed asked/due rules from the generator). |
| `depots` | array | Depot id, grid `location`, and `initialWheelchairInventory` at the start of the horizon. |
| `timeMatrix` | object | Contains `graph`: a square matrix of **integer travel times** (seconds) between node indices referenced by patient `origin` / `destination` and depot locations. |

Optional **`metadata`** may appear on inputs produced by tooling (scenario stem, depot count, etc.); the core simulator consumes the fields above.

## Each `patients[]` entry

| Field | Description |
|--------|-------------|
| `id` | Patient index (`0` = dummy). |
| `priority.priority` / `priority.weight` | Priority class and weight for weighted tardiness in metrics. |
| `location.origin` / `location.destination` | Node ids in the distance matrix (dummy often `(0,0)`). |
| `time.timeAsked` / `time.dueDate` | Seconds since midnight (or consistent epoch); due date drives tardiness. |
| `transportMode` | Mode label and flags (`Hospital Bed` vs `Wheelchair`, equipment kept at destination / present at origin). |

## Depots

Each depot has `id`, `location` (`origin`/`destination` as node ids—often equal for a depot node), and `initialWheelchairInventory` for the soft inventory model.

## `timeMatrix.graph`

Symmetric (typically) matrix: `graph[i][j]` is travel time from node `i` to node `j`. Dimensions must cover all referenced locations.

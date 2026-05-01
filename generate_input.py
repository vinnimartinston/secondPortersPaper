#!/usr/bin/env python3
"""
Lê arquivos de instância no formato do exemplo (*REP.txt) e gera um JSON
compatível com os DTOs Java (PatientDto, LocationDto, TimeDto, TransportModeDto,
TimeTravelDto).

Campos extraídos (demais seções do arquivo são ignoradas):
  - (índice da linha) -> id (0 .. n-1; 0 = dummy)
  - PRIORITY_JOB      -> patients[].priority.priority (classe 0..k)
  - PRIORITY_WEIGHT   -> peso por classe; patients[].priority.weight =
                         PRIORITY_WEIGHT[PRIORITY_JOB]
  - TIME_ASKED        -> patients[].time.timeAsked (exceto ids 1..STRETCHERS: fixo 28800)
  - (ids 1..STRETCHERS) dueDate = timeAsked + folga por PRIORITY_JOB: 1->+60s, 2->+600s,
                        3->+1000s, 4->+1800s; outras classes mantêm DUE_DATE do ficheiro
  - ORIGIN            -> patients[].location.origin (dummy id 0: forçado 0,0)
  - DESTINATION       -> patients[].location.destination (dummy id 0: forçado 0,0)
  - DUE_DATE          -> patients[].time.dueDate
  - TRANSPORT_MODE    -> patients[].transportMode (mapeamento 0–4)
  - DISTANCE_MATRIX   -> timeMatrix.graph (lista de listas de inteiros)
  - (nome do arquivo) -> name (ex.: ``800N1SC3DEP40RT80CH1REP.txt``)
  - STRETCHERS        -> amountOfPorters (inteiro na linha seguinte ao cabeçalho)

Depósitos (fixos no gerador, antes de ``timeMatrix``):

  - ``depots``: lista com um depósito id 1, localização (18, 18), 800 cadeiras.

Por convenção da instância, a linha 0 corresponde ao paciente ``id: 0`` (dummy no
arquivo-fonte); o JSON não inclui campo ``dummy``.

O número de linhas vem do tamanho das listas (ex.: 801), não do cabeçalho
PATIENTS — PATIENTS pode ser igual ao total ou total−1 (só reais, sem contar essa linha).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

# Cabeçalhos conhecidos no formato do exemplo (ordem não importa para o parser).
SECTION_HEADERS: set[str] = {
    "INDEX_PROBLEM",
    "PATIENTS",
    "STRETCHERS",
    "NUM_ROOM",
    "NUM_DEPOT",
    "NUM_PRIORITY",
    "PRIORITY_LIST",
    "PRIORITY_WEIGHT",
    "PRIORITY_JOB",
    "TIME_ASKED",
    "ORIGIN",
    "DESTINATION",
    "DUE_DATE",
    "TRANSPORT_MODE",
    "PATIENT_WEIGHT",
    "DISTANCE_MATRIX",
    "DEPOTS_DISTANCE",
    "LOCATION_TURN_MATRIX",
    "DEPOTS_TO_LOCAL_TURN_MATRIX",
    "LOCAL_TO_DEPOT_TURN_MATRIX",
}

# Alinhado a DepotDto / LocationDto (valores fixos por enquanto).
HARDCODED_DEPOTS: list[dict[str, Any]] = [
    {
        "id": 1,
        "location": {"origin": 18, "destination": 18},
        "initialWheelchairInventory": 100,
    },
    {
        "id": 2,
        "location": {"origin": 22, "destination": 22},
        "initialWheelchairInventory": 100,
    },
    {
        "id": 3,
        "location": {"origin": 23, "destination": 23},
        "initialWheelchairInventory": 100,
    }
]


def parse_ints_line(line: str) -> list[int]:
    """Separa inteiros por tab ou espaço; ignora tokens vazios."""
    raw = line.strip().replace(" ", "\t")
    parts = [p for p in raw.split("\t") if p != ""]
    return [int(p) for p in parts]


def transport_mode_dto(code: int) -> dict[str, Any]:
    mapping: dict[int, tuple[str, bool, bool]] = {
        0: ("Hospital Bed", True, True),
        1: ("Wheelchair", False, False),
        2: ("Wheelchair", True, False),
        3: ("Wheelchair", False, True),
        4: ("Wheelchair", True, True),
    }
    if code not in mapping:
        raise ValueError(f"TRANSPORT_MODE inválido: {code} (esperado 0–4)")
    t, keep_dest, has_origin = mapping[code]
    return {
        "type": t,
        "keepsTheEquipmentAtDestination": keep_dest,
        "hasTheEquipmentAtOrigin": has_origin,
    }


def parse_instance_metadata(instance_name: str) -> dict[str, Any]:
    """Extrai metadados do cenário a partir do nome da instância (sem extensão)."""
    match = re.fullmatch(
        r"(?P<amountOfPatients>\d+)N(?P<profile>\d+)SC(?P<amountOfDepots>\d+)DEP"
        r"(?P<roundTripsPercentage>\d+)RT(?P<wheelchairChangesPercentage>\d+)CH\d+REP",
        instance_name,
    )
    if match is None:
        raise ValueError(
            "Nome de instância inválido para metadata: "
            f"{instance_name!r} (esperado padrão <patients>N<profile>SC<depots>DEP<rt>RT<wc>CHxREP)"
        )
    return {
        "amountOfPatients": int(match.group("amountOfPatients")),
        "profile": int(match.group("profile")),
        "amountOfDepots": int(match.group("amountOfDepots")),
        "roundTripsPercentage": int(match.group("roundTripsPercentage")),
        "wheelchairChangesPercentage": int(match.group("wheelchairChangesPercentage")),
        "instanceName": instance_name,
    }


def parse_instance_file(path: Path) -> dict[str, Any]:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = [ln.rstrip("\n\r") for ln in text.splitlines()]

    raw: dict[str, Any] = {}
    i = 0
    n = len(lines)

    while i < n:
        line = lines[i].strip()
        if line == "":
            i += 1
            continue

        if line not in SECTION_HEADERS:
            # Linha solta antes de qualquer cabeçalho conhecido: pular
            i += 1
            continue

        key = line
        i += 1

        if key == "DISTANCE_MATRIX":
            rows: list[list[int]] = []
            while i < n:
                nxt = lines[i].strip()
                if nxt == "":
                    i += 1
                    continue
                if nxt in SECTION_HEADERS:
                    break
                rows.append(parse_ints_line(lines[i]))
                i += 1
            raw[key] = rows
            continue

        # Valor em uma única linha (número único ou lista tabular)
        while i < n and lines[i].strip() == "":
            i += 1
        if i >= n:
            raw[key] = None
            break

        data_line = lines[i]
        i += 1
        ints = parse_ints_line(data_line)
        raw[key] = ints[0] if len(ints) == 1 else ints

    # Montagem do JSON de saída — contagem = tamanho das listas (ex.: 801; id 0 = linha dummy no .txt)
    declared_n: int | None = None
    if "PATIENTS" in raw and raw["PATIENTS"] is not None:
        declared_n = int(raw["PATIENTS"])

    required_lists = (
        "PRIORITY_JOB",
        "TIME_ASKED",
        "ORIGIN",
        "DESTINATION",
        "DUE_DATE",
        "TRANSPORT_MODE",
    )
    for k in required_lists:
        if k not in raw or not isinstance(raw[k], list):
            raise ValueError(f"Seção ausente ou formato inválido: {k}")

    lists: dict[str, list[int]] = {k: raw[k] for k in required_lists}  # type: ignore[assignment]
    lengths = {k: len(v) for k, v in lists.items()}
    if len(set(lengths.values())) != 1:
        raise ValueError(f"Tamanhos inconsistentes entre listas de pacientes: {lengths}")

    list_len = lengths["PRIORITY_JOB"]

    if declared_n is not None and declared_n not in (list_len, list_len - 1):
        print(
            f"Aviso: PATIENTS={declared_n}; listas têm {list_len} entradas. "
            f"Usando todas as {list_len} linhas (posição 0 = linha dummy no arquivo).",
            file=sys.stderr,
        )

    if "PRIORITY_WEIGHT" not in raw or not isinstance(raw["PRIORITY_WEIGHT"], list):
        raise ValueError("Seção PRIORITY_WEIGHT ausente ou inválida (esperada lista de pesos por classe)")
    priority_weights: list[int] = [int(x) for x in raw["PRIORITY_WEIGHT"]]

    if "STRETCHERS" not in raw or raw["STRETCHERS"] is None:
        raise ValueError("Seção STRETCHERS ausente (necessária para amountOfPorters e timeAsked)")
    try:
        amount_of_porters = int(raw["STRETCHERS"])
    except (TypeError, ValueError) as e:
        raise ValueError(
            f"STRETCHERS inválido: {raw['STRETCHERS']!r} (esperado um inteiro na linha abaixo)"
        ) from e

    # Pacientes com id de 1 até STRETCHERS (inclusive): timeAsked fixo em 28800 (8h em segundos).
    TIME_ASKED_FIXED_STRETCHERS = 28800
    # Para esses mesmos pacientes, dueDate = timeAsked + folga (segundos) por classe de prioridade (1..4 no .txt).
    DUE_DATE_SLACK_SECONDS_BY_PRIORITY: dict[int, int] = {
        1: 60,
        2: 600,
        3: 1000,
        4: 1800,
    }

    def patient_record(idx: int) -> dict[str, Any]:
        tm_code = lists["TRANSPORT_MODE"][idx]
        job = int(lists["PRIORITY_JOB"][idx])
        if job < 0 or job >= len(priority_weights):
            raise ValueError(
                f"PRIORITY_JOB={job} fora do intervalo para PRIORITY_WEIGHT (len={len(priority_weights)})"
            )
        weight = priority_weights[job]
        if 1 <= idx <= amount_of_porters:
            time_asked = TIME_ASKED_FIXED_STRETCHERS
            slack = DUE_DATE_SLACK_SECONDS_BY_PRIORITY.get(job)
            due_date = time_asked + slack if slack is not None else lists["DUE_DATE"][idx]
        else:
            time_asked = lists["TIME_ASKED"][idx]
            due_date = lists["DUE_DATE"][idx]
        origin = 0 if idx == 0 else lists["ORIGIN"][idx]
        destination = 0 if idx == 0 else lists["DESTINATION"][idx]
        return {
            "id": idx,
            "priority": {"priority": job, "weight": weight},
            "location": {"origin": origin, "destination": destination},
            "time": {"timeAsked": time_asked, "dueDate": due_date},
            "transportMode": transport_mode_dto(tm_code),
        }

    if list_len < 1:
        raise ValueError("Listas de pacientes vazias")
    if list_len < 2:
        raise ValueError(
            "É necessário pelo menos 2 linhas: posição 0 (dummy no .txt) e pelo menos um paciente real"
        )

    patients_out: list[dict[str, Any]] = [patient_record(i) for i in range(list_len)]

    if "DISTANCE_MATRIX" not in raw:
        raise ValueError("Seção DISTANCE_MATRIX ausente")

    graph = raw["DISTANCE_MATRIX"]
    if not isinstance(graph, list) or not graph:
        raise ValueError("DISTANCE_MATRIX vazia ou inválida")

    row_lens = {len(r) for r in graph}
    if len(row_lens) != 1:
        raise ValueError(f"Linhas da DISTANCE_MATRIX com larguras diferentes: {row_lens}")
    if len(graph) != next(iter(row_lens)):
        raise ValueError("DISTANCE_MATRIX não é quadrada")

    # Nome da instância = nome do arquivo lido (inclui extensão, ex.: .txt)
    instance_name = path.name
    metadata = parse_instance_metadata(path.stem)

    return {
        "name": instance_name,
        "metadata": metadata,
        "amountOfPorters": amount_of_porters,
        "patients": patients_out,
        "depots": HARDCODED_DEPOTS,
        "timeMatrix": {"graph": graph},
    }


def main() -> int:
    p = argparse.ArgumentParser(
        description="Converte arquivo *REP.txt em JSON para os DTOs do projeto."
    )
    p.add_argument(
        "input",
        type=Path,
        nargs="?",
        default=Path("800N1SC3DEP40RT80CH1REP.txt"),
        help="Caminho do arquivo de instância (padrão: 800N1SC3DEP40RT80CH1REP.txt na raiz)",
    )
    p.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Arquivo JSON de saída (se omitido, imprime no stdout)",
    )
    p.add_argument(
        "--indent",
        type=int,
        default=2,
        help="Indentação JSON (use 0 para compacto)",
    )
    args = p.parse_args()

    if not args.input.is_file():
        print(f"Arquivo não encontrado: {args.input}", file=sys.stderr)
        return 1

    try:
        payload = parse_instance_file(args.input)
    except Exception as e:
        print(f"Erro ao processar: {e}", file=sys.stderr)
        return 1

    indent = None if args.indent == 0 else args.indent
    text = json.dumps(payload, ensure_ascii=False, indent=indent)

    if args.output:
        args.output.write_text(text + "\n", encoding="utf-8")
    else:
        print(text)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

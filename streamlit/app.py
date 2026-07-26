"""
Streamlit dashboard: Gantt of final schedules + depot wheelchair balance (from *_solution.json).
Run from repo root: streamlit run streamlit/app.py
"""

from typing import Callable
import json
from pathlib import Path

import numpy as np
import pandas as pd
import plotly.express as px
import plotly.graph_objects as go
import streamlit as st

REPO_ROOT = Path(__file__).resolve().parent.parent
OUTPUT_ROOT = REPO_ROOT / "files" / "output"


def hms_to_seconds(hms: str) -> int:
    if not hms or not isinstance(hms, str):
        return 0
    parts = hms.strip().split(":")
    if len(parts) != 3:
        return 0
    h, m, s = int(parts[0]), int(parts[1]), int(parts[2])
    return h * 3600 + m * 60 + s


def seconds_to_hms(sec: int | float) -> str:
    sec = max(0, int(sec))
    h, r = divmod(sec, 3600)
    m, s = divmod(r, 60)
    return f"{h:02d}:{m:02d}:{s:02d}"


@st.cache_data(show_spinner=False)
def parse_solution_json(raw: str) -> dict:
    return json.loads(raw)


def list_output_subfolders() -> list[Path]:
    if not OUTPUT_ROOT.is_dir():
        return []
    return sorted([p for p in OUTPUT_ROOT.iterdir() if p.is_dir()], key=lambda p: p.name.lower())


def list_solution_files(folder: Path) -> list[Path]:
    if not folder.is_dir():
        return []
    return sorted(folder.glob("*_solution.json"), key=lambda p: p.name.lower())


def list_metrics_files(folder: Path) -> list[Path]:
    if not folder.is_dir():
        return []
    return sorted(folder.glob("*_metrics.json"), key=lambda p: p.name.lower())


def solution_stem_for_metrics(solution_filename: str) -> str:
    """800N1SC3DEP20RT40CH1REP_solution.json -> 800N1SC3DEP20RT40CH1REP_metrics.json base."""
    if solution_filename.endswith("_solution.json"):
        return solution_filename[: -len("_solution.json")]
    return Path(solution_filename).stem.replace("_solution", "")


def metrics_filename_for_solution(solution_filename: str) -> str:
    return f"{solution_stem_for_metrics(solution_filename)}_metrics.json"


@st.cache_data(show_spinner=False)
def load_metrics_json_cached(raw: str) -> dict:
    return json.loads(raw)


def minutes_to_hms(minutes: float | None) -> str:
    if minutes is None or (isinstance(minutes, float) and np.isnan(minutes)):
        return "—"
    return seconds_to_hms(int(round(float(minutes) * 60)))


def format_number(value: float | None, *, decimals: int = 0) -> str:
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return "—"
    if decimals <= 0:
        return str(int(round(float(value))))
    return f"{float(value):.{decimals}f}"


def format_percent(value: float | None) -> str:
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return "—"
    return f"{float(value):.2%}"


def format_minutes(value: float | None) -> str:
    if value is None or (isinstance(value, float) and np.isnan(value)):
        return "—"
    return f"{float(value):.2f} min"


def _summary(data: dict) -> dict:
    return data.get("summary") or {}


def _objective(data: dict) -> dict:
    return _summary(data).get("objectiveFunction") or {}


def _schedule_agg(data: dict) -> dict:
    return data.get("scheduleTimeAggregates") or {}


def _porter_effort(data: dict) -> dict:
    return data.get("porterEffort") or {}


def _wheelchair_depot(data: dict) -> dict:
    return data.get("wheelchairDepot") or {}


def _peak_wheelchairs(data: dict) -> float | None:
    wc = _wheelchair_depot(data)
    value = wc.get("maxWheelchairsUsedAtSameTime", wc.get("wcMaxChairsInUse"))
    return None if value is None else float(value)


def _peak_wheelchair_share(data: dict) -> float | None:
    wc = _wheelchair_depot(data)
    value = wc.get("timeShareUsingMaxWheelchairs", wc.get("wcMaxUsageFraction"))
    return None if value is None else float(value)


def _depot_min_balance(data: dict, index: int) -> float | None:
    balances = _wheelchair_depot(data).get("depotMinBalance") or []
    if index >= len(balances):
        return None
    value = balances[index]
    return None if value is None else float(value)


def _mean_duration_active_seconds(data: dict) -> float | None:
    value = _schedule_agg(data).get("meanDurationActiveSeconds")
    return None if value is None else float(value)


def _fleet_mean_seconds(data: dict, share_key: str) -> float | None:
    sta = _schedule_agg(data)
    mean_active = sta.get("meanDurationActiveSeconds")
    share = sta.get(share_key)
    if mean_active is None or share is None:
        return None
    return float(mean_active) * float(share)


def _min_duration_active_seconds(data: dict) -> float | None:
    value = _schedule_agg(data).get("minDurationActiveSeconds")
    if value is None or int(value) < 0:
        return None
    return float(value)


def load_metrics_documents(folder: Path, *, per_solution: bool, solution_filename: str | None) -> list[dict]:
    if per_solution and solution_filename:
        mp = folder / metrics_filename_for_solution(solution_filename)
        if not mp.is_file():
            return []
        try:
            return [load_metrics_json_cached(mp.read_text(encoding="utf-8"))]
        except (json.JSONDecodeError, OSError):
            return []
    docs: list[dict] = []
    for mp in list_metrics_files(folder):
        try:
            docs.append(load_metrics_json_cached(mp.read_text(encoding="utf-8")))
        except (json.JSONDecodeError, OSError):
            continue
    return docs


def aggregate_numeric(values: list[float | None]) -> float | None:
    nums = [
        float(v)
        for v in values
        if v is not None and not (isinstance(v, float) and np.isnan(v))
    ]
    if not nums:
        return None
    return float(np.mean(nums))


def aggregate_cell_values(
    docs: list[dict],
    extractor: Callable[[dict], float | None],
) -> float | None:
    if not docs:
        return None
    if len(docs) == 1:
        return extractor(docs[0])
    return aggregate_numeric([extractor(doc) for doc in docs])


ComparisonRowSpec = tuple[str, str, Callable[[dict], float | None], Callable[[float | None], str]]


def comparison_row_specs() -> list[ComparisonRowSpec]:
    return [
        ("section", "Main Metrics", lambda _d: None, lambda _v: ""),
        (
            "metric",
            "Schedule Duration",
            lambda d: _summary(d).get("scheduleDurationSeconds"),
            lambda v: seconds_to_hms(v) if v is not None else "—",
        ),
        ("subsection", "Objective Function", lambda _d: None, lambda _v: ""),
        (
            "metric",
            "Value",
            lambda d: _objective(d).get("value"),
            lambda v: format_number(v),
        ),
        (
            "metric",
            "Tardiness Term",
            lambda d: (_objective(d).get("tardinessTerm") or {}).get("value"),
            lambda v: format_number(v),
        ),
        (
            "metric",
            "Depot Penalty Term",
            lambda d: (_objective(d).get("depotPenaltyTerm") or {}).get("value"),
            lambda v: format_number(v),
        ),
        ("section", "Patient Time Metrics", lambda _d: None, lambda _v: ""),
        (
            "metric",
            "Tardiness (Mean)",
            lambda d: (d.get("tardiness") or {}).get("meanUnweightedTardinessMinutesAllPatients"),
            minutes_to_hms,
        ),
        (
            "metric",
            "Response Time (Mean)",
            lambda d: (d.get("response") or {}).get("meanResponseMinutesAllPatients"),
            minutes_to_hms,
        ),
        ("section", "Porter Time Metrics", lambda _d: None, lambda _v: ""),
        (
            "metric",
            "Duration (Max)",
            lambda d: _schedule_agg(d).get("maxDurationSeconds"),
            lambda v: seconds_to_hms(v) if v is not None else "—",
        ),
        (
            "metric",
            "Duration (Min)",
            _min_duration_active_seconds,
            lambda v: seconds_to_hms(v) if v is not None else "—",
        ),
        (
            "metric",
            "Active (Mean)",
            _mean_duration_active_seconds,
            lambda v: seconds_to_hms(v) if v is not None else "—",
        ),
        (
            "metric",
            "Travel (Mean)",
            lambda d: _fleet_mean_seconds(d, "fleetTravelShare"),
            lambda v: seconds_to_hms(v) if v is not None else "—",
        ),
        (
            "metric",
            "Transport (Mean)",
            lambda d: _fleet_mean_seconds(d, "fleetTransportShare"),
            lambda v: seconds_to_hms(v) if v is not None else "—",
        ),
        (
            "metric",
            "Idle (Mean)",
            lambda d: _fleet_mean_seconds(d, "fleetIdleShare"),
            lambda v: seconds_to_hms(v) if v is not None else "—",
        ),
        ("section", "Porter Effort Metrics", lambda _d: None, lambda _v: ""),
        (
            "metric",
            "Beds (Mean)",
            lambda d: _porter_effort(d).get("effortRateMean"),
            format_percent,
        ),
        (
            "metric",
            "Beds (Max)",
            lambda d: _porter_effort(d).get("maxBedShareAcrossPorters"),
            format_percent,
        ),
        (
            "metric",
            "Beds (Min)",
            lambda d: _porter_effort(d).get("minBedShareAcrossPorters"),
            format_percent,
        ),
        ("section", "Depot inventory", lambda _d: None, lambda _v: ""),
        (
            "metric",
            "Peak wheelchair demand",
            _peak_wheelchairs,
            lambda v: format_number(v),
        ),
        (
            "metric",
            "Time at peak demand (share of horizon)",
            _peak_wheelchair_share,
            format_percent,
        ),
        (
            "metric",
            "Min Balance Depot 1",
            lambda d: _depot_min_balance(d, 0),
            lambda v: format_number(v, decimals=2),
        ),
        (
            "metric",
            "Min Balance Depot 2",
            lambda d: _depot_min_balance(d, 1),
            lambda v: format_number(v, decimals=2),
        ),
        (
            "metric",
            "Min Balance Depot 3",
            lambda d: _depot_min_balance(d, 2),
            lambda v: format_number(v, decimals=2),
        ),
        (
            "metric",
            "Time spent Walking to Depot (mean)",
            lambda d: _wheelchair_depot(d).get("avgWalkingToDepotMinutes"),
            format_minutes,
        ),
    ]


def build_comparison_display_table(
    folder_names: list[str],
    *,
    per_solution: bool,
    solution_filename: str | None,
) -> tuple[pd.DataFrame, set[int]]:
    specs = comparison_row_specs()
    bold_rows: set[int] = set()
    table_rows: list[list[str]] = []

    for kind, label, extractor, formatter in specs:
        row_idx = len(table_rows)
        if kind in {"section", "subsection"}:
            bold_rows.add(row_idx)
            table_rows.append([label] + [""] * len(folder_names))
            continue

        row = [label]
        for folder_name in folder_names:
            docs = load_metrics_documents(
                OUTPUT_ROOT / folder_name,
                per_solution=per_solution,
                solution_filename=solution_filename,
            )
            if not docs:
                row.append("—")
                continue
            if per_solution:
                doc = docs[0]
                sta = _schedule_agg(doc)
                if label == "Duration (Max)":
                    cell = sta.get("maxDurationClock") or seconds_to_hms(sta.get("maxDurationSeconds"))
                elif label == "Duration (Min)":
                    cell = sta.get("minDurationActiveClock") or seconds_to_hms(_min_duration_active_seconds(doc))
                else:
                    cell = formatter(extractor(doc))
            else:
                cell = formatter(aggregate_cell_values(docs, extractor))
            row.append(cell if cell not in (None, "") else "—")
        table_rows.append(row)

    columns = ["Metric"] + folder_names
    return pd.DataFrame(table_rows, columns=columns), bold_rows


def style_comparison_table(df: pd.DataFrame, bold_rows: set[int]):
    def _row_style(row: pd.Series):
        if row.name in bold_rows:
            return ["font-weight: bold"] * len(row)
        return [""] * len(row)

    return df.style.apply(_row_style, axis=1)


def all_schedule_row_labels(final_schedules: list) -> list[str]:
    """One Y-axis label per schedule in order of appearance (all schedules, including empty)."""
    labels: list[str] = []
    seen: set[int] = set()
    for fs in final_schedules or []:
        pid = fs.get("porterId")
        if pid is None:
            continue
        pid = int(pid)
        if pid in seen:
            continue
        seen.add(pid)
        labels.append(f"Schedule · Porter {pid}")
    return labels


def build_gantt_df(final_schedules: list) -> pd.DataFrame:
    rows = []
    for fs in final_schedules or []:
        pid = fs.get("porterId", -1)
        schedule_row = f"Schedule · Porter {pid}"
        for p in fs.get("patients") or []:
            tid = p.get("id")
            seq = p.get("sequence", 0)
            t = p.get("time") or {}
            start = t.get("start")
            end = t.get("end")
            s0 = hms_to_seconds(str(start) if start is not None else "")
            s1 = hms_to_seconds(str(end) if end is not None else "")
            if s1 <= s0:
                continue
            rows.append(
                {
                    "porterId": pid,
                    "patientId": tid,
                    "sequence": seq,
                    "scheduleRow": schedule_row,
                    "start_sec": s0,
                    "end_sec": s1,
                    "duration_sec": s1 - s0,
                    "start_clock": start,
                    "end_clock": end,
                }
            )
    return pd.DataFrame(rows)


def fig_gantt(df: pd.DataFrame, y_categories: list[str]) -> go.Figure:
    if not y_categories:
        fig = go.Figure()
        fig.update_layout(title="No finalSchedules in JSON", height=240)
        return fig
    if df.empty:
        fig = go.Figure()
        x0 = 28800
        for label in y_categories:
            fig.add_trace(
                go.Scatter(
                    x=[x0],
                    y=[label],
                    mode="markers",
                    marker=dict(size=2, opacity=0),
                    showlegend=False,
                    hoverinfo="skip",
                )
            )
        fig.update_layout(
            title="Gantt — final schedules (no patient segments)",
            height=max(360, 56 * max(1, len(y_categories))),
        )
        fig.update_yaxes(categoryorder="array", categoryarray=y_categories, autorange="reversed")
        fig.update_xaxes(
            tickmode="array",
            tickvals=[x0],
            ticktext=[seconds_to_hms(x0)],
        )
        return fig

    df = df.sort_values(["porterId", "start_sec", "patientId"])
    occupied = set(df["scheduleRow"].astype(str).unique()) if not df.empty else set()
    x_pad = int(df["start_sec"].min()) if not df.empty else 28800
    qual = px.colors.qualitative.Plotly
    fig = go.Figure()
    for label in y_categories:
        if label not in occupied:
            fig.add_trace(
                go.Scatter(
                    x=[x_pad],
                    y=[label],
                    mode="markers",
                    marker=dict(size=2, opacity=0),
                    showlegend=False,
                    hoverinfo="skip",
                )
            )
    for _, r in df.iterrows():
        pid = int(r["patientId"])
        mcol = qual[abs(pid) % len(qual)]
        row = r["scheduleRow"]
        dur = float(r["duration_sec"])
        fig.add_trace(
            go.Bar(
                name=str(pid),
                legendgroup=str(pid),
                showlegend=False,
                orientation="h",
                y=[row],
                x=[dur],
                base=[r["start_sec"]],
                marker_color=mcol,
                marker_line_width=0,
                text=[str(pid)],
                textposition="inside",
                insidetextanchor="middle",
                textfont=dict(size=12, color="white"),
                hovertemplate=(
                    f"Porter {r['porterId']} · patient {pid}<br>"
                    f"Start: {r['start_clock']} (s={r['start_sec']})<br>"
                    f"End: {r['end_clock']} (s={r['end_sec']})<extra></extra>"
                ),
            )
        )
    n_rows = len(y_categories)
    fig.update_layout(
        template="plotly_white",
        title="Gantt — one row per schedule (patient id on segment)",
        barmode="overlay",
        height=max(360, 56 * max(1, n_rows)),
        xaxis_title="Time",
        yaxis_title="",
        margin=dict(l=200, r=24, t=48, b=48),
    )
    fig.update_yaxes(categoryorder="array", categoryarray=y_categories, autorange="reversed")
    tick0 = int(df["start_sec"].min() // 1800) * 1800
    tick1 = int(df["end_sec"].max() // 1800 + 1) * 1800
    span = max(tick1 - tick0, 1)
    ticks = list(range(tick0, tick1 + 1, max(300, span // 12)))
    fig.update_xaxes(
        tickmode="array",
        tickvals=ticks,
        ticktext=[seconds_to_hms(t) for t in ticks],
    )
    return fig


def balance_steps_to_plot_xy(steps: list, window_end_exclusive: int) -> tuple[list[float], list[float]]:
    """Piecewise-constant curve for Plotly line_shape=hv."""
    if not steps:
        return [], []
    xs: list[float] = []
    ys: list[float] = []
    for i, s in enumerate(steps):
        t = float(s["timeSeconds"])
        b = float(s["balanceAfterStep"])
        t_next = float(steps[i + 1]["timeSeconds"]) if i + 1 < len(steps) else float(window_end_exclusive - 1)
        xs.extend([t, t_next])
        ys.extend([b, b])
    return xs, ys


def fig_depots(depot_inventory: dict | None, selected_depots: list[int]) -> go.Figure:
    fig = go.Figure()
    if not depot_inventory:
        fig.update_layout(title="No depotInventory in JSON", height=320)
        return fig
    if selected_depots is not None and len(selected_depots) == 0:
        fig.update_layout(
            title="Select at least one depot in the filter above",
            height=320,
        )
        return fig
    depots = depot_inventory.get("depots") or []
    palette = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b"]
    wi = 0
    all_x: list[float] = []
    for d in depots:
        did = d.get("depotId")
        if selected_depots and did not in selected_depots:
            continue
        fts = d.get("finalTimelineSummary") or {}
        steps = fts.get("balanceSteps") or []
        w_start = fts.get("windowStartSeconds", 0)
        w_end = fts.get("windowEndExclusiveSeconds", w_start + 1)
        xs, ys = balance_steps_to_plot_xy(steps, w_end)
        if not xs:
            continue
        color = palette[wi % len(palette)]
        wi += 1
        all_x.extend(xs)
        hms_labels = [seconds_to_hms(int(round(x))) for x in xs]
        fig.add_trace(
            go.Scatter(
                x=xs,
                y=ys,
                mode="lines",
                name=f"Depot {did}",
                line=dict(color=color, width=2),
                line_shape="hv",
                hovertemplate="Depot "
                + str(did)
                + "<br>Time: %{customdata}<br>Balance: %{y:.0f} chairs<extra></extra>",
                customdata=hms_labels,
            )
        )
    fig.add_hline(y=0, line_dash="dash", line_color="rgba(0,0,0,0.35)", opacity=0.7)
    fig.update_layout(
        template="plotly_white",
        title="Wheelchair inventory (final committed timeline)",
        xaxis_title="Time (HH:MM:SS)",
        yaxis_title="Balance",
        height=480,
        legend=dict(orientation="h", yanchor="bottom", y=1.02, xanchor="left", x=0),
        margin=dict(l=48, r=24, t=56, b=48),
    )
    if fig.data and all_x:
        xmin, xmax = min(all_x), max(all_x)
        pad = max(60.0, (xmax - xmin) * 0.02)
        t0 = int((xmin - pad) // 300) * 300
        t1 = int((xmax + pad) // 300 + 1) * 300
        span = max(t1 - t0, 300)
        step = max(300, span // 16)
        ticks = list(range(int(t0), int(t1) + 1, step))
        fig.update_xaxes(
            range=[xmin - pad, xmax + pad],
            tickmode="array",
            tickvals=ticks,
            ticktext=[seconds_to_hms(t) for t in ticks],
        )
    return fig


def main() -> None:
    st.set_page_config(
        page_title="Paper2 solution viewer",
        layout="wide",
        initial_sidebar_state="expanded",
    )
    st.title("Solution validation")

    subfolders = list_output_subfolders()
    if not subfolders:
        st.warning(f"No subfolders under `{OUTPUT_ROOT}`. Create one and add `*_solution.json` files.")
        return

    default_folder_name = "experiment" if any(p.name == "experiment" for p in subfolders) else subfolders[0].name
    folder_names = [p.name for p in subfolders]
    default_idx = folder_names.index(default_folder_name) if default_folder_name in folder_names else 0
    default_compare = (
        [default_folder_name]
        if default_folder_name in folder_names
        else ([folder_names[0]] if folder_names else [])
    )

    with st.sidebar:
        st.subheader("Comparison")
        compare_folders = st.multiselect(
            "Folders (table columns)",
            options=folder_names,
            default=default_compare,
            help="Each column is one output subfolder; values are averages over all *_metrics.json unless filtered below.",
        )
        comparison_table_mode = st.radio(
            "Comparison table mode",
            ["Folder average", "Selected solution"],
            index=0,
            horizontal=True,
            help="Folder average: mean over every *_metrics.json in each column folder. "
            "Selected solution: same metrics from the file matching the solution chosen below.",
        )

        st.divider()
        st.subheader("Gantt & charts")
        chosen_folder_name = st.selectbox(
            "Output folder",
            options=folder_names,
            index=default_idx,
            help=f"Subfolders of `{OUTPUT_ROOT}` — used for Gantt and depot plots.",
        )
        folder_path = OUTPUT_ROOT / chosen_folder_name
        solution_files = list_solution_files(folder_path)
        if not solution_files:
            st.warning(f"No `*_solution.json` in `{folder_path}`.")
            return

        names = [p.name for p in solution_files]
        default_file = "example_solution.json" if "example_solution.json" in names else names[0]
        file_idx = names.index(default_file) if default_file in names else 0
        chosen_file = st.selectbox(
            "Solution file",
            options=names,
            index=file_idx,
            help="Files matching *_solution.json in the selected folder",
        )
    p = folder_path / chosen_file
    if not p.is_file():
        st.error(f"File not found: {p}")
        return
    raw = p.read_text(encoding="utf-8")

    try:
        data = parse_solution_json(raw)
    except json.JSONDecodeError as e:
        st.error(f"Invalid JSON: {e}")
        return

    obj_fn = data.get("objectiveFunction") or {}
    obj_val = obj_fn.get("objectiveValue", data.get("objectiveValue", "?"))
    st.caption(
        f"Simulation clock: **{data.get('simulatorClock', '?')}** · "
        f"Objective: **{obj_val}**"
    )

    per_solution_table = comparison_table_mode == "Selected solution"
    if compare_folders:
        st.subheader("Metrics comparison")
        if per_solution_table:
            st.caption(
                f"Columns = folders selected for comparison. Values = **{chosen_file}** → "
                f"`{metrics_filename_for_solution(chosen_file)}` in each folder (mean across files is not used)."
            )
        else:
            st.caption(
                "Columns = folders selected for comparison. Each cell = **mean** over all "
                "`*_metrics.json` in that folder. Rows include all numeric **summary** fields "
                "(objective function, tardiness by priority, depot penalty) plus schedule/tardiness aggregates."
            )
        cmp_df, bold_rows = build_comparison_display_table(
            compare_folders,
            per_solution=per_solution_table,
            solution_filename=chosen_file if per_solution_table else None,
        )
        st.dataframe(
            style_comparison_table(cmp_df, bold_rows),
            use_container_width=False,
            hide_index=True,
        )
    else:
        st.info("Pick at least one folder under **Comparison** in the sidebar to show the metrics table.")

    final_schedules = data.get("finalSchedules") or []
    y_labels = all_schedule_row_labels(final_schedules)
    df = build_gantt_df(final_schedules)
    st.subheader("Gantt — final schedules")
    st.caption("All schedules are listed on the Y axis; rows without patients show no bars.")
    st.plotly_chart(fig_gantt(df, y_labels), use_container_width=True, theme=None)

    st.subheader("Depot inventory (final timeline)")
    dep_rows = (data.get("depotInventory") or {}).get("depots") or []
    all_ids = [d.get("depotId") for d in dep_rows if d.get("depotId") is not None]
    selected = st.multiselect(
        "Depots to show",
        options=sorted(all_ids),
        default=sorted(all_ids),
        help="Empty = no series; pick one or more depots on the same chart.",
    )
    st.plotly_chart(fig_depots(data.get("depotInventory"), selected), use_container_width=True, theme=None)

    st.caption(
        f"Evaluation window (inventory): {obj_fn.get('evaluationWindowStartClock', '?')} → "
        f"{obj_fn.get('evaluationHorizonLastIncludedSecondClock', '?')} "
        f"(seconds with negative balance, total: {obj_fn.get('totalWheelchairViolationSecondsBelowZero', '?')} s)"
    )


if __name__ == "__main__":
    main()

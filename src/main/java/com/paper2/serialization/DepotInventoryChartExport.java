package com.paper2.serialization;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.paper2.domain.Depot;
import com.paper2.domain.DomainConstants;
import com.paper2.domain.Solution;
import com.paper2.domain.TimeObject;
import com.paper2.domain.inventory.DepotBalanceTimeline;
import com.paper2.domain.inventory.SolutionInventoryState;
import com.paper2.dto.LocationDto;
import com.paper2.dto.solution.BalanceStepDto;
import com.paper2.dto.solution.chart.DepotInventoryChartDepotDto;
import com.paper2.dto.solution.chart.DepotInventoryHorizonSeriesDto;
import com.paper2.dto.solution.chart.SolutionDepotInventoryChartDto;

/**
 * Exports per-depot wheelchair inventory over time as JSON (horizons + balance steps) and as a JPEG chart image
 * built from {@link Solution#getInventoryState()}.
 */
public final class DepotInventoryChartExport {

    private static final int IMG_WIDTH = 960;
    private static final int MARGIN_LEFT = 72;
    private static final int MARGIN_RIGHT = 24;
    private static final int PLOT_HEIGHT = 160;
    private static final int TITLE_GAP = 28;
    private static final int BETWEEN_PLOTS = 16;
    private static final int DEPOT_HEADER = 22;

    private DepotInventoryChartExport() {}

    public static SolutionDepotInventoryChartDto toDto(Solution solution) {
        if (solution == null) {
            return new SolutionDepotInventoryChartDto();
        }
        int simSec = solution.getSimulatorClock() != null ? solution.getSimulatorClock().getSeconds() : 0;
        SolutionInventoryState inv = solution.getInventoryState();
        int anchor = inv != null ? inv.getIterationAnchorSeconds() : DomainConstants.SCHEDULE_START_TIME_SECONDS;

        SolutionDepotInventoryChartDto root = new SolutionDepotInventoryChartDto();
        root.setSimulatorClockSeconds(simSec);
        root.setSimulatorClock(formatClock(simSec));
        root.setIterationAnchorSeconds(anchor);
        root.setIterationAnchorClock(formatClock(anchor));
        root.setScheduleStartTimeSeconds(DomainConstants.SCHEDULE_START_TIME_SECONDS);
        root.setScheduleStartClock(formatClock(DomainConstants.SCHEDULE_START_TIME_SECONDS));

        List<Depot> depots = solution.getDepots() != null ? solution.getDepots() : List.of();
        List<DepotInventoryChartDepotDto> rows = new ArrayList<>();
        for (Depot depot : depots) {
            if (depot == null) {
                continue;
            }
            int id = depot.getId();
            var loc = depot.getLocation();
            LocationDto locDto =
                    loc != null ? new LocationDto(loc.getOrigin(), loc.getDestination()) : new LocationDto(0, 0);

            DepotInventoryHorizonSeriesDto finSeries = emptySeries();
            DepotInventoryHorizonSeriesDto workSeries = emptySeries();
            if (inv != null) {
                DepotBalanceTimeline fin = inv.getFinalCommittedByDepotId().get(id);
                DepotBalanceTimeline work = inv.getWorkingIterationByDepotId().get(id);
                int finalEnd = Math.min(simSec + 1, DomainConstants.MAX_TIME_SECONDS + 1);
                finSeries = buildFinalSeries(fin, DomainConstants.SCHEDULE_START_TIME_SECONDS, finalEnd);
                if (work != null) {
                    int workEnd = anchor + DomainConstants.WORKING_INVENTORY_HORIZON_SECONDS;
                    workSeries = buildWorkingSeries(inv, id, work, anchor, workEnd);
                }
            }
            rows.add(new DepotInventoryChartDepotDto(id, locDto, finSeries, workSeries));
        }
        root.setDepots(rows);
        return root;
    }

    public static String toJson(Solution solution) {
        try {
            return SolutionSerializer.mapper().writeValueAsString(toDto(solution));
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeJson(Path path, Solution solution) throws IOException {
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        SolutionSerializer.mapper().writeValue(path.toFile(), toDto(solution));
    }

    /**
     * Writes a JPEG with one block per depot: committed (final) inventory curve and working-horizon curve.
     */
    public static void writeJpeg(Path path, Solution solution) throws IOException {
        Files.createDirectories(path.getParent() != null ? path.getParent() : Path.of("."));
        BufferedImage img = renderJpeg(toDto(solution));
        ImageIO.write(img, "jpg", path.toFile());
    }

    private static DepotInventoryHorizonSeriesDto emptySeries() {
        DepotInventoryHorizonSeriesDto s = new DepotInventoryHorizonSeriesDto();
        s.setWindowStartSeconds(0);
        s.setWindowStartClock("00:00:00");
        s.setWindowEndExclusiveSeconds(0);
        s.setWindowEndLastIncludedClock("00:00:00");
        s.setMinBalanceInWindow(0);
        s.setMaxBalanceInWindow(0);
        s.setBalanceSteps(List.of());
        return s;
    }

    private static DepotInventoryHorizonSeriesDto buildFinalSeries(
            DepotBalanceTimeline fin, int windowStart, int windowEndExclusive) {
        DepotInventoryHorizonSeriesDto dto = new DepotInventoryHorizonSeriesDto();
        dto.setWindowStartSeconds(windowStart);
        dto.setWindowStartClock(formatClock(windowStart));
        dto.setWindowEndExclusiveSeconds(windowEndExclusive);
        if (windowEndExclusive <= windowStart) {
            dto.setWindowEndLastIncludedClock(formatClock(windowStart));
            dto.setMinBalanceInWindow(0);
            dto.setMaxBalanceInWindow(0);
            dto.setBalanceSteps(List.of());
            return dto;
        }
        dto.setWindowEndLastIncludedClock(formatClock(windowEndExclusive - 1));
        if (fin == null) {
            dto.setMinBalanceInWindow(0);
            dto.setMaxBalanceInWindow(0);
            dto.setBalanceSteps(List.of());
            return dto;
        }
        TreeSet<Integer> times = new TreeSet<>();
        times.add(windowStart);
        for (TimeObject tObj : fin.getNetDeltaAtSecond().navigableKeySet()) {
            int t = tObj.getSeconds();
            if (t >= windowStart && t < windowEndExclusive) {
                times.add(t);
            }
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        List<BalanceStepDto> steps = new ArrayList<>();
        for (int t : times) {
            long bal = fin.balanceAtOrBefore(t);
            min = Math.min(min, bal);
            max = Math.max(max, bal);
            steps.add(new BalanceStepDto(t, formatClock(t), bal));
        }
        if (min == Long.MAX_VALUE) {
            min = 0;
            max = 0;
        }
        dto.setMinBalanceInWindow(min);
        dto.setMaxBalanceInWindow(max);
        dto.setBalanceSteps(steps);
        return dto;
    }

    private static DepotInventoryHorizonSeriesDto buildWorkingSeries(
            SolutionInventoryState state,
            int depotId,
            DepotBalanceTimeline work,
            int anchor,
            int horizonEndExclusive) {
        DepotInventoryHorizonSeriesDto dto = new DepotInventoryHorizonSeriesDto();
        dto.setWindowStartSeconds(anchor);
        dto.setWindowStartClock(formatClock(anchor));
        dto.setWindowEndExclusiveSeconds(horizonEndExclusive);
        if (horizonEndExclusive <= anchor) {
            dto.setWindowEndLastIncludedClock(formatClock(anchor));
            dto.setMinBalanceInWindow(0);
            dto.setMaxBalanceInWindow(0);
            dto.setBalanceSteps(List.of());
            return dto;
        }
        dto.setWindowEndLastIncludedClock(formatClock(horizonEndExclusive - 1));
        if (work == null) {
            dto.setMinBalanceInWindow(0);
            dto.setMaxBalanceInWindow(0);
            dto.setBalanceSteps(List.of());
            return dto;
        }
        TreeSet<Integer> times = new TreeSet<>();
        times.add(anchor);
        for (TimeObject tObj :
                work.getNetDeltaAtSecond()
                        .subMap(new TimeObject(anchor), true, new TimeObject(horizonEndExclusive), false)
                        .keySet()) {
            times.add(tObj.getSeconds());
        }
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        List<BalanceStepDto> steps = new ArrayList<>();
        for (int t : times) {
            long bal = state.totalBalanceAtInWorkingHorizon(depotId, t);
            min = Math.min(min, bal);
            max = Math.max(max, bal);
            steps.add(new BalanceStepDto(t, formatClock(t), bal));
        }
        if (min == Long.MAX_VALUE) {
            min = 0;
            max = 0;
        }
        dto.setMinBalanceInWindow(min);
        dto.setMaxBalanceInWindow(max);
        dto.setBalanceSteps(steps);
        return dto;
    }

    private static String formatClock(int secondsSinceMidnight) {
        int sec = Math.min(Math.max(0, secondsSinceMidnight), DomainConstants.MAX_TIME_SECONDS);
        return new TimeObject(sec).toString();
    }

    private static BufferedImage renderJpeg(SolutionDepotInventoryChartDto dto) {
        List<DepotInventoryChartDepotDto> depots = dto.getDepots();
        int n = depots.size();
        int blockHeight = DEPOT_HEADER + TITLE_GAP + PLOT_HEIGHT + BETWEEN_PLOTS + TITLE_GAP + PLOT_HEIGHT + 24;
        int height = 48 + Math.max(1, n) * blockHeight;
        BufferedImage img = new BufferedImage(IMG_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, IMG_WIDTH, height);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(
                "Depot wheelchair inventory — sim "
                        + dto.getSimulatorClock()
                        + " | anchor "
                        + dto.getIterationAnchorClock(),
                MARGIN_LEFT,
                28);

        int y = 48;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        if (n == 0) {
            g.drawString("(no depots)", MARGIN_LEFT, y);
            g.dispose();
            return img;
        }

        int plotWidth = IMG_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
        for (DepotInventoryChartDepotDto d : depots) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.setColor(Color.BLACK);
            g.drawString("Depot " + d.getDepotId(), MARGIN_LEFT, y + 16);
            y += DEPOT_HEADER;

            drawSeriesPanel(g, "Final committed (merged final schedules)", d.getFinalCommitted(), MARGIN_LEFT, y, plotWidth, PLOT_HEIGHT);
            y += TITLE_GAP + PLOT_HEIGHT + BETWEEN_PLOTS;

            drawSeriesPanel(g, "Working iteration (8h from anchor)", d.getWorkingIteration(), MARGIN_LEFT, y, plotWidth, PLOT_HEIGHT);
            y += TITLE_GAP + PLOT_HEIGHT + 24;
        }

        g.dispose();
        return img;
    }

    private static void drawSeriesPanel(
            Graphics2D g,
            String subtitle,
            DepotInventoryHorizonSeriesDto series,
            int x,
            int y,
            int w,
            int h) {
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(60, 60, 60));
        g.drawString(subtitle, x, y + 12);
        int top = y + 18;
        g.setColor(Color.BLACK);
        g.drawRect(x, top, w, h);

        List<BalanceStepDto> steps = series.getBalanceSteps();
        if (steps == null || steps.isEmpty()) {
            g.setColor(Color.GRAY);
            g.drawString("no data", x + 8, top + h / 2);
            return;
        }

        int t0 = series.getWindowStartSeconds();
        int t1 = Math.max(t0 + 1, series.getWindowEndExclusiveSeconds());
        long ymin = series.getMinBalanceInWindow();
        long ymax = series.getMaxBalanceInWindow();
        if (ymin == ymax) {
            ymin -= 1;
            ymax += 1;
        }

        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(0, 90, 160));
        for (int i = 0; i < steps.size(); i++) {
            BalanceStepDto s = steps.get(i);
            int sx = x + timeToX(s.getTimeSeconds(), t0, t1, w);
            int nx =
                    i + 1 < steps.size()
                            ? x + timeToX(steps.get(i + 1).getTimeSeconds(), t0, t1, w)
                            : x + w;
            int yBal = top + balanceToY(s.getBalanceAfterStep(), ymin, ymax, h);
            int yNextBal =
                    i + 1 < steps.size()
                            ? top + balanceToY(steps.get(i + 1).getBalanceAfterStep(), ymin, ymax, h)
                            : yBal;
            g.drawLine(sx, yBal, nx, yBal);
            if (i + 1 < steps.size() && yBal != yNextBal) {
                g.drawLine(nx, yBal, nx, yNextBal);
            }
        }

        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
        g.drawString(String.valueOf(ymin), x - 2, top + h);
        g.drawString(String.valueOf(ymax), x - 2, top + 10);
        g.drawString(series.getWindowStartClock(), x + 2, top + h + 12);
        g.drawString(series.getWindowEndLastIncludedClock(), x + w - 64, top + h + 12);
    }

    private static int timeToX(int t, int t0, int t1, int w) {
        if (t1 <= t0) {
            return 0;
        }
        double f = (t - (double) t0) / (t1 - (double) t0);
        f = Math.max(0, Math.min(1, f));
        return (int) Math.round(f * w);
    }

    private static int balanceToY(long balance, long ymin, long ymax, int h) {
        if (ymax <= ymin) {
            return h / 2;
        }
        double f = (balance - (double) ymin) / (ymax - (double) ymin);
        f = Math.max(0, Math.min(1, f));
        return h - 1 - (int) Math.round(f * (h - 2));
    }
}

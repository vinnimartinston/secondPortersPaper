package com.paper2.dto.solution;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wheelchair depot inventory breakdown for {@link SolutionResultDto}: per-depot snapshots only (evaluation
 * horizon and FO scalars live under {@link SolutionObjectiveFunctionDto}).
 */
@Data
@NoArgsConstructor
public class SolutionDepotInventoryDto {

    private List<DepotSolutionSnapshotDto> depots = new ArrayList<>();

    public SolutionDepotInventoryDto(List<DepotSolutionSnapshotDto> depots) {
        this.depots = depots != null ? depots : new ArrayList<>();
    }
}

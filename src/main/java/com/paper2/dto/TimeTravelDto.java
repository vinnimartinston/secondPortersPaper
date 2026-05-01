package com.paper2.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Encodes the time travel graph used by the simulation/solver.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeTravelDto {
    private List<List<Integer>> graph;
}

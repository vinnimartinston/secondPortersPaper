package com.paper2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Depot (starting point) information for the simulation/problem instance.
 * <p>
 * {@link #initialWheelchairInventory} is the wheelchair count available at the depot at the start of the
 * evaluation window (soft inventory model).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepotDto {
    private int id;
    private LocationDto location;
    private int initialWheelchairInventory;
}

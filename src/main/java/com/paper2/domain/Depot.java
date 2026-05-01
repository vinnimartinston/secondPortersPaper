package com.paper2.domain;

import com.paper2.dto.DepotDto;

import lombok.Getter;

/**
 * Domain depot (from {@link com.paper2.dto.DepotDto}): location and initial wheelchair pool for inventory tracking.
 */
@Getter
public class Depot {
    private final int id;
    private final Location location;
    /** Wheelchairs available at evaluation window start (soft inventory model). */
    private final int initialWheelchairInventory;

    public Depot(DepotDto dto) {
        this.id = dto.getId();
        this.location = new Location(dto.getLocation());
        this.initialWheelchairInventory = dto.getInitialWheelchairInventory();
    }
}

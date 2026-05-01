package com.paper2.domain;

import com.paper2.dto.LocationDto;

import lombok.Getter;

/**
 * Domain: pickup and delivery location indices (from {@link com.paper2.dto.LocationDto}).
 */
@Getter
public class Location {
    private int origin;
    private int destination;

    public Location(LocationDto dto) {
        this.origin = dto.getOrigin();
        this.destination = dto.getDestination();
    }
}

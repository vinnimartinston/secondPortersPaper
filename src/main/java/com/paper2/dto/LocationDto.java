package com.paper2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pickup and delivery locations (indices as in the problem file).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {
    private int origin;
    private int destination;
}

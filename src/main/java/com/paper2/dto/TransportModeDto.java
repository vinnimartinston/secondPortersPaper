package com.paper2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Transport mode capabilities for one request leg.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransportModeDto {
    private String type;
    private boolean keepsTheEquipmentAtDestination;
    private boolean hasTheEquipmentAtOrigin;
}


package com.paper2.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstanceMetadataDto implements Serializable {
    private int amountOfPatients;
    private int profile;
    private int amountOfDepots;
    private int roundTripsPercentage;
    private int wheelchairChangesPercentage;
    private String instanceName;
}

package com.paper2.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstanceMetadata {
    private int amountOfPatients;
    private int profile;
    private int amountOfDepots;
    private int roundTripsPercentage;
    private int wheelchairChangesPercentage;
    private String instanceName;
}

package com.paper2.dto;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregates the input instance data used to build the simulation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputDto implements Serializable {

    private String name;
    private InstanceMetadataDto metadata;
    private int amountOfPorters;
    private List<PatientDto> patients;
    private List<DepotDto> depots;
    private TimeTravelDto timeMatrix;

    /** Explicit for Jackson/IDE when the Lombok processor is not running. */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

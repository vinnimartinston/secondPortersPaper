package com.paper2.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Porter entity (staff) for simulation / routing decisions.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Porter {
    private int id;

    public boolean shouldGoToDepot(
            MobilityAidPolicy mobilityPatient1, MobilityAidPolicy mobilityPatient2) {
        return mobilityPatient1.isRetainEquipmentAtDestination()
                != mobilityPatient2.isEquipmentPresentAtOrigin();
    }
}

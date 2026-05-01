package com.paper2.domain;

import com.paper2.dto.TransportModeDto;
import lombok.Getter;

/**
 * Domain rules for the patient's mobility aid on this request: which aid (bed, wheelchair, …)
 * and how equipment is positioned at pickup vs delivery.
 * <p>
 * Maps from {@link TransportModeDto} / JSON {@code transportMode}; field names there stay
 * unchanged for deserialization — this type uses clearer domain wording.
 */
@Getter
public class MobilityAidPolicy {

    /** Kind of mobility support (e.g. Hospital Bed, Wheelchair). */
    private final String aidType;

    /**
     * After delivery, the equipment stays with the patient at the destination
     * (vs returning immediately with the porter).
     */
    private final boolean retainEquipmentAtDestination;

    /** The required equipment is already available with the patient at the origin (pickup). */
    private final boolean equipmentPresentAtOrigin;

    public MobilityAidPolicy(TransportModeDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("TransportModeDto is null");
        }
        this.aidType = dto.getType();
        this.retainEquipmentAtDestination = dto.isKeepsTheEquipmentAtDestination();
        this.equipmentPresentAtOrigin = dto.isHasTheEquipmentAtOrigin();
    }
}

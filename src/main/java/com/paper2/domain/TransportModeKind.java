package com.paper2.domain;

/**
 * Mobility modes as they appear in the {@code type} field of input and solution JSON
 * ({@link com.paper2.dto.TransportModeDto}). Centralizes literals per project guidelines (enum instead
 * of scattered strings).
 */
public enum TransportModeKind {

    /** Transport with a hospital bed (typically heavier equipment). */
    HOSPITAL_BED("Hospital Bed"),

    /** Wheelchair transport. */
    WHEELCHAIR("Wheelchair");

    private final String jsonLabel;

    TransportModeKind(String jsonLabel) {
        this.jsonLabel = jsonLabel;
    }

    /**
     * Exact label serialized in JSON (input / {@code *_solution.json}).
     *
     * @return type text, e.g. {@code "Hospital Bed"}
     */
    public String jsonLabel() {
        return jsonLabel;
    }

    /**
     * Resolves the enum from the JSON {@code type} value, ignoring case and surrounding whitespace.
     *
     * @param label value from DTO or domain; may be {@code null}
     * @return matching kind, or {@code null} if empty or unknown
     */
    public static TransportModeKind fromJsonLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String trimmed = label.trim();
        for (TransportModeKind k : values()) {
            if (k.jsonLabel.equalsIgnoreCase(trimmed)) {
                return k;
            }
        }
        return null;
    }

    /**
     * Whether the label denotes a hospital bed (useful for metrics counts and route summary).
     *
     * @param label {@link com.paper2.domain.MobilityAidPolicy#getAidType()} or equivalent
     * @return {@code true} if hospital bed
     */
    public static boolean isHospitalBed(String label) {
        return label != null && HOSPITAL_BED.jsonLabel.equalsIgnoreCase(label.trim());
    }
}

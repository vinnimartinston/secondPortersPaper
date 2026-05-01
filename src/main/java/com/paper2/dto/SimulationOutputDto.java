package com.paper2.dto;

/**
 * Serializable result of one simulation run (written as JSON under the output folder).
 * <p>
 * Plain class (no Lombok) so the IDE does not depend on the annotation processor.
 */
public class SimulationOutputDto {

    private String instanceName;
    private long finishedAtEpochMs;
    private String status;
    private String message;

    public SimulationOutputDto() {
    }

    public SimulationOutputDto(
            String instanceName, long finishedAtEpochMs, String status, String message) {
        this.instanceName = instanceName;
        this.finishedAtEpochMs = finishedAtEpochMs;
        this.status = status;
        this.message = message;
    }

    public static SimulationOutputDto error(String instanceName, String message) {
        return new SimulationOutputDto(
                instanceName, System.currentTimeMillis(), "ERROR", message);
    }

    public static SimulationOutputDto ok(String instanceName, String message) {
        return new SimulationOutputDto(
                instanceName, System.currentTimeMillis(), "OK", message);
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public long getFinishedAtEpochMs() {
        return finishedAtEpochMs;
    }

    public void setFinishedAtEpochMs(long finishedAtEpochMs) {
        this.finishedAtEpochMs = finishedAtEpochMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

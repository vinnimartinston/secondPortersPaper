package com.paper2.mapper;

import java.util.List;

import com.paper2.domain.Depot;
import com.paper2.domain.Graph;
import com.paper2.domain.Input;
import com.paper2.domain.InstanceMetadata;
import com.paper2.domain.Patient;
import com.paper2.domain.Priority;
import com.paper2.dto.DepotDto;
import com.paper2.dto.InstanceMetadataDto;
import com.paper2.dto.InputDto;
import com.paper2.dto.PatientDto;
import com.paper2.dto.PriorityDto;
import com.paper2.dto.TimeTravelDto;

/**
 * Maps JSON/API {@link InputDto} (and nested DTOs) into immutable {@link com.paper2.domain} models.
 */
public final class InputMapper {

    private InputMapper() {}

    /**
     * Converts the root input DTO into the domain aggregate {@link Input}.
     *
     * @throws IllegalArgumentException if {@code dto} is null
     */
    public static Input toDomain(InputDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("InputDto is null");
        }
        Graph graph = toGraph(dto.getTimeMatrix());
        List<Patient> patients =
                dto.getPatients() == null
                        ? List.of()
                        : dto.getPatients().stream().map(patient -> toPatient(patient, graph)).toList();
        List<Depot> depots =
                dto.getDepots() == null
                        ? List.of()
                        : dto.getDepots().stream().map(InputMapper::toDepot).toList();
        InstanceMetadata metadata = toMetadata(dto.getMetadata());
        
        return new Input(dto.getName(), metadata, dto.getAmountOfPorters(), patients, depots, graph);
    }

    public static Patient toPatient(PatientDto dto, Graph graph) {
        if (dto == null) {
            throw new IllegalArgumentException("PatientDto is null");
        }
        return new Patient(dto, graph);
    }

    public static Depot toDepot(DepotDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("DepotDto is null");
        }
        return new Depot(dto);
    }

    public static Priority toPriority(PriorityDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("PriorityDto is null");
        }
        return new Priority(dto);
    }

    public static InstanceMetadata toMetadata(InstanceMetadataDto dto) {
        if (dto == null) {
            return null;
        }
        return new InstanceMetadata(
                dto.getAmountOfPatients(),
                dto.getProfile(),
                dto.getAmountOfDepots(),
                dto.getRoundTripsPercentage(),
                dto.getWheelchairChangesPercentage(),
                dto.getInstanceName());
    }


    public static Graph toGraph(TimeTravelDto dto) {
        if (dto == null || dto.getGraph() == null) {
            return new Graph(List.of());
        }
        List<List<Integer>> rows =
                dto.getGraph().stream()
                        .map(
                                row ->
                                        row == null
                                                ? List.<Integer>of()
                                                : List.copyOf(row))
                        .toList();
        return new Graph(rows);
    }
}

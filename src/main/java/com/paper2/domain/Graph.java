package com.paper2.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain: time-travel / distance graph (from {@link com.paper2.dto.TimeTravelDto}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Graph {

    private List<List<Integer>> graph;

    public TimeObject getTravelTime(Location location) {
        return new TimeObject(graph.get(location.getOrigin()).get(location.getDestination()));
    }

    public TimeObject getTravelTimeBetweenTwoLocations(
            Location location1, Location location2, boolean shouldGoToDepot, Depot depot) {
        if (shouldGoToDepot) {
            return this.getTravelTimeToDepot(location1, depot).addTime(this.getTravelTimeFromDepot(location2, depot));
        } else {
            return this.getTravelTimeBetweenTwoLocations(location1, location2);
        }
    }

    public TimeObject getTravelTimeBetweenTwoLocations(Location location1, Location location2){
        return new TimeObject(graph.get(location1.getDestination()).get(location2.getOrigin()));
    }

    public TimeObject getTravelTimeFromDepot(Location location, Depot depot){
        return this.getTravelTimeBetweenTwoLocations(depot.getLocation(), location);
    }

    public TimeObject getTravelTimeToDepot(Location location, Depot depot){
        return this.getTravelTimeBetweenTwoLocations(location, depot.getLocation());
    }
}

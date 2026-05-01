package com.paper2.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request timing in the same time units as the instance (e.g. seconds).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeDto {
    private int timeAsked;
    private int dueDate;

}

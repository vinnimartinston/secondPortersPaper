package com.paper2.dto.solution;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One step in a piecewise-constant wheelchair balance curve (compact serialization).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BalanceStepDto {
    /** Calendar second since midnight when the balance changes. */
    private int timeSeconds;
    /** Same instant as {@code HH:MM:SS}. */
    private String timeClock;
    /** Balance at the end of this second (after applying the step). */
    private long balanceAfterStep;
}

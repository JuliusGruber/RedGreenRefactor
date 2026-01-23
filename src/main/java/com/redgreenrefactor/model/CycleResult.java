package com.redgreenrefactor.model;

import java.util.List;

/**
 * Represents the result of a single TDD cycle (PLAN → RED → GREEN → REFACTOR).
 *
 * @param cycleNumber     The cycle number (1-indexed)
 * @param testDescription The test that was implemented in this cycle
 * @param success         Whether the cycle completed successfully
 * @param commitIds       The commit IDs created during this cycle (typically 4)
 * @param error           Error message if the cycle failed (nullable)
 */
public record CycleResult(
        int cycleNumber,
        String testDescription,
        boolean success,
        List<String> commitIds,
        String error
) {
    public CycleResult {
        commitIds = commitIds == null ? List.of() : List.copyOf(commitIds);
    }

    /**
     * Creates a successful cycle result.
     *
     * @param cycleNumber     The cycle number
     * @param testDescription The test description
     * @param commitIds       The commits created
     * @return A successful CycleResult
     */
    public static CycleResult success(int cycleNumber, String testDescription, List<String> commitIds) {
        return new CycleResult(cycleNumber, testDescription, true, commitIds, null);
    }

    /**
     * Creates a failed cycle result.
     *
     * @param cycleNumber     The cycle number
     * @param testDescription The test description (may be null if failed during planning)
     * @param error           The error message
     * @return A failed CycleResult
     */
    public static CycleResult failure(int cycleNumber, String testDescription, String error) {
        return new CycleResult(cycleNumber, testDescription, false, List.of(), error);
    }
}

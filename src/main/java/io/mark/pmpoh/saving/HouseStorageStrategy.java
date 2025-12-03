package io.mark.pmpoh.saving;

import io.mark.pmpoh.poh.RoomPosition;

import java.util.Map;

/**
 * Interface for room position storage strategies (local file, HTTP, etc.)
 */
public interface HouseStorageStrategy {
    
    /**
     * Check if save data exists for the current user
     */
    boolean exists(String username);
    
    /**
     * Load room positions
     */
    Map<Integer, RoomPosition> load(String username);
    
    /**
     * Save room positions
     */
    void save(String username, Map<Integer, RoomPosition> roomsByIndex);
}


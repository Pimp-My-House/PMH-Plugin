package io.mark.pmpoh.saving;

import io.mark.pmpoh.poh.RoomPosition;
import io.mark.pmpoh.saving.impl.LocalFileStorageStrategy;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.Map;

/**
 * Utility class for saving and loading room position data
 * Uses strategy pattern to support both local file and HTTP storage
 */
@Slf4j
@Singleton
public class HouseStorageStorage {

    private static final HouseStorageStrategy localStrategy = new LocalFileStorageStrategy();

    /**
     * Check if save data exists (uses default strategy - local file)
     */
    public static boolean saveFileExists(String username) {
        return localStrategy.exists(username);
    }
    
    /**
     * Load room positions (uses default strategy - local file)
     */
    public static Map<Integer, RoomPosition> load(String username) {
        return localStrategy.load(username);
    }
    
    /**
     * Save room positions (uses default strategy - local file)
     */
    public static void save(String username, Map<Integer, RoomPosition> roomsByIndex) {
        localStrategy.save(username, roomsByIndex);
    }

}


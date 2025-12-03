package io.mark.pmpoh.service;

import io.mark.pmpoh.poh.RoomPosition;
import io.mark.pmpoh.saving.HouseStorageStorage;
import io.mark.pmpoh.util.RoomPositionUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Service for managing room state, processing, and persistence
 */
@Slf4j
@Singleton
public class RoomManagementService {

    @Inject
    private Client client;

    @Getter
    private final Map<Integer, RoomPosition> roomsByIndex = new HashMap<>();
    
    private final Map<Integer, RoomPosition> currentBatch = new HashMap<>();

    @Setter
    @Getter
    private boolean batchProcessingScheduled = false;

    /**
     * Add a room to the current batch for processing
     * @param index Room index
     * @param room Room position
     */
    public void addToBatch(int index, RoomPosition room) {
        currentBatch.put(index, room);
    }

    /**
     * Process the current batch of rooms
     * @return List of room indices that had objects removed (for cleanup)
     */
    public List<Integer> processRoomBatch() {
        Map<Integer, RoomPosition> oldState = new HashMap<>(roomsByIndex);
        RoomPositionUtil.ProcessResult result = RoomPositionUtil.processRoomBatch(oldState, currentBatch);

        List<Integer> roomsToCleanup = new ArrayList<>();

        for (RoomPositionUtil.RoomMove move : result.getMovedRooms()) {
            oldState.entrySet().stream()
                .filter(e -> e.getValue() == move.getOldRoom())
                .findFirst()
                .ifPresent(e -> roomsToCleanup.add(e.getKey()));
        }

        Set<RoomPosition> newRooms = new HashSet<>(result.getUpdatedRooms().values());
        oldState.entrySet().stream()
            .filter(e -> !newRooms.contains(e.getValue()))
            .forEach(e -> roomsToCleanup.add(e.getKey()));

        roomsByIndex.clear();
        roomsByIndex.putAll(result.getUpdatedRooms());

        saveRooms();
        logRoomChanges(result, oldState);
        currentBatch.clear();
        
        return roomsToCleanup;
    }
    
    /**
     * Get room at the specified x, y coordinates
     * @param x Room x coordinate (1-8)
     * @param y Room y coordinate (1-8)
     * @return RoomPosition at that location, or null if not found
     */
    public RoomPosition getRoomAt(int x, int y) {
        return roomsByIndex.values().stream()
            .filter(room -> room.getX() == x && room.getY() == y)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Load rooms from storage
     * @param username Username to load rooms for
     */
    public void loadRooms(String username) {
        if (HouseStorageStorage.saveFileExists(username)) {
            Map<Integer, RoomPosition> loadedRooms = HouseStorageStorage.load(username);
            roomsByIndex.clear();
            roomsByIndex.putAll(loadedRooms);
            log.info("Loaded {} rooms from save file", loadedRooms.size());
        }
    }
    
    /**
     * Save rooms to storage
     */
    public void saveRooms() {
        String username = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
        if (username != null) {
            HouseStorageStorage.save(username, roomsByIndex);
        }
    }
    
    /**
     * Check if save file exists
     * @param username Username to check
     * @return true if save file exists, false otherwise
     */
    public boolean hasSaveFile(String username) {
        return HouseStorageStorage.saveFileExists(username);
    }

    /**
     * Log room changes for debugging
     */
    private void logRoomChanges(RoomPositionUtil.ProcessResult result, Map<Integer, RoomPosition> oldState) {
        if (!result.getMovedRooms().isEmpty()) {
            for (RoomPositionUtil.RoomMove move : result.getMovedRooms()) {
                log.info("Room Moved -> {} (Old: ({}, {}, {}) -> New: ({}, {}, {}))", 
                    move.getNewRoom().getRoomName(),
                    move.getOldRoom().getX(), move.getOldRoom().getY(), move.getOldRoom().getLevel(),
                    move.getNewRoom().getX(), move.getNewRoom().getY(), move.getNewRoom().getLevel());
            }
            
            // Log all rooms' positions
            log.info("All rooms positions (Before -> After):");
            for (Map.Entry<Integer, RoomPosition> entry : roomsByIndex.entrySet()) {
                int newIndex = entry.getKey();
                RoomPosition newRoom = entry.getValue();
                
                Map.Entry<Integer, RoomPosition> matchedOld = RoomPositionUtil.findMatchingOldRoom(newRoom, oldState);
                
                if (matchedOld != null) {
                    RoomPosition matchedOldRoom = matchedOld.getValue();
                    int matchedOldIndex = matchedOld.getKey();
                    
                    if (matchedOldRoom.getX() != newRoom.getX() || 
                        matchedOldRoom.getY() != newRoom.getY() || 
                        matchedOldRoom.getLevel() != newRoom.getLevel()) {
                        log.info("  Index {} (was {}): {} ({}, {}, {}) -> ({}, {}, {})", 
                            newIndex, matchedOldIndex, newRoom.getRoomName(),
                            matchedOldRoom.getX(), matchedOldRoom.getY(), matchedOldRoom.getLevel(),
                            newRoom.getX(), newRoom.getY(), newRoom.getLevel());
                    } else if (matchedOldIndex != newIndex) {
                        log.info("  Index {} (was {}): {} at ({}, {}, {}) [index changed]", 
                            newIndex, matchedOldIndex, newRoom.getRoomName(),
                            newRoom.getX(), newRoom.getY(), newRoom.getLevel());
                    } else {
                        log.info("  Index {}: {} at ({}, {}, {}) [unchanged]", 
                            newIndex, newRoom.getRoomName(),
                            newRoom.getX(), newRoom.getY(), newRoom.getLevel());
                    }
                } else {
                    log.info("  Index {}: {} at ({}, {}, {}) [new]", 
                        newIndex, newRoom.getRoomName(),
                        newRoom.getX(), newRoom.getY(), newRoom.getLevel());
                }
            }
        }
        
        // Log summaries
        if (result.getAddedCount() > 0 || result.getRemappedCount() > 0) {
            log.info("Room Added -> {} new, {} remapped", result.getAddedCount(), result.getRemappedCount());
        }
        
        if (result.getRemovedCount() > 0) {
            log.info("Room Removed -> {} removed", result.getRemovedCount());
        }
    }
}


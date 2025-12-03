package io.mark.pmpoh.util;

import io.mark.pmpoh.poh.RoomPosition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * Utility class for processing and tracking room position changes
 */
public class RoomPositionUtil {
    
    /**
     * Result of processing a room batch
     */
    @Getter
    @RequiredArgsConstructor
    public static class ProcessResult {
        private final List<RoomMove> movedRooms;
        private final int addedCount;
        private final int removedCount;
        private final int remappedCount;
        private final Map<Integer, RoomPosition> updatedRooms;
    }
    
    /**
     * Represents a room that moved
     */
    @Getter
    @RequiredArgsConstructor
    public static class RoomMove {
        private final RoomPosition oldRoom;
        private final RoomPosition newRoom;
        private final int oldIndex;
        private final int newIndex;
    }
    
    /**
     * Process a batch of rooms - remap by matching criteria and update coordinates
     * 
     * @param oldState Previous state of rooms (index -> RoomPosition)
     * @param currentBatch Current batch of rooms (index -> RoomPosition)
     * @return ProcessResult containing moved rooms, counts, and updated room map
     */
    public static ProcessResult processRoomBatch(
            Map<Integer, RoomPosition> oldState,
            Map<Integer, RoomPosition> currentBatch) {
        
        if (currentBatch.isEmpty()) {
            return new ProcessResult(
                Collections.emptyList(),
                0,
                0,
                0,
                new HashMap<>(oldState)
            );
        }
        
        List<RoomMove> movedRooms = new ArrayList<>();
        Set<Integer> matchedOldIndices = new HashSet<>();
        
        int addedCount = 0;
        int removedCount = 0;
        int remappedCount = 0;
        
        // Match each room in current batch to old state by criteria
        for (Map.Entry<Integer, RoomPosition> newEntry : currentBatch.entrySet()) {
            int newIndex = newEntry.getKey();
            RoomPosition newRoom = newEntry.getValue();
            
            // Find matching old room by criteria
            RoomPosition matchedOldRoom = null;
            Integer matchedOldIndex = null;
            
            for (Map.Entry<Integer, RoomPosition> oldEntry : oldState.entrySet()) {
                int oldIndex = oldEntry.getKey();
                RoomPosition oldRoom = oldEntry.getValue();
                
                if (!matchedOldIndices.contains(oldIndex) && oldRoom.matchesForRemapping(newRoom)) {
                    matchedOldRoom = oldRoom;
                    matchedOldIndex = oldIndex;
                    matchedOldIndices.add(oldIndex);
                    break;
                }
            }
            
            if (matchedOldRoom != null) {
                if (matchedOldIndex != newIndex) {
                    remappedCount++;
                }
                
                // Preserve objects from old room to new room
                if (matchedOldRoom.getObjects() != null && !matchedOldRoom.getObjects().isEmpty()) {
                    newRoom.setObjects(new java.util.ArrayList<>(matchedOldRoom.getObjects()));
                }
                
                if (matchedOldRoom.getX() != newRoom.getX() || 
                    matchedOldRoom.getY() != newRoom.getY() || 
                    matchedOldRoom.getLevel() != newRoom.getLevel()) {
                    movedRooms.add(new RoomMove(matchedOldRoom, newRoom, matchedOldIndex, newIndex));
                }
            } else {
                addedCount++;
            }
        }
        
        // Count deleted rooms
        for (Map.Entry<Integer, RoomPosition> entry : oldState.entrySet()) {
            if (!matchedOldIndices.contains(entry.getKey())) {
                removedCount++;
            }
        }
        
        // Create updated map with current batch (which now has preserved objects)
        Map<Integer, RoomPosition> updatedRooms = new HashMap<>(currentBatch);
        
        return new ProcessResult(movedRooms, addedCount, removedCount, remappedCount, updatedRooms);
    }
    
    /**
     * Find matching old room for a new room by criteria
     */
    public static Map.Entry<Integer, RoomPosition> findMatchingOldRoom(
            RoomPosition newRoom,
            Map<Integer, RoomPosition> oldState) {
        
        for (Map.Entry<Integer, RoomPosition> oldEntry : oldState.entrySet()) {
            if (oldEntry.getValue().matchesForRemapping(newRoom)) {
                return oldEntry;
            }
        }
        return null;
    }
}


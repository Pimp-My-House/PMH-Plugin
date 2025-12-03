package io.mark.pmpoh.service;

import io.mark.pmpoh.objects.ObjectManager;
import io.mark.pmpoh.objects.ObjectType;
import io.mark.pmpoh.poh.ObjectSpawn;
import io.mark.pmpoh.poh.RoomPosition;
import io.mark.pmpoh.util.UsableChunkCache;
import io.mark.pmpoh.util.ZoneTileUtil;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing object spawning and tracking
 */
@Slf4j
@Singleton
public class ObjectSpawnService {
    
    @Inject
    private Client client;
    
    @Inject
    private ObjectManager objectManager;
    
    // Track spawned objects by room index
    private final Map<Integer, List<RuneLiteObject>> spawnedObjectsByRoom = new HashMap<>();
    
    /**
     * Spawn an object from ObjectSpawn data
     * @param objectSpawn The object spawn data
     * @param localPoint The local point to spawn at
     * @return The spawned RuneLiteObject, or null if failed
     */
    public RuneLiteObject spawnObject(ObjectSpawn objectSpawn, LocalPoint localPoint) {
        ObjectType objectType = objectManager.getByGameval(objectSpawn.getGameval());
        if (objectType == null) {
            log.warn("Failed to spawn object: gameval '{}' not found", objectSpawn.getGameval());
            return null;
        }

        RuneLiteObject runeLiteObject = client.createRuneLiteObject();
        runeLiteObject.setDrawFrontTilesFirst(false);

        net.runelite.api.Model model = objectType.getModel(client);
        if (model != null) {
            runeLiteObject.setModel(model);
        } else {
            log.warn("Failed to get model for object: {}", objectSpawn.getGameval());
        }

        runeLiteObject.setOrientation(objectSpawn.getOrientation());
        runeLiteObject.setLocation(localPoint, client.getPlane());
        runeLiteObject.setActive(true);
        client.registerRuneLiteObject(runeLiteObject);
        
        log.debug("Spawned object {} at local point ({}, {})", objectSpawn.getGameval(), localPoint.getX(), localPoint.getY());
        return runeLiteObject;
    }
    
    /**
     * Load and spawn objects from room positions
     * @param roomsByIndex Map of room index to RoomPosition
     * @return Number of objects spawned
     */
    public int loadObjectsFromRooms(Map<Integer, RoomPosition> roomsByIndex) {
        // Check if cache is valid
        if (!UsableChunkCache.getInstance().isValid()) {
            log.warn("Cannot load objects: usable chunk cache is not valid");
            return 0;
        }

        log.debug("Loading objects from {} rooms", roomsByIndex.size());
        int totalObjects = 0;
        int spawnedObjects = 0;

        for (Map.Entry<Integer, RoomPosition> entry : roomsByIndex.entrySet()) {
            Integer roomIndex = entry.getKey();
            RoomPosition room = entry.getValue();
            
            if (room.getObjects() == null || room.getObjects().isEmpty()) {
                log.debug("Room {} at ({},{}) has no objects", roomIndex, room.getX(), room.getY());
                continue;
            }
            
            log.debug("Room {} at ({},{}) has {} objects", roomIndex, room.getX(), room.getY(), room.getObjects().size());

            // Remove existing objects for this room (in case it moved)
            removeRoomObjects(roomIndex);
            
            // Create new list for this room's objects
            List<RuneLiteObject> roomObjects = new ArrayList<>();
            
            int zoneX = room.getX();
            int zoneY = room.getY();

            for (ObjectSpawn objectSpawn : room.getObjects()) {
                totalObjects++;
                
                // Convert zone and tile coordinates to local point
                LocalPoint localPoint = ZoneTileUtil.zoneTileCoordsToLocalPoint(
                    zoneX, zoneY, 
                    objectSpawn.getTileX(), 
                    objectSpawn.getTileY()
                );

                if (localPoint == null) {
                    log.warn("Failed to convert zone/tile to local point: zone ({}, {}), tile ({}, {}), gameval: {}", 
                        zoneX, zoneY, objectSpawn.getTileX(), objectSpawn.getTileY(), objectSpawn.getGameval());
                    continue;
                }
                
                log.debug("Converting object {} at zone ({},{}), tile ({},{}) to local point ({},{})", 
                    objectSpawn.getGameval(), zoneX, zoneY, objectSpawn.getTileX(), objectSpawn.getTileY(),
                    localPoint.getX(), localPoint.getY());

                // Spawn the object
                RuneLiteObject obj = spawnObject(objectSpawn, localPoint);
                if (obj != null) {
                    roomObjects.add(obj);
                    spawnedObjects++;
                }
            }
            
            // Store objects for this room
            if (!roomObjects.isEmpty()) {
                spawnedObjectsByRoom.put(roomIndex, roomObjects);
            }
        }
        
        log.info("Loaded {} objects from rooms (spawned {})", totalObjects, spawnedObjects);
        return spawnedObjects;
    }
    
    /**
     * Remove all objects for a room
     * @param roomIndex The room index
     */
    public void removeRoomObjects(Integer roomIndex) {
        List<RuneLiteObject> objects = spawnedObjectsByRoom.remove(roomIndex);
        if (objects != null) {
            for (RuneLiteObject obj : objects) {
                if (obj != null && obj.isActive()) {
                    client.removeRuneLiteObject(obj);
                }
            }
            log.debug("Removed {} objects from room index {}", objects.size(), roomIndex);
        }
    }
    
    /**
     * Remove all objects for multiple rooms
     * @param roomIndices List of room indices
     */
    public void removeRoomObjects(List<Integer> roomIndices) {
        for (Integer roomIndex : roomIndices) {
            removeRoomObjects(roomIndex);
        }
    }
    
    /**
     * Clear all spawned objects
     */
    public void clearAllObjects() {
        for (Integer roomIndex : new ArrayList<>(spawnedObjectsByRoom.keySet())) {
            removeRoomObjects(roomIndex);
        }
        spawnedObjectsByRoom.clear();
    }
}


package io.mark.pmpoh.util;

import net.runelite.api.Constants;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/**
 * Utility class for mapping tiles within zones (chunks) to local points.
 * Zones are 8x8 chunks, and tiles are numbered 0-63 in row-major order (left to right, top to bottom).
 * Zone coordinates (1-8) map to room coordinates, counting from top-left of usable chunks.
 */
public class ZoneTileUtil
{
    private static final int CHUNK_SIZE = Constants.CHUNK_SIZE; // 8
    private static final int SCENE_SIZE = Constants.SCENE_SIZE; // 104
    private static final int START_X = CHUNK_SIZE; // 8
    private static final int START_Z = CHUNK_SIZE; // 8

    /**
     * Convert zone (room) coordinates and tile index to local point.
     * Uses the same coordinate system as the overlay: zones counted from top-left of usable chunks.
     * 
     * @param zoneX Zone X coordinate (1-8, room X coordinate)
     * @param zoneY Zone Y coordinate (1-8, room Y coordinate)
     * @param tileIndex Tile index within the zone (0-63, where 0 is top-left, row-major order)
     * @param minUsableChunkX Minimum usable chunk X in the grid (0-based)
     * @param minUsableChunkZ Minimum usable chunk Z in the grid (0-based)
     * @return LocalPoint for the specified tile, or null if coordinates are invalid
     */
    public static LocalPoint zoneTileToLocalPoint(int zoneX, int zoneY, int tileIndex, int minUsableChunkX, int minUsableChunkZ)
    {
        if (zoneX < 1 || zoneX > 8 || zoneY < 1 || zoneY > 8)
        {
            return null;
        }

        if (tileIndex < 0 || tileIndex >= (CHUNK_SIZE * CHUNK_SIZE))
        {
            return null;
        }

        // Convert tile index to tile coordinates within the chunk (0-7 for both X and Z)
        // Tile 0 = (0,0), Tile 1 = (1,0), Tile 8 = (0,1), etc. (row-major order)
        int tileX = tileIndex % CHUNK_SIZE;
        int tileZ = tileIndex / CHUNK_SIZE;

        // Convert zone coordinates to chunk grid coordinates (0-based)
        // Zone (1,1) maps to (minUsableChunkX, minUsableChunkZ)
        int chunkGridX = minUsableChunkX + (zoneX - 1);
        int chunkGridZ = minUsableChunkZ + (zoneY - 1);

        // Convert chunk grid coordinates to actual chunk coordinates
        int chunkX = START_X + (chunkGridX * CHUNK_SIZE);
        int chunkZ = START_Z + (chunkGridZ * CHUNK_SIZE);

        // Calculate local coordinates
        int localX = (chunkX + tileX) << Perspective.LOCAL_COORD_BITS;
        int localZ = (chunkZ + tileZ) << Perspective.LOCAL_COORD_BITS;

        return new LocalPoint(localX, localZ);
    }

    /**
     * Convert zone (room) coordinates and tile index to local point.
     * Simplified version that assumes zones start at (1,1) = chunk (0,0) in the usable grid.
     * 
     * @param zoneX Zone X coordinate (1-8, room X coordinate)
     * @param zoneY Zone Y coordinate (1-8, room Y coordinate)
     * @param tileIndex Tile index within the zone (0-63, where 0 is top-left, row-major order)
     * @return LocalPoint for the specified tile, or null if coordinates are invalid
     */
    public static LocalPoint zoneTileToLocalPoint(int zoneX, int zoneY, int tileIndex)
    {
        // Default to assuming usable chunks start at (0,0) in the grid
        return zoneTileToLocalPoint(zoneX, zoneY, tileIndex, 0, 0);
    }

    /**
     * Convert zone (room) coordinates and tile coordinates to local point.
     * 
     * @param zoneX Zone X coordinate (1-8, room X coordinate)
     * @param zoneY Zone Y coordinate (1-8, room Y coordinate)
     * @param tileX Tile X within zone (0-7)
     * @param tileZ Tile Z within zone (0-7)
     * @param minUsableChunkX Minimum usable chunk X in the grid (0-based)
     * @param minUsableChunkZ Minimum usable chunk Z in the grid (0-based)
     * @return LocalPoint for the specified tile, or null if coordinates are invalid
     */
    public static LocalPoint zoneTileCoordsToLocalPoint(int zoneX, int zoneY, int tileX, int tileZ, int minUsableChunkX, int minUsableChunkZ)
    {
        if (zoneX < 1 || zoneX > 8 || zoneY < 1 || zoneY > 8)
        {
            return null;
        }

        if (tileX < 0 || tileX >= CHUNK_SIZE || tileZ < 0 || tileZ >= CHUNK_SIZE)
        {
            return null;
        }

        // Convert zone coordinates to chunk grid coordinates (0-based)
        int chunkGridX = minUsableChunkX + (zoneX - 1);
        int chunkGridZ = minUsableChunkZ + (zoneY - 1);

        // Convert chunk grid coordinates to actual chunk coordinates
        int chunkX = START_X + (chunkGridX * CHUNK_SIZE);
        int chunkZ = START_Z + (chunkGridZ * CHUNK_SIZE);

        // Calculate local coordinates
        int localX = (chunkX + tileX) << Perspective.LOCAL_COORD_BITS;
        int localZ = (chunkZ + tileZ) << Perspective.LOCAL_COORD_BITS;

        return new LocalPoint(localX, localZ);
    }

    /**
     * Convert zone (room) coordinates and tile coordinates to local point.
     * Simplified version that assumes zones start at (1,1) = chunk (0,0) in the usable grid.
     * 
     * @param zoneX Zone X coordinate (1-8, room X coordinate)
     * @param zoneY Zone Y coordinate (1-8, room Y coordinate)
     * @param tileX Tile X within zone (0-7)
     * @param tileZ Tile Z within zone (0-7)
     * @return LocalPoint for the specified tile, or null if coordinates are invalid
     */
    public static LocalPoint zoneTileCoordsToLocalPoint(int zoneX, int zoneY, int tileX, int tileZ)
    {
        // Use cached usable chunk bounds
        UsableChunkCache cache = UsableChunkCache.getInstance();
        if (!cache.isValid())
        {
            return null;
        }
        return zoneTileCoordsToLocalPoint(zoneX, zoneY, tileX, tileZ, cache.getMinUsableX(), cache.getMinUsableZ());
    }

    /**
     * Convert local point to zone and tile coordinates.
     * This version uses cached usable chunk bounds.
     * 
     * @param localPoint The local point
     * @return Array of [zoneX, zoneY, tileIndex] or null if invalid
     */
    public static int[] localPointToZoneTile(LocalPoint localPoint)
    {
        if (localPoint == null)
        {
            return null;
        }

        // Use cached usable chunk bounds
        UsableChunkCache cache = UsableChunkCache.getInstance();
        if (!cache.isValid())
        {
            return null;
        }

        int minUsableX = cache.getMinUsableX();
        int minUsableZ = cache.getMinUsableZ();

        int localX = localPoint.getX();
        int localZ = localPoint.getY();

        // Convert from local coordinates to tile coordinates
        int tileX = (localX >> Perspective.LOCAL_COORD_BITS) - START_X;
        int tileZ = (localZ >> Perspective.LOCAL_COORD_BITS) - START_Z;

        // Check if within valid bounds
        if (tileX < 0 || tileZ < 0)
        {
            return null;
        }

        // Calculate chunk grid coordinates
        int chunkGridX = tileX / CHUNK_SIZE;
        int chunkGridZ = tileZ / CHUNK_SIZE;

        // Convert chunk grid coordinates to zone coordinates (relative to usable chunks)
        int zoneX = (chunkGridX - minUsableX) + 1;
        int zoneY = (chunkGridZ - minUsableZ) + 1;

        // Check if zone is valid
        if (zoneX < 1 || zoneX > 8 || zoneY < 1 || zoneY > 8)
        {
            return null;
        }

        // Calculate tile coordinates within the zone
        int tileXInZone = tileX % CHUNK_SIZE;
        int tileZInZone = tileZ % CHUNK_SIZE;

        // Convert to tile index (row-major order)
        int tileIndex = (tileZInZone * CHUNK_SIZE) + tileXInZone;

        return new int[]{zoneX, zoneY, tileIndex};
    }

    /**
     * Get the anchor local point for a zone (top-left corner, tile 0).
     * 
     * @param zoneX Zone X coordinate (1-8)
     * @param zoneY Zone Y coordinate (1-8)
     * @param minUsableChunkX Minimum usable chunk X in the grid (0-based)
     * @param minUsableChunkZ Minimum usable chunk Z in the grid (0-based)
     * @return LocalPoint for the zone anchor (tile 0), or null if invalid
     */
    public static LocalPoint getZoneAnchor(int zoneX, int zoneY, int minUsableChunkX, int minUsableChunkZ)
    {
        return zoneTileToLocalPoint(zoneX, zoneY, 0, minUsableChunkX, minUsableChunkZ);
    }

    /**
     * Get the anchor local point for a zone (top-left corner, tile 0).
     * Simplified version that assumes zones start at (1,1) = chunk (0,0) in the usable grid.
     * 
     * @param zoneX Zone X coordinate (1-8)
     * @param zoneY Zone Y coordinate (1-8)
     * @return LocalPoint for the zone anchor (tile 0), or null if invalid
     */
    public static LocalPoint getZoneAnchor(int zoneX, int zoneY)
    {
        return zoneTileToLocalPoint(zoneX, zoneY, 0);
    }
}


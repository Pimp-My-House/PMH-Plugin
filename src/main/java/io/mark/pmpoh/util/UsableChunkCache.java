package io.mark.pmpoh.util;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.Perspective;

/**
 * Cache for usable chunk bounds and mappings.
 * Calculated once when entering POH and cached until reload.
 */
@Slf4j
@Getter
@Setter
public class UsableChunkCache
{
    private static UsableChunkCache instance;
    
    private int chunksX;
    private int chunksZ;
    private int totalChunks;
    private int usableChunks;
    private int minUsableX;
    private int minUsableZ;
    private int maxUsableX;
    private int maxUsableZ;
    private boolean[][] usableChunkGrid;
    private boolean isValid = false;

    private UsableChunkCache() {}

    public static UsableChunkCache getInstance()
    {
        if (instance == null)
        {
            instance = new UsableChunkCache();
        }
        return instance;
    }

    /**
     * Calculate and cache usable chunk bounds
     */
    public void calculate(Client client)
    {
        int startX = Constants.CHUNK_SIZE;
        int startZ = Constants.CHUNK_SIZE;
        int endX = Constants.SCENE_SIZE - Constants.CHUNK_SIZE;
        int endZ = Constants.SCENE_SIZE - Constants.CHUNK_SIZE;

        // Calculate the number of chunks
        chunksX = (endX - startX) / Constants.CHUNK_SIZE + 1;
        chunksZ = (endZ - startZ) / Constants.CHUNK_SIZE + 1;
        totalChunks = chunksX * chunksZ;

        // Determine which chunks are usable (chunks where ALL tiles have height != 0)
        usableChunkGrid = new boolean[chunksX][chunksZ];
        usableChunks = 0;
        int plane = client.getPlane();
        
        minUsableX = Integer.MAX_VALUE;
        minUsableZ = Integer.MAX_VALUE;
        maxUsableX = -1;
        maxUsableZ = -1;

        for (int chunkX = startX; chunkX <= endX; chunkX += Constants.CHUNK_SIZE)
        {
            for (int chunkZ = startZ; chunkZ <= endZ; chunkZ += Constants.CHUNK_SIZE)
            {
                int chunkGridX = (chunkX - startX) / Constants.CHUNK_SIZE;
                int chunkGridZ = (chunkZ - startZ) / Constants.CHUNK_SIZE;
                
                boolean isUsable = true;
                // Check all tiles within the chunk - all must have height != 0 to be usable
                for (int tileX = 0; tileX < Constants.CHUNK_SIZE && isUsable; tileX++)
                {
                    for (int tileZ = 0; tileZ < Constants.CHUNK_SIZE && isUsable; tileZ++)
                    {
                        int localX = (chunkX + tileX) << Perspective.LOCAL_COORD_BITS;
                        int localZ = (chunkZ + tileZ) << Perspective.LOCAL_COORD_BITS;
                        net.runelite.api.coords.LocalPoint lp = new net.runelite.api.coords.LocalPoint(localX, localZ);
                        int height = getTileHeight(client.getWorldView(lp.getWorldView()), localX, localZ, plane);
                        if (height == 0)
                        {
                            isUsable = false;
                        }
                    }
                }
                if (isUsable)
                {
                    usableChunkGrid[chunkGridX][chunkGridZ] = true;
                    usableChunks++;
                    
                    if (chunkGridX < minUsableX) minUsableX = chunkGridX;
                    if (chunkGridX > maxUsableX) maxUsableX = chunkGridX;
                    if (chunkGridZ < minUsableZ) minUsableZ = chunkGridZ;
                    if (chunkGridZ > maxUsableZ) maxUsableZ = chunkGridZ;
                }
            }
        }

        isValid = true;
        
        log.info("Grid Size: {}x{}", chunksX, chunksZ);
        log.info("Total Chunks: {}", totalChunks);
        log.info("Usable Chunks: {}", usableChunks);
        log.info("Unusable Chunks: {}", (totalChunks - usableChunks));
        if (usableChunks > 0)
        {
            log.info("Usable Chunks Bounding Box: {}x{} (from {},{} to {},{})", 
                (maxUsableX - minUsableX + 1), (maxUsableZ - minUsableZ + 1),
                minUsableX, minUsableZ, maxUsableX, maxUsableZ);
        }
    }

    /**
     * Clear the cache (call on reload/exit)
     */
    public void clear()
    {
        isValid = false;
        usableChunkGrid = null;
        chunksX = 0;
        chunksZ = 0;
        totalChunks = 0;
        usableChunks = 0;
        minUsableX = Integer.MAX_VALUE;
        minUsableZ = Integer.MAX_VALUE;
        maxUsableX = -1;
        maxUsableZ = -1;
    }

    private static int getTileHeight(net.runelite.api.WorldView wv, int localX, int localY, int plane) {
        int offset = wv.isTopLevel() ? 40 : 0;
        int sceneX = (localX >> 7) + offset;
        int sceneY = (localY >> 7) + offset;
        if (sceneX >= 0 && sceneY >= 0 && sceneX < wv.getSizeX() + offset && sceneY < wv.getSizeY() + offset) {
            int[][][] tileHeights = wv.getScene().getTileHeights();
            int x = localX & 127;
            int y = localY & 127;
            int var8 = x * tileHeights[plane][sceneX + 1][sceneY] + (128 - x) * tileHeights[plane][sceneX][sceneY] >> 7;
            int var9 = tileHeights[plane][sceneX][sceneY + 1] * (128 - x) + x * tileHeights[plane][sceneX + 1][sceneY + 1] >> 7;
            return (128 - y) * var8 + y * var9 >> 7;
        } else {
            return 0;
        }
    }
}


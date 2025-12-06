package io.mark.pmpoh.overlay;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.GeneralPath;
import javax.inject.Inject;

import io.mark.pmpoh.PimpMyPohPlugin;
import io.mark.pmpoh.poh.RoomPosition;
import io.mark.pmpoh.service.RoomManagementService;
import io.mark.pmpoh.util.UsableChunkCache;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class RoomDeveloperOverlay extends Overlay {

    private static final Color CHUNK_BORDER_COLOR = Color.BLUE;
    private static final Color USABLE_CHUNK_BORDER_COLOR = Color.GREEN;
    private static final int STROKE_WIDTH = 4;

    // Debug flags
    private static final boolean SHOW_CHUNK_BORDERS = false;
    private static final boolean SHOW_ROOM_NAMES = true;

    private final Client client;
    private final PimpMyPohPlugin pimpMyPohPlugin;
    private final RoomManagementService roomManagementService;

    @Inject
    public RoomDeveloperOverlay(Client client, PimpMyPohPlugin pimpMyPohPlugin, RoomManagementService roomManagementService)
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        this.client = client;
        this.pimpMyPohPlugin = pimpMyPohPlugin;
        this.roomManagementService = roomManagementService;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {

        if (!pimpMyPohPlugin.isDevMode()) return null;
        
        if (!pimpMyPohPlugin.isInPoh()) return null;

        renderZoneBorders(graphics);

        return null;
    }

    private void renderZoneBorders(Graphics2D graphics)
    {
        // Use cached usable chunk data
        UsableChunkCache cache = UsableChunkCache.getInstance();
        if (!cache.isValid())
        {
            return;
        }

        int startX = Constants.CHUNK_SIZE;
        int startZ = Constants.CHUNK_SIZE;
        int endX = Constants.SCENE_SIZE - Constants.CHUNK_SIZE;
        int endZ = Constants.SCENE_SIZE - Constants.CHUNK_SIZE;

        boolean[][] usableChunkGrid = cache.getUsableChunkGrid();
        int minUsableX = cache.getMinUsableX();
        int minUsableZ = cache.getMinUsableZ();
        int maxUsableX = cache.getMaxUsableX();
        int maxUsableZ = cache.getMaxUsableZ();
        
        int usableChunksWidth = (maxUsableX >= minUsableX) ? (maxUsableX - minUsableX + 1) : 0;
        int usableChunksHeight = (maxUsableZ >= minUsableZ) ? (maxUsableZ - minUsableZ + 1) : 0;

        int plane = client.getPlane();

        graphics.setStroke(new BasicStroke(STROKE_WIDTH));
        
        // Draw individual chunk borders - green for usable, blue for unusable
        // Also display room names at center of usable chunks
        graphics.setFont(new Font("Arial", Font.BOLD, 12));
        
        for (int chunkX = startX; chunkX <= endX; chunkX += Constants.CHUNK_SIZE)
        {
            for (int chunkZ = startZ; chunkZ <= endZ; chunkZ += Constants.CHUNK_SIZE)
            {
                int chunkGridX = (chunkX - startX) / Constants.CHUNK_SIZE;
                int chunkGridZ = (chunkZ - startZ) / Constants.CHUNK_SIZE;
                boolean isUsable = usableChunkGrid[chunkGridX][chunkGridZ];
                
                // Get the four corners of the chunk
                LocalPoint topLeft = new LocalPoint(chunkX << Perspective.LOCAL_COORD_BITS, chunkZ << Perspective.LOCAL_COORD_BITS);
                LocalPoint topRight = new LocalPoint((chunkX + Constants.CHUNK_SIZE) << Perspective.LOCAL_COORD_BITS, chunkZ << Perspective.LOCAL_COORD_BITS);
                LocalPoint bottomLeft = new LocalPoint(chunkX << Perspective.LOCAL_COORD_BITS, (chunkZ + Constants.CHUNK_SIZE) << Perspective.LOCAL_COORD_BITS);
                LocalPoint bottomRight = new LocalPoint((chunkX + Constants.CHUNK_SIZE) << Perspective.LOCAL_COORD_BITS, (chunkZ + Constants.CHUNK_SIZE) << Perspective.LOCAL_COORD_BITS);
                
                Point p1 = Perspective.localToCanvas(client, topLeft, plane);
                Point p2 = Perspective.localToCanvas(client, topRight, plane);
                Point p3 = Perspective.localToCanvas(client, bottomRight, plane);
                Point p4 = Perspective.localToCanvas(client, bottomLeft, plane);
                
                if (p1 != null && p2 != null && p3 != null && p4 != null)
                {
                    // Draw chunk borders if enabled
                    if (SHOW_CHUNK_BORDERS)
                    {
                        graphics.setColor(isUsable ? USABLE_CHUNK_BORDER_COLOR : CHUNK_BORDER_COLOR);
                        
                        GeneralPath chunkPath = new GeneralPath();
                        chunkPath.moveTo(p1.getX(), p1.getY());
                        chunkPath.lineTo(p2.getX(), p2.getY());
                        chunkPath.lineTo(p3.getX(), p3.getY());
                        chunkPath.lineTo(p4.getX(), p4.getY());
                        chunkPath.closePath();
                        graphics.draw(chunkPath);
                    }
                    
                    // Display room name at center of usable chunks
                    int centerChunkX = chunkX + Constants.CHUNK_SIZE / 2;
                    int centerChunkZ = chunkZ + Constants.CHUNK_SIZE / 2;
                    LocalPoint centerLp = new LocalPoint(centerChunkX << Perspective.LOCAL_COORD_BITS, centerChunkZ << Perspective.LOCAL_COORD_BITS);
                    Point centerP = Perspective.localToCanvas(client, centerLp, plane);
                    
                    // Display room name at center of usable chunks if enabled
                    if (SHOW_ROOM_NAMES && centerP != null && isUsable && usableChunksWidth > 0 && usableChunksHeight > 0)
                    {
                        // Convert chunk grid position to room coordinates (counting from top left)
                        // Top left chunk (minUsableX, minUsableZ) = room (1, 1)
                        // X increases from left to right, Y increases from top to bottom
                        int roomX = (chunkGridX - minUsableX) + 1;
                        int roomY = (chunkGridZ - minUsableZ) + 1; // Count from top (minUsableZ is top)
                        
                        // Clamp to valid room coordinates (1-8)
                        if (roomX >= 1 && roomX <= 8 && roomY >= 1 && roomY <= 8)
                        {
                            RoomPosition room = roomManagementService.getRoomAt(roomX, roomY);
                            
                            String roomText;
                            if (room != null && room.getRoomName() != null)
                            {
                                roomText = room.getRoomName() + " (" + roomX + "," + roomY + ")";
                            }
                            else
                            {
                                roomText = "(" + roomX + "," + roomY + ")";
                            }
                            
                            // Draw text with background for better visibility
                            graphics.setColor(new Color(0, 0, 0, 128)); // Semi-transparent black background
                            int textWidth = graphics.getFontMetrics().stringWidth(roomText);
                            int textHeight = graphics.getFontMetrics().getHeight();
                            graphics.fillRect(centerP.getX() - textWidth / 2 - 2, centerP.getY() - textHeight / 2 - 2, textWidth + 4, textHeight + 4);
                            
                            graphics.setColor(Color.WHITE);
                            graphics.drawString(roomText, centerP.getX() - textWidth / 2, centerP.getY() + textHeight / 4);
                        }
                    }
                }
            }
        }
    }

}

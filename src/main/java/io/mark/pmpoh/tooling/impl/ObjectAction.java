package io.mark.pmpoh.tooling.impl;

import io.mark.pmpoh.PimpMyPohConfig;
import io.mark.pmpoh.PimpMyPohPlugin;
import io.mark.pmpoh.objects.ObjectManager;
import io.mark.pmpoh.objects.ObjectType;
import io.mark.pmpoh.poh.ObjectSpawn;
import io.mark.pmpoh.poh.RoomPosition;
import io.mark.pmpoh.service.RoomManagementService;
import io.mark.pmpoh.tooling.ActionType;
import io.mark.pmpoh.tooling.BrushType;
import io.mark.pmpoh.util.Rotation;
import io.mark.pmpoh.util.ZoneTileUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the current action state in the editor.
 * Links together the action type, brush type, and selected tool.
 */
@Slf4j
@Getter
@Setter
@Singleton
public class ObjectAction implements MouseListener, KeyListener
{

    @Inject
    private Client client;

    @Inject
    private ObjectManager objectManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private PimpMyPohPlugin plugin;

    @Inject
    private PimpMyPohConfig config;

    @Inject
    private RoomManagementService roomManagementService;

    @Inject
    private net.runelite.client.plugins.PluginManager pluginManager;

    private ActionType actionType = ActionType.PLACE_OBJECT;
    private BrushType brushType = BrushType.SINGLE;
    private String selectedGameval;
    private RuneLiteObject previewObject;
    private int orientation = 0;
    private boolean mousePressed = false;
    private int clickX;
    private int clickY;
    @Getter
    private boolean editMode = false;
    private LocalPoint lastPreviewLocation;
    private int lastPreviewOrientation = -1;
    private int previousOrbState = -1;
    private int previousOrbSpeed = -1;

    // RuneLiteObject cache with LRU eviction (max 200 entries)
    private static final int MAX_OBJECT_CACHE_SIZE = 200;
    private final Map<String, RuneLiteObject> objectCache = new LinkedHashMap<String, RuneLiteObject>(MAX_OBJECT_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, RuneLiteObject> eldest) {
            if (size() > MAX_OBJECT_CACHE_SIZE) {
                // Unregister the object before removing from cache
                RuneLiteObject obj = eldest.getValue();
                if (obj != null && obj.isActive()) {
                    client.removeRuneLiteObject(obj);
                }
                return true;
            }
            return false;
        }
    };


    /**
     * Check if an object is currently selected for placement
     */
    public boolean hasSelectedObject()
    {
        return selectedGameval != null && !selectedGameval.isEmpty();
    }


    @Override
    public MouseEvent mouseClicked(MouseEvent mouseEvent) {
        return mouseEvent;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent mouseEvent) {
        if (shouldProcess() && editMode && mouseEvent.getButton() == MouseEvent.BUTTON1) {
            mousePressed = true;
            Point mousePos = client.getMouseCanvasPosition();
            clickX = mousePos.getX();
            clickY = mousePos.getY();

            if (client.getSelectedSceneTile().getLocalLocation() != null && selectedGameval != null) {
                clientThread.invoke(() -> {
                    spawnObject();
                });
                mouseEvent.consume();
            }
        }
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent mouseEvent) {
        mousePressed = false;
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent mouseEvent) {
        return mouseEvent;
    }


    @Override
    public MouseEvent mouseExited(MouseEvent mouseEvent) {
        // Remove preview when mouse exits the game area
        clientThread.invokeLater(() -> removePreview());
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent mouseEvent) {
        updatePreviewIfNeeded();
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent mouseEvent) {
        updatePreviewIfNeeded();
        return mouseEvent;
    }

    private void updatePreviewIfNeeded() {
        if (shouldProcess() && actionType == ActionType.PLACE_OBJECT && editMode && hasSelectedObject()) {
            Tile tile = client.getSelectedSceneTile();
            clientThread.invokeLater(() -> updatePreviewObject(tile));
        } else {
            clientThread.invokeLater(() -> removePreview());
        }
    }

    public boolean shouldProcess() {
        return client.getGameState() == GameState.LOGGED_IN;
    }

    public void spawnObject() {
        Tile tile = client.getSelectedSceneTile();
        if (tile == null) return;

        LocalPoint localPoint = tile.getLocalLocation();
        if (localPoint == null) return;

        ObjectType objectType = objectManager.getByGameval(selectedGameval);
        if (objectType == null) return;

        // Create and register object
        RuneLiteObject runeLiteObject = client.createRuneLiteObject();
        runeLiteObject.setDrawFrontTilesFirst(false);

        Model model = objectType.getModel(client);
        if (model != null) {
            runeLiteObject.setModel(model);
        }

        runeLiteObject.setOrientation(orientation);
        runeLiteObject.setLocation(localPoint, 0);
        runeLiteObject.setActive(true);
        client.registerRuneLiteObject(runeLiteObject);

        // Save object to room
        saveObjectToRoom(localPoint, selectedGameval, orientation);
    }

    /**
     * Save object to room position with tile coordinates
     */
    private void saveObjectToRoom(LocalPoint localPoint, String gameval, int orientation) {
        // Convert local point to zone and tile coordinates (uses cached bounds)
        int[] zoneTile = ZoneTileUtil.localPointToZoneTile(localPoint);
        if (zoneTile == null || zoneTile.length < 3) {
            log.warn("Failed to convert local point to zone/tile coordinates. LocalPoint: ({}, {})", 
                    localPoint.getX(), localPoint.getY());
            return;
        }

        int zoneX = zoneTile[0];
        int zoneY = zoneTile[1];
        int tileIndex = zoneTile[2];

        // Convert tile index to tile coordinates within zone
        int tileX = tileIndex % 8;
        int tileZ = tileIndex / 8;

        // Find the room at this zone location
        RoomPosition room = roomManagementService.getRoomAt(zoneX, zoneY);
        if (room == null) {
            log.error("No room found at zone ({}, {}). Cannot save object. Available rooms: {}", 
                    zoneX, zoneY, roomManagementService.getRoomsByIndex().size());
            return;
        }

        // Create ObjectSpawn with tile coordinates
        ObjectSpawn objectSpawn = new ObjectSpawn();
        objectSpawn.setGameval(gameval);
        objectSpawn.setTileX(tileX);
        objectSpawn.setTileY(tileZ);
        objectSpawn.setOrientation(orientation);

        // Add to room's objects list
        if (room.getObjects() == null) {
            room.setObjects(new java.util.ArrayList<>());
        }
        room.getObjects().add(objectSpawn);

        log.debug("Added object {} to room {} at zone ({}, {}), tile ({}, {}). Total objects in room: {}", 
                gameval, room.getRoomName(), zoneX, zoneY, tileX, tileZ, room.getObjects().size());

        // Save room positions immediately
        try {
            roomManagementService.saveRooms();
            log.info("Successfully saved object {} to room {} at zone ({}, {}), tile ({}, {})",
                    gameval, room.getRoomName(), zoneX, zoneY, tileX, tileZ);
        } catch (Exception e) {
            log.error("Failed to save rooms after placing object {}", gameval, e);
        }
    }

    /**
     * Update the preview object position to follow the mouse
     */
    private void updatePreviewObject(Tile tile) {
        if (client.isMenuOpen() || tile == null || !hasSelectedObject()) {
            if (previewObject != null) previewObject.setActive(false);
            return;
        }

        LocalPoint localPoint = tile.getLocalLocation();
        if (localPoint == null || !localPoint.isInScene()) {
            if (previewObject != null) previewObject.setActive(false);
            lastPreviewLocation = null;
            return;
        }

        // Create preview object if needed
        if (previewObject == null) {
            previewObject = client.createRuneLiteObject();
            client.registerRuneLiteObject(previewObject);
            previewObject.setActive(false);
        }

        ObjectType objectType = objectManager.getByGameval(selectedGameval);
        if (objectType == null) {
            previewObject.setActive(false);
            return;
        }

        // Calculate orientation
        int currentOrientation = calculateOrientation(localPoint);

        // Skip update if nothing changed
        if (localPoint.equals(lastPreviewLocation) && currentOrientation == lastPreviewOrientation && previewObject.isActive()) {
            return;
        }

        // Update preview
        Model model = objectType.getModel(client, true);
        if (model == null) {
            previewObject.setActive(false);
            return;
        }

        previewObject.setModel(model);
        previewObject.setOrientation(currentOrientation);
        previewObject.setLocation(localPoint, client.getTopLevelWorldView().getPlane());
        previewObject.setActive(true);

        lastPreviewLocation = localPoint;
        lastPreviewOrientation = currentOrientation;
    }

    private int calculateOrientation(LocalPoint localPoint) {
        if (!mousePressed) {
            return Rotation.roundRotation(orientation);
        }

        Point mousePos = client.getMouseCanvasPosition();
        double dx = mousePos.getX() - clickX;
        double dy = -(mousePos.getY() - clickY);

        if (Math.sqrt(dx * dx + dy * dy) < 40) {
            return Rotation.roundRotation(orientation);
        }

        return Rotation.getJagexDegrees((int)dx, (int)dy, client.getCameraYaw(), client.getCameraPitch());
    }

    /**
     * Remove the preview object
     */
    private void removePreview() {
        if (previewObject != null) {
            previewObject.setActive(false);
            client.removeRuneLiteObject(previewObject);
            previewObject = null;
        }
        lastPreviewLocation = null;
        lastPreviewOrientation = -1;
    }

    /**
     * Clear selection and remove preview
     */
    public void clearSelection() {
        this.selectedGameval = null;
        if (editMode) {
            editMode = false; // Exit edit mode when selection is cleared
            // Restore previous orb state and speed
            clientThread.invoke(() -> {
                if (previousOrbState != -1) {
                    client.setOculusOrbState(previousOrbState);
                }
                if (previousOrbSpeed != -1) {
                    client.setOculusOrbNormalSpeed(previousOrbSpeed);
                }
            });
        }
        removePreview();
    }

    /**
     * Clear the RuneLiteObject cache
     */
    public void clearObjectCache() {
        objectCache.values().stream()
                .filter(obj -> obj != null && obj.isActive())
                .forEach(obj -> client.removeRuneLiteObject(obj));
        objectCache.clear();
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // No action needed
    }

    @Override
    public void keyPressed(java.awt.event.KeyEvent keyEvent) {
        // Toggle edit mode with configured keybind
        if (config.editModeKeybind().matches(keyEvent)) {
            if (!plugin.hasSaveFile()) {
                clientThread.invoke(() ->
                        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Please view poh viewer before carrying on", null));
                return;
            }

            editMode = !editMode;
            Tile tile = client.getSelectedSceneTile();
            clientThread.invoke(() -> {
                if (editMode) {
                    client.setOculusOrbState(1);
                    client.setOculusOrbNormalSpeed(36);
                    
                    if (hasSelectedObject()) {
                        updatePreviewObject(tile);
                    }
                } else {
                    client.setOculusOrbState(0);
                    client.setOculusOrbNormalSpeed(12);
                    removePreview();
                }
            });
        }

        // Rotate with configured keybind
        if (config.rotateKeybind().matches(keyEvent) && editMode && hasSelectedObject()) {
            orientation = (orientation + 256) % 2048;
            Tile tile = client.getSelectedSceneTile();
            if (tile != null) {
                clientThread.invoke(() -> updatePreviewObject(tile));
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // No action needed
    }
}

/*
 * Copyright (c) 2022, Abex
 * Copyright (c) 2022, Mark
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.mark.pmpoh;

import com.google.inject.Provides;
import io.mark.pmpoh.objects.ObjectManager;
import io.mark.pmpoh.overlay.RoomDeveloperOverlay;
import io.mark.pmpoh.service.ObjectSpawnService;
import io.mark.pmpoh.service.RoomManagementService;
import io.mark.pmpoh.tooling.impl.ObjectAction;
import io.mark.pmpoh.poh.RoomPosition;
import io.mark.pmpoh.util.UsableChunkCache;
import io.mark.pmpoh.ui.EditorPanel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.*;

@PluginDescriptor(
        name = "Pimp My POH",
        description = "Customize your POH with full freedom: place objects anywhere, spawn NPCs with pathing, exclude NPCs from rooms, edit terrain, and toggle viewing other players' custom POH layouts.",
        tags = {"poh", "construction", "custom", "objects", "npcs", "editor", "terrain"}
)

@Slf4j
public class PimpMyPohPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private PimpMyPohConfig config;

    @Setter(AccessLevel.PACKAGE)
    @Getter(AccessLevel.PACKAGE)
    private EditorPanel panel;

    private NavigationButton button;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ObjectManager objectManager;

    @Inject
    @Getter
    private ObjectAction objectAction;

    @Inject
    private MouseManager mouseManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private RoomDeveloperOverlay roomDeveloperOverlay;
    
    @Inject
    private RoomManagementService roomManagementService;
    
    @Inject
    private ObjectSpawnService objectSpawnService;

    @Inject
    private PluginManager pluginManager;

    @Provides
    PimpMyPohConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PimpMyPohConfig.class);
    }

    private static final List<Integer> POH_REGIONS = List.of(7257, 7513, 7514, 7769, 7770, 8025, 8026);


    @Override
    protected void startUp() {
        panel = injector.getInstance(EditorPanel.class);
        
        objectManager.setOnLoadCompleteCallback(() -> {
            clientThread.invokeLater(() -> {
                if (objectManager.isReady() && isInPoh() && roomManagementService.getRoomsByIndex().isEmpty()) {
                    UsableChunkCache.getInstance().calculate(client);
                    if (UsableChunkCache.getInstance().isValid()) {
                        loadHouseFiles();
                    } else {
                        log.warn("UsableChunkCache is not valid, will retry when world view loads");
                    }
                }
            });
        });
        
        objectManager.init();
        panel.setup();
        mouseManager.registerMouseListener(objectAction);
        keyManager.registerKeyListener(objectAction);
        overlayManager.add(roomDeveloperOverlay);
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        button = NavigationButton.builder()
                .tooltip("Pimp My Poh")
                .icon(icon)
                .priority(3)
                .panel(panel)
                .build();
        clientToolbar.addNavigation(button);
    }

    @Override
    public void shutDown() {
        clientToolbar.removeNavigation(button);
        objectManager.clean();
        overlayManager.remove(roomDeveloperOverlay);
        mouseManager.unregisterMouseListener(objectAction);
        keyManager.unregisterKeyListener(objectAction);
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged gameStateChanged) {
        // Whenever game state changes (login/logout/world hop), refresh toolbox button state
        SwingUtilities.invokeLater(() -> {
            if (panel != null) {
                panel.updateToolboxButtonState();
            }
        });
    }

    @Subscribe
    public void onWorldViewLoaded(WorldViewLoaded worldViewLoaded) {
        boolean isInPoh = isInPoh();

        if (isInPoh) {
            UsableChunkCache.getInstance().calculate(client);
            
            // Ensure cache is valid before loading
            if (!UsableChunkCache.getInstance().isValid()) {
                log.warn("UsableChunkCache is not valid after calculation");
                return;
            }

            // Wait for ObjectManager to be ready before loading house files
            if (!objectManager.isReady()) {
                log.info("ObjectManager not ready yet, will load house when objects are ready");
                // The callback set in startUp() will handle loading when objects are ready
                return;
            }

            loadHouseFiles();
        } else {
            UsableChunkCache.getInstance().clear();
            // Update toolbox button state
            SwingUtilities.invokeLater(() -> {
                if (panel != null) {
                    panel.updateToolboxButtonState();
                }
            });
        }
    }
    
    private void loadHouseFiles() {
        String username = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
        if (username != null) {
            roomManagementService.loadRooms(username);
            log.info("Loaded {} rooms from save file", roomManagementService.getRoomsByIndex().size());
            
            // Load objects after a short delay to ensure everything is ready
            clientThread.invokeLater(() -> {
                int spawned = objectSpawnService.loadObjectsFromRooms(roomManagementService.getRoomsByIndex());
                log.info("Spawned {} objects from save file", spawned);
            });
        }
        
        // Update toolbox button state
        SwingUtilities.invokeLater(() -> {
            if (panel != null) {
                panel.updateToolboxButtonState();
            }
        });
    }
    
    /**
     * Check if a save file exists (for ObjectAction to check before allowing edit mode)
     */
    public boolean hasSaveFile() {
        String username = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
        return username != null && roomManagementService.hasSaveFile(username);
    }

    /**
     * Check if the player is currently in a POH
     * @return true if in POH, false otherwise
     */
    public boolean isInPoh() {
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        WorldView worldView = client.getTopLevelWorldView();
        if (worldView == null) {
            return false;
        }

        int[] regions = worldView.getMapRegions();
        for (int region : regions) {
            if (POH_REGIONS.contains(region)) {
                return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event) {
        if (event.getScriptId() == 1376) {
            Object[] args = event.getScriptEvent().getArguments();

            if (args != null && args.length == 6) {
                try {
                    int index = ((Number) args[1]).intValue();
                    int dbRowId = ((Number) args[2]).intValue();
                    int roomInfo = ((Number) args[3]).intValue();
                    int flag1 = ((Number) args[4]).intValue();
                    int flag2 = ((Number) args[5]).intValue();

                    RoomPosition roomPos = RoomPosition.fromBitpacked(client, index, dbRowId, roomInfo, flag1, flag2);
                    roomManagementService.addToBatch(index, roomPos);
                    
                    if (!roomManagementService.isBatchProcessingScheduled()) {
                        roomManagementService.setBatchProcessingScheduled(true);
                        clientThread.invokeLater(() -> {
                            processRoomBatch();
                            roomManagementService.setBatchProcessingScheduled(false);
                        });
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse room script arguments", e);
                }
            }
        }
    }
    
    /**
     * Process the current batch of rooms
     */
    private void processRoomBatch() {
        List<Integer> roomsToCleanup = roomManagementService.processRoomBatch();
        objectSpawnService.removeRoomObjects(roomsToCleanup);
        objectSpawnService.loadObjectsFromRooms(roomManagementService.getRoomsByIndex());
    }

}

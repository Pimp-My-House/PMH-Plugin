package io.mark.pmpoh.saving.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.mark.pmpoh.poh.RoomPosition;
import io.mark.pmpoh.saving.HouseStorageStrategy;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.reflect.TypeToken;

/**
 * Local file storage strategy for room position data
 */
@Slf4j
@Singleton
public class LocalFileStorageStrategy implements HouseStorageStrategy {


    private Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static final String SAVE_DIR = "pimp-my-poh";
    
    private File getSaveFile(String username) {
        String userHome = System.getProperty("user.home");
        Path saveDir = Paths.get(userHome, ".runelite", SAVE_DIR);
        
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            log.warn("Failed to create save directory", e);
        }
        
        String saveFileName = username + "-room-positions.json";
        return saveDir.resolve(saveFileName).toFile();
    }
    
    @Override
    public boolean exists(String username) {
        File saveFile = getSaveFile(username);
        return saveFile.exists() && saveFile.isFile();
    }
    
    @Override
    public Map<Integer, RoomPosition> load(String username) {
        File saveFile = getSaveFile(username);
        
        if (!saveFile.exists()) {
            log.debug("No save file found at: {}", saveFile.getAbsolutePath());
            return new HashMap<>();
        }
        
        try (FileReader reader = new FileReader(saveFile)) {
            java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<Integer, RoomPosition>>(){}.getType();
            Map<Integer, RoomPosition> rooms = gson.fromJson(reader, type);
            if (rooms != null) {
                log.info("Loaded {} rooms from local file", rooms.size());
                return rooms;
            }
        } catch (IOException e) {
            log.warn("Failed to load room positions from local file", e);
        }
        
        return new HashMap<>();
    }
    
    @Override
    public void save(String username, Map<Integer, RoomPosition> roomsByIndex) {
        File saveFile = getSaveFile(username);
        
        try (FileWriter writer = new FileWriter(saveFile)) {
            gson.toJson(roomsByIndex, writer);
            log.debug("Saved {} rooms to local file", roomsByIndex.size());
        } catch (IOException e) {
            log.warn("Failed to save room positions to local file", e);
        }
    }
}


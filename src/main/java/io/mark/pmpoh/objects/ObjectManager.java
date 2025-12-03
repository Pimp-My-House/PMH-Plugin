package io.mark.pmpoh.objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class ObjectManager {
    @Getter
    private final Map<String, ObjectType> objectsByGameval = new HashMap<>();
    
    @Getter
    private volatile boolean isLoading = false;
    
    @Getter
    private volatile boolean isReady = false;
    
    @Getter
    private volatile String loadingError = null;
    
    @Setter
    private Runnable onLoadCompleteCallback = null;
    
    private static final String OBJECTS_URL = "https://raw.githubusercontent.com/Pimp-My-House/PMH-Assets/main/objects/objects.json";
    private static final String MANIFEST_URL = "https://raw.githubusercontent.com/Pimp-My-House/PMH-Assets/main/manifest.json";
    private static final String SAVE_DIR = "pimp-my-poh";
    private static final String OBJECTS_FILE = "objects.json";
    private static final int BUFFER_SIZE = 8192;
    private static final int HASH_PREFIX_LENGTH = 8;

    private File getObjectsFile() {
        String userHome = System.getProperty("user.home");
        Path saveDir = Paths.get(userHome, ".runelite", SAVE_DIR);
        
        try {
            Files.createDirectories(saveDir);
        } catch (IOException e) {
            log.warn("Failed to create save directory", e);
        }
        
        return saveDir.resolve(OBJECTS_FILE).toFile();
    }
    
    public void init() {
        isLoading = true;
        isReady = false;
        loadingError = null;
        
        // Start async download and load
        CompletableFuture.runAsync(() -> {
            try {
                downloadAndLoadObjects();
            } catch (Exception e) {
                log.error("Failed to download and load objects.json", e);
                loadingError = "Failed to load objects: " + e.getMessage();
                isLoading = false;
            }
        });
    }
    
    private void downloadAndLoadObjects() {
        File localFile = getObjectsFile();
        
        try {
            if (shouldDownload(localFile)) {
                downloadObjectsFile(localFile);
            }
            
            loadFromFile(localFile);
            markAsReady();
            
        } catch (Exception e) {
            log.error("Failed to download objects.json", e);
            handleLoadFailure(localFile, e);
        }
    }
    
    private boolean shouldDownload(File localFile) {
        if (!localFile.exists()) {
            log.info("Local objects.json not found, will download");
            return true;
        }
        
        String remoteSha256 = getRemoteSha256();
        if (remoteSha256 == null) {
            log.info("Could not check manifest, using existing local file");
            return false;
        }
        
        String localSha256 = calculateFileSha256(localFile);
        if (localSha256 != null && localSha256.equals(remoteSha256)) {
            log.info("Local objects.json is up to date (SHA256: {}...)", 
                localSha256.substring(0, HASH_PREFIX_LENGTH));
            return false;
        }
        
        log.info("Local objects.json is outdated. Remote: {}..., Local: {}", 
            remoteSha256.substring(0, HASH_PREFIX_LENGTH),
            localSha256 != null ? localSha256.substring(0, HASH_PREFIX_LENGTH) + "..." : "unknown");
        return true;
    }
    
    private void downloadObjectsFile(File localFile) throws IOException {
        log.info("Downloading objects.json from GitHub...");
        
        URL url = new URL(OBJECTS_URL);
        try (InputStream inputStream = url.openStream();
             FileOutputStream outputStream = new FileOutputStream(localFile)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        
        log.info("Downloaded objects.json to {}", localFile.getAbsolutePath());
    }
    
    private void markAsReady() {
        isReady = true;
        isLoading = false;
        log.info("Successfully loaded {} objects from objects.json", objectsByGameval.size());
        notifyLoadComplete();
    }
    
    private void handleLoadFailure(File localFile, Exception e) {
        if (localFile.exists()) {
            log.info("Download failed, attempting to load from local file...");
            try {
                loadFromFile(localFile);
                isReady = true;
                isLoading = false;
                log.info("Successfully loaded {} objects from local file", objectsByGameval.size());
                notifyLoadComplete();
                return;
            } catch (Exception localException) {
                log.error("Failed to load from local file", localException);
            }
        }
        
        loadingError = "Failed to download and load objects: " + e.getMessage();
        isLoading = false;
        notifyLoadComplete();
    }
    
    private void notifyLoadComplete() {
        if (onLoadCompleteCallback != null) {
            onLoadCompleteCallback.run();
        }
    }
    
    private String getRemoteSha256() {
        try {
            URL manifestUrl = new URL(MANIFEST_URL);
            Gson gson = new Gson();
            
            try (InputStream inputStream = manifestUrl.openStream();
                 InputStreamReader reader = new InputStreamReader(inputStream)) {
                
                JsonObject manifest = gson.fromJson(reader, JsonObject.class);
                JsonObject objectsJson = manifest.getAsJsonObject("objects.json");
                
                if (objectsJson != null && objectsJson.has("sha256")) {
                    return objectsJson.get("sha256").getAsString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch manifest.json, will download objects.json anyway", e);
        }
        
        return null;
    }
    
    private String calculateFileSha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            log.warn("Failed to calculate SHA256 for local file", e);
            return null;
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private void loadFromFile(File file) throws IOException {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ObjectType.class, new ObjectTypeDeserializer())
                .create();

        Type listType = new TypeToken<List<ObjectType>>(){}.getType();
        
        try (FileReader reader = new FileReader(file)) {
            List<ObjectType> objectTypes = gson.fromJson(reader, listType);
            
            if (objectTypes == null) {
                throw new IOException("Failed to parse objects.json: null result");
            }
            
            objectsByGameval.clear();
            objectTypes.forEach(objectType -> objectsByGameval.put(objectType.name, objectType));
        }
    }
    
    public void retryLoad() {
        isLoading = true;
        isReady = false;
        loadingError = null;
        
        CompletableFuture.runAsync(() -> {
            try {
                downloadAndLoadObjects();
            } catch (Exception e) {
                log.error("Failed to retry download and load objects.json", e);
                loadingError = "Failed to load objects: " + e.getMessage();
                isLoading = false;
            }
        });
    }

    public ObjectType getByGameval(String gameval) {
        return objectsByGameval.get(gameval);
    }

    public void clean() {
        objectsByGameval.clear();
    }

    public List<Map.Entry<String, ObjectType>> getAllObjectsSorted() {
        return objectsByGameval.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());
    }

}

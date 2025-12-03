package io.mark.pmpoh.objects;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.gameval.VarbitID;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Slf4j
public class ObjectType
{
    public int id;
    public String name;
    public int animationId;
    public int[] objectModels;
    public int[] objectTypes;
    public int modelSizeX;
    public int modelSizeY;
    public int modelSizeZ;
    public int offsetX = 0;
    public int offsetY = 0;
    public int offsetZ = 0;
    public int ambient;
    public int contrast;
    public int[] recolorToReplace;
    public int[] recolorToFind;
    public int[] textureToReplace;
    public int[] retextureToFind;
    public boolean rotated = false;

    // Model cache with LRU eviction (max 200 entries, shared across all ObjectType instances)
    private static final int MAX_CACHE_SIZE = 200;
    private static final Map<String, Model> modelCache = new LinkedHashMap<String, Model>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Model> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    @Override
    public String toString()
    {
        return name + " (" + id + ")";
    }

    public final Model getModel(Client client, boolean preview) {

        ModelData model = loadModel(client);
        if (model == null) {
            return null;
        }

        Model result;
        if (preview) {
            ModelData merge = client.mergeModels(model,client.loadModelData(18871));
            merge.cloneTransparencies();

            byte[] transparencies = merge.getFaceTransparencies();

            for (int i = 0; i < merge.getFaceCount(); i++) {
                transparencies[i] = 5;
            }

            result = merge.light(ambient + 64, contrast + 768, -50, -10, -50);
        } else {
            result = model.shallowCopy().light(ambient + 64, contrast + 768, -50, -10, -50);
        }

        return result;
    }

    public final Model getModel(Client client) {
        String cacheKey = name + "_normal";
        Model cachedModel = modelCache.get(cacheKey);
        if (cachedModel != null) {
            return cachedModel;
        }

        ModelData data = loadModel(client);
        if (data == null) {
            return null;
        }

        Model result = data.shallowCopy().light(ambient + 64, contrast + 768, -50, -10, -50);
        
        // Cache the model
        modelCache.put(cacheKey, result);
        return result;
    }

    protected ModelData loadModel(Client client) {
        if (objectModels == null || objectModels.length == 0) {
            return null;
        }

        List<Integer> modelsToLoad = new ArrayList<>(objectModels.length);
        for (int id : objectModels) {
            modelsToLoad.add(id);
        }

        ModelData[] datas = new ModelData[modelsToLoad.size()];

        for (int i = 0; i < datas.length; i++) {
            int modelId = modelsToLoad.get(i);
            ModelData model = client.loadModelData(modelId);
            if (model == null) {
                return null;
            }
            datas[i] = model;
        }


        return client.mergeModels(datas);
    }

    /**
     * Clear the model cache
     */
    public static void clearModelCache() {
        modelCache.clear();
    }

}
package io.mark.pmpoh.objects;

import com.google.gson.*;

import java.lang.reflect.Type;

public class ObjectTypeDeserializer implements JsonDeserializer<ObjectType> {
    @Override
    public ObjectType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        ObjectType objectType = new ObjectType();

        // Map shortened field names to full field names
        if (jsonObject.has("i")) {
            objectType.id = jsonObject.get("i").getAsInt();
        }
        if (jsonObject.has("g")) {
            objectType.name = jsonObject.get("g").getAsString();
        }
        if (jsonObject.has("a")) {
            objectType.animationId = jsonObject.get("a").getAsInt();
        }
        if (jsonObject.has("om")) {
            objectType.objectModels = parseIntArray(jsonObject.get("om").getAsJsonArray());
        }
        if (jsonObject.has("ot")) {
            objectType.objectTypes = parseIntArray(jsonObject.get("ot").getAsJsonArray());
        }
        if (jsonObject.has("sz")) {
            objectType.modelSizeZ = jsonObject.get("sz").getAsInt();
        }
        if (jsonObject.has("ox")) {
            objectType.offsetX = jsonObject.get("ox").getAsInt();
        }
        if (jsonObject.has("oy")) {
            objectType.offsetY = jsonObject.get("oy").getAsInt();
        }
        if (jsonObject.has("oz")) {
            objectType.offsetZ = jsonObject.get("oz").getAsInt();
        }
        if (jsonObject.has("am")) {
            objectType.ambient = jsonObject.get("am").getAsInt();
        }
        if (jsonObject.has("co")) {
            objectType.contrast = jsonObject.get("co").getAsInt();
        }
        if (jsonObject.has("rr")) {
            objectType.recolorToReplace = parseIntArray(jsonObject.get("rr").getAsJsonArray());
        }
        if (jsonObject.has("rf")) {
            objectType.recolorToFind = parseIntArray(jsonObject.get("rf").getAsJsonArray());
        }
        if (jsonObject.has("tr")) {
            objectType.textureToReplace = parseIntArray(jsonObject.get("tr").getAsJsonArray());
        }
        if (jsonObject.has("tf")) {
            objectType.retextureToFind = parseIntArray(jsonObject.get("tf").getAsJsonArray());
        }
        if (jsonObject.has("r")) {
            objectType.rotated = jsonObject.get("r").getAsBoolean();
        }

        return objectType;
    }

    private int[] parseIntArray(JsonArray jsonArray) {
        int[] result = new int[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            result[i] = jsonArray.get(i).getAsInt();
        }
        return result;
    }
}


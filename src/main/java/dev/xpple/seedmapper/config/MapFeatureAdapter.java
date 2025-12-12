package dev.xpple.seedmapper.config;

import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dev.xpple.seedmapper.seedmap.MapFeature;

import java.io.IOException;

public class MapFeatureAdapter extends TypeAdapter<MapFeature> {
    @Override
    public void write(JsonWriter writer, MapFeature feature) throws IOException {
        writer.value(feature.getName());
    }

    @Override
    public MapFeature read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            throw new JsonSyntaxException("Expected map feature name but found null");
        }
        String name = reader.nextString();
        MapFeature feature = MapFeature.BY_NAME.get(name);
        if (feature == null) {
            throw new JsonSyntaxException("Unknown map feature: " + name);
        }
        return feature;
    }
}

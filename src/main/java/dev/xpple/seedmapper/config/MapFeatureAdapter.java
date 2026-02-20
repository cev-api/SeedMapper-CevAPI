package dev.xpple.seedmapper.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
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
        String name = reader.nextString();
        MapFeature feature = MapFeature.BY_NAME.get(name);
        // Keep config loading resilient to stale/unknown feature names from older builds.
        return feature != null ? feature : MapFeature.WAYPOINT;
    }
}

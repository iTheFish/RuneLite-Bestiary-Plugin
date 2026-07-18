package net.runelite.client.plugins.bestiary.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import net.runelite.api.coords.WorldPoint;

import java.io.IOException;

/** Gson TypeAdapter that serialises WorldPoint as {x, y, plane}. */
public class WorldPointAdapter extends TypeAdapter<WorldPoint> {

    @Override
    public void write(JsonWriter out, WorldPoint value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("x").value(value.getX());
        out.name("y").value(value.getY());
        out.name("plane").value(value.getPlane());
        out.endObject();
    }

    @Override
    public WorldPoint read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        int x = 0, y = 0, plane = 0;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "x":     x     = in.nextInt(); break;
                case "y":     y     = in.nextInt(); break;
                case "plane": plane = in.nextInt(); break;
                default:      in.skipValue();       break;
            }
        }
        in.endObject();
        return new WorldPoint(x, y, plane);
    }
}


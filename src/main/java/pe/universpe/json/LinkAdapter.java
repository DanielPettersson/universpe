package pe.universpe.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import pe.universpe.graph.Link;

import java.lang.reflect.Type;

public class LinkAdapter implements JsonSerializer<Link> {

    @Override
    public JsonElement serialize(Link link, Type type, JsonSerializationContext jsonSerializationContext) {
        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("source", link.getSource().getId());
        jsonObject.addProperty("target", link.getTarget().getId());
        return jsonObject;
    }
}

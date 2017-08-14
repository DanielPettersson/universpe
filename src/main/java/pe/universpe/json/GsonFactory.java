package pe.universpe.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NoArgsConstructor;
import pe.universpe.graph.Link;
import pe.universpe.graph.Node;

import static lombok.AccessLevel.PRIVATE;

@NoArgsConstructor(access = PRIVATE)
public class GsonFactory {

    public static Gson create() {
        final GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Link.class, new LinkAdapter());
        gsonBuilder.registerTypeAdapter(Node.class, new NodeAdapter());
        return gsonBuilder.create();
    }

}

package pe.universpe;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pe.universpe.data.Fetcher;
import pe.universpe.data.Filter;
import pe.universpe.graph.Data;
import pe.universpe.json.GsonFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static spark.Spark.*;

@Slf4j
public class Main {

    private static final Map<UUID, Data> dataMap = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> progressMap = new ConcurrentHashMap<>();

    public static void main(final String[] args) {

        port(8000);
        staticFiles.location("");

        final ExecutorService executorService = Executors.newCachedThreadPool();

        get("request-data", (req, res) -> {
            final UUID uuid = UUID.randomUUID();
            executorService.submit(new CreateDataRunnable(new Filter(req), uuid));
            return uuid.toString();
        });

        get("progress-data", (req, res) -> {
            final UUID uuid = UUID.fromString(req.queryParams("data-uuid"));
            return progressMap.getOrDefault(uuid, 0);
        });

        get("get-data", (req, res) -> {
            res.type("application/json");
            final UUID uuid = UUID.fromString(req.queryParams("data-uuid"));
            final Data data = dataMap.get(uuid);
            dataMap.remove(uuid);
            progressMap.remove(uuid);
            return data;
        }, d -> GsonFactory.create().toJson(d));
    }

    @AllArgsConstructor
    public static class CreateDataRunnable implements Runnable {

        private final Filter filter;
        private final UUID uuid;

        @Override
        public void run() {
            final Data data = new Fetcher(uuid).fetchData(filter);
            dataMap.put(uuid, data);
            progressMap.put(uuid, 100);
        }
    }

}

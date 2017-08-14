package pe.universpe;

import lombok.extern.slf4j.Slf4j;
import pe.universpe.data.Fetcher;
import pe.universpe.data.Filter;
import pe.universpe.graph.Data;
import pe.universpe.json.GsonFactory;

import java.sql.SQLException;

import static spark.Spark.*;

@Slf4j
public class Main {

    public static void main(final String[] args) throws SQLException {

        port(80);
        staticFiles.location("");

        get("data.json",
                (req, res) -> {
                    res.type("application/json");
                    final Data data = new Fetcher().fetchData();
                    return new Filter(req).doFilter(data);
                }, d -> GsonFactory.create().toJson(d));
    }
}

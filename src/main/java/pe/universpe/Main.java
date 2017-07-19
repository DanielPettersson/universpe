package pe.universpe;

import static spark.Spark.*;

public class Main {

    public static void main(final String[] args) {

        port(80);
        staticFiles.location("");
        init();

    }
}

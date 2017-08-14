package pe.universpe.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import pe.universpe.graph.Node;

import java.lang.reflect.Type;
import java.text.NumberFormat;
import java.util.Locale;

public class NodeAdapter implements JsonSerializer<Node> {

    private final NumberFormat currencyFormatInstance;

    NodeAdapter() {
        currencyFormatInstance = NumberFormat.getCurrencyInstance(new Locale("sv","SE"));
    }

    @Override
    public JsonElement serialize(Node node, Type type, JsonSerializationContext jsonSerializationContext) {


        final String amountStr = currencyFormatInstance.format(node.getVal() * 1000);

        final JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("id", node.getId());
        jsonObject.addProperty("name", String.format("%s, (%s) %s", node.getId(), formatNodeType(node.getType()), amountStr));
        jsonObject.addProperty("group", node.getType().ordinal());
        jsonObject.addProperty("val", node.getVal());
        jsonObject.addProperty("color", node.isSelected() ? "#FF0000" : "");
        return jsonObject;
    }

    private static String formatNodeType(final Node.Type type) {
        switch (type) {
            case Company:
                return "Företag";
            case Client:
                return "Kund";
            case Supplier:
                return "Leverantör";
            default:
                return "";
        }

    }
}

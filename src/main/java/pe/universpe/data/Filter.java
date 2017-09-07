package pe.universpe.data;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import pe.universpe.graph.Data;
import pe.universpe.graph.Link;
import pe.universpe.graph.Node;
import spark.Request;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Getter
public class Filter {

    private static final long NORMALIZE_CEIL = 100000;
    private static final String PE_ACCOUNTING = "PE Accounting Sweden AB";

    int minThousands;
    List<String> includeCompany;
    List<String> excludeCompany;
    List<String> includeSupplier;
    List<String> excludeSupplier;
    List<String> includeClient;
    List<String> excludeClient;

    boolean prune;

    public Filter(final Request req) {
        minThousands = getIntParam(req, "minThousands");
        includeCompany = getListParam(req, "includeCompany");
        excludeCompany = getListParam(req, "excludeCompany");
        includeSupplier = getListParam(req, "includeSupplier");
        excludeSupplier = getListParam(req, "excludeSupplier");
        includeClient = getListParam(req, "includeClient");
        excludeClient = getListParam(req, "excludeClient");
        prune = parseBoolean(req.queryParams("prune"));
    }

    Data postProcess(final pe.universpe.graph.Data data) {

        final Collection<Link> links = data.getLinks();
        final Collection<Node> nodes = data.getNodes();

        nodes.stream().filter(n -> n.getId().equalsIgnoreCase(PE_ACCOUNTING) || isIncludedClient(n) || isIncludedSupplier(n)).forEach(n -> n.setSelected(true));

        Set<Node> filteredNodes = nodes.stream().filter(n -> n.isSelected() || n.getVal() / 100000 >= minThousands).collect(toSet());

        final List<Link> filteredLinks = links.stream()
                .filter(l -> filteredNodes.contains(l.getSource()) && filteredNodes.contains(l.getTarget()))
                .collect(toList());

        final Set<Node> filteredNodes2 = filteredNodes.stream()
                .filter(n -> !prune || filteredLinks.stream().anyMatch(l -> l.getSource().equals(n) || l.getTarget().equals(n)))
                .collect(toSet());

        final long maxVal = Math.max(filteredNodes2.stream().mapToLong(Node::getVal).max().orElse(1L), 1);
        final float normalizeFactor = NORMALIZE_CEIL / (float) maxVal;
        filteredNodes2.forEach(n -> n.setNormalizedVal(Float.valueOf(n.getVal() * normalizeFactor).longValue()));

        filteredNodes2.stream().filter(n -> n.getId().equals(PE_ACCOUNTING)).forEach(n -> n.setSelected(true));

        log.info("Filtered to " + filteredNodes2.size() + " nodes and " + filteredLinks.size() + " links");
        log.info("Normalize factor " + normalizeFactor);


        return new Data(filteredNodes2, filteredLinks);
    }


    private static int getIntParam(final Request req, final String paramName) {
        try {
            return parseInt(req.queryParams(paramName));
        } catch (NumberFormatException e) {
            return  0;
        }
    }

    private static List<String> getListParam(final Request req, final String paramName) {
        return req.queryParams(paramName).isEmpty() ? emptyList() : Arrays.stream(req.queryParams(paramName).split(",")).map(String::trim).collect(toList());
    }

    private boolean isIncludedClient(final Node node) {
        return includeClient.stream().anyMatch(val -> node.getType().isClient() && node.getId().toLowerCase().contains(val.toLowerCase()));
    }

    private boolean isIncludedSupplier(final Node node) {
        return includeSupplier.stream().anyMatch(val -> node.getType().isSupplier() && node.getId().toLowerCase().contains(val.toLowerCase()));
    }

}

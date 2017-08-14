package pe.universpe.data;

import lombok.AccessLevel;
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

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Filter {

    int minThousands;
    List<String> includeCompany;
    List<String> excludeCompany;
    List<String> includeSupplier;
    List<String> excludeSupplier;
    List<String> includeClient;
    List<String> excludeClient;

    public Filter(final Request req) {
        minThousands = parseInt(req.queryParams("minThousands"));
        includeCompany = getListParam(req, "includeCompany");
        excludeCompany = getListParam(req, "excludeCompany");
        includeSupplier = getListParam(req, "includeSupplier");
        excludeSupplier = getListParam(req, "excludeSupplier");
        includeClient = getListParam(req, "includeClient");
        excludeClient = getListParam(req, "excludeClient");
    }

    public Data doFilter(final pe.universpe.graph.Data data) {

        final Collection<Link> links = data.getLinks();
        final Collection<Node> nodes = data.getNodes();

        nodes.forEach(n -> {

            if (n.getType().isClient() && !includeClient.isEmpty() && isIncluded(n.getId(), includeClient)) {
                n.setSelected(true);
            } else if (n.getType().isSupplier() && !includeSupplier.isEmpty() && isIncluded(n.getId(), includeSupplier)) {
                n.setSelected(true);
            }
        });

        final Set<Node> clientIncCompanySet = links.stream()
                .filter(l -> l.getTarget().getType().isClient())
                .filter(l -> isIncluded(l.getTarget().getId(), includeClient))
                .map(Link::getSource)
                .collect(toSet());

        final Set<Node> supplierIncCompanySet = links.stream()
                .filter(l -> l.getSource().getType().isSupplier())
                .filter(l -> isIncluded(l.getSource().getId(), includeSupplier))
                .map(Link::getTarget)
                .collect(toSet());

        final Set<Node> clientExCompanySet = links.stream()
                .filter(l -> l.getTarget().getType().isClient())
                .filter(l -> isExcluded(l.getTarget().getId(), excludeClient))
                .map(Link::getSource)
                .collect(toSet());

        final Set<Node> supplierExCompanySet = links.stream()
                .filter(l -> l.getSource().getType().isSupplier())
                .filter(l -> isExcluded(l.getSource().getId(), excludeSupplier))
                .map(Link::getTarget)
                .collect(toSet());

        final Set<Node> filteredNodes = nodes.stream()
                .filter(n -> n.getVal() > minThousands || n.isSelected())
                .filter(n -> !n.getType().isCompany() || (isIncluded(n.getId(), includeCompany) && !isExcluded(n.getId(), excludeCompany)))
                .filter(n -> !n.getType().isCompany() || includeClient.isEmpty() || clientIncCompanySet.contains(n))
                .filter(n -> !n.getType().isCompany() || excludeClient.isEmpty() || !clientExCompanySet.contains(n))
                .filter(n -> !n.getType().isCompany() || includeSupplier.isEmpty() || supplierIncCompanySet.contains(n))
                .filter(n -> !n.getType().isCompany() || excludeSupplier.isEmpty() || !supplierExCompanySet.contains(n))
                .collect(toSet());

        final List<Link> filteredLinks = links.stream()
                .filter(l -> filteredNodes.contains(l.getSource()) && filteredNodes.contains(l.getTarget()))
                .collect(toList());

        final Set<Node> filteredAndPrunedNodes = filteredNodes.stream()
                .filter(n -> filteredLinks.stream().anyMatch(l -> l.getSource().equals(n) || l.getTarget().equals(n)))
                .collect(toSet());

        log.info("Filtered to " + filteredAndPrunedNodes.size() + " nodes and " + filteredLinks.size() + " links");

        return new Data(filteredAndPrunedNodes, filteredLinks);
    }

    private static boolean isIncluded(final String val, final List<String> vals) {
        return vals.isEmpty() || vals.stream().anyMatch(v -> val.toLowerCase().contains(v.toLowerCase()));
    }

    private static boolean isExcluded(final String val, final List<String> vals) {
        return vals.stream().anyMatch(v -> val.toLowerCase().contains(v.toLowerCase()));
    }

    private static List<String> getListParam(final Request req, final String paramName) {
        return req.queryParams(paramName).isEmpty() ? emptyList() : Arrays.stream(req.queryParams(paramName).split(",")).map(String::trim).collect(toList());
    }



}

package pe.universpe;

import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import pe.universpe.graph.Data;
import pe.universpe.graph.Link;
import pe.universpe.graph.Node;
import pe.universpe.jooq.tables.*;
import pe.universpe.json.LinkAdapter;
import spark.Request;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.jooq.impl.DSL.sum;
import static spark.Spark.*;

@Slf4j
public class Main {


    public static void main(final String[] args) throws SQLException {


        port(80);
        staticFiles.location("");



        get("data.json",
                (req, res) -> {
                    res.type("application/json");

                    final int minThousands = parseInt(req.queryParams("minThousands"));
                    final List<String> includeCompany = getListParam(req, "includeCompany");
                    final List<String> excludeCompany = getListParam(req, "excludeCompany");
                    final List<String> includeSupplier = getListParam(req, "includeSupplier");
                    final List<String> excludeSupplier = getListParam(req, "excludeSupplier");
                    final List<String> includeClient = getListParam(req, "includeClient");
                    final List<String> excludeClient = getListParam(req, "excludeClient");

                    final Map<String, Node> nodes = new HashMap<>();
                    final List<Link> links = new ArrayList<>();

                    try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/accounting", "root", "")) {
                        DSLContext jooq = DSL.using(conn, SQLDialect.MYSQL);

                        final Company c = Company.COMPANY.as("c");
                        final Client cl = Client.CLIENT.as("cl");
                        final ClientInvoice cli = ClientInvoice.CLIENT_INVOICE.as("cli");
                        final Supplier s = Supplier.SUPPLIER.as("s");
                        final SupplierInvoice si = SupplierInvoice.SUPPLIER_INVOICE.as("si");
                        final AccessGrantBlacklist agb = AccessGrantBlacklist.ACCESS_GRANT_BLACKLIST.as("agb");

                        jooq.select(c.ID, c.NAME).from(c)
                                .where(c.STATUS.eq("Production"))
                                .and(c.END_DATE.isNull().or(c.END_DATE.gt(DSL.currentDate())))
                                .and(c.ID.notIn(jooq.selectDistinct(agb.COMPANY_ID).from(agb)))
                                .fetch().forEach(company -> {

                            final Node companyNode = nodes.computeIfAbsent(company.value2(), Node::new);
                            companyNode.setGroup(NodeType.Company.ordinal());

                            jooq.select(cl.ID, cl.NAME, sum(cli.ACCOUNTING_AMOUNT))
                                    .from(cl)
                                    .join(cli).on(cli.CLIENT_ID.eq(cl.ID))
                                    .where(cl.ACTIVE.isTrue())
                                    .and(cl.COMPANY_ID.eq(company.value1()))
                                    .and(cli.CERTIFIED.isTrue())
                                    .and(cli.DELETED.isFalse())
                                    .groupBy(cl.ID)
                                    .fetch().forEach(client -> {

                                final Node clientNode = nodes.computeIfAbsent(client.value2(), k -> new Node(k, NodeType.Client.ordinal()));
                                links.add(new Link(companyNode, clientNode));
                                companyNode.incrementVal(client.value3().intValue() / 10000);
                                clientNode.incrementVal(client.value3().intValue() / 10000);
                            });

                            jooq.select(s.ID, s.NAME, sum(si.CURRENCY_RATE.multiply(si.AMOUNT.add(si.VAT))))
                                    .from(s)
                                    .join(si).onKey()
                                    .where(s.ACTIVE.isTrue())
                                    .and(s.COMPANY_ID.eq(company.value1()))
                                    .and(si.CERTIFIED.isTrue())
                                    .groupBy(s.ID)
                                    .fetch().forEach(supplier -> {

                                final Node supplierNode = nodes.computeIfAbsent(supplier.value2(), k -> new Node(k, NodeType.Supplier.ordinal()));
                                links.add(new Link(supplierNode, companyNode));
                                companyNode.incrementVal(supplier.value3().intValue() / 10000);
                                supplierNode.incrementVal(supplier.value3().intValue() / 10000);
                            });

                        });


                        log.info("Found " + nodes.size() + " nodes and " + links.size() + " links");

                    }

                    nodes.values().forEach(n -> {

                        if (n.getGroup() == NodeType.Client.ordinal() && !includeClient.isEmpty() && isIncluded(n.getId(), includeClient)) {
                            n.setColor("#FF0000");
                        } else if (n.getGroup() == NodeType.Supplier.ordinal() && !includeSupplier.isEmpty() && isIncluded(n.getId(), includeSupplier)) {
                            n.setColor("#FF0000");
                        }
                    });

                    final Set<Node> clientIncCompanySet = links.stream()
                            .filter(l -> l.getTarget().getGroup() == NodeType.Client.ordinal())
                            .filter(l -> isIncluded(l.getTarget().getId(), includeClient))
                            .map(Link::getSource)
                            .collect(toSet());

                    final Set<Node> supplierIncCompanySet = links.stream()
                            .filter(l -> l.getSource().getGroup() == NodeType.Supplier.ordinal())
                            .filter(l -> isIncluded(l.getSource().getId(), includeSupplier))
                            .map(Link::getTarget)
                            .collect(toSet());

                    final Set<Node> clientExCompanySet = links.stream()
                            .filter(l -> l.getTarget().getGroup() == NodeType.Client.ordinal())
                            .filter(l -> isExcluded(l.getTarget().getId(), excludeClient))
                            .map(Link::getSource)
                            .collect(toSet());

                    final Set<Node> supplierExCompanySet = links.stream()
                            .filter(l -> l.getSource().getGroup() == NodeType.Supplier.ordinal())
                            .filter(l -> isExcluded(l.getSource().getId(), excludeSupplier))
                            .map(Link::getTarget)
                            .collect(toSet());

                    final Set<Node> filteredNodes = nodes.values().stream()
                            .filter(n -> n.getVal() > minThousands || !n.getColor().isEmpty())
                            .filter(n -> n.getGroup() != NodeType.Company.ordinal() || (isIncluded(n.getId(), includeCompany) && !isExcluded(n.getId(), excludeCompany)))
                            .filter(n -> n.getGroup() != NodeType.Company.ordinal() || includeClient.isEmpty() || clientIncCompanySet.contains(n))
                            .filter(n -> n.getGroup() != NodeType.Company.ordinal() || excludeClient.isEmpty() || !clientExCompanySet.contains(n))
                            .filter(n -> n.getGroup() != NodeType.Company.ordinal() || includeSupplier.isEmpty() || supplierIncCompanySet.contains(n))
                            .filter(n -> n.getGroup() != NodeType.Company.ordinal() || excludeSupplier.isEmpty() || !supplierExCompanySet.contains(n))
                            .collect(toSet());

                    final List<Link> filteredLinks = links.stream()
                            .filter(l -> filteredNodes.contains(l.getSource()) && filteredNodes.contains(l.getTarget()))
                            .collect(toList());

                    final Set<Node> filteredAndPrunedNodes = filteredNodes.stream()
                            .filter(n -> filteredLinks.stream().anyMatch(l -> l.getSource().equals(n) || l.getTarget().equals(n)))
                            .collect(toSet());

                    log.info("Filtered to " + filteredAndPrunedNodes.size() + " nodes and " + filteredLinks.size() + " links");

                    return new Data(filteredAndPrunedNodes, filteredLinks);

                }, d -> {
                    final GsonBuilder gsonBuilder = new GsonBuilder();
                    gsonBuilder.registerTypeAdapter(Link.class, new LinkAdapter());
                    return gsonBuilder.create().toJson(d);
                });

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

    enum NodeType {
        Company, Client, Supplier;
    }

}

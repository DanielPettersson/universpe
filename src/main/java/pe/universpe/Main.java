package pe.universpe;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import pe.universpe.graph.Data;
import pe.universpe.graph.Link;
import pe.universpe.graph.Node;
import pe.universpe.jooq.tables.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.jooq.impl.DSL.sum;
import static spark.Spark.*;

@Slf4j
public class Main {


    public static void main(final String[] args) throws SQLException {

        final Map<String, Node> nodes = new HashMap<>();
        final List<Link> links = new ArrayList<>();

        port(80);
        staticFiles.location("");
        init();
        get("data.json",
                (req, res) -> {
                    res.type("application/json");

                    final String minThousandsStr = req.queryParams("minThousands");
                    final int minThousands = minThousandsStr != null ? Integer.parseInt(minThousandsStr) : 30000;
                    final String company = req.queryParams("company") != null ? req.queryParams("company") : "";

                    return generateData(nodes.values(), links, minThousands, company);
                }, d -> new Gson().toJson(d));

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/accounting", "root", "")) {
            DSLContext jooq = DSL.using(conn, SQLDialect.MYSQL);

            final Company c = Company.COMPANY.as("c");
            final Client cl = Client.CLIENT.as("cl");
            final ClientInvoice cli = ClientInvoice.CLIENT_INVOICE.as("cli");
            final Supplier s = Supplier.SUPPLIER.as("s");
            final SupplierInvoice si = SupplierInvoice.SUPPLIER_INVOICE.as("si");

            jooq.select(c.ID, c.NAME).from(c).where(c.STATUS.eq("Production")).and(c.END_DATE.isNull().or(c.END_DATE.gt(DSL.currentDate()))).fetch().forEach(company -> {

                final Node companyNode = nodes.computeIfAbsent(company.value2(), Node::new);
                companyNode.setGroup(1);

                jooq.select(cl.ID, cl.NAME, sum(cli.ACCOUNTING_AMOUNT))
                        .from(cl)
                        .join(cli).on(cli.CLIENT_ID.eq(cl.ID))
                        .where(cl.ACTIVE.isTrue())
                        .and(cl.COMPANY_ID.eq(company.value1()))
                        .and(cli.CERTIFIED.isTrue())
                        .and(cli.DELETED.isFalse())
                        .groupBy(cl.ID)
                        .having(sum(cli.ACCOUNTING_AMOUNT).gt(BigDecimal.valueOf(100000)))
                        .fetch().forEach(client -> {

                    final Node clientNode = nodes.computeIfAbsent(client.value2(), k -> new Node(k, 2));
                    links.add(new Link(companyNode.getId(), clientNode.getId()));
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
                        .having(sum(si.CURRENCY_RATE.multiply(si.AMOUNT.add(si.VAT))).gt(BigDecimal.valueOf(100000)))
                        .fetch().forEach(supplier -> {

                    final Node supplierNode = nodes.computeIfAbsent(supplier.value2(), k -> new Node(k, 3));
                    links.add(new Link(supplierNode.getId(), companyNode.getId()));
                    companyNode.incrementVal(supplier.value3().intValue() / 10000);
                    supplierNode.incrementVal(supplier.value3().intValue() / 10000);
                });

            });


            log.info("Found " + nodes.size() + " nodes and " + links.size() + " links");

        }



    }

    private static Data generateData(final Collection<Node> nodes, final List<Link> links, final int minThousands, final String company) {

        final Set<Node> filteredNodes1 = nodes.stream().filter(n -> n.getVal() > minThousands && (n.getGroup() != 1 || n.getId().contains(company))).collect(toSet());
        final List<Link> filteredLinks = links.stream().filter(l -> filteredNodes1.contains(new Node(l.getSource(), 0)) && filteredNodes1.contains(new Node(l.getTarget(), 0))).collect(toList());
        final Set<Node> filteredNodes2 = filteredNodes1.stream().filter(n -> filteredLinks.stream().anyMatch(l -> l.getSource().equals(n.getId()) ||l.getTarget().equals(n.getId()))).collect(toSet());

        log.info("Filtered to " + filteredNodes2.size() + " nodes and " + filteredLinks.size() + " links. By minThousands " + minThousands + " and company " + company);

        return new Data(filteredNodes2, filteredLinks);

    }
}

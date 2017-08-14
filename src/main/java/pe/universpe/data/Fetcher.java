package pe.universpe.data;

import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import pe.universpe.graph.Data;
import pe.universpe.graph.Link;
import pe.universpe.graph.Node;
import pe.universpe.jooq.tables.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.sum;

@Slf4j
public class Fetcher {

    private static final Company c = Company.COMPANY.as("c");
    private static final Client cl = Client.CLIENT.as("cl");
    private static final ClientInvoice cli = ClientInvoice.CLIENT_INVOICE.as("cli");
    private static final Supplier s = Supplier.SUPPLIER.as("s");
    private static final SupplierInvoice si = SupplierInvoice.SUPPLIER_INVOICE.as("si");
    private static final AccessGrantBlacklist agb = AccessGrantBlacklist.ACCESS_GRANT_BLACKLIST.as("agb");

    public Data fetchData() throws SQLException {

        final Map<String, Node> nodes = new HashMap<>();
        final List<Link> links = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/accounting", "root", "")) {
            DSLContext jooq = DSL.using(conn, SQLDialect.MYSQL);

            jooq.select(c.ID, c.NAME).from(c)
                    .where(c.STATUS.eq("Production"))
                    .and(c.END_DATE.isNull().or(c.END_DATE.gt(DSL.currentDate())))
                    .and(c.ID.notIn(jooq.selectDistinct(agb.COMPANY_ID).from(agb)))
                    .fetch().forEach(company -> {

                final Node companyNode = nodes.computeIfAbsent(company.value2(), Node::new);
                companyNode.setType(Node.Type.Company);

                jooq.select(cl.ID, cl.NAME, sum(cli.ACCOUNTING_AMOUNT))
                        .from(cl)
                        .join(cli).on(cli.CLIENT_ID.eq(cl.ID))
                        .where(cl.ACTIVE.isTrue())
                        .and(cl.COMPANY_ID.eq(company.value1()))
                        .and(cli.CERTIFIED.isTrue())
                        .and(cli.DELETED.isFalse())
                        .groupBy(cl.ID)
                        .fetch().forEach(client -> {

                    final Node clientNode = nodes.computeIfAbsent(client.value2(), k -> new Node(k, Node.Type.Client));
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

                    final Node supplierNode = nodes.computeIfAbsent(supplier.value2(), k -> new Node(k, Node.Type.Supplier));
                    links.add(new Link(supplierNode, companyNode));
                    companyNode.incrementVal(supplier.value3().intValue() / 10000);
                    supplierNode.incrementVal(supplier.value3().intValue() / 10000);
                });

            });


            log.info("Found " + nodes.size() + " nodes and " + links.size() + " links");

        }

        return new Data(nodes.values(), links);

    }

}

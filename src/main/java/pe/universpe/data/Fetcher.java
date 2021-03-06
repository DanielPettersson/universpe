package pe.universpe.data;

import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.UInteger;
import pe.universpe.Main;
import pe.universpe.graph.Data;
import pe.universpe.graph.Link;
import pe.universpe.graph.Node;
import pe.universpe.jooq.tables.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import static org.jooq.impl.DSL.*;

@Slf4j
public class Fetcher {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/accounting";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "";

    private final UUID progressUuid;

    private static final Company c = Company.COMPANY.as("c");
    private static final Client cl = Client.CLIENT.as("cl");
    private static final ClientInvoice cli = ClientInvoice.CLIENT_INVOICE.as("cli");
    private static final Supplier s = Supplier.SUPPLIER.as("s");
    private static final SupplierInvoice si = SupplierInvoice.SUPPLIER_INVOICE.as("si");
    private static final AccessGrantBlacklist agb = AccessGrantBlacklist.ACCESS_GRANT_BLACKLIST.as("agb");

    public Fetcher(UUID progressUuid) {
        this.progressUuid = progressUuid;
    }

    public Data fetchData(Filter filter) {

        final Map<String, Node> nodes = new HashMap<>();
        final List<Link> links = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            DSLContext jooq = DSL.using(conn, SQLDialect.MYSQL);

            final Result<Record2<UInteger, String>> companies = jooq.select(c.ID, c.NAME).from(c)
                    .where(c.STATUS.eq("Production"))
                    .and(c.END_DATE.isNull().or(c.END_DATE.gt(DSL.currentDate())))
                    .and(c.ID.notIn(jooq.selectDistinct(agb.COMPANY_ID).from(agb)))
                    .and(include(filter.getIncludeCompany(), c.NAME))
                    .and(exclude(filter.getExcludeCompany(), c.NAME))
                    .fetch();

            for (int i = 0; i < companies.size(); i++) {

                final Record2<UInteger, String> company = companies.get(i);

                final Node companyNode = nodes.computeIfAbsent(company.value2().toLowerCase(), k -> new Node(company.value2()));
                companyNode.setType(Node.Type.Company);

                jooq.select(cl.ID, cl.NAME, sum(cli.ACCOUNTING_AMOUNT))
                        .from(cl)
                        .join(cli).on(cli.CLIENT_ID.eq(cl.ID))
                        .where(cl.ACTIVE.isTrue())
                        .and(cl.COMPANY_ID.eq(company.value1()))
                        .and(cli.CERTIFIED.isTrue())
                        .and(cli.DELETED.isFalse())
                        .and(include(filter.getIncludeClient(), cl.NAME))
                        .and(exclude(filter.getExcludeClient(), cl.NAME))
                        .groupBy(cl.ID)
                        .fetch().forEach(client -> {

                    final Node clientNode = nodes.computeIfAbsent(client.value2().toLowerCase(), k -> new Node(client.value2(), Node.Type.Client));
                    links.add(new Link(companyNode, clientNode));
                    companyNode.incrementVal(client.value3().longValue());
                    clientNode.incrementVal(client.value3().longValue());
                });

                jooq.select(s.ID, s.NAME, sum(si.CURRENCY_RATE.multiply(si.AMOUNT.add(si.VAT))))
                        .from(s)
                        .join(si).onKey()
                        .where(s.ACTIVE.isTrue())
                        .and(s.COMPANY_ID.eq(company.value1()))
                        .and(si.CERTIFIED.isTrue())
                        .and(include(filter.getIncludeSupplier(), s.NAME))
                        .and(exclude(filter.getExcludeSupplier(), s.NAME))
                        .groupBy(s.ID)
                        .fetch().forEach(supplier -> {

                    final Node supplierNode = nodes.computeIfAbsent(supplier.value2().toLowerCase(), k -> new Node(supplier.value2(), Node.Type.Supplier));
                    links.add(new Link(supplierNode, companyNode));
                    companyNode.incrementVal(supplier.value3().longValue());
                    supplierNode.incrementVal(supplier.value3().longValue());
                });

                final Float progress = (float) i / companies.size() * 100;
                Main.progressMap.put(progressUuid, progress.intValue());
            }

            log.info("Found " + nodes.size() + " nodes and " + links.size() + " links");

        } catch (SQLException e) {
            // Ignore
        }

        return filter.postProcess(new Data(nodes.values(), links));

    }

    private static Condition include(final List<String> vals, final Field<String> field) {
        if (vals.isEmpty()) {
            return trueCondition();
        } else {
            return vals.stream().map(field::contains).reduce(falseCondition(), Condition::or);
        }
    }

    private static Condition exclude(final List<String> vals, final Field<String> field) {
        if (vals.isEmpty()) {
            return trueCondition();
        } else {
            return not(vals.stream().map(field::contains).reduce(falseCondition(), Condition::or));
        }
    }

}

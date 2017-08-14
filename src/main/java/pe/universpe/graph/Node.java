package pe.universpe.graph;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = "id")
public class Node {
    final String id;
    Type type;
    long val;
    boolean selected;

    public Node(final String id) {
        this.id = id;
    }

    public Node(final String id, final Type type) {
        this.id = id;
        this.type = type;
    }

    public void incrementVal(final int val) {
        this.val += val;
    }

    public enum Type {
        Company, Client, Supplier;

        public boolean isCompany() {
            return this == Company;
        }

        public boolean isClient() {
            return this == Client;
        }

        public boolean isSupplier() {
            return this == Supplier;
        }

    }


}

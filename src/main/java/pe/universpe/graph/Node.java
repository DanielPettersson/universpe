package pe.universpe.graph;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(of = "id")
public class Node {
    final String id;
    int group;
    int val;
    String color;

    public Node(String id) {
        this.id = id;
    }

    public Node(String id, int group) {
        this.id = id;
        this.group = group;
    }

    public void incrementVal(final int val) {
        this.val += val;
    }
}

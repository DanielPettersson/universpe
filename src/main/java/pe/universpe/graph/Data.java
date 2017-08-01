package pe.universpe.graph;

import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
public class Data {
    Set<Node> nodes;
    List<Link> links;
}

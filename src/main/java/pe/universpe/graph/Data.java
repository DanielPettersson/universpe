package pe.universpe.graph;

import lombok.Value;

import java.util.Collection;

@Value
public class Data {
    Collection<Node> nodes;
    Collection<Link> links;
}

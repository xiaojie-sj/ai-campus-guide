package com.hbeu.campusguide.algorithm;

import com.hbeu.campusguide.domain.RoadEdge;
import com.hbeu.campusguide.domain.RoadNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PathPlannerTest {
    private final PathPlanner planner = new PathPlanner();

    @Test
    void choosesShortestConnectedRoadPath() {
        List<RoadNode> nodes = List.of(node(1, 0, 0), node(2, 1, 0), node(3, 2, 0));
        List<RoadEdge> edges = List.of(edge(1, 2), edge(2, 3));

        PathPlanner.PathResult result = planner.plan(1, 3, nodes, edges);

        assertThat(result.found()).isTrue();
        assertThat(result.nodes()).containsExactly(1L, 2L, 3L);
        assertThat(result.distance()).isEqualTo(2.0);
    }

    @Test
    void failsWhenNoWalkablePathExists() {
        List<RoadNode> nodes = List.of(node(1, 0, 0), node(2, 0, 4), node(3, 4, 4));
        RoadEdge disabled = edge(1, 2);
        disabled.setWalkable(false);

        PathPlanner.PathResult result = planner.plan(1, 3, nodes, List.of(disabled, edge(2, 3)));

        assertThat(result.found()).isFalse();
    }

    private RoadNode node(long id, double x, double z) {
        RoadNode node = new RoadNode();
        node.setId(id);
        node.setName("node-" + id);
        node.setX(x);
        node.setZ(z);
        node.setEnabled(true);
        return node;
    }

    private RoadEdge edge(long from, long to) {
        RoadEdge edge = new RoadEdge();
        edge.setFromNodeId(from);
        edge.setToNodeId(to);
        edge.setWalkable(true);
        edge.setEnabled(true);
        return edge;
    }
}

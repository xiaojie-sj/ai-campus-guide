package com.hbeu.campusguide.algorithm;

import com.hbeu.campusguide.domain.RoadEdge;
import com.hbeu.campusguide.domain.RoadNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* planner over the configured walking graph. Edges are undirected and may
 * be disabled without rebuilding the graph.
 */
@Component
public class PathPlanner {

    public PathResult plan(long startNodeId, long endNodeId, List<RoadNode> nodes, List<RoadEdge> edges) {
        Map<Long, RoadNode> nodeMap = new HashMap<>();
        nodes.stream().filter(node -> Boolean.TRUE.equals(node.getEnabled()))
                .forEach(node -> nodeMap.put(node.getId(), node));
        if (!nodeMap.containsKey(startNodeId) || !nodeMap.containsKey(endNodeId)) {
            return PathResult.empty();
        }

        Map<Long, List<EdgeCost>> adjacency = new HashMap<>();
        for (RoadEdge edge : edges) {
            if (!Boolean.TRUE.equals(edge.getEnabled()) || !Boolean.TRUE.equals(edge.getWalkable())) {
                continue;
            }
            if (!nodeMap.containsKey(edge.getFromNodeId()) || !nodeMap.containsKey(edge.getToNodeId())) {
                continue;
            }
            RoadNode from = nodeMap.get(edge.getFromNodeId());
            RoadNode to = nodeMap.get(edge.getToNodeId());
            double distance = edge.getDistance() == null
                    ? Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ())
                    : edge.getDistance();
            adjacency.computeIfAbsent(from.getId(), ignored -> new ArrayList<>())
                    .add(new EdgeCost(to.getId(), distance));
            adjacency.computeIfAbsent(to.getId(), ignored -> new ArrayList<>())
                    .add(new EdgeCost(from.getId(), distance));
        }

        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        PriorityQueue<QueueItem> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueItem::score));
        gScore.put(startNodeId, 0.0);
        queue.add(new QueueItem(startNodeId, heuristic(nodeMap.get(startNodeId), nodeMap.get(endNodeId))));

        while (!queue.isEmpty()) {
            QueueItem current = queue.poll();
            if (!closed.add(current.nodeId())) {
                continue;
            }
            if (current.nodeId().equals(endNodeId)) {
                break;
            }
            for (EdgeCost next : adjacency.getOrDefault(current.nodeId(), List.of())) {
                double candidate = gScore.get(current.nodeId()) + next.distance();
                if (candidate < gScore.getOrDefault(next.nodeId(), Double.POSITIVE_INFINITY)) {
                    gScore.put(next.nodeId(), candidate);
                    cameFrom.put(next.nodeId(), current.nodeId());
                    queue.add(new QueueItem(next.nodeId(),
                            candidate + heuristic(nodeMap.get(next.nodeId()), nodeMap.get(endNodeId))));
                }
            }
        }

        if (!gScore.containsKey(endNodeId)) {
            return PathResult.empty();
        }
        List<Long> nodePath = new ArrayList<>();
        Long cursor = endNodeId;
        while (cursor != null) {
            nodePath.add(0, cursor);
            if (cursor.equals(startNodeId)) {
                break;
            }
            cursor = cameFrom.get(cursor);
        }
        if (nodePath.isEmpty() || !nodePath.get(0).equals(startNodeId)) {
            return PathResult.empty();
        }
        return new PathResult(nodePath, gScore.get(endNodeId));
    }

    private double heuristic(RoadNode from, RoadNode to) {
        return Math.hypot(from.getX() - to.getX(), from.getZ() - to.getZ());
    }

    private record EdgeCost(Long nodeId, double distance) {
    }

    private record QueueItem(Long nodeId, double score) {
    }

    public record PathResult(List<Long> nodes, double distance) {
        public static PathResult empty() {
            return new PathResult(List.of(), Double.POSITIVE_INFINITY);
        }

        public boolean found() {
            return !nodes.isEmpty();
        }
    }
}

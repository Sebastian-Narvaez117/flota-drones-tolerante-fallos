package com.bft.drone.controller;

import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.NodeState;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoint de estado del nodo para el dashboard Flask.
 * GET /status -> retorna el estado completo del nodo en JSON
 */
@RestController
@RequiredArgsConstructor
public class StatusController {

    private final NodeConfig nodeConfig;
    private final NodeState nodeState;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "nodeId",          nodeConfig.getId(),
            "nodeIp",          nodeConfig.getIp(),
            "nodeRole",        nodeConfig.getRole(),
            "coordinatorId",   nodeState.getCoordinatorId(),
            "currentRound",    nodeState.getCurrentRound(),
            "electionActive",  nodeState.isElectionInProgress(),
            "decisionHistory", nodeState.getDecisionHistory(),
            "recentEvents",    nodeState.getRecentEvents(20)
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "nodeId", String.valueOf(nodeConfig.getId()));
    }
}

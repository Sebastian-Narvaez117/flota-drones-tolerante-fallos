package com.bft.drone.service;

import com.bft.drone.config.ClusterConfig;
import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.NodeState;
import com.bft.drone.websocket.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BftService {

    private final NodeConfig nodeConfig;
    private final ClusterConfig clusterConfig;
    private final NodeState nodeState;
    private final EventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    private static final List<String> TRAJECTORIES = List.of(
            "NORTE:100,ESTE:50",
            "SUR:80,OESTE:30",
            "NORTE:60,OESTE:20",
            "ESTE:120,SUR:40"
    );
    private final ConcurrentHashMap<Integer, String> roundCoordinatorValue = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Map<Integer, String>> roundPeerValues = new ConcurrentHashMap<>();


    // Estado local de la ronda actual
    private String coordinatorValue;
    private final Map<Integer, String> peerValues = new HashMap<>();

    @Scheduled(fixedDelayString = "${bft.round-interval-ms:8000}")
    public void startRoundIfCoordinator() {
        if (!isCoordinator() || electionInProgress()) return;

        int round = nodeState.incrementAndGetRound();
        String truePath = getTruePathForRound(round);
        log.info("=== Ronda BFT {} iniciada (coordinador {}) ===", round, nodeConfig.getId());
        nodeState.logEvent("BFT RONDA " + round + " iniciada por coordinador " + nodeConfig.getId());

        // Fase 1: enviar PROPOSE a todos los tenientes
        for (ClusterConfig.NodeInfo node : clusterConfig.getOtherNodes(nodeConfig.getId())) {
            String trajectory = nodeConfig.isTraitor()
                    ? fakeTrajectoryFor(node.getId(), round)
                    : truePath;
            sendProposeTo(node, round, trajectory);
        }

        // El coordinador también guarda su propio valor verdadero
        coordinatorValue = truePath;
        nodeState.clearRelayMessages();
        peerValues.clear();
    }

    // ---------- Fase 1: recepción de PROPOSE ----------
    public void receivePropose(int fromNodeId, int round, String trajectory) {
        
        nodeState.setCurrentRound(round);
        coordinatorValue = trajectory;
        roundPeerValues.put(round, new ConcurrentHashMap<>());
        peerValues.clear();
        nodeState.logEvent("PROPUESTA ronda " + round + ": " + trajectory);
        eventPublisher.bftPropose(round, trajectory);

        // Fase 2: reenviar a los otros tenientes (no al coordinador ni a sí mismo)
        clusterConfig.getOtherNodes(nodeConfig.getId()).stream()
                .filter(n -> n.getId() != fromNodeId)
                .forEach(n -> {
                    String relayValue = nodeConfig.isTraitor()
                            ? fakeTrajectoryFor(n.getId(), round)
                            : trajectory;
                    sendRelayTo(n, round, relayValue);
                });
    }

    // ---------- Fase 2: recepción de RELAY ----------
    public void receiveRelay(int fromNodeId, int round, String trajectory) {
        roundPeerValues.computeIfAbsent(round, k -> new ConcurrentHashMap<>())
                   .put(fromNodeId, trajectory);
        nodeState.logEvent("REENVIO ronda " + round + " de nodo " + fromNodeId + ": " + trajectory);
        eventPublisher.bftRelay(round, fromNodeId, trajectory);

        int expectedRelays = clusterConfig.getNodes().size() - 2; // total - coordinador - yo
        if (roundPeerValues.size() >= expectedRelays) {
            decide(round);
        }
    }

    // ---------- Fase 3: decisión por mayoría ----------
    private void decide(int round) {
        List<String> allValues = new ArrayList<>();
        String coordVal = roundCoordinatorValue.get(round);
        if (coordVal != null) allValues.add(coordVal);
        Map<Integer, String> peers = roundPeerValues.getOrDefault(round, Map.of());
        allValues.addAll(peers.values());
        roundCoordinatorValue.remove(round);
        roundPeerValues.remove(round);
        String decision = majorityVote(allValues);
        nodeState.recordDecision(round, decision);
        nodeState.logEvent("DECISION ronda " + round + ": " + decision + " (valores: " + allValues + ")");
        eventPublisher.bftDecision(round, decision);
    }

    // ---------- Métodos auxiliares ----------
    private boolean isCoordinator() {
        return nodeConfig.getId() == nodeState.getCoordinatorId();
    }

    private boolean electionInProgress() {
        return nodeState.isElectionInProgress();
    }

    private String getTruePathForRound(int round) {
        return TRAJECTORIES.get(round % TRAJECTORIES.size());
    }

    private String fakeTrajectoryFor(int targetNodeId, int round) {
        int idx = (targetNodeId + round) % TRAJECTORIES.size();
        return "FALSO-" + TRAJECTORIES.get(idx);
    }

    private void sendProposeTo(ClusterConfig.NodeInfo node, int round, String trajectory) {
        try {
            restTemplate.postForEntity(node.getBaseUrl() + "/bft/propose",
                    Map.of("fromNodeId", nodeConfig.getId(), "round", round, "trajectory", trajectory),
                    Void.class);
            log.info("PROPUESTA -> nodo {} : '{}'", node.getId(), trajectory);
            eventPublisher.bftProposeTo(round, node.getId(), trajectory);
        } catch (Exception e) {
            log.warn("Fallo PROPUESTA a nodo {}", node.getId());
        }
    }

    private void sendRelayTo(ClusterConfig.NodeInfo node, int round, String trajectory) {
        try {
            restTemplate.postForEntity(node.getBaseUrl() + "/bft/relay",
                    Map.of("fromNodeId", nodeConfig.getId(), "round", round, "trajectory", trajectory),
                    Void.class);
            log.info("REENVIO -> nodo {} : '{}'", node.getId(), trajectory);
            eventPublisher.bftRelayTo(round, nodeConfig.getId(), node.getId(), trajectory);
        } catch (Exception e) {
            log.warn("Fallo REENVIO a nodo {}", node.getId());
        }
    }

    private String majorityVote(List<String> values) {
        return values.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("DESCONOCIDO");
    }
}
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

    // Almacenamiento por ronda
    private final Map<Integer, String> roundCoordinatorValue = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Integer, String>> roundPeerValues = new ConcurrentHashMap<>();

    // ================================================================
    // El coordinador inicia una ronda
    // ================================================================

    @Scheduled(fixedDelayString = "${bft.round-interval-ms:8000}")
    public void startRoundIfCoordinator() {
        if (!isCoordinator() || electionInProgress()) return;

        int round = nodeState.incrementAndGetRound();
        String truePath = getTruePathForRound(round);
        log.info("=== Ronda BFT {} iniciada (coordinador {}) ===", round, nodeConfig.getId());
        nodeState.logEvent("BFT RONDA " + round + " iniciada por coordinador " + nodeConfig.getId());

        // Guardar el valor que el coordinador propone (incluso si es traidor, usa el valor de la ronda)
        roundCoordinatorValue.put(round, truePath);
        roundPeerValues.put(round, new ConcurrentHashMap<>());

        // Fase 1: enviar PROPUESTA a todos los demás nodos
        for (ClusterConfig.NodeInfo node : clusterConfig.getOtherNodes(nodeConfig.getId())) {
            String trajectory = nodeConfig.isTraitor()
                    ? fakeTrajectoryFor(node.getId(), round)
                    : truePath;
            sendProposeTo(node, round, trajectory);
        }

        // El coordinador NO reenvía. Solo registrará su propia decisión
        // (basada en el valor que él mismo propuso, aunque sea traidor)
        nodeState.recordDecision(round, truePath);
        nodeState.logEvent("DECISION ronda " + round + ": " + truePath + " (coordinador)");
        eventPublisher.bftDecision(round, truePath);

        // Limpia los mensajes relay del estado (no se usan ya)
        nodeState.clearRelayMessages();
    }

    // ================================================================
    // Fase 1: un teniente recibe PROPUESTA del coordinador
    // ================================================================

    public void receivePropose(int fromNodeId, int round, String trajectory) {
    log.info("[NODO {}] 📥 PROPUESTA recibida ronda {} de nodo {}: '{}'",
            nodeConfig.getId(), round, fromNodeId, trajectory);

    nodeState.setCurrentRound(round);
    roundCoordinatorValue.put(round, trajectory);
    roundPeerValues.put(round, new ConcurrentHashMap<>());

    nodeState.logEvent("PROPUESTA ronda " + round + ": " + trajectory);
    eventPublisher.bftPropose(round, trajectory);

    // Reenviar a los otros tenientes
    List<ClusterConfig.NodeInfo> others = clusterConfig.getOtherNodes(nodeConfig.getId()).stream()
            .filter(n -> n.getId() != fromNodeId)
            .collect(Collectors.toList());

    log.info("[NODO {}] 🔄 Reenviando PROPUESTA a {} tenientes: {}",
            nodeConfig.getId(), others.size(),
            others.stream().map(n -> "D" + n.getId()).collect(Collectors.toList()));

    for (ClusterConfig.NodeInfo n : others) {
        String relayValue = nodeConfig.isTraitor()
                ? fakeTrajectoryFor(n.getId(), round)
                : trajectory;
        sendRelayTo(n, round, relayValue);
    }

    // Log del estado después de reenviar
    log.info("[NODO {}] 📊 Estado ronda {}: coordValue={}, peers actuales={}",
            nodeConfig.getId(), round,
            roundCoordinatorValue.get(round),
            roundPeerValues.get(round).size());
}

    // ================================================================
    // Fase 2: un teniente recibe un REENVÍO de otro teniente
    // ================================================================

    public void receiveRelay(int fromNodeId, int round, String trajectory) {
    Map<Integer, String> peers = roundPeerValues.computeIfAbsent(round, k -> new ConcurrentHashMap<>());
    peers.put(fromNodeId, trajectory);

    int currentPeers = peers.size();
    int expected = clusterConfig.getNodes().size() - 2;
    String coordVal = roundCoordinatorValue.get(round);

    log.info("[NODO {}] 📥 REENVÍO ronda {} de D{}: '{}' (peers={}/{}, coordPresente={})",
            nodeConfig.getId(), round, fromNodeId, trajectory,
            currentPeers, expected, coordVal != null);

    nodeState.logEvent("REENVIO ronda " + round + " de nodo " + fromNodeId + ": " + trajectory);
    eventPublisher.bftRelay(round, fromNodeId, trajectory);

    if (coordVal != null && currentPeers >= expected) {
        log.info("[NODO {}] 🎯 Disparando DECISIÓN ronda {} (peers={}, coordVal='{}')",
                nodeConfig.getId(), round, currentPeers, coordVal);
        decide(round);
    } else {
        log.info("[NODO {}] ⏳ Aún no decide ronda {}: falta {}",
                nodeConfig.getId(), round,
                (coordVal == null ? "propuesta del coordinador" : "") +
                (currentPeers < expected ? " " + (expected - currentPeers) + " reenvíos" : ""));
    }
}

    // ================================================================
    // Fase 3: decidir por mayoría (con tie-breaker determinista)
    // ================================================================

    private synchronized void decide(int round) {
    if (!roundCoordinatorValue.containsKey(round)) {
        log.warn("[NODO {}] ⚠️ Intento de decidir ronda {} ya decidida (concurrente)", nodeConfig.getId(), round);
        return;
    }

    List<String> allValues = new ArrayList<>();
    allValues.add(roundCoordinatorValue.get(round));
    allValues.addAll(roundPeerValues.getOrDefault(round, Map.of()).values());

    String decision = majorityVote(allValues);
    log.info("[NODO {}] 🏁 DECISIÓN ronda {} = '{}' (valores: {})",
            nodeConfig.getId(), round, decision, allValues);

    nodeState.recordDecision(round, decision);
    nodeState.logEvent("DECISION ronda " + round + ": " + decision + " (valores: " + allValues + ")");
    eventPublisher.bftDecision(round, decision);

    roundCoordinatorValue.remove(round);
    roundPeerValues.remove(round);
}

    // ================================================================
    // Métodos auxiliares
    // ================================================================

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

    /**
     * Votación por mayoría. Si hay empate, se elige el valor
     * lexicográficamente menor para garantizar que todos los nodos
     * decidan lo mismo.
     */
    private String majorityVote(List<String> values) {
        if (values.isEmpty()) return "DESCONOCIDO";

        // Contar ocurrencias
        Map<String, Long> counts = values.stream()
                .collect(Collectors.groupingBy(v -> v, Collectors.counting()));

        // Encontrar el máximo número de ocurrencias
        long maxCount = counts.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);

        // Tomar todos los que empatan en el máximo
        List<String> topValues = counts.entrySet().stream()
                .filter(e -> e.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .sorted()   // orden alfabético determinista
                .collect(Collectors.toList());

        // Devolver el primero (será el menor lexicográficamente)
        return topValues.get(0);
    }
}
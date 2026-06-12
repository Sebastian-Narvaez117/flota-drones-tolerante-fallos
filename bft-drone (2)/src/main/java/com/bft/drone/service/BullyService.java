package com.bft.drone.service;

import com.bft.drone.config.BullyConfig;
import com.bft.drone.config.ClusterConfig;
import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.NodeState;
import com.bft.drone.websocket.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class BullyService {

    private final NodeConfig nodeConfig;
    private final ClusterConfig clusterConfig;
    private final BullyConfig bullyConfig;   // ← ahora sí se inyecta
    private final NodeState nodeState;
    private final EventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    private final AtomicBoolean receivedOk = new AtomicBoolean(false);

    // ── Heartbeat periódico del coordinador ──────────────────────────────
    @Scheduled(fixedDelayString = "${bully.heartbeat-interval-ms:3000}")
    public void sendHeartbeatIfCoordinator() {
        if (!isCoordinator()) return;
        clusterConfig.getOtherNodes(nodeConfig.getId()).forEach(node -> {
            try {
                restTemplate.postForEntity(node.getBaseUrl() + "/bully/heartbeat",
                        Map.of("coordinatorId", nodeConfig.getId()), Void.class);
            } catch (Exception e) {
                log.warn("No se pudo enviar latido al nodo {}", node.getId());
            }
        });
        eventPublisher.heartbeat(nodeConfig.getId());
    }

    // ── Watchdog: verifica si el coordinador sigue vivo ──────────────────
    @Scheduled(fixedDelay = 2000L)
    public void checkCoordinatorAlive() {
        if (isCoordinator() || electionInProgress()) return;

        if (noCoordinatorKnown()) {
            startElection();
            return;
        }

        long elapsed = System.currentTimeMillis() - nodeState.getLastHeartbeatMs();
        // Usa el timeout configurado (por defecto 15s, suficiente para la VM)
        if (elapsed > bullyConfig.getHeartbeatTimeoutMs()) {
            log.warn("Coordinador {} no responde ({}ms). Iniciando elección.",
                    nodeState.getCoordinatorId(), elapsed);
            nodeState.logEvent("Coordinador caído tras " + elapsed + "ms. Elección iniciada.");
            startElection();
        }
    }

    // ── Inicio de elección ───────────────────────────────────────────────
    public void startElection() {
        if (!nodeState.startElectionIfNotInProgress()) return;

        receivedOk.set(false);
        log.info("Nodo {} inicia elección Bully", nodeConfig.getId());
        nodeState.logEvent("Elección iniciada por nodo " + nodeConfig.getId());
        eventPublisher.bullyElection(nodeConfig.getId());

        var higherNodes = clusterConfig.getHigherNodes(nodeConfig.getId());
        if (higherNodes.isEmpty()) {
            becomeCoordinator();
            return;
        }

        higherNodes.forEach(this::sendElectionMessage);

        CompletableFuture.delayedExecutor(bullyConfig.getElectionTimeoutMs(), TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (!receivedOk.get()) {
                        becomeCoordinator();
                    } else {
                        log.info("Recibí OK, espero anuncio del nuevo coordinador.");
                        nodeState.endElection();
                    }
                });
    }

    // ── Recepción de mensajes ────────────────────────────────────────────
    public void receiveElection(int fromNodeId) {
        log.info("Recibido ELECCION de nodo {}", fromNodeId);
        nodeState.logEvent("ELECCION recibida de nodo " + fromNodeId);
        eventPublisher.bullyElection(fromNodeId);
        sendOkTo(fromNodeId);
        startElection();
    }

    public void receiveOk(int fromNodeId) {
        log.info("Recibido OK de nodo {}", fromNodeId);
        nodeState.logEvent("OK recibido de nodo " + fromNodeId);
        eventPublisher.bullyOk(fromNodeId);
        receivedOk.set(true);
    }

    public void receiveCoordinator(int newCoordinatorId) {
        log.info("Nuevo coordinador: nodo {}", newCoordinatorId);
        nodeState.setCoordinatorId(newCoordinatorId);
        nodeState.endElection();
        nodeState.markHeartbeat();
        nodeState.logEvent("Nuevo coordinador: nodo " + newCoordinatorId);
        eventPublisher.bullyCoordinator(newCoordinatorId);
    }

    public void receiveHeartbeat(int coordinatorId) {
        nodeState.markHeartbeat();
        nodeState.setCoordinatorId(coordinatorId);
    }

    // ── Proclamarse coordinador ──────────────────────────────────────────
    private void becomeCoordinator() {
        log.info("Nodo {} se proclama COORDINADOR", nodeConfig.getId());
        nodeState.setCoordinatorId(nodeConfig.getId());
        nodeState.endElection();
        nodeState.markHeartbeat(); // ← importante: reset para no auto-disparar elección
        nodeState.logEvent("Nodo " + nodeConfig.getId() + " es el nuevo COORDINADOR");
        eventPublisher.bullyCoordinator(nodeConfig.getId());

        clusterConfig.getOtherNodes(nodeConfig.getId()).forEach(node -> {
            try {
                restTemplate.postForEntity(node.getBaseUrl() + "/bully/coordinator",
                        Map.of("newCoordinatorId", nodeConfig.getId(),
                               "newCoordinatorIp",  nodeConfig.getIp()),
                        Void.class);
            } catch (Exception e) {
                log.warn("No se pudo notificar al nodo {} sobre nuevo coordinador", node.getId());
            }
        });
    }

    // ── Auxiliares ───────────────────────────────────────────────────────
    private boolean isCoordinator() {
        return nodeConfig.getId() == nodeState.getCoordinatorId();
    }

    private boolean electionInProgress() {
        return nodeState.isElectionInProgress();
    }

    private boolean noCoordinatorKnown() {
        return nodeState.getCoordinatorId() == -1;
    }

    private void sendElectionMessage(ClusterConfig.NodeInfo node) {
        try {
            restTemplate.postForEntity(node.getBaseUrl() + "/bully/election",
                    Map.of("fromNodeId", nodeConfig.getId()), Void.class);
            log.info("Enviado ELECCION al nodo {}", node.getId());
        } catch (Exception e) {
            log.warn("Nodo {} no respondió al ELECCION (puede estar caído)", node.getId());
        }
    }

    private void sendOkTo(int fromNodeId) {
        try {
            ClusterConfig.NodeInfo sender = clusterConfig.getNodeById(fromNodeId);
            restTemplate.postForEntity(sender.getBaseUrl() + "/bully/ok",
                    Map.of("fromNodeId", nodeConfig.getId()), Void.class);
            log.info("Enviado OK al nodo {}", fromNodeId);
        } catch (Exception e) {
            log.error("No se pudo enviar OK al nodo {}", fromNodeId, e);
        }
    }
}
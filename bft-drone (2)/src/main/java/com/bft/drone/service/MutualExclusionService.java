package com.bft.drone.service;

import com.bft.drone.config.ClusterConfig;
import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.NodeState;
import com.bft.drone.websocket.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exclusión mutua distribuida — Algoritmo de Ricart-Agrawala.
 *
 * Cada nodo puede solicitar acceso a una sección crítica.
 * Se basa en relojes lógicos de Lamport para ordenar las solicitudes.
 *
 * Protocolo:
 *  REQUEST:  el nodo difunde su solicitud con timestamp de Lamport.
 *  REPLY:    un nodo responde OK cuando no quiere entrar o tiene menor prioridad.
 *  RELEASE:  al salir de la sección crítica, responde a los diferidos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MutualExclusionService {

    private final NodeConfig nodeConfig;
    private final ClusterConfig clusterConfig;
    private final NodeState nodeState;
    private final EventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    // Reloj lógico de Lamport
    private final AtomicInteger lamportClock = new AtomicInteger(0);

    // Estado actual del nodo respecto a la sección crítica
    private enum State { RELEASED, WANTING, HELD }
    private volatile State state = State.RELEASED;

    // Timestamp de nuestra solicitud actual
    private volatile int requestTimestamp = 0;

    // Nodos que aún no nos han respondido OK
    private final Set<Integer> pendingReplies = ConcurrentHashMap.newKeySet();

    // Nodos a los que diferimos nuestra respuesta
    private final Queue<Integer> deferredQueue = new ConcurrentLinkedQueue<>();

    // Semáforo para bloquear hasta tener todos los OK
    private final Semaphore accessGranted = new Semaphore(0);

    // ── API pública ──────────────────────────────────────────────────────

    /**
     * Solicita acceso a la sección crítica. Bloquea hasta obtenerlo.
     * Uso: mutualExclusionService.requestAccess(); ... sección crítica ... mutualExclusionService.releaseAccess();
     */
    public void requestAccess() throws InterruptedException {
        state = State.WANTING;
        requestTimestamp = lamportClock.incrementAndGet();
        log.info("Nodo {} solicita exclusión mutua (timestamp={})",
                nodeConfig.getId(), requestTimestamp);
        nodeState.logEvent("ME REQUEST ts=" + requestTimestamp);

        // Colectar IDs de todos los otros nodos
        List<ClusterConfig.NodeInfo> others = clusterConfig.getOtherNodes(nodeConfig.getId());
        pendingReplies.clear();
        others.forEach(n -> pendingReplies.add(n.getId()));

        // Difundir REQUEST a todos
        others.forEach(node -> sendMeRequest(node, requestTimestamp));

        // Esperar hasta recibir OK de todos
        accessGranted.acquire();
        state = State.HELD;
        log.info("Nodo {} ENTRÓ a sección crítica", nodeConfig.getId());
        nodeState.logEvent("ME HELD — sección crítica activa");

        eventPublisher.publish(
                com.bft.drone.model.enums.EventType.ME_HELD,
                Map.of("nodeId", nodeConfig.getId(), "timestamp", requestTimestamp));
    }

    /**
     * Libera la sección crítica y responde a los nodos diferidos.
     */
    public void releaseAccess() {
        state = State.RELEASED;
        log.info("Nodo {} SALIÓ de sección crítica", nodeConfig.getId());
        nodeState.logEvent("ME RELEASE");

        eventPublisher.publish(
                com.bft.drone.model.enums.EventType.ME_RELEASED,
                Map.of("nodeId", nodeConfig.getId()));

        // Responder a todos los que estaban esperando
        Integer deferred;
        while ((deferred = deferredQueue.poll()) != null) {
            sendMeReply(deferred);
        }
    }

    // ── Recepción de mensajes ────────────────────────────────────────────

    /**
     * Otro nodo solicita entrar a la sección crítica.
     */
    public void receiveRequest(int fromNodeId, int timestamp) {
        // Actualizar reloj de Lamport
        lamportClock.updateAndGet(c -> Math.max(c, timestamp) + 1);

        log.info("ME REQUEST de nodo {} ts={}", fromNodeId, timestamp);

        boolean shouldDefer = state == State.HELD ||
                (state == State.WANTING && hasHigherPriority(timestamp, fromNodeId));

        if (shouldDefer) {
            deferredQueue.add(fromNodeId);
            log.debug("Diferido nodo {}", fromNodeId);
        } else {
            sendMeReply(fromNodeId);
        }
    }

    /**
     * Un nodo nos dio OK para entrar.
     */
    public void receiveReply(int fromNodeId) {
        lamportClock.incrementAndGet();
        log.info("ME REPLY de nodo {}", fromNodeId);
        pendingReplies.remove(fromNodeId);

        if (pendingReplies.isEmpty() && state == State.WANTING) {
            accessGranted.release();
        }
    }

    // ── Auxiliares ───────────────────────────────────────────────────────

    /**
     * Devuelve true si nuestra solicitud tiene mayor prioridad que la del otro nodo.
     * Prioridad: menor timestamp gana. En empate, menor ID gana.
     */
    private boolean hasHigherPriority(int otherTimestamp, int otherNodeId) {
        return requestTimestamp < otherTimestamp ||
               (requestTimestamp == otherTimestamp && nodeConfig.getId() < otherNodeId);
    }

    private void sendMeRequest(ClusterConfig.NodeInfo node, int timestamp) {
        try {
            restTemplate.postForEntity(node.getBaseUrl() + "/me/request",
                    Map.of("fromNodeId", nodeConfig.getId(), "timestamp", timestamp),
                    Void.class);
        } catch (Exception e) {
            // Si el nodo no responde, lo eliminamos de pendingReplies para no bloquear
            pendingReplies.remove(node.getId());
            log.warn("Nodo {} no respondió al REQUEST de exclusión mutua", node.getId());
            if (pendingReplies.isEmpty() && state == State.WANTING) {
                accessGranted.release();
            }
        }
    }

    private void sendMeReply(int toNodeId) {
        try {
            ClusterConfig.NodeInfo node = clusterConfig.getNodeById(toNodeId);
            restTemplate.postForEntity(node.getBaseUrl() + "/me/reply",
                    Map.of("fromNodeId", nodeConfig.getId()), Void.class);
            log.debug("ME REPLY enviado a nodo {}", toNodeId);
        } catch (Exception e) {
            log.warn("No se pudo enviar REPLY a nodo {}", toNodeId);
        }
    }
}
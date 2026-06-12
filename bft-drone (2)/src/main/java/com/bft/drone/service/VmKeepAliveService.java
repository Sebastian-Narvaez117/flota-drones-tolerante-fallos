package com.bft.drone.service;

import com.bft.drone.config.ClusterConfig;
import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.NodeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.InetAddress;

/**
 * Mantiene viva la ruta de red hacia todos los nodos del clúster.
 * Especialmente útil para la VM con alta latencia: el SO descarta
 * entradas ARP y conexiones TCP inactivas. Un ping cada 5s las renueva.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VmKeepAliveService {

    private final ClusterConfig clusterConfig;
    private final NodeConfig nodeConfig;
    private final NodeState nodeState;
    private final RestTemplate restTemplate;

    /**
     * Ping ICMP a todos los nodos cada 5 segundos.
     * Mantiene viva la tabla ARP y rutas TCP del SO.
     */
    @Scheduled(fixedDelay = 5000L)
    public void pingAllNodes() {
        clusterConfig.getOtherNodes(nodeConfig.getId()).forEach(node -> {
            try {
                InetAddress addr = InetAddress.getByName(node.getIp());
                boolean reachable = addr.isReachable(2000); // timeout 2s
                if (!reachable) {
                    log.debug("Nodo {} ({}) no respondió ping ICMP", node.getId(), node.getIp());
                }
            } catch (IOException e) {
                log.debug("Error ping a nodo {}: {}", node.getId(), e.getMessage());
            }
        });
    }

    /**
     * Health-check HTTP liviano cada 5 segundos.
     * Mantiene viva la conexión TCP y registra el último tiempo de respuesta.
     * Esto es lo que realmente resuelve el problema de la VM:
     * mantiene el socket TCP abierto y actualiza el heartbeat si el nodo
     * que responde es el coordinador actual.
     */
    @Scheduled(fixedDelay = 5000L, initialDelay = 2500L)
    public void httpHealthCheck() {
        clusterConfig.getOtherNodes(nodeConfig.getId()).forEach(node -> {
            try {
                restTemplate.getForEntity(node.getBaseUrl() + "/health", String.class);
                // Si el nodo que responde es el coordinador, renovar heartbeat local
                if (node.getId() == nodeState.getCoordinatorId()) {
                    nodeState.markHeartbeat();
                }
            } catch (Exception e) {
                log.debug("Health-check falló nodo {}: {}", node.getId(), e.getMessage());
            }
        });
    }
}
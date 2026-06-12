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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementación del Algoritmo de Cristian para sincronización de relojes.
 *
 * Funcionamiento:
 *  1. El cliente envía una petición al servidor de tiempo (coordinador) en t0.
 *  2. El servidor responde con su tiempo actual Ts.
 *  3. El cliente recibe la respuesta en t1.
 *  4. El cliente ajusta su reloj: T_local = Ts + (t1 - t0) / 2
 *     (se asume que el tiempo de ida es igual al de vuelta).
 *
 * El offset calculado se aplica como corrección sobre System.currentTimeMillis().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CristianService {

    private final NodeConfig nodeConfig;
    private final ClusterConfig clusterConfig;
    private final NodeState nodeState;
    private final EventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    /**
     * Offset acumulado respecto al reloj del coordinador (en ms).
     * clockOffset > 0 → nuestro reloj va atrasado.
     * clockOffset < 0 → nuestro reloj va adelantado.
     */
    private final AtomicLong clockOffset = new AtomicLong(0);

    /**
     * Tiempo local corregido (usa el offset calculado).
     */
    public long getSynchronizedTime() {
        return System.currentTimeMillis() + clockOffset.get();
    }

    /**
     * Cada 10 segundos, los nodos no-coordinadores sincronizan su reloj
     * contra el coordinador actual usando el algoritmo de Cristian.
     */
    @Scheduled(fixedDelay = 10_000L, initialDelay = 5000L)
    public void synchronizeClockWithCoordinator() {
        // El coordinador es la fuente de tiempo, no se sincroniza a sí mismo
        if (nodeConfig.getId() == nodeState.getCoordinatorId()) return;
        if (nodeState.getCoordinatorId() == -1) return; // sin coordinador aún

        try {
            ClusterConfig.NodeInfo coordinator =
                    clusterConfig.getNodeById(nodeState.getCoordinatorId());

            long t0 = System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    coordinator.getBaseUrl() + "/cristian/time", Map.class);

            long t1 = System.currentTimeMillis();

            if (response == null || !response.containsKey("serverTime")) {
                log.warn("Respuesta inválida del servidor de tiempo");
                return;
            }

            long serverTime = ((Number) response.get("serverTime")).longValue();
            long rtt = t1 - t0;                    // Round-Trip Time
            long estimatedServerTimeNow = serverTime + rtt / 2;
            long newOffset = estimatedServerTimeNow - t1;

            clockOffset.set(newOffset);

            log.info("Cristian sync → RTT={}ms, offset={}ms, hora_sync={}",
                    rtt, newOffset, estimatedServerTimeNow);
            nodeState.logEvent(String.format(
                    "Cristian: RTT=%dms offset=%dms (coordinador nodo %d)",
                    rtt, newOffset, nodeState.getCoordinatorId()));

            eventPublisher.publish(
                    com.bft.drone.model.enums.EventType.CRISTIAN_SYNC,
                    Map.of("rtt", rtt, "offset", newOffset,
                           "serverTime", serverTime,
                           "coordinatorId", nodeState.getCoordinatorId()));

        } catch (Exception e) {
            log.warn("Fallo sincronización Cristian con coordinador {}: {}",
                    nodeState.getCoordinatorId(), e.getMessage());
        }
    }
}
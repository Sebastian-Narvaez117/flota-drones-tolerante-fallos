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
    private final AtomicLong lastRttMs = new AtomicLong(-1);

    /**
     * Tiempo local corregido (usa el offset calculado).
     */
    public long getSynchronizedTime() {
        return System.currentTimeMillis() + clockOffset.get();
    }

    /** Retorna el offset actual, o 0 si nunca se sincronizó */
    public long getClockOffset() {
        return clockOffset.get();
    }

    /** Retorna el último RTT medido, o -1 si nunca hubo sincronización */
    public long getLastRtt() {
        return lastRttMs.get();
    }

    @Scheduled(fixedDelay = 10_000L, initialDelay = 5000L)
    public void synchronizeClockWithCoordinator() {
        if (nodeConfig.getId() == nodeState.getCoordinatorId()) return;
        if (nodeState.getCoordinatorId() == -1) return;

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
            long rtt = t1 - t0;
            long estimatedServerTimeNow = serverTime + rtt / 2;
            long newOffset = estimatedServerTimeNow - t1;

            clockOffset.set(newOffset);
            lastRttMs.set(rtt);

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
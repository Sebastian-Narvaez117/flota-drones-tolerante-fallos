package com.bft.drone.websocket;

import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.enums.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final NodeConfig nodeConfig;
    private static final String TOPIC = "/topic/events";

    // Método único público
    public void publish(EventType type, Map<String, Object> data) {
        Map<String, Object> event = Map.of(
            "type",      type.name(),
            "nodeId",    nodeConfig.getId(),
            "nodeRole",  nodeConfig.getRole().name(),
            "data",      data,
            "timestamp", Instant.now().toEpochMilli()
        );
        messagingTemplate.convertAndSend(TOPIC, event);
    }

// Metodo para cada tipo de evento, para facilitar su uso en el código del servicio BFT
public void bftPropose(int round, String trajectory) {
    publish(EventType.PROPUESTA_BFT, Map.of("round", round, "trajectory", trajectory));
}

// Metodo para propuesta dirigida a un nodo específico (usado por el traidor)
public void bftProposeTo(int round, int toNode, String trajectory) {
    publish(EventType.PROPUESTA_BFT_A, Map.of("round", round, "toNode", toNode, "trajectory", trajectory));
}

// Metodo para relay genérico (usado por tenientes leales)
public void bftRelay(int round, int fromNode, String trajectory) {
    publish(EventType.REENVIO_BFT, Map.of("round", round, "fromNode", fromNode, "trajectory", trajectory));
}

// Metodo para relay dirigido a un nodo específico (usado por el traidor)
public void bftRelayTo(int round, int fromNode, int toNode, String trajectory) {
    publish(EventType.REENVIO_BFT_A, Map.of("round", round, "fromNode", fromNode, "toNode", toNode, "trajectory", trajectory));
}

// Metodo para decisión tomada por un teniente
public void bftDecision(int round, String decision) {
    publish(EventType.DECISION_BFT, Map.of("round", round, "decision", decision));
}

// Métodos para eventos del algoritmo Bully
public void bullyElection(int fromNode) {
    publish(EventType.BULLY_ELECCION_INICIADA, Map.of("fromNode", fromNode));
}

// Método para indicar que se recibió un OK en la elección Bully
public void bullyOk(int fromNode) {
    publish(EventType.BULLY_OK_RECIBIDO, Map.of("fromNode", fromNode));
}

// Método para indicar que hay un nuevo coordinador tras la elección Bully
public void bullyCoordinator(int newCoordinator) {
    publish(EventType.BULLY_NUEVO_COORDINADOR, Map.of("newCoordinator", newCoordinator));
}

// Método para indicar que se envió un heartbeat (latido) al coordinador y que el nodo está vivo
public void heartbeat(int coordinatorId) {
    publish(EventType.HEARTBEAT_ENVIADO, Map.of("coordinatorId", coordinatorId));
}
    }

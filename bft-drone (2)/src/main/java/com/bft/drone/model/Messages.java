package com.bft.drone.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// ============================================================
// Mensajes BFT (Algoritmo OM-1)
// ============================================================

/**
 * Fase 1: el coordinador propone una trayectoria a todos los tenientes.
 * POST /bft/propose
 */
@Data @NoArgsConstructor @AllArgsConstructor
class BftProposeMessage {
    private int fromNodeId;       // ID del coordinador
    private int round;            // numero de ronda
    private String trajectory;   // trayectoria propuesta (ej: "NORTE:100,ESTE:50")
}

/**
 * Fase 2: cada teniente reenvía lo que recibió del coordinador a sus pares.
 * POST /bft/relay
 */
@Data @NoArgsConstructor @AllArgsConstructor
class BftRelayMessage {
    private int fromNodeId;       // quien reenvía
    private int round;            // numero de ronda
    private String trajectory;   // valor que este nodo recibio del coordinador
}

/**
 * Resultado de consenso de un nodo al final de una ronda.
 */
@Data @NoArgsConstructor @AllArgsConstructor
class BftDecision {
    private int nodeId;
    private int round;
    private String decidedTrajectory;   // valor decidido por mayoria
    private boolean consensusReached;   // si coincide con la mayoria del cluster
}

// ============================================================
// Mensajes Bully (Eleccion de lider)
// ============================================================

/**
 * Mensaje ELECTION: un nodo inicia la eleccion.
 * POST /bully/election
 */
@Data @NoArgsConstructor @AllArgsConstructor
class BullyElectionMessage {
    private int fromNodeId;   // quien inicio la eleccion
}

/**
 * Mensaje OK: un nodo con ID mayor responde que tomara el control.
 * POST /bully/ok
 */
@Data @NoArgsConstructor @AllArgsConstructor
class BullyOkMessage {
    private int fromNodeId;   // quien responde con OK
}

/**
 * Mensaje COORDINATOR: el nuevo coordinador se anuncia.
 * POST /bully/coordinator
 */
@Data @NoArgsConstructor @AllArgsConstructor
class BullyCoordinatorMessage {
    private int newCoordinatorId;   // ID del nuevo coordinador
    private String newCoordinatorIp;
}

// ============================================================
// Evento WebSocket (hacia el dashboard Flask)
// ============================================================

/**
 * Evento generico enviado por WebSocket al dashboard.
 * Se publica en /topic/events
 */
@Data @NoArgsConstructor @AllArgsConstructor
class DroneEvent {
    private String type;        // BFT_PROPOSE | BFT_RELAY | BFT_DECISION | BULLY_ELECTION | BULLY_OK | BULLY_COORDINATOR | HEARTBEAT
    private int nodeId;         // nodo que genera el evento
    private String payload;     // JSON con datos del evento
    private long timestamp;     // epoch ms
}

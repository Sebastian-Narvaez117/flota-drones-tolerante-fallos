package com.bft.drone.model.enums;

/**
 * Tipos de eventos que se envían al dashboard vía WebSocket.
 * Antes usabas Strings como "BFT_PROPOSE", ahora son tipos seguros.
 */
public enum EventType {
    // Eventos del algoritmo BFT (Generales Bizantinos)
    PROPUESTA_BFT,
    PROPUESTA_BFT_A,
    REENVIO_BFT,
    REENVIO_BFT_A,
    DECISION_BFT,
    
    // Eventos del algoritmo Bully (Elección de líder)
    BULLY_ELECCION_INICIADA,
    BULLY_OK_RECIBIDO,
    BULLY_NUEVO_COORDINADOR,
    
    // Evento de vida del nodo
    HEARTBEAT_ENVIADO,
    // Eventos del algoritmo de exclusión mutua
    ME_HELD,
    ME_RELEASED, CRISTIAN_SYNC
}
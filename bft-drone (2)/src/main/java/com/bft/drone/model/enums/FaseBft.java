package com.bft.drone.model.enums;

/**
 * Fases del algoritmo de los Generales Bizantinos (OM-1).
 * 
 * Flujo: PROPONER → REENVIAR → DECIDIR
 */
public enum FaseBft {
    PROPONER,    // Fase 1: El coordinador envía su propuesta
    REENVIAR,    // Fase 2: Los tenientes reenvían lo recibido a sus pares
    DECIDIR;     // Fase 3: Cada nodo decide por mayoría
}
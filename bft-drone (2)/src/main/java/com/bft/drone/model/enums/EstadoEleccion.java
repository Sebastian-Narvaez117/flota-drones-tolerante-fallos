package com.bft.drone.model.enums;

/**
 * Estado actual del nodo respecto a la elección de coordinador (Bully).
 * Es más claro que usar un booleano porque tiene 3 estados, no 2.
 */
public enum EstadoEleccion {
    ESPERANDO,      // No hay elección en curso, sigo al coordinador
    EN_ELECCION,    // Estoy participando en una elección
    SOY_COORDINADOR // Yo soy el coordinador actual
}
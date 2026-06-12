package com.bft.drone.model.enums;

/**
 * Rol del dron en la flota.
 * Un dron leal sigue el protocolo, un traidor envía mensajes falsos.
 */
public enum RolNodo {
    leal,      // Sigue el algoritmo correctamente
    traidor;   // Envía trayectorias falsas (hackeado por el enemigo)
}
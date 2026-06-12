package com.bft.drone.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Representa la trayectoria que un dron debe seguir.
 * Antes usabas Strings como "NORTE:100,ESTE:50", ahora es un objeto tipado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Trajectory {
    private String direccion;   // Ej: "NORTE", "SUR", "ESTE", "OESTE"
    private int distancia;      // Ej: 100 (metros)
    
    /**
     * Convierte la trayectoria a un formato legible para el log.
     */
    public String comoTexto() {
        return direccion + ":" + distancia;
    }
    
    /**
     * Crea una trayectoria desde un texto .
     */
    public static Trajectory desdeTexto(String texto) {
        String[] partes = texto.split(":");
        return new Trajectory(partes[0], Integer.parseInt(partes[1]));
    }
}
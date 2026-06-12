package com.bft.drone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.bft.drone.model.enums.RolNodo;



/**
 * Configuracion del nodo actual y del cluster completo.
 * Se carga desde application.yml y se sobreescribe con variables de entorno.
 *
 * Variables requeridas al arrancar el JAR:
 *   NODE_ID    : identificador del nodo (1-4)
 *   NODE_IP    : IP de este nodo
 *   NODE_ROLE  : loyal | traitor
 *   SERVER_PORT: puerto HTTP (default 8080)
 */
@Data
@Component
@ConfigurationProperties(prefix = "node")
public class NodeConfig {
    private int id;
    private String ip;
    private RolNodo role;
    private int port;

    public boolean isTraitor() {
        return RolNodo.traidor == role;
    }

    public boolean isLoyal() {
        return !isTraitor();
    }
}

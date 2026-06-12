package com.bft.drone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "cluster")
public class ClusterConfig {

    private List<NodeInfo> nodes;

    @Data
    public static class NodeInfo {
        private int id;
        private String ip;
        private int port;

        public String getBaseUrl() {
            return "http://" + ip + ":" + port;
        }
    }

    /** Retorna la info de un nodo por su ID */
    public NodeInfo getNodeById(int id) {
        return nodes.stream()
                .filter(n -> n.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nodo no encontrado: " + id));
    }

    /** Retorna todos los nodos excepto el indicado */
    public List<NodeInfo> getOtherNodes(int excludeId) {
        return nodes.stream()
                .filter(n -> n.getId() != excludeId)
                .toList();
    }

    /** Retorna nodos con ID mayor al indicado (para el algoritmo Bully) */
    public List<NodeInfo> getHigherNodes(int currentId) {
        return nodes.stream()
                .filter(n -> n.getId() > currentId)
                .toList();
    }
}

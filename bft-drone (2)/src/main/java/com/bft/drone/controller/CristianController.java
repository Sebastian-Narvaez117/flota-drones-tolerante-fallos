package com.bft.drone.controller;

import com.bft.drone.config.NodeConfig;
import com.bft.drone.model.NodeState;
import com.bft.drone.service.CristianService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints del Algoritmo de Cristian.
 * GET /cristian/time  → servidor de tiempo (solo el coordinador responde significativamente)
 * GET /cristian/status → estado de sincronización del nodo
 */
@RestController
@RequestMapping("/cristian")
@RequiredArgsConstructor
public class CristianController {

    private final NodeConfig nodeConfig;
    private final NodeState nodeState;
    private final CristianService cristianService;

    /**
     * Servidor de tiempo: responde con el timestamp actual.
     * Cualquier nodo puede responder, pero los clientes solo consultan al coordinador.
     */
    @GetMapping("/time")
    public ResponseEntity<Map<String, Object>> getServerTime() {
        return ResponseEntity.ok(Map.of(
                "serverTime",    System.currentTimeMillis(),
                "nodeId",        nodeConfig.getId(),
                "isCoordinator", nodeConfig.getId() == nodeState.getCoordinatorId()
        ));
    }

    /**
     * Estado de sincronización local: cuánto vale el offset calculado por Cristian.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        return ResponseEntity.ok(Map.of(
                "nodeId",          nodeConfig.getId(),
                "synchronizedTime", cristianService.getSynchronizedTime(),
                "systemTime",      System.currentTimeMillis(),
                "coordinatorId",   nodeState.getCoordinatorId()
        ));
    }
}
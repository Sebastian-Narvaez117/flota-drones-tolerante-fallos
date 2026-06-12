package com.bft.drone.controller;

import com.bft.drone.service.BullyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints REST para el algoritmo Bully.
 *
 * POST /bully/election    -> Mensaje ELECTION de un nodo con ID menor
 * POST /bully/ok          -> Mensaje OK de un nodo con ID mayor
 * POST /bully/coordinator -> Anuncio del nuevo coordinador
 * POST /bully/heartbeat   -> Heartbeat periodico del coordinador activo
 */
@Slf4j
@RestController
@RequestMapping("/bully")
@RequiredArgsConstructor
public class BullyController {

    private final BullyService bullyService;

    @PostMapping("/election")
    public ResponseEntity<Void> election(@RequestBody Map<String, Object> body) {
        int fromNodeId = (int) body.get("fromNodeId");
        bullyService.receiveElection(fromNodeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ok")
    public ResponseEntity<Void> ok(@RequestBody Map<String, Object> body) {
        int fromNodeId = (int) body.get("fromNodeId");
        bullyService.receiveOk(fromNodeId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/coordinator")
    public ResponseEntity<Void> coordinator(@RequestBody Map<String, Object> body) {
        int newCoordinatorId = (int) body.get("newCoordinatorId");
        bullyService.receiveCoordinator(newCoordinatorId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody Map<String, Object> body) {
        int coordinatorId = (int) body.get("coordinatorId");
        bullyService.receiveHeartbeat(coordinatorId);
        return ResponseEntity.ok().build();
    }
}

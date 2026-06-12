package com.bft.drone.controller;

import com.bft.drone.service.BftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints REST para el algoritmo BFT OM(1).
 *
 * POST /bft/propose  -> Fase 1: coordinador propone trayectoria
 * POST /bft/relay    -> Fase 2: teniente reenvía valor a sus pares
 * GET  /bft/status   -> Estado de la ronda actual y historial
 */
@Slf4j
@RestController
@RequestMapping("/bft")
@RequiredArgsConstructor
public class BftController {

    private final BftService bftService;

    @PostMapping("/propose")
    public ResponseEntity<Void> propose(@RequestBody Map<String, Object> body) {
        int fromNodeId = (int) body.get("fromNodeId");
        int round      = (int) body.get("round");
        String traj    = (String) body.get("trajectory");
        bftService.receivePropose(fromNodeId, round, traj);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/relay")
    public ResponseEntity<Void> relay(@RequestBody Map<String, Object> body) {
        int fromNodeId = (int) body.get("fromNodeId");
        int round      = (int) body.get("round");
        String traj    = (String) body.get("trajectory");
        bftService.receiveRelay(fromNodeId, round, traj);
        return ResponseEntity.ok().build();
    }
}

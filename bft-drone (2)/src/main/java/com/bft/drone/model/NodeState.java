package com.bft.drone.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NodeState {

    // --- Estado Bully ---
    private volatile int coordinatorId = -1;
    private volatile boolean electionInProgress = false;
    private volatile long lastHeartbeatMs = System.currentTimeMillis();

    // --- Estado BFT ---
    private int currentRound = 0;

    // Mensajes relay recibidos en la ronda actual: nodeId -> trajectory
    private final Map<Integer, String> relayMessages = new ConcurrentHashMap<>();

    // Historial de decisiones: ronda -> trajectory decidida
    private final Map<Integer, String> decisionHistory = new ConcurrentHashMap<>();

    // Log de eventos para exponer al dashboard via REST
    private final List<String> eventLog = new ArrayList<>();

    // -------------------------------------------------------
    // Métodos sincronizados para el estado Bully
    // -------------------------------------------------------

    public synchronized int getCoordinatorId() {
        return coordinatorId;
    }

    public synchronized void setCoordinatorId(int id) {
        this.coordinatorId = id;
    }

    public synchronized boolean isElectionInProgress() {
        return electionInProgress;
    }

    /**
     * Intenta iniciar una elección si no hay otra en curso.
     * @return true si la elección fue iniciada por este nodo, false si ya estaba en curso.
     */
    public synchronized boolean startElectionIfNotInProgress() {
        if (electionInProgress) {
            return false;
        }
        electionInProgress = true;
        return true;
    }

    public synchronized void endElection() {
        electionInProgress = false;
    }

    public long getLastHeartbeatMs() {
        return lastHeartbeatMs;
    }

    public void markHeartbeat() {
        lastHeartbeatMs = System.currentTimeMillis();
    }

    // -------------------------------------------------------
    // Métodos sincronizados para el estado BFT
    // -------------------------------------------------------

    public synchronized int getCurrentRound() {
        return currentRound;
    }

    public synchronized int incrementAndGetRound() {
        return ++currentRound;
    }

    public synchronized void setCurrentRound(int round) {
        this.currentRound = round;
    }

    // -------------------------------------------------------
    // Historial de decisiones 
    // -------------------------------------------------------
    public void recordDecision(int round, String trajectory) {
        decisionHistory.put(round, trajectory);
    }

    public Map<Integer, String> getDecisionHistory() {
        // devolvemos una copia inmutable para evitar modificaciones externas
        return Map.copyOf(decisionHistory);
    }

    // -------------------------------------------------------
    // Relay messages (
    // -------------------------------------------------------
    public void clearRelayMessages() {
        relayMessages.clear();
    }

    public void recordRelay(int fromNodeId, String trajectory) {
        relayMessages.put(fromNodeId, trajectory);
    }

    // -------------------------------------------------------
    // Log de eventos (
    // -------------------------------------------------------
    public void logEvent(String event) {
        synchronized (eventLog) {
            eventLog.add("[" + System.currentTimeMillis() + "] " + event);
            if (eventLog.size() > 100) {
                eventLog.remove(0);
            }
        }
    }

    public List<String> getRecentEvents(int count) {
        synchronized (eventLog) {
            int size = eventLog.size();
            return new ArrayList<>(eventLog.subList(Math.max(0, size - count), size));
        }
    }
}
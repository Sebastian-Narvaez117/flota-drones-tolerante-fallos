from flask import Flask, render_template, jsonify
import requests
import threading
import time
from collections import deque

app = Flask(__name__)

NODES = [
    {"id": 1, "ip": "192.168.0.101", "port": 8080},
    {"id": 4, "ip": "192.168.0.102", "port": 8080},
    {"id": 3, "ip": "192.168.0.103", "port": 8080},
    {"id": 2, "ip": "192.168.0.104", "port": 8080},
]

node_status_cache = {}
cristian_cache    = {}   # nodeId -> {synchronizedTime, offset, rtt, coordinatorId}
me_cache          = {}   # nodeId -> {state, lamportClock, pendingReplies, deferredQueue}
cache_lock        = threading.Lock()

# Historial de eventos de elección para el panel Bully
bully_event_history = deque(maxlen=60)
me_event_history    = deque(maxlen=60)

# ----------------------------------------------------------------
# Helpers de fetch
# ----------------------------------------------------------------

def base_url(node):
    return f"http://{node['ip']}:{node['port']}"

def safe_get(url, timeout=2):
    try:
        r = requests.get(url, timeout=timeout)
        if r.status_code == 200:
            return r.json()
    except Exception:
        pass
    return None

def fetch_node_status(node):
    data = safe_get(f"{base_url(node)}/status")
    if data:
        data["online"] = True
        data.setdefault("nodePort", node["port"])
        return data
    return {
        "nodeId": node["id"], "nodeIp": node["ip"], "nodePort": node["port"],
        "coordinatorId": -1, "currentRound": 0, "electionActive": False,
        "nodeRole": "unknown", "decisionHistory": {}, "recentEvents": [],
        "online": False,
    }

def fetch_cristian_status(node):
    data = safe_get(f"{base_url(node)}/cristian/status")
    if data:
        data["online"] = True
        return data
    return {"nodeId": node["id"], "online": False, "synchronizedTime": None,
            "clockOffset": None, "lastRtt": None, "coordinatorId": -1}

def fetch_me_status(node):
    data = safe_get(f"{base_url(node)}/me/status")
    if data:
        data["online"] = True
        return data
    return {"nodeId": node["id"], "online": False, "state": "UNKNOWN",
            "lamportClock": 0, "pendingReplies": [], "deferredQueue": []}

# ----------------------------------------------------------------
# Poller background
# ----------------------------------------------------------------

def poll_nodes():
    while True:
        for node in NODES:
            status   = fetch_node_status(node)
            cristian = fetch_cristian_status(node)
            me       = fetch_me_status(node)

            with cache_lock:
                prev = node_status_cache.get(node["id"], {})

                # Detectar evento Bully nuevo
                prev_coord = prev.get("coordinatorId", -1)
                new_coord  = status.get("coordinatorId", -1)
                if new_coord != prev_coord and new_coord > 0:
                    bully_event_history.append({
                        "type": "coordinator", "nodeId": node["id"],
                        "msg": f"D{node['id']} reconoce coordinador D{new_coord}",
                        "ts": int(time.time() * 1000),
                    })
                if not prev.get("electionActive") and status.get("electionActive"):
                    bully_event_history.append({
                        "type": "election", "nodeId": node["id"],
                        "msg": f"D{node['id']} inició ELECTION",
                        "ts": int(time.time() * 1000),
                    })

                # Detectar evento ME nuevo
                prev_me_state = me_cache.get(node["id"], {}).get("state", "")
                new_me_state  = me.get("state", "")
                if prev_me_state != new_me_state and new_me_state not in ("UNKNOWN", ""):
                    me_event_history.append({
                        "nodeId": node["id"], "state": new_me_state,
                        "lamport": me.get("lamportClock", 0),
                        "ts": int(time.time() * 1000),
                    })

                node_status_cache[node["id"]] = status
                cristian_cache[node["id"]]    = cristian
                me_cache[node["id"]]          = me

        time.sleep(2)

threading.Thread(target=poll_nodes, daemon=True).start()

# ----------------------------------------------------------------
# Rutas Flask
# ----------------------------------------------------------------

@app.route("/")
def index():
    return render_template("dashboard.html", nodes=NODES)

@app.route("/api/status")
def api_status():
    with cache_lock:
        return jsonify(list(node_status_cache.values()))

@app.route("/api/cristian")
def api_cristian():
    with cache_lock:
        return jsonify(list(cristian_cache.values()))

@app.route("/api/me")
def api_me():
    with cache_lock:
        return jsonify({
            "nodes":   list(me_cache.values()),
            "history": list(me_event_history),
        })

@app.route("/api/bully/events")
def api_bully_events():
    with cache_lock:
        return jsonify(list(bully_event_history))

@app.route("/api/coordinator")
def api_coordinator():
    """Retorna info extendida del nodo coordinador actual."""
    with cache_lock:
        nodes = list(node_status_cache.values())

    coordinator_id = -1
    for n in nodes:
        if n.get("online") and n.get("coordinatorId", -1) > 0:
            coordinator_id = n["coordinatorId"]
            break

    coord_node = next((n for n in nodes if n.get("nodeId") == coordinator_id), None)
    return jsonify({
        "coordinatorId":   coordinator_id,
        "coordinatorIp":   coord_node.get("nodeIp", "—") if coord_node else "—",
        "coordinatorRole": coord_node.get("nodeRole", "—") if coord_node else "—",
        "currentRound":    coord_node.get("currentRound", 0) if coord_node else 0,
        "online":          coord_node.get("online", False) if coord_node else False,
        "recentEvents":    coord_node.get("recentEvents", [])[:10] if coord_node else [],
    })

@app.route("/api/node/<int:node_id>")
def api_node(node_id):
    with cache_lock:
        return jsonify(node_status_cache.get(node_id, {}))

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
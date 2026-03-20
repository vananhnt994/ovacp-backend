#!/bin/sh
# ==============================================================================
#  OCVAP Backend – Startup Script fuer Cloud Run
#  Startet alle 6 Microservices in einem Container.
#  Gateway (Port $PORT) ist der Hauptprozess (PID 1 via exec).
#
#  Empfohlene Cloud Run Einstellungen:
#    --memory=2Gi --cpu=2 --timeout=600 --startup-cpu-boost
# ==============================================================================

set -e

# Cloud Run setzt PORT; Fallback auf 8080
GATEWAY_PORT="${PORT:-8080}"

# Feste Heap-Groessen statt Prozentwert (zuverlaessiger bei 6 JVMs)
JVM_COMMON="-XX:+UseContainerSupport -XX:+UseSerialGC -Xss256k"
JVM_SMALL="$JVM_COMMON -Xmx128m"
JVM_MEDIUM="$JVM_COMMON -Xmx192m"
JVM_LARGE="$JVM_COMMON -Xmx256m"

echo "=== [1/6] Starte Eureka Server (Port 8761) ==="
java $JVM_SMALL -jar /app/eureka.jar &

# Warten bis Eureka bereit ist (max 45s statt 120s)
echo "=== Warte auf Eureka ==="
for i in $(seq 1 45); do
  if wget -qO- http://localhost:8761/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "=== Eureka ist bereit nach ${i}s ==="
    break
  fi
  if [ "$i" -eq 45 ]; then
    echo "=== WARNUNG: Eureka-Timeout nach 45s, starte trotzdem ==="
  fi
  sleep 1
done

# Alle Backend-Services parallel starten
echo "=== [2/6] Starte File Management (Port 8082) ==="
java $JVM_MEDIUM -jar /app/file-management.jar &

echo "=== [3/6] Starte Usermanagement (Port 8081) ==="
java $JVM_MEDIUM -jar /app/usermanagement.jar &

echo "=== [4/6] Starte AI Analysis Service (Port 8083) ==="
java $JVM_LARGE -jar /app/ai-analysis.jar &

echo "=== [5/6] Starte Chart Data Service (Port 8084) ==="
java $JVM_MEDIUM -jar /app/chart-data.jar &

# Kurz warten (kuerzer – Gateway kann ohne vollstaendige Registration starten)
sleep 3

echo "=== [6/6] Starte API Gateway (Port $GATEWAY_PORT) – Hauptprozess ==="
exec java $JVM_MEDIUM \
  -Dserver.port="$GATEWAY_PORT" \
  -Dcors.allowed-origins="${CORS_ALLOWED_ORIGINS:-http://localhost:3000}" \
  -Droute.usermanagement="http://localhost:8081" \
  -Droute.file-management="http://localhost:8082" \
  -Droute.ai-analysis="http://localhost:8083" \
  -Droute.chart-data="http://localhost:8084" \
  -jar /app/gateway.jar

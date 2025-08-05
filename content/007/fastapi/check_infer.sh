#!/bin/bash
# real_world_examples.sh - Exemplos reais de uso da API

API_URL="http://localhost:8000"

echo "🌍 EXEMPLOS DO MUNDO REAL - High Cardinality Detection"
echo "======================================================"

# EXEMPLO 1: PROMETHEUS METRICS - Container IDs
echo -e "\n📊 EXEMPLO 1: PROMETHEUS - Container IDs"
echo "Simula métricas do Prometheus com container IDs únicos"
curl -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "container_id",
        "values": [
          "docker://1a2b3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890",
          "docker://2b3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890ab",
          "docker://3c4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890abcd",
          "docker://4d5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
          "docker://5e6f7890abcdef1234567890abcdef1234567890abcdef1234567890abcdef12"
        ],
        "metric_name": "container_cpu_usage_seconds_total"
      },
      {
        "name": "image",
        "values": ["nginx:1.21", "redis:6.2", "postgres:13"],
        "metric_name": "container_cpu_usage_seconds_total"
      }
    ],
    "time_window_hours": 24
  }' | jq '.high_risk_labels[] | {label: .label_name, risk: .risk_level, cardinality: .current_cardinality, pattern: .pattern_detected}'

# EXEMPLO 2: JAEGER TRACING - Trace e Span IDs
echo -e "\n🔍 EXEMPLO 2: JAEGER TRACING"
echo "Simula dados de tracing distribuído com alta cardinalidade"
curl -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "trace_id",
        "values": [
          "1a2b3c4d5e6f7890", "2b3c4d5e6f7890ab", "3c4d5e6f7890abcd",
          "4d5e6f7890abcdef", "5e6f7890abcdef12", "6f7890abcdef1234",
          "7890abcdef123456", "90abcdef12345678", "abcdef1234567890"
        ],
        "metric_name": "jaeger_spans_total"
      },
      {
        "name": "span_id", 
        "values": [
          "1a2b3c4d", "2b3c4d5e", "3c4d5e6f", "4d5e6f78",
          "5e6f7890", "6f7890ab", "7890abcd", "90abcdef"
        ],
        "metric_name": "jaeger_spans_total"
      },
      {
        "name": "operation_name",
        "values": ["HTTP GET", "HTTP POST", "DB Query", "Cache Read"],
        "metric_name": "jaeger_spans_total"
      }
    ],
    "time_window_hours": 1
  }' | jq '{
    analysis_id: .analysis_id,
    critical_alerts: .summary.critical_labels,
    high_risk_alerts: .summary.high_risk_labels,
    recommendations: [.high_risk_labels[].recommendations[0]]
  }'

# EXEMPLO 3: NGINX ACCESS LOGS - IPs e User Agents
echo -e "\n🌐 EXEMPLO 3: NGINX ACCESS LOGS"
echo "Simula logs do Nginx com IPs e User Agents únicos"
curl -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "remote_addr",
        "values": [
          "203.0.113.1", "203.0.113.2", "203.0.113.3",
          "198.51.100.1", "198.51.100.2", "192.0.2.1",
          "10.0.0.1", "172.16.0.1", "192.168.1.1"
        ],
        "metric_name": "nginx_http_requests_total"
      },
      {
        "name": "user_agent",
        "values": [
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
          "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15",
          "curl/7.68.0", "Go-http-client/1.1", "Python-urllib/3.9"
        ],
        "metric_name": "nginx_http_requests_total"
      },
      {
        "name": "status",
        "values": ["200", "404", "500", "403"],
        "metric_name": "nginx_http_requests_total"
      }
    ]
  }' | jq '.high_risk_labels[] | select(.risk_level != "low") | {
    label: .label_name,
    current: .current_cardinality,
    predicted: .predicted_cardinality_24h,
    growth_rate: .estimated_growth_rate,
    top_recommendation: .recommendations[0]
  }'

# EXEMPLO 4: KUBERNETES EVENTS - Resource Names
echo -e "\n☸️ EXEMPLO 4: KUBERNETES EVENTS"
echo "Simula eventos do Kubernetes com nomes únicos de recursos"
curl -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "involved_object_name",
        "values": [
          "web-deployment-7d4b8c9f6d-abc12",
          "web-deployment-7d4b8c9f6d-def34", 
          "web-deployment-7d4b8c9f6d-ghi56",
          "api-deployment-8e5c9d0f7e-jkl78",
          "api-deployment-8e5c9d0f7e-mno90",
          "worker-deployment-9f6e0a1g8f-pqr12",
          "cache-statefulset-0", "cache-statefulset-1",
          "db-statefulset-0", "db-statefulset-1"
        ],
        "metric_name": "kubernetes_events_total"
      },
      {
        "name": "reason",
        "values": ["Created", "Started", "Pulled", "Scheduled", "FailedMount"],
        "metric_name": "kubernetes_events_total"
      },
      {
        "name": "namespace",
        "values": ["default", "kube-system", "monitoring"],
        "metric_name": "kubernetes_events_total"
      }
    ]
  }' | jq 'if .high_risk_labels | length > 0 then 
    "⚠️ ALERTA: Detectados " + (.high_risk_labels | length | tostring) + " labels de risco!" 
  else 
    "✅ Nenhum problema detectado"
  end'

# EXEMPLO 5: APPLICATION LOGS - Session IDs
echo -e "\n📱 EXEMPLO 5: APPLICATION LOGS - Sessions"
echo "Simula logs de aplicação com session IDs únicos"
curl -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "session_id",
        "values": [
          "sess_1640995200_12345", "sess_1640995260_23456", 
          "sess_1640995320_34567", "sess_1640995380_45678",
          "sess_1640995440_56789", "sess_1640995500_67890",
          "sess_1640995560_78901", "sess_1640995620_89012"
        ],
        "metric_name": "application_requests_total"
      },
      {
        "name": "user_id",
        "values": ["user123", "user456", "user789", "user012"],
        "metric_name": "application_requests_total"
      },
      {
        "name": "endpoint",
        "values": ["/api/users", "/api/orders", "/api/products", "/health"],
        "metric_name": "application_requests_total"
      }
    ]
  }' | jq '.summary'

# EXEMPLO 6: DATABASE METRICS - Query IDs
echo -e "\n🗄️ EXEMPLO 6: DATABASE METRICS - Query Hashes"
echo "Simula métricas de banco com query hashes únicos"
curl -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "query_hash",
        "values": [
          "abc123def456", "def456ghi789", "ghi789jkl012",
          "jkl012mno345", "mno345pqr678", "pqr678stu901"
        ],
        "metric_name": "mysql_queries_total"
      },
      {
        "name": "schema",
        "values": ["app_production", "app_staging", "app_logs"],
        "metric_name": "mysql_queries_total"
      },
      {
        "name": "query_type",
        "values": ["SELECT", "INSERT", "UPDATE", "DELETE"],
        "metric_name": "mysql_queries_total"
      }
    ]
  }'

# EXEMPLO 7: LOAD BALANCER - Request IDs
echo -e "\n⚖️ EXEMPLO 7: LOAD BALANCER LOGS"
echo "Simula logs de load balancer com request IDs únicos"
curl -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "request_id",
        "values": [
          "req_2024_001_abc123", "req_2024_002_def456",
          "req_2024_003_ghi789", "req_2024_004_jkl012",
          "req_2024_005_mno345", "req_2024_006_pqr678"
        ],
        "metric_name": "haproxy_http_requests_total"
      },
      {
        "name": "backend",
        "values": ["web-servers", "api-servers", "static-files"],
        "metric_name": "haproxy_http_requests_total"
      }
    ]
  }' | jq 'if (.high_risk_labels | length) > 0 then 
    {
      alert: "CARDINALITY ISSUE DETECTED!",
      problematic_labels: [.high_risk_labels[] | .label_name],
      action_needed: "Review label cardinality immediately"
    }
  else
    {status: "OK", message: "No cardinality issues detected"}
  end'

# EXEMPLO 8: MONITORING SCRIPT - Check crítico
echo -e "\n🚨 EXEMPLO 8: SCRIPT DE MONITORAMENTO"
echo "Exemplo de como usar em um script de monitoramento"

# Simula uma verificação que retorna dados críticos
RESPONSE=$(curl -s -X POST "$API_URL/analyze/cardinality" \
  -H "Content-Type: application/json" \
  -d '{
    "labels": [
      {
        "name": "transaction_id",
        "values": [
          "txn_550e8400-e29b-41d4-a716-446655440001",
          "txn_550e8400-e29b-41d4-a716-446655440002",
          "txn_550e8400-e29b-41d4-a716-446655440003",
          "txn_6a7f9500-f3ac-52e5-b827-557766551112",
          "txn_7b8e0611-04bd-63f6-c938-668877662223"
        ],
        "metric_name": "payment_transactions_total"
      }
    ]
  }')

# Extrai informações críticas
CRITICAL_COUNT=$(echo "$RESPONSE" | jq '.summary.critical_labels')
HIGH_RISK_COUNT=$(echo "$RESPONSE" | jq '.summary.high_risk_labels')
TOTAL_PREDICTED=$(echo "$RESPONSE" | jq '.summary.total_predicted_cardinality')

echo "Resultado da verificação:"
echo "  📊 Labels críticos: $CRITICAL_COUNT"
echo "  ⚠️  Labels alto risco: $HIGH_RISK_COUNT"  
echo "  🔮 Cardinalidade predita: $TOTAL_PREDICTED"

# Lógica de alerta
if [ "$CRITICAL_COUNT" -gt 0 ]; then
    echo "🔥 AÇÃO NECESSÁRIA: Labels críticos detectados!"
    echo "$RESPONSE" | jq '.high_risk_labels[] | select(.risk_level == "critical") | .recommendations[0]'
elif [ "$HIGH_RISK_COUNT" -gt 0 ]; then
    echo "⚠️ ATENÇÃO: Labels de alto risco detectados"
else
    echo "✅ Tudo OK - Sem problemas de cardinalidade"
fi

echo -e "\n💡 COMANDOS ÚTEIS PARA PRODUÇÃO:"
echo "=================================="
echo ""
echo "1. 🔍 Verificação rápida de saúde:"
echo "   curl -s $API_URL/health | jq '.status'"
echo ""
echo "2. 📊 Análise de métrica específica:"
echo "   curl -X POST $API_URL/analyze/cardinality \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -d '{\"labels\":[{\"name\":\"SEU_LABEL\",\"values\":[...],\"metric_name\":\"SUA_METRICA\"}]}' \\"
echo "     | jq '.summary'"
echo ""
echo "3. ⚠️ Só alertas críticos:"
echo "   curl -s ... | jq '.high_risk_labels[] | select(.risk_level == \"critical\")'"
echo ""
echo "4. 📈 Ordenar por cardinalidade:"
echo "   curl -s ... | jq '.high_risk_labels | sort_by(.current_cardinality) | reverse'"
echo ""
echo "5. 🎯 Extrair recomendações:"
echo "   curl -s ... | jq '.high_risk_labels[].recommendations[]'"

echo -e "\n✅ Exemplos concluídos!"
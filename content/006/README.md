## Building a custom Opentelemetry Collector with custom processor

### Prerequisites

- curl
- ocb
- docker
- docker compose

### Reproducing

```
curl --proto '=https' --tlsv1.2 -fL -o ocb \
https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/cmd%2Fbuilder%2Fv0.128.0/ocb_0.128.0_linux_amd64
chmod +x ocb
```

```
ocb --config builder-config.yaml
```

```
./dist/sherlock-collector --config ./dist/config.yaml
```

### Results


### References

```
🔗 https://opentelemetry.io/docs/collector/custom-collector/
🔗 https://github.com/open-telemetry/opentelemetry-collector-releases/releases/
🔗 https://github.com/open-telemetry/opentelemetry-collector/tree/main/cmd/builder
🔗 https://opentelemetry.io/docs/collector/installation/
```


go clean -modcache
go mod tidy


```
# teste-schema-1.sh
curl -X POST http://localhost:4318/v1/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "resourceMetrics": [{
      "scopeMetrics": [{
        "metrics": [{
          "name": "job_status",
          "gauge": {
            "dataPoints": [{
              "attributes": [
                {"key": "dc", "value": {"stringValue": "dc1"}},
                {"key": "offline", "value": {"stringValue": "no"}}
              ],
              "asInt": "1"
            }]
          }
        }]
      }]
    }]
  }'

# teste-schema-2.sh (com label extra)
curl -X POST http://localhost:4318/v1/metrics \
  -H "Content-Type: application/json" \
  -d '{
    "resourceMetrics": [{
      "scopeMetrics": [{
        "metrics": [{
          "name": "job_status",
          "gauge": {
            "dataPoints": [{
              "attributes": [
                {"key": "dc", "value": {"stringValue": "dc1"}},
                {"key": "offline", "value": {"stringValue": "no"}},
                {"key": "ko", "value": {"stringValue": "no"}}
              ],
              "asInt": "1"
            }]
          }
        }]
      }]
    }]
  }'
```
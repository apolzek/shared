## Building a sherlock collector 


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

```
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  --data @span.json
```

Ref

https://github.com/open-telemetry/opentelemetry-collector-releases/releases/
https://github.com/open-telemetry/opentelemetry-collector/tree/main/cmd/builder
https://opentelemetry.io/docs/collector/installation/


```
curl -X POST http://localhost:4318/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "resourceLogs": [
      {
        "resource": {
          "attributes": [
            { "key": "service.name", "value": { "stringValue": "my-service" } }
          ]
        },
        "scopeLogs": [
          {
            "scope": {
              "name": "example-logger"
            },
            "logRecords": [
              {
                "timeUnixNano": "1718127000000000000",
                "severityNumber": 9,
                "severityText": "INFO",
                "body": {
                  "stringValue": "Hello from curl log"
                },
                "attributes": [
                  { "key": "env", "value": { "stringValue": "dev" } }
                ]
              }
            ]
          }
        ]
      }
    ]
  }'
```


```
curl -X POST http://localhost:8080/log \
  -H "Content-Type: application/json" \
  -d '{"service":"user-api","log":"generic log"}'
```


```
curl -X POST http://localhost:4318/v1/logs \
  -H "Content-Type: application/json" \
  -d '{
    "resourceLogs": [
      {
        "resource": {},
        "scopeLogs": [
          {
            "logRecords": [
              {
                "timeUnixNano": 1672531200000000000,
                "body": {
                  "stringValue": "Log de teste via curl"
                },
                "severityText": "INFO"
              }
            ]
          }
        ]
      }
    ]
  }'

```


```
nc -nltp 8000
```


https://opentelemetry.io/docs/collector/custom-collector/

```
> logs.json && > logs-cold.json && > logs-hot.json
```

```
jq -s '[.[] | .resourceLogs[]?.scopeLogs[]?.logRecords[]?] | length' logs-hot.json
jq -s '[.[] | .resourceLogs[]?.scopeLogs[]?.logRecords[]?] | length' logs-cold.json
jq -s '[.[] | .resourceLogs[]?.scopeLogs[]?.logRecords[]?] | length' logs.json
```

```
docker compose up
```
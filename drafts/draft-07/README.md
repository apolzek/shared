```
ocb --config builder-config.yaml
```

```
cd dist
./otelcol-custom --config config.yaml
```

```
curl -X POST http://localhost:4318/v1/traces \
  -H "Content-Type: application/json" \
  --data @span.json
```
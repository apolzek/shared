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
https://github.com/open-telemetry/opentelemetry-collector-releases/releases/
https://github.com/open-telemetry/opentelemetry-collector/tree/main/cmd/builder
https://opentelemetry.io/docs/collector/installation/
https://opentelemetry.io/docs/collector/custom-collector/
```

https://github.com/open-telemetry/opentelemetry-collector-releases/releases/download/v0.133.0/otelcol-contrib_0.133.0_linux_amd64.tar.gz

```
for ((;;)); do otel-cli span --endpoint http://localhost:4317 --service "app1-frontend" --name "GET /api/users/{id}" --attrs "http.method=GET,http.route=/api/users/{id},http.scheme=https,http.status_code=504,net.host.name=frontend-server,service.version=2.1.0,team=platform,datacenter=sp1,site=saopaulo,vertical=payments,context_business=ecommerce,product=checkout,upstream.service=app-backend" --protocol grpc --verbose; sleep 1; done
```
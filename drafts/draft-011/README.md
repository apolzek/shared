helm repo add fluent https://fluent.github.io/helm-charts
helm install fluent-bit fluent/fluent-bit

helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm install my-opentelemetry-collector open-telemetry/opentelemetry-collector \
   --set image.repository="otel/opentelemetry-collector-k8s" \
   --set mode=deployment \


sudo sysctl fs.inotify.max_user_watches=524288
sudo sysctl -p

helm install my-release oci://registry-1.docker.io/bitnamicharts/clickhouse
https://github.com/bitnami/charts/blob/main/bitnami/clickhouse/values.yaml


helm repo add grafana https://grafana.github.io/helm-charts
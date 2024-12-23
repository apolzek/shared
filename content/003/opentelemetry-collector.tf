# resource "kubernetes_secret" "opentelemetry-collector" {
#   metadata {
#     name      = "grafana"
#     namespace = var.namespace
#   }

#   data = {
#     admin-user     = "admin"
#     admin-password = random_password.grafana.result
#   }
# }

# resource "random_password" "grafana" {
#   length = 24
# }

# resource "helm_release" "opentelemetry-collector" {
#   chart      = "opentelemetry-collector"
#   name       = "opentelemetry-collector"
#   repository = "https://open-telemetry.github.io/opentelemetry-helm-charts"
#   namespace  = var.namespace
#   version    = "0.67.0"

#   values = [
#     templatefile("${path.module}/templates/otel-values.yaml", {
#       # admin_existing_secret = kubernetes_secret.grafana.metadata[0].name
#       # admin_user_key        = "admin-user"
#       # admin_password_key    = "admin-password"
#       # prometheus_svc        = "${helm_release.prometheus.name}-server"
#       # replicas              = 1
#     })
#   ]
# }
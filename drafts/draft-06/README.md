https://github.com/grafana/faro-web-sdk

The Grafana Faro Web SDK, part of the Grafana Faro project, is a highly configurable web SDK for real user monitoring (RUM) that instruments browser frontend applications to capture observability signals. Frontend telemetry can then be correlated with backend and infrastructure data for full-stack observability.

The Grafana Faro Web SDK can instrument frontend JavaScript applications to collect telemetry and forward it to the Grafana Alloy (with faro receiver integration enabled), to a Grafana Cloud instance or to a custom receiver. Grafana Alloy can then send this data to Loki or Tempo.

https://grafana.com/docs/grafana-cloud/monitor-applications/frontend-observability/
https://grafana.com/docs/grafana-cloud/monitor-applications/frontend-observability/instrument/
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/quick-start-browser.md
https://github.com/grafana/faro-web-sdk/tree/main/demo
https://github.com/grafana/faro-web-sdk/tree/main/packages/core
https://github.com/grafana/faro-web-sdk/tree/main/packages/web-tracing
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/quick-start-browser.md


http://localhost:12348/metrics
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/quick-start-browser.md
https://github.com/blueswen/observability-ironman30-lab/blob/6fbf4e32f915f2e83ea4141a7defa6334991cd81/app/todo-app/jquery-app/index.html#L34
https://github.com/grafana/faro-web-sdk/blob/c5562b387f8ad8b7318f3aaedfa7c3e2ce005fca/docs/sources/tutorials/use-cdn-library.md?plain=1#L27
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/use-cdn-library.md
https://grafana.com/grafana/dashboards/17766-frontend-monitoring/

https://medium.com/bazaar-tech/frontend-observability-explained-d12a37fc6595
https://medium.com/@achanandhi.m/getting-started-with-grafana-faro-frontend-monitoring-7c943b13478c
https://blog.prateekjain.dev/beginners-guide-to-the-grafana-open-source-ecosystem-433926713dfe

node --require ./instrumentation.js app.js



Web Vitals: Useful for setting and tracking performance thresholds (Learn more).
Unhandled Application Exceptions: Helps speed up problem resolution and improves code quality.
Visitor Browser Information: Valuable for audience analysis.
URL Changes: Tracks user journeys across pages.
Tracing: Enables end-to-end observability when backend tracing is also configured.
Session Identification: Makes it easy to correlate events across telemetry types.
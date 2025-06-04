# PoC Grafana Alloy

## Resumo

Esta PoC demonstra uma aplicação web composta por um frontend desenvolvido com HTML, CSS e JavaScript puro, instrumentado com Grafana Faro e Grafana Web Trace, e um backend em Node.js puro, instrumentado com OpenTelemetry. Os dados de observabilidade do frontend são enviados para o Grafana Alloy, enquanto os dados do backend são encaminhados diretamente para o Grafana Tempo. Logs, métricas e traces são visualizados por meio do Grafana. O principal objetivo desta PoC é explorar os tipos de dados que podem ser extraídos utilizando o Grafana Faro e entender como obter informações relevantes sobre o comportamento e desempenho do frontend.

## Arquitetura e componentes

A arquitetura utiliza o **Grafana** como a interface central de visualização de logs, métricas e traces. Ele é configurado para se conectar aos demais componentes da stack de observabilidade e exibir os dados de forma unificada em dashboards. Através do Grafana, é possível analisar em tempo real os sinais emitidos pelo frontend e backend da aplicação.

O **Grafana Alloy** atua como o agente de coleta de dados do frontend. Ele recebe os sinais enviados pelo Grafana Faro Web SDK, como erros de JavaScript, tempos de carregamento de páginas e interações do usuário. Alloy aceita esses dados através do receptor Faro e os encaminha para outras ferramentas da stack, como o Loki e o Tempo, permitindo a centralização da observabilidade do frontend.

O **Loki** é o componente responsável pelo gerenciamento de logs. Ele armazena e indexa os logs que podem ser enviados por diferentes fontes, incluindo o Grafana Alloy ou outros serviços conectados. Na arquitetura, Loki é usado para armazenar logs tanto do frontend quanto do backend, permitindo buscas rápidas e correlação com outros sinais como traces e métricas.

O **Tempo** é o componente de tracing distribuído, que armazena e consulta traces gerados pelas aplicações instrumentadas. Neste caso, ele recebe dados diretamente do backend Node.js, via o protocolo OTLP (gRPC/HTTP), graças à instrumentação feita com OpenTelemetry. Isso permite acompanhar chamadas de ponta a ponta, identificar gargalos e entender o fluxo de execução da aplicação backend.

O **Prometheus** é utilizado para a coleta e armazenamento de métricas. Embora sua presença nesta stack seja opcional, ele é importante para monitorar o próprio ambiente de observabilidade, como consumo de recursos, estado dos containers e outras métricas de infraestrutura. Ele também pode ser integrado com Grafana para exibição de métricas em tempo real.

## Referências

```
https://github.com/grafana/faro-web-sdk
https://grafana.com/docs/grafana-cloud/monitor-applications/frontend-observability/instrument/
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/quick-start-browser.md
https://github.com/grafana/faro-web-sdk/tree/main/demo
https://github.com/grafana/faro-web-sdk/tree/main/packages/core
https://github.com/grafana/faro-web-sdk/tree/main/packages/web-tracing
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/quick-start-browser.md
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/quick-start-browser.md
https://github.com/blueswen/observability-ironman30-lab/blob/6fbf4e32f915f2e83ea4141a7defa6334991cd81/app/todo-app/jquery-app/index.html#L34
https://github.com/grafana/faro-web-sdk/blob/c5562b387f8ad8b7318f3aaedfa7c3e2ce005fca/docs/sources/tutorials/use-cdn-library.md?plain=1#L27
https://github.com/grafana/faro-web-sdk/blob/main/docs/sources/tutorials/use-cdn-library.md
https://grafana.com/grafana/dashboards/17766-frontend-monitoring/
```

## Reproduzir localmente

```
docker compose up
node --require ./instrumentation.js app.js
http://localhost:12348/metrics
```
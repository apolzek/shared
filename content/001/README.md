## Basic examples of load balancer and reverse proxy configuration

Caddy
```sh
make up service=caddy_reverse_proxy
make down service=caddy_reverse_proxy
make up service=caddy_load_balancer
make down service=caddy_load_balancer
```

Envoy
```sh
make up service=envoy_load_balancer
make down service=envoy_load_balancer
make up service=envoy_reverse_proxy
make down service=envoy_reverse_proxy
```

HAProxy
```sh
make up service=haproxy_load_balancer
make down service=haproxy_load_balancer
make up service=haproxy_reverse_proxy
make down service=haproxy_reverse_proxy
```

Nginx
```sh
make up service=nginx_reverse_proxy
make down service=nginx_reverse_proxy
make up service=nginx_load_balancer
make down service=nginx_load_balancer
```

Traefik
```sh
make up service=traefik_load_balancer
make down service=traefik_load_balancer
make up service=traefik_reverse_proxy
make down service=traefik_reverse_proxy
```
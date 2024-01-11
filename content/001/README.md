## Basic examples of load balancer and reverse proxy configuration

### Load Balancer and Reverse Proxy

In the dynamic landscape of web infrastructure, Reverse Proxies and Load Balancers play pivotal roles in optimizing performance, ensuring scalability, and enhancing security. These two components often work hand in hand, complementing each other to deliver a seamless and efficient user experience.

A Reverse Proxy, at its core, acts as an intermediary between client devices and web servers. Unlike a traditional forward proxy, which handles requests on behalf of clients, a reverse proxy manages server-side requests. It accepts incoming client requests and forwards them to the appropriate server, concealing the identity and characteristics of the backend servers from external users. This not only provides an additional layer of security but also enables the implementation of various optimizations, such as SSL termination and content caching, to improve overall website performance.

On the other hand, a Load Balancer is designed to distribute incoming network traffic across multiple servers, ensuring no single server is overwhelmed with requests. This distribution of traffic helps prevent server overloads, reduces response times, and enhances the reliability and availability of web applications. Load Balancers can operate at different layers of the OSI model, directing traffic based on factors like server health, response times, or using predefined algorithms.

![image](./.image/drawing.png)

The relationship between a Reverse Proxy and a Load Balancer is symbiotic. In many scenarios, a Reverse Proxy is deployed in conjunction with a Load Balancer to create a robust and scalable infrastructure. The Reverse Proxy handles tasks such as SSL termination, caching, and compression, offloading these responsibilities from the backend servers. Meanwhile, the Load Balancer distributes incoming requests among multiple servers, optimizing resource utilization and ensuring high availability.

#### Caddy

load balancer
```sh
make up service=caddy_load_balancer
curl http://localhost/ # test
make down service=caddy_load_balancer
```

reverse proxy
```sh
make up service=caddy_reverse_proxy
curl http://localhost/front-a # test
curl http://localhost/front-b # test
make down service=caddy_reverse_proxy
```

#### Envoy

load balancer
```sh
make up service=envoy_load_balancer
curl http://localhost:8888/ # test
make down service=envoy_load_balancer
```

reverse proxy
```sh
make up service=envoy_reverse_proxy
curl http://localhost:8888/front-a # test
curl http://localhost:8888/front-b # test
make down service=envoy_reverse_proxy
```

#### HAProxy

load balancer
```sh
make up service=haproxy_load_balancer
curl http://localhost/ # test
make down service=haproxy_load_balancer
```

reverse proxy
```sh
make up service=haproxy_reverse_proxy
curl http://localhost/front-a # test
curl http://localhost/front-b # test
make down service=haproxy_reverse_proxy
```

#### Nginx

load balancer
```sh
make up service=nginx_load_balancer
curl http://localhost/ # test
make down service=nginx_load_balancer
```

reverse proxy
```sh
make up service=nginx_reverse_proxy
curl http://localhost/front-a # test
curl http://localhost/front-b # test
make down service=nginx_reverse_proxy
```

#### Traefik

load balancer
```sh
make up service=traefik_load_balancer
curl http://localhost/ # test
make down service=traefik_load_balancer
```

reverse proxy
```sh
make up service=traefik_reverse_proxy
curl http://localhost/front-a # test
curl http://localhost/front-b # test
make down service=traefik_reverse_proxy
```

`FYU: Preferably test using curl instead browser`
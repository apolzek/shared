## Monitoring PostgreSQL in container

**PoC:** Create an observability ecosystem for PostgreSQL using containers. Ensure visibility into the database and perform load testing.

### compose

| Service             | Endpoint                                           |
| ------------------- | -------------------------------------------------- |
| PostgreSQL          | postgresql://localhost:5432/app_db?sslmode=disable |
| pgAdmin             | [http://localhost:8080](http://localhost:8080)     |
| PostgreSQL Exporter | N/A                                                |
| Prometheus          | [http://localhost:9090](http://localhost:9090)     |
| Grafana             | [http://localhost:3000](http://localhost:3000)     |
| Loki                | N/A                                                |
| Promtail            | N/A                                                |

### Reproduce locally

Start
```
make start
```

Stop
```
make destroy
```

Login database using psql
```sh
psql -h localhost -U rinha -d app_db
```

Check extensions
```sql
SELECT * FROM pg_available_extensions;
SELECT * FROM pg_available_extensions WHERE name = 'postgis';
```

```bash
docker exec -it postgres_db psql -U rinha -d app_db -c "CREATE EXTENSION postgis;"
docker exec -it postgres_db psql -U rinha -d app_db -c "CREATE EXTENSION pg_stat_statements;"
docker exec -it postgres_db psql -U rinha -d app_db -c "SELECT * FROM pg_extension;"
```

### References

https://medium.com/@shaileshkumarmishra/find-slow-queries-in-postgresql-42dddafc8a0e
https://mxulises.medium.com/simple-prometheus-setup-on-docker-compose-f702d5f98579
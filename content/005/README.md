
```sh
psql -h localhost -U rinha -d app_db
```

```sql
SELECT * FROM pg_available_extensions WHERE name = 'postgis';
SELECT * FROM pg_available_extensions;
```

```bash
docker exec -it postgres_db psql -U rinha -d app_db -c "CREATE EXTENSION postgis;"
docker exec -it postgres_db psql -U rinha -d app_db -c "CREATE EXTENSION pg_stat_statements;"
docker exec -it postgres_db psql -U rinha -d app_db -c "SELECT * FROM pg_extension;"
```

```sh
$ docker run -it --rm -v .... postgres:10.5 \
-c shared_preload_libraries='pg_stat_statements' \
-c pg_stat_statements.max=10000 \
-c pg_stat_statements.track=all
```

```sql
SELECT
  (total_exec_time / 1000 / 60) as total_min,
  mean_exec_time as avg_ms,
  calls,
  query
FROM pg_stat_statements
ORDER BY 1 DESC
LIMIT 500;
```
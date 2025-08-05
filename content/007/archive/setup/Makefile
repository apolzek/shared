# Load environment variables
ENV_FILE = .env
FLINK_TM_SCALE = 1
SPARK_WORKER_SCALE = 1

# Build images first
build:
	docker compose build

# Start environment (no force recreate)
up: build
	docker compose --env-file $(ENV_FILE) up -d \
		--scale flink-taskmanager=$(FLINK_TM_SCALE) \
		--scale spark-worker=$(SPARK_WORKER_SCALE) \
		--remove-orphans

# Start environment and force recreate containers
up-force: build
	docker compose --env-file $(ENV_FILE) up -d --force-recreate \
		--scale flink-taskmanager=$(FLINK_TM_SCALE) \
		--scale spark-worker=$(SPARK_WORKER_SCALE) \
		--remove-orphans

# Stop and remove all services, volumes, orphans
down:
	docker compose down -v --remove-orphans

# Clean all Docker resources (containers, volumes, images, networks, cache)
clean:
	docker compose down -v --remove-orphans
	docker image prune -a -f
	docker volume prune -f
	docker network prune -f
	docker builder prune -a -f

# Full reset: clean everything then start fresh
reset: clean up

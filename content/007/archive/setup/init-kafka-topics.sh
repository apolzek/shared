#!/bin/bash

set -e

echo "Waiting for kafka_1:29092 to be reachable..."
while ! nc -z kafka 29092; do
  sleep 2
done

echo "Kafka is up. Creating topics..."

kafka-topics --create --if-not-exists \
  --bootstrap-server kafka:29092 \
  --replication-factor 3 \
  --partitions 6 \
  --topic raw-logs

echo "Topic creation complete."

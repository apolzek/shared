#!/bin/sh

while ! nc -z kafka 29092; do
  echo "Kafka (host 1) is not available yet. Retrying in 5 seconds..."
  sleep 5
done

echo "Kafka is up! Starting Fluent Bit..."

# Start Fluent Bit with the provided configuration
exec /fluent-bit/bin/fluent-bit -c /fluent-bit/etc/fluent-bit.conf

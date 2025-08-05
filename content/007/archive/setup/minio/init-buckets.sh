#!/bin/sh

sleep 5

mc alias set myminio http://localhost:9000 minioadmin minioadmin

for bucket in mylogs flink-data; do
  mc ls myminio/$bucket || mc mb myminio/$bucket
  mc anonymous set download myminio/$bucket
  sleep 1
done
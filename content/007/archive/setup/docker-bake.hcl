group "default" {
  targets = [
    "flink-jobmanager",
    "flink-taskmanager"
  ]
}

target "flink-jobmanager" {
  context = "."
  dockerfile = "flink-s3a.Dockerfile"
  tags = ["flink-jobmanager:latest"]
  platforms = ["linux/amd64"]
}

target "flink-taskmanager" {
  context = "."
  dockerfile = "flink-s3a.Dockerfile"
  tags = ["flink-taskmanager:latest"]
  platforms = ["linux/amd64"]
}
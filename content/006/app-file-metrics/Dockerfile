FROM golang:1.24.4-alpine AS builder
WORKDIR /app
COPY . .
RUN go mod tidy && \
    go build -o app-file-metrics
FROM alpine:latest
RUN apk --no-cache add ca-certificates
WORKDIR /app
COPY --from=builder /app/app-file-metrics .
COPY metrics.txt .
EXPOSE 2112
ENTRYPOINT ["./app-file-metrics"]

docker run -p 8013:8013 -v $(pwd)/:/etc/flagd/ -it ghcr.io/open-feature/flagd:latest start --uri file:/etc/flagd/flags.flagd.json

http://localhost:8080/hello

https://openfeature.dev/docs/tutorials/getting-started/go
https://openfeature.dev/docs/reference/technologies/server/go/
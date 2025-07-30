package main

import (
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"sync"

	"github.com/fsnotify/fsnotify"
)

var (
	metricsContent string
	mutex          sync.RWMutex
	metricsFile    = "metrics.txt"
)

func loadMetrics() {
	data, err := ioutil.ReadFile(metricsFile)
	if err != nil {
		log.Printf("Erro lendo o arquivo de métricas: %v", err)
		return
	}

	mutex.Lock()
	metricsContent = string(data)
	mutex.Unlock()
	log.Println("Métricas recarregadas.")
}

func metricsHandler(w http.ResponseWriter, r *http.Request) {
	mutex.RLock()
	defer mutex.RUnlock()
	w.Header().Set("Content-Type", "text/plain; version=0.0.4")
	fmt.Fprint(w, metricsContent)
}

func watchFileChanges() {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Fatal("Erro criando watcher:", err)
	}
	defer watcher.Close()

	err = watcher.Add(metricsFile)
	if err != nil {
		log.Fatal("Erro adicionando arquivo ao watcher:", err)
	}

	for {
		select {
		case event := <-watcher.Events:
			if event.Op&(fsnotify.Write|fsnotify.Create) != 0 {
				log.Println("Mudança detectada no arquivo. Recarregando...")
				loadMetrics()
			}
		case err := <-watcher.Errors:
			log.Println("Erro no watcher:", err)
		}
	}
}

func main() {
	// Verifica se o arquivo existe
	if _, err := os.Stat(metricsFile); os.IsNotExist(err) {
		log.Fatalf("Arquivo %s não encontrado", metricsFile)
	}

	// Carrega inicialmente
	loadMetrics()

	// Inicia o watcher em background
	go watchFileChanges()

	http.HandleFunc("/metrics", metricsHandler)
	log.Println("Servidor escutando em :2112/metrics")
	log.Fatal(http.ListenAndServe(":2112", nil))
}

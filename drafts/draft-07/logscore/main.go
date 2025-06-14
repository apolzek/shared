package main

import (
	"bufio"
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"strconv"

	"github.com/gin-gonic/gin"
	_ "github.com/mattn/go-sqlite3"
)

type OllamaRequest struct {
	Model  string `json:"model"`
	Prompt string `json:"prompt"`
}

type ollamaChunk struct {
	Response string `json:"response"`
	Done     bool   `json:"done"`
}

func ScoreLog(logText string, service string) (int, string, error) {
	prompt := fmt.Sprintf(`Dado o log abaixo e o serviço, responda apenas com o seguinte formato, sem explicações adicionais:

Nota: <número inteiro entre 0 e 5>
Recomendação: <uma frase curta explicando a relevância do log para observabilidade ou problema identificado>

Exemplo:
Nota: 3
Recomendação: Falha na conexão com o banco de dados pode indicar problema no serviço de autenticação.

Agora avalie o log:

Log: "%s"
Serviço: "%s"
Resposta:
`, logText, service)

	reqBody := OllamaRequest{
		Model:  "deepseek-coder",
		Prompt: prompt,
	}

	jsonData, _ := json.Marshal(reqBody)

	resp, err := http.Post("http://localhost:11434/api/generate", "application/json", bytes.NewBuffer(jsonData))
	if err != nil {
		return 0, "", err
	}
	defer resp.Body.Close()

	var fullResponse string
	scanner := bufio.NewScanner(resp.Body)
	for scanner.Scan() {
		line := scanner.Bytes()
		var chunk ollamaChunk
		err := json.Unmarshal(line, &chunk)
		if err != nil {
			continue
		}
		fullResponse += chunk.Response
		if chunk.Done {
			break
		}
	}

	fmt.Println("Resposta do modelo:", fullResponse)

	// Extrair a nota e recomendação do texto
	reNota := regexp.MustCompile(`Nota:\s*(\d)`)
	reRec := regexp.MustCompile(`Recomendação:\s*(.*)`)

	notaMatch := reNota.FindStringSubmatch(fullResponse)
	recMatch := reRec.FindStringSubmatch(fullResponse)

	if len(notaMatch) < 2 || len(recMatch) < 2 {
		return 0, fullResponse, fmt.Errorf("resposta inválida do modelo: %s", fullResponse)
	}

	score, err := strconv.Atoi(notaMatch[1])
	if err != nil {
		return 0, fullResponse, err
	}

	recomendacao := recMatch[1]

	return score, recomendacao, nil
}

func SaveLog(db *sql.DB, service string, recommendation string, score int) error {
	stmt, err := db.Prepare("INSERT INTO logs(service, recommendation, score) VALUES (?, ?, ?)")
	if err != nil {
		return err
	}
	defer stmt.Close()

	_, err = stmt.Exec(service, recommendation, score)
	return err
}

func main() {
	db, err := sql.Open("sqlite3", "./logs.db")
	if err != nil {
		panic(err)
	}
	defer db.Close()

	_, err = db.Exec(`CREATE TABLE IF NOT EXISTS logs (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		service TEXT,
		recommendation TEXT,
		score INTEGER
	)`)
	if err != nil {
		panic(err)
	}

	r := gin.Default()

	r.POST("/log", func(c *gin.Context) {
		var req struct {
			Service string `json:"service"`
			Log     string `json:"log"`
		}
		if err := c.BindJSON(&req); err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "JSON inválido"})
			return
		}

		score, recommendation, err := ScoreLog(req.Log, req.Service)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
			return
		}

		err = SaveLog(db, req.Service, recommendation, score)
		if err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "falha ao salvar no banco"})
			return
		}

		c.JSON(http.StatusOK, gin.H{
			"service":        req.Service,
			"recommendation": recommendation,
			"score":          score,
		})
	})

	r.Run(":8080")
}

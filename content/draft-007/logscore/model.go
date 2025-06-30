package main

import (
	"gorm.io/gorm"
)

type LogEntry struct {
	gorm.Model
	Service string
	LogText string
	Score   int
}

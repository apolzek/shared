// schemadetectorprocessor.go
package schemadetectorprocessor

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"sort"
	"strings"
	"time"

	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/pcommon"
	"go.opentelemetry.io/collector/pdata/pmetric"
	"go.opentelemetry.io/collector/processor"
	"go.opentelemetry.io/collector/processor/processorhelper"
	"go.uber.org/zap"
)

const (
	// typeStr is the type of the processor
	typeStr = "schemadetector"
	// stability level
	stability = component.StabilityLevelDevelopment
)

// Config defines the configuration for the schema detector processor
type Config struct {
	// LogFilePath is the path where schema changes will be logged
	LogFilePath string `mapstructure:"log_file_path"`

	// MetricFilters allows filtering which metrics to monitor
	// If empty, all metrics will be monitored
	MetricFilters []string `mapstructure:"metric_filters"`

	// ToleranceWindowSeconds defines the time window in seconds to wait before
	// considering a schema change as legitimate (to handle late/out-of-order data)
	ToleranceWindowSeconds int `mapstructure:"tolerance_window_seconds"`
}

// Validate checks if the processor configuration is valid
func (cfg *Config) Validate() error {
	if cfg.LogFilePath == "" {
		cfg.LogFilePath = "schema_changes.json"
	}
	if cfg.ToleranceWindowSeconds <= 0 {
		cfg.ToleranceWindowSeconds = 30 // 30 segundos por padrão
	}
	return nil
}

// MetricSchema represents the schema of a metric
type MetricSchema struct {
	MetricName string    `json:"metric_name"`
	LabelKeys  []string  `json:"label_keys"`
	LastSeen   time.Time `json:"last_seen"`
	FirstSeen  time.Time `json:"first_seen"`
	MetricType string    `json:"metric_type"`
}

// SchemaChange represents a simplified change in metric schema
type SchemaChange struct {
	MetricName   string    `json:"metric_name"`
	Timestamp    time.Time `json:"timestamp"`
	OldLabelKeys []string  `json:"old_label_keys"`
	NewLabelKeys []string  `json:"new_label_keys"`
	MetricType   string    `json:"metric_type"`
}

// schemaDetectorProcessor processes metrics and detects schema changes
type schemaDetectorProcessor struct {
	config *Config
	logger *zap.Logger
	// SIMPLIFICAÇÃO: Manter apenas o schema mais recente por métrica
	// Removendo metricSchemas que causava confusão
	latestSchemas map[string]MetricSchema
}

// processMetrics processes the metrics and detects schema changes
func (p *schemaDetectorProcessor) processMetrics(ctx context.Context, md pmetric.Metrics) (pmetric.Metrics, error) {
	// Limpa schemas antigos periodicamente para evitar vazamento de memória
	p.cleanOldSchemas()

	resourceMetrics := md.ResourceMetrics()

	for i := 0; i < resourceMetrics.Len(); i++ {
		rm := resourceMetrics.At(i)
		scopeMetrics := rm.ScopeMetrics()

		for j := 0; j < scopeMetrics.Len(); j++ {
			sm := scopeMetrics.At(j)
			metrics := sm.Metrics()

			for k := 0; k < metrics.Len(); k++ {
				metric := metrics.At(k)
				p.processMetric(metric)
			}
		}
	}

	return md, nil
}

// processMetric processes a single metric and detects schema changes
func (p *schemaDetectorProcessor) processMetric(metric pmetric.Metric) {
	metricName := metric.Name()

	// Check if metric should be filtered
	if !p.shouldProcessMetric(metricName) {
		return
	}

	metricType := p.getMetricTypeString(metric.Type())

	// Extract data points based on metric type
	switch metric.Type() {
	case pmetric.MetricTypeGauge:
		gauge := metric.Gauge()
		for i := 0; i < gauge.DataPoints().Len(); i++ {
			dp := gauge.DataPoints().At(i)
			p.processDataPoint(metricName, metricType, dp.Attributes())
		}
	case pmetric.MetricTypeSum:
		sum := metric.Sum()
		for i := 0; i < sum.DataPoints().Len(); i++ {
			dp := sum.DataPoints().At(i)
			p.processDataPoint(metricName, metricType, dp.Attributes())
		}
	case pmetric.MetricTypeHistogram:
		histogram := metric.Histogram()
		for i := 0; i < histogram.DataPoints().Len(); i++ {
			dp := histogram.DataPoints().At(i)
			p.processDataPoint(metricName, metricType, dp.Attributes())
		}
	case pmetric.MetricTypeSummary:
		summary := metric.Summary()
		for i := 0; i < summary.DataPoints().Len(); i++ {
			dp := summary.DataPoints().At(i)
			p.processDataPoint(metricName, metricType, dp.Attributes())
		}
	default:
		return
	}
}

// processDataPoint processes a single data point's attributes
func (p *schemaDetectorProcessor) processDataPoint(metricName, metricType string, attrs pcommon.Map) {
	labelKeys := p.extractLabelKeys(attrs)
	p.processSchemaForMetric(metricName, metricType, labelKeys)
}

// extractLabelKeys extracts only the label keys from attributes (ignoring values)
func (p *schemaDetectorProcessor) extractLabelKeys(attrs pcommon.Map) []string {
	labelKeys := make([]string, 0, attrs.Len())

	attrs.Range(func(k string, v pcommon.Value) bool {
		labelKeys = append(labelKeys, k)
		return true
	})

	// Sort label keys for consistent comparison
	sort.Strings(labelKeys)

	return labelKeys
}

// processSchemaForMetric processes schema for a metric with given label keys
func (p *schemaDetectorProcessor) processSchemaForMetric(metricName, metricType string, labelKeys []string) {
	now := time.Now()

	currentSchema := MetricSchema{
		MetricName: metricName,
		LabelKeys:  labelKeys,
		LastSeen:   now,
		MetricType: metricType,
	}

	// SIMPLIFICAÇÃO: Trabalhar apenas com latestSchemas
	if existingSchema, exists := p.latestSchemas[metricName]; exists {
		// Verificar se as label keys são diferentes
		if !p.labelKeysEqual(existingSchema.LabelKeys, currentSchema.LabelKeys) {
			// Schema diferente detectado, verificar janela de tolerância
			p.detectSchemaChange(metricName, metricType, existingSchema, currentSchema)
		}
		// Atualizar sempre o último schema visto
		currentSchema.FirstSeen = existingSchema.FirstSeen // Manter primeiro tempo
		p.latestSchemas[metricName] = currentSchema
	} else {
		// Primeira vez vendo esta métrica
		currentSchema.FirstSeen = now
		p.latestSchemas[metricName] = currentSchema

		p.logger.Debug("First time seeing metric",
			zap.String("metric", metricName),
			zap.Strings("label_keys", labelKeys),
		)
	}
}

// SIMPLIFICAÇÃO TOTAL: detectSchemaChange com lógica muito mais simples
func (p *schemaDetectorProcessor) detectSchemaChange(metricName, metricType string, oldSchema, newSchema MetricSchema) {
	now := time.Now()
	toleranceWindow := time.Duration(p.config.ToleranceWindowSeconds) * time.Second

	// Verificar se passou tempo suficiente desde a última mudança
	timeSinceLastSeen := now.Sub(oldSchema.LastSeen)

	if timeSinceLastSeen > toleranceWindow {
		// Tempo suficiente passou, esta é uma mudança legítima
		change := SchemaChange{
			MetricName:   metricName,
			Timestamp:    now,
			OldLabelKeys: oldSchema.LabelKeys,
			NewLabelKeys: newSchema.LabelKeys,
			MetricType:   metricType,
		}

		p.logSchemaChange(change)

		p.logger.Info("Schema change detected",
			zap.String("metric", metricName),
			zap.String("metric_type", metricType),
			zap.Strings("old_labels", oldSchema.LabelKeys),
			zap.Strings("new_labels", newSchema.LabelKeys),
			zap.Duration("time_since_last_seen", timeSinceLastSeen),
			zap.Duration("tolerance_window", toleranceWindow),
		)
	} else {
		// Muito pouco tempo passou, provavelmente dados atrasados
		p.logger.Debug("Schema change detected but within tolerance window, ignoring",
			zap.String("metric", metricName),
			zap.Duration("time_since_last_seen", timeSinceLastSeen),
			zap.Duration("tolerance_window", toleranceWindow),
			zap.Strings("old_labels", oldSchema.LabelKeys),
			zap.Strings("new_labels", newSchema.LabelKeys),
		)
	}
}

// Removidas as funções desnecessárias relacionadas a metricSchemas

// cleanOldSchemas limpa schemas antigos para evitar vazamento de memória
func (p *schemaDetectorProcessor) cleanOldSchemas() {
	now := time.Now()
	toleranceWindow := time.Duration(p.config.ToleranceWindowSeconds) * time.Second
	maxRetentionTime := toleranceWindow * 10 // Mantenha por 10x a janela de tolerância

	var toRemove []string

	// Limpar apenas latestSchemas (não há mais metricSchemas)
	for metricName, schema := range p.latestSchemas {
		if now.Sub(schema.LastSeen) > maxRetentionTime {
			toRemove = append(toRemove, metricName)
		}
	}

	for _, metricName := range toRemove {
		delete(p.latestSchemas, metricName)
		p.logger.Debug("Removed old schema from memory",
			zap.String("metric", metricName),
			zap.Duration("max_retention", maxRetentionTime),
		)
	}

	if len(toRemove) > 0 {
		p.logger.Debug("Schema cleanup completed",
			zap.Int("removed_count", len(toRemove)),
			zap.Int("remaining_count", len(p.latestSchemas)),
		)
	}
}

// logSchemaChange logs the schema change to the JSON file
func (p *schemaDetectorProcessor) logSchemaChange(change SchemaChange) {
	// Read existing log file
	var changes []SchemaChange

	if data, err := os.ReadFile(p.config.LogFilePath); err == nil {
		if err := json.Unmarshal(data, &changes); err != nil {
			p.logger.Warn("Failed to unmarshal existing schema changes",
				zap.Error(err),
				zap.String("file_path", p.config.LogFilePath),
			)
			changes = []SchemaChange{} // Start fresh if file is corrupted
		}
	}

	// Add new change
	changes = append(changes, change)

	// Keep only last 1000 changes to prevent file from growing too large
	if len(changes) > 1000 {
		changes = changes[len(changes)-1000:]
		p.logger.Debug("Trimmed schema changes log to last 1000 entries")
	}

	// Write back to file
	data, err := json.MarshalIndent(changes, "", "  ")
	if err != nil {
		p.logger.Error("Failed to marshal schema changes", zap.Error(err))
		return
	}

	if err := os.WriteFile(p.config.LogFilePath, data, 0644); err != nil {
		p.logger.Error("Failed to write schema changes to file",
			zap.Error(err),
			zap.String("file_path", p.config.LogFilePath))
	} else {
		p.logger.Debug("Schema change logged successfully",
			zap.String("file_path", p.config.LogFilePath),
			zap.String("metric", change.MetricName),
			zap.Int("total_changes_logged", len(changes)),
		)
	}
}

// shouldProcessMetric checks if a metric should be processed based on filters
func (p *schemaDetectorProcessor) shouldProcessMetric(metricName string) bool {
	if len(p.config.MetricFilters) == 0 {
		return true
	}

	for _, filter := range p.config.MetricFilters {
		if strings.Contains(metricName, filter) {
			return true
		}
	}

	return false
}

// labelKeysEqual checks if two label key arrays are equal
func (p *schemaDetectorProcessor) labelKeysEqual(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}

	for i, key := range a {
		if key != b[i] {
			return false
		}
	}

	return true
}

// createSchemaKey creates a unique key for a metric schema
func (p *schemaDetectorProcessor) createSchemaKey(metricName string, labelKeys []string) string {
	return fmt.Sprintf("%s:%s", metricName, strings.Join(labelKeys, ","))
}

// getMetricTypeString returns the string representation of metric type
func (p *schemaDetectorProcessor) getMetricTypeString(metricType pmetric.MetricType) string {
	switch metricType {
	case pmetric.MetricTypeGauge:
		return "gauge"
	case pmetric.MetricTypeSum:
		return "sum"
	case pmetric.MetricTypeHistogram:
		return "histogram"
	case pmetric.MetricTypeSummary:
		return "summary"
	case pmetric.MetricTypeExponentialHistogram:
		return "exponential_histogram"
	default:
		return "unknown"
	}
}

// newMetricsProcessor creates a metrics processor following the pattern
func newMetricsProcessor(
	ctx context.Context,
	set processor.Settings,
	cfg component.Config,
	next consumer.Metrics,
) (processor.Metrics, error) {
	c := cfg.(*Config)
	p := &schemaDetectorProcessor{
		config:        c,
		logger:        set.Logger,
		latestSchemas: make(map[string]MetricSchema), // SIMPLIFICADO: apenas latest
	}

	p.logger.Info("Schema detector processor initialized",
		zap.String("log_file_path", c.LogFilePath),
		zap.Strings("metric_filters", c.MetricFilters),
		zap.Int("tolerance_window_seconds", c.ToleranceWindowSeconds),
	)

	return processorhelper.NewMetrics(
		ctx,
		set,
		cfg,
		next,
		p.processMetrics,
		processorhelper.WithCapabilities(consumer.Capabilities{MutatesData: false}),
	)
}

// createDefaultConfig creates the default configuration for the processor
func createDefaultConfig() component.Config {
	return &Config{
		LogFilePath:            "schema_changes.json",
		MetricFilters:          []string{},
		ToleranceWindowSeconds: 30,
	}
}

// NewFactory creates a new processor factory
func NewFactory() processor.Factory {
	return processor.NewFactory(
		component.MustNewType(typeStr),
		createDefaultConfig,
		processor.WithMetrics(newMetricsProcessor, stability),
	)
}

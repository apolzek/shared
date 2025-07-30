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

	// EnableDiff enables detailed diff output in the log
	EnableDiff bool `mapstructure:"enable_diff"`
}

// Validate checks if the processor configuration is valid
func (cfg *Config) Validate() error {
	if cfg.LogFilePath == "" {
		cfg.LogFilePath = "schema_metrics.json"
	}
	return nil
}

// MetricSchema represents the schema of a metric
type MetricSchema struct {
	MetricName string            `json:"metric_name"`
	Labels     map[string]string `json:"labels"`
	LabelKeys  []string          `json:"label_keys"`
	LastSeen   time.Time         `json:"last_seen"`
	FirstSeen  time.Time         `json:"first_seen"`
	MetricType string            `json:"metric_type,omitempty"`
}

// SchemaChange represents a change in metric schema
type SchemaChange struct {
	MetricName string       `json:"metric_name"`
	Timestamp  time.Time    `json:"timestamp"`
	ChangeType string       `json:"change_type"` // "labels_added", "labels_removed", "labels_changed"
	Before     MetricSchema `json:"before"`
	After      MetricSchema `json:"after"`
	Diff       SchemaDiff   `json:"diff,omitempty"`
}

// SchemaDiff represents the differences between schemas
type SchemaDiff struct {
	AddedLabels   []string `json:"added_labels,omitempty"`
	RemovedLabels []string `json:"removed_labels,omitempty"`
	ChangedLabels []string `json:"changed_labels,omitempty"`
}

// schemaDetectorProcessor processes metrics and detects schema changes
type schemaDetectorProcessor struct {
	config        *Config
	logger        *zap.Logger
	metricSchemas map[string]MetricSchema
}

// processMetrics processes the metrics and detects schema changes
func (p *schemaDetectorProcessor) processMetrics(ctx context.Context, md pmetric.Metrics) (pmetric.Metrics, error) {
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
			p.processNumberDataPoint(metricName, metricType, dp)
		}
	case pmetric.MetricTypeSum:
		sum := metric.Sum()
		for i := 0; i < sum.DataPoints().Len(); i++ {
			dp := sum.DataPoints().At(i)
			p.processNumberDataPoint(metricName, metricType, dp)
		}
	case pmetric.MetricTypeHistogram:
		histogram := metric.Histogram()
		for i := 0; i < histogram.DataPoints().Len(); i++ {
			dp := histogram.DataPoints().At(i)
			p.processHistogramDataPoint(metricName, metricType, dp)
		}
	case pmetric.MetricTypeSummary:
		summary := metric.Summary()
		for i := 0; i < summary.DataPoints().Len(); i++ {
			dp := summary.DataPoints().At(i)
			p.processSummaryDataPoint(metricName, metricType, dp)
		}
	default:
		// For other metric types, we'll skip for now
		return
	}
}

// processNumberDataPoint processes a single number data point
func (p *schemaDetectorProcessor) processNumberDataPoint(metricName, metricType string, dp pmetric.NumberDataPoint) {
	labels, labelKeys := p.extractAttributesFromMap(dp.Attributes())
	p.processSchemaForMetric(metricName, metricType, labels, labelKeys)
}

// processHistogramDataPoint processes a single histogram data point
func (p *schemaDetectorProcessor) processHistogramDataPoint(metricName, metricType string, dp pmetric.HistogramDataPoint) {
	labels, labelKeys := p.extractAttributesFromMap(dp.Attributes())
	p.processSchemaForMetric(metricName, metricType, labels, labelKeys)
}

// processSummaryDataPoint processes a single summary data point
func (p *schemaDetectorProcessor) processSummaryDataPoint(metricName, metricType string, dp pmetric.SummaryDataPoint) {
	labels, labelKeys := p.extractAttributesFromMap(dp.Attributes())
	p.processSchemaForMetric(metricName, metricType, labels, labelKeys)
}

// extractAttributesFromMap extracts attributes from pcommon.Map
func (p *schemaDetectorProcessor) extractAttributesFromMap(attrs pcommon.Map) (map[string]string, []string) {
	labels := make(map[string]string)
	labelKeys := make([]string, 0)

	attrs.Range(func(k string, v pcommon.Value) bool {
		labels[k] = v.AsString()
		labelKeys = append(labelKeys, k)
		return true
	})

	// Sort label keys for consistent comparison
	sort.Strings(labelKeys)

	return labels, labelKeys
}

// processSchemaForMetric processes schema for a metric with given labels
func (p *schemaDetectorProcessor) processSchemaForMetric(metricName, metricType string, labels map[string]string, labelKeys []string) {
	// Create a unique key for this metric schema
	schemaKey := p.createSchemaKey(metricName, labelKeys)

	currentSchema := MetricSchema{
		MetricName: metricName,
		Labels:     labels,
		LabelKeys:  labelKeys,
		LastSeen:   time.Now(),
		MetricType: metricType,
	}

	// Check if we've seen this schema before
	if existingSchema, exists := p.metricSchemas[schemaKey]; exists {
		// Update last seen time
		existingSchema.LastSeen = time.Now()
		p.metricSchemas[schemaKey] = existingSchema
	} else {
		// This is a new schema, check if there's a similar one (schema change)
		currentSchema.FirstSeen = time.Now()
		p.detectSchemaChange(metricName, currentSchema)
		p.metricSchemas[schemaKey] = currentSchema
	}
}

// detectSchemaChange detects if there's a schema change for a metric
func (p *schemaDetectorProcessor) detectSchemaChange(metricName string, newSchema MetricSchema) {
	// Look for existing schemas with the same metric name but different label structure
	for key, existingSchema := range p.metricSchemas {
		if existingSchema.MetricName == metricName && !p.schemasEqual(existingSchema, newSchema) {
			// Schema change detected
			change := p.createSchemaChange(existingSchema, newSchema)
			p.logSchemaChange(change)

			p.logger.Info("Schema change detected",
				zap.String("metric", metricName),
				zap.String("change_type", change.ChangeType),
				zap.Strings("added_labels", change.Diff.AddedLabels),
				zap.Strings("removed_labels", change.Diff.RemovedLabels),
				zap.Strings("changed_labels", change.Diff.ChangedLabels),
			)

			// Remove the old schema
			delete(p.metricSchemas, key)
			break
		}
	}
}

// createSchemaChange creates a SchemaChange object
func (p *schemaDetectorProcessor) createSchemaChange(before, after MetricSchema) SchemaChange {
	diff := p.calculateDiff(before, after)

	changeType := "labels_changed"
	if len(diff.AddedLabels) > 0 && len(diff.RemovedLabels) == 0 && len(diff.ChangedLabels) == 0 {
		changeType = "labels_added"
	} else if len(diff.RemovedLabels) > 0 && len(diff.AddedLabels) == 0 && len(diff.ChangedLabels) == 0 {
		changeType = "labels_removed"
	}

	return SchemaChange{
		MetricName: before.MetricName,
		Timestamp:  time.Now(),
		ChangeType: changeType,
		Before:     before,
		After:      after,
		Diff:       diff,
	}
}

// calculateDiff calculates the difference between two schemas
func (p *schemaDetectorProcessor) calculateDiff(before, after MetricSchema) SchemaDiff {
	beforeKeys := make(map[string]bool)
	afterKeys := make(map[string]bool)

	for _, key := range before.LabelKeys {
		beforeKeys[key] = true
	}

	for _, key := range after.LabelKeys {
		afterKeys[key] = true
	}

	var addedLabels, removedLabels, changedLabels []string

	// Find added labels
	for key := range afterKeys {
		if !beforeKeys[key] {
			addedLabels = append(addedLabels, key)
		}
	}

	// Find removed labels
	for key := range beforeKeys {
		if !afterKeys[key] {
			removedLabels = append(removedLabels, key)
		}
	}

	// Find changed labels (same key, different value)
	for key := range beforeKeys {
		if afterKeys[key] {
			if before.Labels[key] != after.Labels[key] {
				changedLabels = append(changedLabels, key)
			}
		}
	}

	sort.Strings(addedLabels)
	sort.Strings(removedLabels)
	sort.Strings(changedLabels)

	return SchemaDiff{
		AddedLabels:   addedLabels,
		RemovedLabels: removedLabels,
		ChangedLabels: changedLabels,
	}
}

// logSchemaChange logs the schema change to the JSON file
func (p *schemaDetectorProcessor) logSchemaChange(change SchemaChange) {
	// Read existing log file
	var changes []SchemaChange

	if data, err := os.ReadFile(p.config.LogFilePath); err == nil {
		if err := json.Unmarshal(data, &changes); err != nil {
			p.logger.Warn("Failed to unmarshal existing schema changes", zap.Error(err))
			changes = []SchemaChange{} // Start fresh if file is corrupted
		}
	}

	// Add new change
	changes = append(changes, change)

	// Keep only last 1000 changes to prevent file from growing too large
	if len(changes) > 1000 {
		changes = changes[len(changes)-1000:]
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
			zap.String("change_type", change.ChangeType))
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

// schemasEqual checks if two schemas are equal
func (p *schemaDetectorProcessor) schemasEqual(a, b MetricSchema) bool {
	if len(a.LabelKeys) != len(b.LabelKeys) {
		return false
	}

	for i, key := range a.LabelKeys {
		if key != b.LabelKeys[i] {
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
		metricSchemas: make(map[string]MetricSchema),
	}
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
		LogFilePath:   "schema_metrics.json",
		MetricFilters: []string{},
		EnableDiff:    true,
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

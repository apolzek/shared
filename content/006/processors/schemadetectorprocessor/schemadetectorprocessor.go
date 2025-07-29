package schemadetectorprocessor

import (
	"context"
	"fmt"
	"sort"
	"strings"
	"sync"
	"time"

	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/pcommon"
	"go.opentelemetry.io/collector/pdata/pmetric"
	"go.opentelemetry.io/collector/processor"
	"go.opentelemetry.io/collector/processor/processorhelper"
	"go.uber.org/zap"
)

type Config struct {
	IncludeMetrics []string      `mapstructure:"include_metrics"`
	LogLevel       string        `mapstructure:"log_level"`
	TTL            time.Duration `mapstructure:"ttl"`
}

type metricSchema struct {
	attributes map[string]string
	lastSeen   time.Time
}

type schemaDetectorProcessor struct {
	config *Config
	logger *zap.Logger

	mu      sync.RWMutex
	schemas map[string]*metricSchema // metric_name -> schema
	cancel  context.CancelFunc
}

func (p *schemaDetectorProcessor) processMetrics(ctx context.Context, md pmetric.Metrics) (pmetric.Metrics, error) {
	rm := md.ResourceMetrics()

	for i := 0; i < rm.Len(); i++ {
		resourceMetric := rm.At(i)
		sm := resourceMetric.ScopeMetrics()

		for j := 0; j < sm.Len(); j++ {
			scopeMetric := sm.At(j)
			metrics := scopeMetric.Metrics()

			for k := 0; k < metrics.Len(); k++ {
				metric := metrics.At(k)
				p.detectSchemaChange(metric)
			}
		}
	}

	return md, nil
}

func (p *schemaDetectorProcessor) detectSchemaChange(metric pmetric.Metric) {
	metricName := metric.Name()

	if !p.shouldMonitor(metricName) {
		return
	}

	currentSchema := p.extractSchema(metric)
	if len(currentSchema) == 0 {
		return
	}

	p.mu.Lock()
	defer p.mu.Unlock()

	if existingSchema, exists := p.schemas[metricName]; exists {
		if changes := p.compareSchemas(existingSchema.attributes, currentSchema); len(changes) > 0 {
			p.logSchemaChange(metricName, existingSchema.attributes, currentSchema, changes)
		}
	} else {
		p.logger.Info("New metric schema detected",
			zap.String("metric", metricName),
			zap.Strings("attributes", p.getAttributeKeys(currentSchema)))
	}

	p.schemas[metricName] = &metricSchema{
		attributes: currentSchema,
		lastSeen:   time.Now(),
	}
}

func (p *schemaDetectorProcessor) extractSchema(metric pmetric.Metric) map[string]string {
	schema := make(map[string]string)

	// Extract attributes from the first data point of each metric type
	switch metric.Type() {
	case pmetric.MetricTypeGauge:
		if metric.Gauge().DataPoints().Len() > 0 {
			dp := metric.Gauge().DataPoints().At(0)
			p.extractAttributesFromDataPoint(dp.Attributes(), schema)
		}
	case pmetric.MetricTypeSum:
		if metric.Sum().DataPoints().Len() > 0 {
			dp := metric.Sum().DataPoints().At(0)
			p.extractAttributesFromDataPoint(dp.Attributes(), schema)
		}
	case pmetric.MetricTypeHistogram:
		if metric.Histogram().DataPoints().Len() > 0 {
			dp := metric.Histogram().DataPoints().At(0)
			p.extractAttributesFromDataPoint(dp.Attributes(), schema)
		}
	case pmetric.MetricTypeExponentialHistogram:
		if metric.ExponentialHistogram().DataPoints().Len() > 0 {
			dp := metric.ExponentialHistogram().DataPoints().At(0)
			p.extractAttributesFromDataPoint(dp.Attributes(), schema)
		}
	case pmetric.MetricTypeSummary:
		if metric.Summary().DataPoints().Len() > 0 {
			dp := metric.Summary().DataPoints().At(0)
			p.extractAttributesFromDataPoint(dp.Attributes(), schema)
		}
	}

	return schema
}

func (p *schemaDetectorProcessor) extractAttributesFromDataPoint(attrs pcommon.Map, schema map[string]string) {
	attrs.Range(func(k string, v pcommon.Value) bool {
		schema[k] = v.AsString()
		return true
	})
}

func (p *schemaDetectorProcessor) shouldMonitor(metricName string) bool {
	if len(p.config.IncludeMetrics) == 0 {
		return true // Monitor all metrics if no filter specified
	}

	for _, pattern := range p.config.IncludeMetrics {
		// Simple pattern matching (contains)
		if strings.Contains(metricName, pattern) {
			return true
		}
	}
	return false
}

func (p *schemaDetectorProcessor) compareSchemas(oldSchema, newSchema map[string]string) []string {
	var changes []string

	// Find added attributes
	for key := range newSchema {
		if _, exists := oldSchema[key]; !exists {
			changes = append(changes, fmt.Sprintf("+%s", key))
		}
	}

	// Find removed attributes
	for key := range oldSchema {
		if _, exists := newSchema[key]; !exists {
			changes = append(changes, fmt.Sprintf("-%s", key))
		}
	}

	// Find changed attribute values
	for key, newValue := range newSchema {
		if oldValue, exists := oldSchema[key]; exists && oldValue != newValue {
			changes = append(changes, fmt.Sprintf("~%s (%s → %s)", key, oldValue, newValue))
		}
	}

	return changes
}

func (p *schemaDetectorProcessor) logSchemaChange(metricName string, oldSchema, newSchema map[string]string, changes []string) {
	logLevel := strings.ToLower(p.config.LogLevel)

	message := fmt.Sprintf("Schema change detected for metric '%s'", metricName)
	fields := []zap.Field{
		zap.String("metric", metricName),
		zap.Strings("previous_attributes", p.getAttributeKeys(oldSchema)),
		zap.Strings("current_attributes", p.getAttributeKeys(newSchema)),
		zap.Strings("changes", changes),
	}

	switch logLevel {
	case "warn":
		p.logger.Warn(message, fields...)
	case "error":
		p.logger.Error(message, fields...)
	default:
		p.logger.Info(message, fields...)
	}
}

func (p *schemaDetectorProcessor) getAttributeKeys(schema map[string]string) []string {
	keys := make([]string, 0, len(schema))
	for key := range schema {
		keys = append(keys, key)
	}
	sort.Strings(keys)
	return keys
}

func (p *schemaDetectorProcessor) cleanup(ctx context.Context) {
	ticker := time.NewTicker(p.config.TTL / 2) // Cleanup twice per TTL period
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			p.mu.Lock()
			now := time.Now()
			for metricName, schema := range p.schemas {
				if now.Sub(schema.lastSeen) > p.config.TTL {
					delete(p.schemas, metricName)
					p.logger.Debug("Cleaned up old schema", zap.String("metric", metricName))
				}
			}
			p.mu.Unlock()
		}
	}
}

// Implementar o método Shutdown para fechar a goroutine de limpeza
func (p *schemaDetectorProcessor) Shutdown(ctx context.Context) error {
	if p.cancel != nil {
		p.cancel()
	}
	return nil
}

func newMetricsProcessor(
	ctx context.Context,
	set processor.Settings,
	cfg component.Config,
	next consumer.Metrics,
) (processor.Metrics, error) {
	config := cfg.(*Config)

	// Validar configuração
	if config.TTL <= 0 {
		config.TTL = 24 * time.Hour
	}

	cleanupCtx, cancel := context.WithCancel(ctx)

	p := &schemaDetectorProcessor{
		config:  config,
		logger:  set.Logger,
		schemas: make(map[string]*metricSchema),
		cancel:  cancel,
	}

	// Iniciar goroutine de limpeza com contexto cancelável
	go p.cleanup(cleanupCtx)

	return processorhelper.NewMetrics(
		ctx,
		set,
		cfg,
		next,
		p.processMetrics,
		processorhelper.WithCapabilities(consumer.Capabilities{MutatesData: false}),
		processorhelper.WithShutdown(p.Shutdown),
	)
}

func createDefaultConfig() component.Config {
	return &Config{
		IncludeMetrics: []string{}, // Empty means monitor all metrics
		LogLevel:       "info",
		TTL:            24 * time.Hour,
	}
}

func NewFactory() processor.Factory {
	return processor.NewFactory(
		component.MustNewType("schemadetector"),
		createDefaultConfig,
		processor.WithMetrics(newMetricsProcessor, component.StabilityLevelDevelopment),
	)
}

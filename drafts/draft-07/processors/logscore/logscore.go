package logscore

import (
	"bytes"
	"context"
	"encoding/json"
	"math/rand/v2"
	"net/http"
	"time"

	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/plog"
	"go.opentelemetry.io/collector/pdata/ptrace"
	"go.opentelemetry.io/collector/processor"
	"go.opentelemetry.io/collector/processor/processorhelper"
)

// Config define a configuração do processador
type Config struct {
	Prefix       string  `mapstructure:"prefix"`
	SampleRatio  float64 `mapstructure:"sample_ratio"` // taxa de amostragem (0.0 a 1.0)
	PostEndpoint string  `mapstructure:"post_endpoint"`
}

type logscore struct {
	consumer.Traces
	config *Config
	client *http.Client
}

func (p *logscore) processLogs(ctx context.Context, ld plog.Logs) (plog.Logs, error) {
	rls := ld.ResourceLogs()

	// iterar sobre os logs
	for i := 0; i < rls.Len(); i++ {
		scopeLogs := rls.At(i).ScopeLogs()
		for j := 0; j < scopeLogs.Len(); j++ {
			logRecords := scopeLogs.At(j).LogRecords()
			for k := 0; k < logRecords.Len(); k++ {
				logRecord := logRecords.At(k)

				// Amostragem simples head sampling
				if rand.Float64() <= p.config.SampleRatio {
					// Prefixar o corpo do log
					logRecord.Body().SetStr(p.config.Prefix + logRecord.Body().Str())

					// Criar payload para envio
					payload := map[string]interface{}{
						"timestamp": logRecord.Timestamp().AsTime().Format(time.RFC3339Nano),
						"body":      logRecord.Body().Str(),
						"severity":  logRecord.SeverityText(),
					}

					data, err := json.Marshal(payload)
					if err == nil {
						req, err := http.NewRequestWithContext(ctx, "POST", p.config.PostEndpoint, bytes.NewBuffer(data))
						if err == nil {
							req.Header.Set("Content-Type", "application/json")
							_, _ = p.client.Do(req) // ignorar erro, pode logar se quiser
						}
					}
				}
			}
		}
	}
	return ld, nil
}

func newLogsProcessor(
	ctx context.Context,
	set processor.Settings,
	cfg component.Config,
	next consumer.Logs,
) (processor.Logs, error) {
	sp := &logscore{
		config: cfg.(*Config),
		client: &http.Client{Timeout: 3 * time.Second},
	}

	return processorhelper.NewLogs(
		ctx,
		set,
		cfg,
		next,
		sp.processLogs,
		processorhelper.WithCapabilities(consumer.Capabilities{MutatesData: true}),
	)
}

// processTraces manipula os traces
func (p *logscore) processTraces(ctx context.Context, td ptrace.Traces) (ptrace.Traces, error) {
	for i := 0; i < td.ResourceSpans().Len(); i++ {
		rs := td.ResourceSpans().At(i)
		for j := 0; j < rs.ScopeSpans().Len(); j++ {
			ss := rs.ScopeSpans().At(j)
			for k := 0; k < ss.Spans().Len(); k++ {
				span := ss.Spans().At(k)
				span.SetName(p.config.Prefix + span.Name())
			}
		}
	}
	return td, nil
}

func newTracesProcessor(
	ctx context.Context,
	set processor.Settings, // tipo correto na versão atual
	cfg component.Config,
	next consumer.Traces,
) (processor.Traces, error) {
	sp := &logscore{
		Traces: next,
		config: cfg.(*Config),
	}

	return processorhelper.NewTraces(
		ctx,
		set,
		cfg,
		next,
		sp.processTraces,
		processorhelper.WithCapabilities(consumer.Capabilities{MutatesData: true}),
	)
}

func NewFactory() processor.Factory {
	return processor.NewFactory(
		component.MustNewType("logscore"),
		createDefaultConfig,
		processor.WithTraces(newTracesProcessor, component.StabilityLevelDevelopment),
		processor.WithLogs(newLogsProcessor, component.StabilityLevelDevelopment), // <-- ESSENCIAL
	)
}

func createDefaultConfig() component.Config {
	return &Config{
		Prefix: "custom-",
	}
}

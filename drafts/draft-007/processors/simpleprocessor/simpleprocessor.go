package simpleprocessor

import (
	"context"

	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/plog"
	"go.opentelemetry.io/collector/pdata/ptrace"
	"go.opentelemetry.io/collector/processor"
	"go.opentelemetry.io/collector/processor/processorhelper"
)

// Config define a configuração do processador
type Config struct {
	Prefix string `mapstructure:"prefix"`
}

// simpleProcessor implementa a lógica do processador
type simpleProcessor struct {
	consumer.Traces
	config *Config
}

func (p *simpleProcessor) processLogs(ctx context.Context, ld plog.Logs) (plog.Logs, error) {
	rls := ld.ResourceLogs()
	for i := 0; i < rls.Len(); i++ {
		scopeLogs := rls.At(i).ScopeLogs()
		for j := 0; j < scopeLogs.Len(); j++ {
			logRecords := scopeLogs.At(j).LogRecords()
			for k := 0; k < logRecords.Len(); k++ {
				logRecord := logRecords.At(k)
				logRecord.Body().SetStr(p.config.Prefix + logRecord.Body().Str())
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
	sp := &simpleProcessor{
		config: cfg.(*Config),
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
func (p *simpleProcessor) processTraces(ctx context.Context, td ptrace.Traces) (ptrace.Traces, error) {
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
	sp := &simpleProcessor{
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
		component.MustNewType("simpleprocessor"),
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

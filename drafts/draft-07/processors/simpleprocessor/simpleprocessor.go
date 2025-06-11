package simpleprocessor

import (
	"context"

	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
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

// NewFactory retorna a factory do processador
func NewFactory() processor.Factory {
	return processor.NewFactory(
		component.MustNewType("simpleprocessor"),
		createDefaultConfig,
		processor.WithTraces(newTracesProcessor, component.StabilityLevelDevelopment),
	)
}

func createDefaultConfig() component.Config {
	return &Config{
		Prefix: "custom-",
	}
}

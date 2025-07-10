package sleepprocessor

import (
	"context"
	"time"

	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/ptrace"
	"go.opentelemetry.io/collector/processor"
	"go.opentelemetry.io/collector/processor/processorhelper"
)

type Config struct {
	SleepMilliseconds uint64 `mapstructure:"sleep_milliseconds"`
}

type sleepTraceProcessor struct {
	sleepDuration time.Duration
}

func (p *sleepTraceProcessor) processTraces(ctx context.Context, td ptrace.Traces) (ptrace.Traces, error) {
	time.Sleep(p.sleepDuration)
	return td, nil
}

func newTracesProcessor(
	ctx context.Context,
	set processor.Settings,
	cfg component.Config,
	next consumer.Traces,
) (processor.Traces, error) {
	c := cfg.(*Config)
	p := &sleepTraceProcessor{
		sleepDuration: time.Duration(c.SleepMilliseconds) * time.Millisecond,
	}
	return processorhelper.NewTraces(
		ctx,
		set,
		cfg,
		next,
		p.processTraces,
		processorhelper.WithCapabilities(consumer.Capabilities{MutatesData: false}),
	)
}

func createDefaultConfig() component.Config {
	return &Config{
		SleepMilliseconds: 100,
	}
}

func NewFactory() processor.Factory {
	return processor.NewFactory(
		component.MustNewType("sleepprocessor"),
		createDefaultConfig,
		processor.WithTraces(newTracesProcessor, component.StabilityLevelDevelopment),
	)
}

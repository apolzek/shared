package unhealthyprocessor

import (
	"context"
	"strings"

	"go.opentelemetry.io/collector/component"
	"go.opentelemetry.io/collector/consumer"
	"go.opentelemetry.io/collector/pdata/pcommon"
	"go.opentelemetry.io/collector/pdata/ptrace"
	"go.opentelemetry.io/collector/processor"
	"go.opentelemetry.io/collector/processor/processorhelper"
)

type Config struct{}

type unhealthyTraceProcessor struct{}

func (p *unhealthyTraceProcessor) processTraces(ctx context.Context, td ptrace.Traces) (ptrace.Traces, error) {
	rs := td.ResourceSpans()

	for i := 0; i < rs.Len(); i++ {
		rspan := rs.At(i)
		ss := rspan.ScopeSpans()

		unhealthy := false

		for j := 0; j < ss.Len(); j++ {
			spans := ss.At(j).Spans()
			for k := 0; k < spans.Len(); k++ {
				span := spans.At(k)

				if containsUnhealthyKeywords(span.Name()) {
					unhealthy = true
				}

				attrs := span.Attributes()
				if attrs.Len() > 0 {
					attrs.Range(func(k string, v pcommon.Value) bool {
						str := strings.ToLower(v.AsString())
						if strings.Contains(str, "error") || strings.Contains(str, "exception") {
							unhealthy = true
							return false
						}
						return true
					})

					if scVal, ok := attrs.Get("http.status_code"); ok && scVal.Type() == pcommon.ValueTypeInt {
						code := scVal.Int()
						if code >= 500 && code <= 599 {
							unhealthy = true
						}
					}
				}
			}
		}

		if unhealthy {
			rspan.Resource().Attributes().PutBool("sherlock.unhealthy", true)
		}
	}

	return td, nil
}

func containsUnhealthyKeywords(input string) bool {
	lower := strings.ToLower(input)
	return strings.Contains(lower, "error") || strings.Contains(lower, "exception")
}

func newTracesProcessor(
	ctx context.Context,
	set processor.Settings,
	cfg component.Config,
	next consumer.Traces,
) (processor.Traces, error) {
	p := &unhealthyTraceProcessor{}
	return processorhelper.NewTraces(
		ctx,
		set,
		cfg,
		next,
		p.processTraces,
		processorhelper.WithCapabilities(consumer.Capabilities{MutatesData: true}),
	)
}

func createDefaultConfig() component.Config {
	return &Config{}
}

func NewFactory() processor.Factory {
	return processor.NewFactory(
		component.MustNewType("sherlockunhealthy"),
		createDefaultConfig,
		processor.WithTraces(newTracesProcessor, component.StabilityLevelDevelopment),
	)
}

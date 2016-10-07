package org.openfs.snmpcg.aggregator;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AbstractListAggregationStrategy;
import org.openfs.snmpcg.counters.PollDelta;

import java.util.List;

public class DeltaListAggregationStrategy extends
		AbstractListAggregationStrategy<List<PollDelta>> {

	@SuppressWarnings("unchecked")
	@Override
	public List<PollDelta> getValue(Exchange msg) {
		return msg.getIn().getBody(List.class);
	}

}

package org.openfs.snmpcg.aggregator;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AbstractListAggregationStrategy;

import org.openfs.snmpcg.counters.PollData;

public class PollDataAggregationStrategy extends
		AbstractListAggregationStrategy<List<PollData>> {

	@SuppressWarnings("unchecked")
	@Override
	public List<PollData> getValue(Exchange msg) {
		return msg.getIn().getBody(List.class);
	}

}

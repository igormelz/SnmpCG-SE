package org.openfs.snmpcg.aggregator;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import org.openfs.snmpcg.counters.PollDelta;

import java.util.ArrayList;

public class DeltaAggregationStrategy implements AggregationStrategy {

    @SuppressWarnings("unchecked")
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        
    	PollDelta newBody = newExchange.getIn().getBody(PollDelta.class);
    	ArrayList<PollDelta> list = null;

        if (oldExchange == null) {
             list = new ArrayList<PollDelta>();
             list.add(newBody);
             newExchange.getIn().setBody(list);
             return newExchange;
         } else {
             list = oldExchange.getIn().getBody(ArrayList.class);
             if(newBody != null)
            	 list.add(newBody);
             return oldExchange;
         }
    }
}

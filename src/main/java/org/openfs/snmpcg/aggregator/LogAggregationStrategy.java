package org.openfs.snmpcg.aggregator;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import org.openfs.snmpcg.counters.PollDelta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LogAggregationStrategy implements AggregationStrategy {

    @SuppressWarnings("unchecked")
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        
    	Map<String, Object> newBody = newExchange.getIn().getBody(Map.class);   	
    	ArrayList<Map<String, Object>> list = null; // ;

        if (oldExchange == null) {
             list = new ArrayList<Map<String, Object>>();
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

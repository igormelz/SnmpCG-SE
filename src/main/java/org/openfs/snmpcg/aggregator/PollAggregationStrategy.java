package org.openfs.snmpcg.aggregator;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import org.openfs.snmpcg.CamelHeaders;

import java.util.List;

public class PollAggregationStrategy implements AggregationStrategy {

    @SuppressWarnings("unchecked")
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Exchange answer = newExchange;

        if (oldExchange != null) {
        	
            List<?> iftable = oldExchange.getIn().getBody(List.class);
            int c = oldExchange.getIn().getHeader(CamelHeaders.SIZE, int.class);
            
            List<?> msgList = newExchange.getIn().getBody(List.class);
            if(msgList != null) {
            	iftable.addAll(newExchange.getIn().getBody(List.class));
            	oldExchange.getIn().setHeader(CamelHeaders.SIZE, c + msgList.size());
            }
            
            answer = oldExchange;
        } else {
        	List<?> msgList = newExchange.getIn().getBody(List.class);
        	newExchange.getIn().setHeader(CamelHeaders.SIZE, msgList.size());
        }
        
        return answer;
    }
}

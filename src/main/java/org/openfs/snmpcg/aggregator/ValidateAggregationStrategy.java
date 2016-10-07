package org.openfs.snmpcg.aggregator;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import org.openfs.snmpcg.CamelHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ValidateAggregationStrategy implements AggregationStrategy {

    @SuppressWarnings("unchecked")
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Exchange answer = newExchange;

        if (oldExchange != null) {
            Map<String, List<?>> iftable = oldExchange.getIn().getBody(Map.class);
            
            int u = oldExchange.getIn().getHeader(CamelHeaders.UP, int.class);
            int d = oldExchange.getIn().getHeader(CamelHeaders.DOWN, int.class);
            
            List<?> msgList = newExchange.getIn().getBody(List.class);
            if(msgList != null) {
            	oldExchange.getIn().setHeader(CamelHeaders.UP, u+1);
            } else {
        		oldExchange.getIn().setHeader(CamelHeaders.DOWN, d+1);            	
            }            
            iftable.put(newExchange.getIn().getHeader(CamelHeaders.HOST,String.class), msgList);
            
            answer = oldExchange;
        } else {   	
            Map<String, List<?>> iftable = new HashMap<String, List<?>>();
            
            List<?> msgList = newExchange.getIn().getBody(List.class);
            if(msgList != null) {
            	newExchange.getIn().setHeader(CamelHeaders.UP, 1);
        		newExchange.getIn().setHeader(CamelHeaders.DOWN, 0);
            } else {
            	newExchange.getIn().setHeader(CamelHeaders.UP, 0);
        		newExchange.getIn().setHeader(CamelHeaders.DOWN, 1);            	
            }
            iftable.put(newExchange.getIn().getHeader(CamelHeaders.HOST,String.class), msgList);
            newExchange.getIn().setBody(iftable);
        }
        return answer;
    }
}

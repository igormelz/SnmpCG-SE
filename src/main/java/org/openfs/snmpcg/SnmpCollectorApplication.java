package org.openfs.snmpcg;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SnmpCollectorApplication {

	@Value("${snmpcg.minPoolThreads:10}")
	private int poolSize;
	
	@Value("${snmpcg.maxPoolThreads:30}")
	private int maxPoolSize;
	
	public static void main(String[] args) {
		SpringApplication.run(SnmpCollectorApplication.class, args);
	}

	@Bean
	ThreadPoolProfile camelThreadPoolProfile() {
		ThreadPoolProfile customProfile = new ThreadPoolProfile();
		customProfile.setId("SnmpCGThreadPoolProfile");
		customProfile.setDefaultProfile(true);
		customProfile.setPoolSize(poolSize);
		customProfile.setMaxPoolSize(maxPoolSize);
		return customProfile;
	}
	
	@Component
	class RestApi extends RouteBuilder {

		@Override
		public void configure() {
			restConfiguration()
				.contextPath("/api")
				.bindingMode(RestBindingMode.json);
			
			rest("/v1").description("SnmpCG REST service")
					.consumes("application/json")
					.produces("application/json")
				.get("/sources").description("the list sources")
					.param().name("status").type(RestParamType.query).endParam()
					.route().routeId("sources-api")
					.bean("snmpSources","getSources")
					.endRest()
				.get("/sources/{source}").description("source details")
					.route().routeId("sources-api-details")
					.bean("snmpSources","getSource(${header.source})")
					.endRest()
				.post("/sources").description("add source")
					.route().routeId("sources-api-add")	
					.bean("snmpSources","addSource")
					.endRest()
				.delete("/sources/{source}").description("delete source")
					.route().routeId("sources-api-delete")	
					.bean("snmpSources","removeSource")
					.endRest()
				.get("/sources/{source}/interfaces").description("the list source interfaces")
					.param().name("trace").type(RestParamType.query).endParam()
					.param().name("chargeable").type(RestParamType.query).endParam()
					.route().routeId("sources-api-interfaces")
					.bean("snmpSources","getSourceInterfaces")
					.endRest()
				.put("/sources/{source}/interfaces").description("update interfaces")
					.param().name("trace").type(RestParamType.query).endParam()
					.param().name("chargeable").type(RestParamType.query).endParam()
					.route().routeId("sources-api-update-interfaces")
					.bean("snmpSources","updateSourceInterface")
					.endRest()
				.get("/interfaces").description("the list interfaces")
					.param().name("trace").type(RestParamType.query).endParam()
					.param().name("chargeable").type(RestParamType.query).endParam()
					.route().routeId("interfaces-api")
					.bean("snmpSources","getInterfaces")
					.endRest();
		}
	}

	@Component
	class Backend extends RouteBuilder {

		class NullAggregationStrategy implements AggregationStrategy {
			@Override
			public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
				return null;
			}
		}
		
		@Override
		public void configure() {

			// scheduled poll source status
			from("timer://validate?period={{snmpcg.validateStatusTimer:3m}}").routeId("pollStatus")
				.split(method("snmpSources", "getDownSources"),new NullAggregationStrategy()).parallelProcessing()
					.bean("snmpPoll", "pollStatus")
				.end();
			
			// scheduled poll counters
			from("quartz2://snmp/poll?cron=0+0/5+*+*+*+?").routeId("pollCounters")
				.filter(method("snmpSources","validateStartPoll"))
					.log("started")
					.split(method("snmpSources", "getReadySources"),new NullAggregationStrategy()).parallelProcessing().executorServiceRef("SnmpCGThreadPoolProfile")
						.bean("snmpPoll", "pollCounters")
					.end()
					.log("${bean:snmpSources?method=logEndPoll}")
					.bean("snmpSources","setRecoveryState")
					.to("direct:storeCdr","direct:storeTrace")
				.end();
			
			// store CDR
			from("direct:storeCdr").routeId("storeCDR")
				.bean("snmpSources","exportChargingDataRecords")
				.filter(header("countChargingDataRecords").isGreaterThan(0))
					.to("{{snmpcg.flushChargingDataRecordEndpoint:direct:writeCdrFile}}")
				.end();
			
			// store Trace
			from("direct:storeTrace").routeId("storeTrace")
				.bean("snmpSources","exportTraceRecords")
				.filter(header("countTraceRecords").isGreaterThan(0))
					.to("{{snmpcg.flushTraceCountersEndpoint:direct:writeTraceCounterFile}}")
				.end();
		}
	}
	
}

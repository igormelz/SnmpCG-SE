package org.openfs.snmpcg;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SnmpCollectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(SnmpCollectorApplication.class, args);
	}

	@Bean
	public ServletRegistrationBean camelServletRegistrationBean() {
		ServletRegistrationBean registration = new ServletRegistrationBean(
				new CamelHttpTransportServlet(), "/api/*");
		registration.setName("CamelServlet");
		return registration;
	}

	@Bean
	CamelContextConfiguration contextConfiguration() {
		return new CamelContextConfiguration() {

			@Override
			public void beforeApplicationStart(CamelContext context) {

				context.setMessageHistory(false);
				//context.disableJMX();

				if (context.isAllowUseOriginalMessage()) {
					context.setAllowUseOriginalMessage(false);
				}

				context.getExecutorServiceManager().setThreadNamePattern("Thread-#counter#");
			}

			@Override
			public void afterApplicationStart(CamelContext arg0) {
				// TODO Auto-generated method stub
			}
		};
	}

	@Component
	class CamelRoutes extends RouteBuilder {

		@Override
		public void configure() throws Exception {

			restConfiguration().component("servlet").bindingMode(
					RestBindingMode.json);
			rest("/v1/")
					.consumes("application/json")
					.produces("application/json")
					
					.get("/sources/").description("get list sources")
					.param().name("status").description("filter by status").type(RestParamType.query).endParam()
					.to("bean:sources?method=getSources")
					
					.get("/sources/{source}")
					.to("bean:sources?method=getSource(${header.source})")
					
					.post("/sources/{source}")
					.to("direct:addSource")
					//.to("bean:sources?method=addSource")
					
					.get("/sources/del/{source}")
					.to("bean:sources?method=removeSource")
					
					.delete("/sources/{source}")
					.to("bean:sources?method=removeSource")
					
					.get("/sources/{source}/interfaces")
					.param().name("polling").description("filter by polling status").type(RestParamType.query).endParam()
					.to("bean:sources?method=getSourceInterfaces")
					
					.get("/sources/interfaces")
					.to("bean:sources?method=getChargingInterfaces")
					
					//.put("/sources/{source}/interfaces/{ifDescr}")
					//.to("bean:sources?method=updateInterface(${header.source},${header.ifDescr})")
					;

			// add new source 
			from("direct:addSource")
			.to("bean:sources?method=addSource")
			.to("direct:pollStatus");

			// scheduled poll source status
			from("timer://validate?period={{snmpcg.validate.period:3m}}").routeId("pollStatus")
			.to("direct:pollStatus");
			
			from("direct:pollStatus")
				.split(method("sources", "getDownSources")).parallelProcessing()
					.bean(SnmpUtils.class, "pollStatus")
				.end()
				;
			
			// scheduled poll counters
			from("quartz2://snmp/poll?cron=0+0/5+*+*+*+?").routeId("pollCounters")
					.log("start poll sources")
					.split(method("sources", "getReadySources")).parallelProcessing()
						.bean(SnmpUtils.class, "pollCounters")
					.end()
					// call external camel route 
					.to("{{snmpcg.route.flushCounters}}")
					.to("{{snmpcg.route.flushChargingDataRecord}}")
					.to("bean:sources?method=setRecoveryState")
					;
					
		}

	}
}

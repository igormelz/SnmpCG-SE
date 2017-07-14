package org.openfs.snmpcg;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class SnmpCollectorApplication {

	@Value("${snmpcg.poll.threads.min:20}")
	private int poolSize;
	
	@Value("${snmpcg.poll.threads.max:40}")
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
					.param().name("status").type(RestParamType.query).endParam()
					.to("bean:sources?method=getSources")
					
					.get("/sources/{source}")
					.to("bean:sources?method=getSource(${header.source})")
					
					.post("/sources/{source}")
					.to("bean:sources?method=addSource")
					
					.get("/sources/del/{source}")
					.to("bean:sources?method=removeSource")
					
					.delete("/sources/{source}")
					.to("bean:sources?method=removeSource")
					
					.get("/sources/{source}/interfaces")
					.param().name("trace").type(RestParamType.query).endParam()
					.param().name("chargeable").type(RestParamType.query).endParam()
					.to("bean:sources?method=getSourceInterfaces")
					
					.put("/sources/{source}/interfaces")
					//.param().name("trace").type(RestParamType.query).endParam()
					//.param().name("chargeable").type(RestParamType.query).endParam()
					.to("bean:sources?method=updateSourceInterface")
					
					.get("/interfaces")
					.param().name("trace").type(RestParamType.query).endParam()
					.param().name("chargeable").type(RestParamType.query).endParam()
					.to("bean:sources?method=getInterfaces")
					;
			

			// scheduled poll source status
			from("timer://validate?period={{snmpcg.validate.period:3m}}").routeId("pollStatus")
				.split(method("sources", "getDownSources")).parallelProcessing()
					.bean("snmpService", "pollStatus")
				.end()
				;
			
			// scheduled poll counters
			from("quartz2://snmp/poll?cron=0+0/5+*+*+*+?").routeId("pollCounters")
					.log("start poll sources")
					.split(method("sources", "getReadySources")).parallelProcessing().executorServiceRef("SnmpCGThreadPoolProfile")
						.bean("snmpService", "pollCounters")
					.end()
					// call external camel route 
					.to("{{snmpcg.route.flushCounters}}")
					.to("{{snmpcg.route.flushChargingDataRecord}}")
					.to("bean:sources?method=setRecoveryState")
					;
					
		}

	}
}

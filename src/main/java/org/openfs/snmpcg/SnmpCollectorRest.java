package org.openfs.snmpcg;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.stereotype.Component;

@Component
public class SnmpCollectorRest extends RouteBuilder {

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
				.endRest()
			.get("/cluster/members")
				.route().routeId("cluster-api-members")
				.bean("clusterInfoService","getStatus")
				.endRest()
			.get("/cluster/master")
				.route().routeId("cluster-api-master")
				.bean("clusterInfoService","getMaster")
				.endRest()
			.get("/cluster/leader")
				.route().routeId("cluster-api-leader")
				.bean("clusterInfoService","getLeaderStatus")
				.endRest()
			;
	}

}

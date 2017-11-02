package org.openfs.snmpcg;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.apache.camel.component.hazelcast.policy.HazelcastRoutePolicy;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.RoutePolicy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.openfs.snmpcg.model.SnmpSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.hazelcast.config.Config;
import com.hazelcast.config.InMemoryFormat;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

@SpringBootApplication
public class SnmpCollectorApplication {

    @Value("${snmpcg.minPoolThreads:10}")
    private int poolSize;

    @Value("${snmpcg.maxPoolThreads:30}")
    private int maxPoolSize;

    @Value("${snmpcg.nodeIp:auto}")
    private String nodeIp;

    @Value("${snmpcg.memberIp:auto}")
    private String memberIp;

    public static void main(String[] args) {
        SpringApplication.run(SnmpCollectorApplication.class, args);
    }

    @Bean
    public Config getConfig() {
        Config config = new Config().setInstanceName("hzSnmpCG");
        config.getMapConfig("sources").setInMemoryFormat(InMemoryFormat.OBJECT);
        if (!nodeIp.equalsIgnoreCase("auto")) {
            config.getNetworkConfig().getInterfaces().addInterface(nodeIp);
            config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
            config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true).addMember(memberIp).addMember(nodeIp);
        }
        return config;
    }

    @Bean
    RoutePolicy clusterPolicy(HazelcastInstance instance) {
        HazelcastRoutePolicy policy = new HazelcastRoutePolicy(instance);
        policy.setLockMapName("snmpcg:lock:map");
        policy.setLockKey("pollCounter-policy");
        policy.setLockValue("locked");
        policy.setTryLockTimeout(1, TimeUnit.MINUTES);
        return policy;
    }


    @Bean
    public ConcurrentMap<String, SnmpSource> getCacheMap(HazelcastInstance instance) {
        return instance.getMap("sources");
    }

    @Bean 
    public IMap<String,Object> getConfigMap(HazelcastInstance instance) {
        return instance.getMap("config");
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
                .routePolicyRef("clusterPolicy")
                .split(method("snmpSources", "getDownSources"), new NullAggregationStrategy()).parallelProcessing()
                    .bean("snmpPoll", "pollStatus")
                    .setHeader(HazelcastConstants.OPERATION, constant("put"))
                    .to("hazelcast-map:sources?hazelcastInstanceName=hzSnmpCG")
                .end();

            // scheduled poll counters
            from("quartz2://snmp/poll?cron=0+0/5+*+*+*+?&pauseJob=true&deleteJob=false").routeId("pollCounters")
                .routePolicyRef("clusterPolicy")
                .filter(method("snmpSources", "validateStartPoll"))
                .split(method("snmpSources", "getReadySources"), new NullAggregationStrategy()).parallelProcessing().executorServiceRef("SnmpCGThreadPoolProfile")
                .bean("snmpPoll", "pollCounters").setHeader(HazelcastConstants.OPERATION, constant("put"))
                .to("hazelcast-map:sources?hazelcastInstanceName=hzSnmpCG").end().log("${bean:snmpSources?method=logEndPoll}").to("direct:storeCdr", "direct:storeTrace").end();

            // store CDR
            from("direct:storeCdr").routeId("storeCDR").bean("snmpSources", "exportChargingDataRecords").filter(header("countChargingDataRecords").isGreaterThan(0))
                .to("{{snmpcg.flushChargingDataRecordEndpoint:direct:writeCdrFile}}").end();

            // store Trace
            from("direct:storeTrace").routeId("storeTrace").bean("snmpSources", "exportTraceRecords").filter(header("countTraceRecords").isGreaterThan(0))
                .to("{{snmpcg.flushTraceCountersEndpoint:direct:writeTraceCounterFile}}").end();
        }
    }

}

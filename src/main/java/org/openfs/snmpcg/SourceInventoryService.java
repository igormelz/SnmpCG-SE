package org.openfs.snmpcg;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.stereotype.Service;
import org.openfs.snmpcg.model.SnmpInterface;
import org.openfs.snmpcg.model.SnmpSource;
import org.openfs.snmpcg.model.SnmpConstants;

import com.hazelcast.core.IMap;

@Service("snmpSources")
public class SourceInventoryService {

    private static final Logger log = LoggerFactory.getLogger(SourceInventoryService.class);

    @Value("${snmpcg.snmpCommunity:public}")
    private String community;

    @Value("${snmpcg.snmpTimeout:5}")
    private int timeout;

    @Value("${snmpcg.snmpRetries:3}")
    private int retries;

    @Value("${snmpcg.cdrTimeStampFormat:yyyy-MM-dd HH:mm:ss}")
    private String cdrTimeStampFormat;

    @Value("${snmpcg.cdrFieldSeparator:;}")
    private String fieldSeparator;

    @Value("${snmpcg.tagkeys.source:RouterId}")
    private String sourceTagKeysProperties;

    @Value("${snmpcg.tagkeys.interface:CircuitId}")
    private String interfaceTagKeysProperties;

    @Autowired
    private ConcurrentMap<String, SnmpSource> sources;

    @Autowired
    private IMap<String, Object> config;

    private SimpleDateFormat timeStampFormat;
    private final static Pattern IPADDR_PATTERN = Pattern.compile("\\d+.\\d+.\\d+.\\d+");

    private StopWatch polltimer = new StopWatch();
    private CounterService counterSources;
    private GaugeService gaugeSources;

    @Autowired
    public SourceInventoryService(GaugeService gaugeService, CounterService counterService) {
        this.counterSources = counterService;
        this.gaugeSources = gaugeService;
    }

    @PostConstruct
    public void initConfig() {
        config.putIfAbsent("sourceTagKeys", new LinkedHashSet<String>(Arrays.asList(sourceTagKeysProperties.split(","))));
        config.putIfAbsent("interfaceTagKeys", new LinkedHashSet<String>(Arrays.asList(interfaceTagKeysProperties.split(","))));
        config.putIfAbsent("snmpCommunity", community);
        config.putIfAbsent("snmpRetries", retries);
        config.putIfAbsent("snmpTimeout", timeout);
        if (!config.containsKey("cdrTimeStampFormat")) {
            config.put("cdrTimeStampFormat", cdrTimeStampFormat);
            timeStampFormat = new SimpleDateFormat(cdrTimeStampFormat);
        } else {
            timeStampFormat = new SimpleDateFormat(config.get("cdrTimeStampFormat").toString());
        }
        if (!config.containsKey(fieldSeparator)) {
            config.put("cdrFieldSeparator", fieldSeparator);
        } else {
            fieldSeparator = config.get("cdrFieldSeparator").toString();
        }
    }

    @Handler
    public void addSource(Exchange exchange) {
        StringBuilder sb = new StringBuilder("add source ");
        if (exchange.getIn().getBody() == null) {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            sb.append(" fail. No payload defined");
            exchange.getIn().setBody(Collections.singletonMap("Status", sb.toString()));
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>)exchange.getIn().getBody(Map.class);

        if (data == null || !data.containsKey("ipaddr")) {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            sb.append(" fail. No ipaddr defined");
            exchange.getIn().setBody(Collections.singletonMap("Status", sb.toString()));
            return;
        }

        String host = data.get("ipaddr").toString();
        if (!IPADDR_PATTERN.matcher(host).matches()) {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            sb.append(host).append(" fail. No vaild ipaddr");
            exchange.getIn().setBody(Collections.singletonMap("Status", sb.toString()));
            return;
        }

        if (sources.containsKey(host)) {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            sb.append(host).append(" allready exists");
            exchange.getIn().setBody(Collections.singletonMap("Status", sb.toString()));
            return;
        }

        String hostCommunity = config.get("snmpCommunity").toString();
        if (data.get("community") != null && !data.get("community").toString().isEmpty()) {
            hostCommunity = data.get("community").toString();
        }

        int hostRetries = (int)config.get("snmpRetries");
        if (data.get("retries") != null) {
            hostRetries = (int)data.get("retries");
        }

        int hostTimeout = (int)config.get("snmpTimeout");
        if (data.get("timeout") != null) {
            hostTimeout = (int)data.get("timeout");
        }

        SnmpSource source = new SnmpSource(host, hostCommunity, hostRetries, hostTimeout);
        if (source != null) {
            if (data.get("tags") != null && data.get("tags") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> tags = (Map<String, String>)data.get("tags");
                source.addTags(tags);
            }
            // commit
            sources.put(host, source);
            sb.append(host).append(" to next poll with community:").append(hostCommunity);
            exchange.getIn().setBody(Collections.singletonMap("Status", sb.toString()));
            log.info(sb.toString());
        }
    }

    @Handler
    public List<SnmpSource> getReadySources() {
        return sources.values().stream().filter(info -> info.getStatus().equalsIgnoreCase(SnmpConstants.SUCCESS)).collect(Collectors.toList());
    }

    @Handler
    public List<SnmpSource> getDownSources() {
        return sources.values().stream().filter(source -> !source.getStatus().equalsIgnoreCase(SnmpConstants.SUCCESS)).collect(Collectors.toList());
    }

    @Handler
    public void getConfig(Exchange exchange) {
        exchange.getIn().setBody(config);
    }

    @Handler
    public void getSources(Exchange exchange) {

        // process status
        String status = exchange.getIn().getHeader("status", String.class);
        if (status != null && !status.isEmpty()) {
            exchange.getIn().setBody(sources.values().stream().filter(e -> e.getStatus().equalsIgnoreCase(status)).map(mapSource).collect(Collectors.toList()));
            return;
        }

        // process stats
        if (exchange.getIn().getHeader("stats") != null) {
            exchange.getIn().setBody(sources.values().stream().collect(Collectors.groupingBy(SnmpSource::getStatus, Collectors.counting())));
            return;
        }

        // return list sources
        exchange.getIn().setBody(sources.values().stream().map(mapSource).collect(Collectors.toList()));
    }

    Function<SnmpSource, Map<String, Object>> mapSource = source -> {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("ipAddress", source.getIpAddress());
        map.put("Status", source.getStatus());
        map.put("sysUptime", source.getSysUptime());
        map.put("sysName", source.getSysName());
        map.put("sysDescr", source.getSysDescr());
        map.put("snmpCommunity", source.getTarget().getCommunity().toString());
        map.put("snmpRetries", source.getTarget().getRetries());
        map.put("snmpTimeout", source.getTarget().getTimeout() / 1000L);
        map.put("ifNumber", source.getIftable().size());
        map.put("pollTime", source.getPollTime());
        long counter_up = source.getInterfaces().stream().filter(SnmpInterface::isUp).count();
        map.put("statusUpCounter", counter_up);
        map.put("statusDownCounter", source.getIftable().size() - counter_up);
        map.put("chargeableCounter", source.getInterfaces().stream().filter(SnmpInterface::isChargeable).count());
        map.put("traceCounter", source.getInterfaces().stream().filter(SnmpInterface::isTrace).count());
        map.put("pollResponse", source.getPollResponse());
        map.put("tags", source.getTags());
        return map;
    };

    @Handler
    public void getInterfaces(Exchange exchange) {
        List<Map<String, Object>> ifList = sources.values().stream().map(mapInterface).flatMap(List::stream).collect(Collectors.toList());

        String filter = exchange.getIn().getHeader("chargeable", String.class);
        if (filter != null) {
            exchange.getIn().setBody(ifList.stream().filter(e -> (Boolean)e.get("chargeable")).collect(Collectors.toList()));
            return;
        }

        filter = exchange.getIn().getHeader("trace", String.class);
        if (filter != null) {
            exchange.getIn().setBody(ifList.stream().filter(e -> (Boolean)e.get("trace")).collect(Collectors.toList()));
            return;
        }

        filter = exchange.getIn().getHeader("stats", String.class);
        if (filter != null) {
            Map<String, Long> answer = new HashMap<String, Long>(3);
            answer.put("traceCount", ifList.stream().filter(e -> (Boolean)e.get("trace")).count());
            answer.put("chargeableCount", ifList.stream().filter(e -> (Boolean)e.get("chargeable")).count());
            answer.put("ifnumberCount", (long)ifList.size());
            exchange.getIn().setBody(answer);
            return;
        }

        exchange.getIn().setBody(ifList);
    }

    Function<SnmpSource, List<Map<String, Object>>> mapInterface = source -> {
        List<Map<String, Object>> answer = source.getInterfaces().stream().map(e -> {

            Map<String, Object> srcmap = new HashMap<String, Object>();
            srcmap.put("ipAddress", source.getIpAddress());
            srcmap.put("sysName", source.getSysName());
            srcmap.put("tags", source.getTags());

            Map<String, Object> iface = new HashMap<String, Object>();
            iface.put("source", srcmap);
            iface.put("pollTime", source.getPollTime());
            iface.put("pollDuration", source.getPollDuration());
            iface.put("ifIndex", e.getIfIndex());
            iface.put("ifDescr", e.getIfDescr());
            iface.put("ifAlias", e.getIfAlias());
            iface.put("chargeable", e.isChargeable());
            iface.put("trace", e.isTrace());
            iface.put("up", e.isUp());
            iface.put("pollInOctets", e.getPollInOctets());
            iface.put("pollOutOctets", e.getPollOutOctets());
            iface.put("tags", e.getTags());
            iface.put("portType", e.getPortType());
            return iface;
        }).collect(Collectors.toList());
        return answer;
    };

    @Handler
    public void getSourceInterfaces(Exchange exchange) {
        String source = exchange.getIn().getHeader("source", String.class);
        if (!sources.containsKey(source)) {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            Map<String, String> answer = new HashMap<String, String>(1);
            answer.put("Status", "source " + source + " not found");
            exchange.getIn().setBody(answer);
            return;
        }

        List<SnmpInterface> ifTable = sources.get(source).getInterfaces();
        // filter chargeable on (off)
        String filter = exchange.getIn().getHeader("chargeable", String.class);
        if (filter != null) {
            if ("on".equalsIgnoreCase(filter)) {
                exchange.getIn().setBody(ifTable.stream().filter(i -> i.isChargeable()).collect(Collectors.toList()));
            } else {
                exchange.getIn().setBody(ifTable.stream().filter(i -> !i.isChargeable()).collect(Collectors.toList()));
            }
            return;
        }
        // filter trace=on (off)
        filter = exchange.getIn().getHeader("trace", String.class);
        if (filter != null) {
            if ("on".equalsIgnoreCase(filter)) {
                exchange.getIn().setBody(ifTable.stream().filter(i -> i.isTrace()).collect(Collectors.toList()));
            } else {
                exchange.getIn().setBody(ifTable.stream().filter(i -> !i.isTrace()).collect(Collectors.toList()));
            }
            return;
        }
        // filter status = up (down)
        filter = exchange.getIn().getHeader("status", String.class);
        if (filter != null) {
            if ("up".equalsIgnoreCase(filter)) {
                exchange.getIn().setBody(ifTable.stream().filter(i -> i.isUp()).collect(Collectors.toList()));
            } else {
                exchange.getIn().setBody(ifTable.stream().filter(i -> i.isDown()).collect(Collectors.toList()));
            }
            return;
        }
        exchange.getIn().setBody(ifTable);
    }

    @Handler
    public void exportTraceRecords(Exchange exchange) {
        if (getReadySources().isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        getReadySources().stream().forEach(source -> {
            source.getInterfaces().stream().filter(SnmpInterface::isTrace).forEach(ifEntry -> {
                sb.append(timeStampFormat.format(source.getPollTime())).append(fieldSeparator);
                sb.append(source.getIpAddress()).append(fieldSeparator);
                sb.append(ifEntry.getIfIndex()).append(fieldSeparator);
                sb.append(ifEntry.getIfDescr()).append(fieldSeparator);
                sb.append(ifEntry.getIfName()).append(fieldSeparator);
                sb.append(ifEntry.getIfAlias().replace(fieldSeparator.charAt(0), '.')).append(fieldSeparator);
                sb.append(ifEntry.getIfAdminStatus()).append(fieldSeparator);
                sb.append(ifEntry.getIfOperStatus()).append(fieldSeparator);
                sb.append(ifEntry.getIfInOctets()).append(fieldSeparator);
                sb.append(ifEntry.getIfOutOctets()).append(fieldSeparator);
                sb.append(source.getSysUptime()).append(fieldSeparator);
                sb.append(System.lineSeparator());
            });
        });
        exchange.getIn().setBody(sb.toString());
        long numRecords = getReadySources().stream().collect(Collectors.summingLong(source -> source.getInterfaces().stream().filter(SnmpInterface::isTrace).count()));
        exchange.getIn().setHeader("countTraceRecords", numRecords);
        gaugeSources.submit("gauge.snmp.sources.traceRecords", (double)numRecords);
    }

    @SuppressWarnings("unchecked")
    @Handler
    public void exportChargingDataRecords(Exchange exchange) {
        if (getReadySources().isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        getReadySources().stream().forEach(source -> {
            source.getInterfaces().stream().filter(SnmpInterface::isChargeable).forEach(ifEntry -> {
                sb.append(source.getIpAddress()).append(fieldSeparator);
                for (String skey : (LinkedHashSet<String>)config.get("sourceTagKeys")) {
                    sb.append(source.getTags().get(skey).replace(fieldSeparator.charAt(0), '.')).append(fieldSeparator);
                }
                sb.append(ifEntry.getIfDescr()).append(fieldSeparator);
                for (String ikey : (LinkedHashSet<String>)config.get("interfaceTagKeys")) {
                    sb.append(ifEntry.getTags().get(ikey).replace(fieldSeparator.charAt(0), '.')).append(fieldSeparator);
                }
                //sb.append(ifEntry.getIfAlias().replace(fieldSeparator.charAt(0), '.')).append(fieldSeparator);
                if (ifEntry.getPortType() == SnmpConstants.EGRESS) {
                    sb.append(ifEntry.getPollOutOctets()).append(fieldSeparator);
                    sb.append(ifEntry.getPollInOctets()).append(fieldSeparator);
                } else {
                    sb.append(ifEntry.getPollInOctets()).append(fieldSeparator);
                    sb.append(ifEntry.getPollOutOctets()).append(fieldSeparator);
                }
                sb.append(timeStampFormat.format(source.getPollTime())).append(fieldSeparator);
                sb.append(source.getPollDuration()).append(fieldSeparator);
                sb.append((ifEntry.isUp()) ? 1 : 0);
                sb.append(System.lineSeparator());
            });
        });
        exchange.getIn().setBody(sb.toString());
        long numRecords = getReadySources().stream().collect(Collectors.summingLong(source -> source.getInterfaces().stream().filter(SnmpInterface::isChargeable).count()));
        exchange.getIn().setHeader("countChargingDataRecords", numRecords);
        gaugeSources.submit("gauge.snmp.sources.chargingRecords", (double)numRecords);
    }

    Function<SnmpSource, List<Object>> listChargingData = source -> {
        List<Object> answer = new ArrayList<Object>();
        answer.add(source.getIpAddress());
        source.getIftable().values().stream().filter(e -> e.isChargeable()).forEach(e -> {
            answer.add(e.getIfIndex());
            answer.add(e.getIfDescr());
            answer.add(e.getIfName());
            answer.add(e.getIfAlias());
            answer.add(e.getPollInOctets());
            answer.add(e.getPollOutOctets());
            answer.add(source.getPollTime());
            answer.add(source.getPollDuration());
        });
        return answer;
    };

    @Handler
    public List<List<Object>> exportListChargingData() {
        if (getReadySources().isEmpty()) {
            return null;
        }
        return getReadySources().stream().map(listChargingData).collect(Collectors.toList());
    }

    /**
     * rest handler to get source information
     * 
     * @param sourceIpAddr
     * @return Map source values to be converted to JSON object
     */
    @Handler
    public Map<String, Object> getSource(String sourceIpAddr) {
        SnmpSource source = sources.get(sourceIpAddr);
        if (source == null) {
            Map<String, Object> answer = new HashMap<String, Object>(1);
            answer.put("Status", "source " + sourceIpAddr + " not found");
            return answer;
        }
        return mapSource.apply(sources.get(sourceIpAddr));
    }

    @Handler
    public void updateSource(Exchange exchange) {
        String sourceIpAddr = exchange.getIn().getHeader("source", String.class);
        SnmpSource source = sources.get(sourceIpAddr);
        if (source == null) {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            exchange.getIn().setBody(null);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>)exchange.getIn().getBody(Map.class);
        if (data != null) {
            boolean updated = false;
            if (data.get("community") != null && !data.get("community").toString().isEmpty()) {
                source.setCommunity(data.get("community").toString());
                updated = true;
            }

            if (data.get("retries") != null) {
                source.setRetries((int)data.get("retries"));
                updated = true;
            }

            if (data.get("timeout") != null) {
                source.setTimeout((int)data.get("timeout"));
                updated = true;
            }

            if (data.get("tags") != null && data.get("tags") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> tag = (Map<String, String>)data.get("tags");
                source.addTags(tag);
                updated = true;
            }

            // commit
            if (updated) {
                sources.put(sourceIpAddr, source);
                exchange.getIn().setBody(Collections.singletonMap("Status", "success"));
            }
        }
    }

    @Handler
    public void updateSourceInterface(Exchange exchange) {
        String sourceIpAddr = exchange.getIn().getHeader("source", String.class);
        SnmpSource source = sources.get(sourceIpAddr);
        if (source == null) {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            exchange.getIn().setBody(null);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>)exchange.getIn().getBody(Map.class);
        if (data != null) {
            boolean updated = false;
            if (data.get("ifDescr") != null && data.get("ifDescr") instanceof String) {
                SnmpInterface ifEntry = source.getIftable().get(data.get("ifDescr"));
                if (ifEntry == null) {
                    exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
                    exchange.getIn().setBody(null);
                    return;
                }

                if (data.containsKey("trace")) {
                    ifEntry.setTrace((Boolean)data.get("trace"));
                    updated = true;
                }

                if (data.containsKey("chargeable")) {
                    ifEntry.setChargeable((Boolean)data.get("chargeable"));
                    updated = true;
                }

                if (data.containsKey("portType")) {
                    ifEntry.setPortType((int)data.get("portType"));
                    updated = true;
                }

                if (data.get("tags") != null && data.get("tags") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> tag = (Map<String, String>)data.get("tags");
                    ifEntry.addTags(tag);
                    updated = true;
                }
            }
            // batch update
            if (data.get("ifDescr") != null && data.get("ifDescr") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> batchIfList = (List<String>)data.get("ifDescr");
                if (data.containsKey("trace")) {
                    batchIfList.stream().forEach(ifdescr -> {
                        if (source.getIftable().containsKey(ifdescr)) {
                            source.getIftable().get(ifdescr).setTrace((Boolean)data.get("trace"));
                        }
                    });
                    updated = true;
                }
                if (data.containsKey("chargeable")) {
                    batchIfList.stream().forEach(ifdescr -> {
                        if (source.getIftable().containsKey(ifdescr)) {
                            source.getIftable().get(ifdescr).setChargeable((Boolean)data.get("chargeable"));
                        }
                    });
                    updated = true;
                }
                if (data.get("tags") != null && data.get("tags") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> tag = (Map<String, String>)data.get("tags");
                    batchIfList.stream().forEach(ifdescr -> {
                        if (source.getIftable().containsKey(ifdescr)) {
                            source.getIftable().get(ifdescr).addTags(tag);
                        }
                    });
                    updated = true;
                }
            }

            // commit
            if (updated) {
                exchange.getIn().setBody(Collections.singletonMap("Status", "success"));
                sources.put(sourceIpAddr, source);
            }
        }

    }

    @Handler
    public void removeSource(Exchange exchange) {
        String sourceIpAddr = exchange.getIn().getHeader("source", String.class);
        StringBuilder msg = new StringBuilder("source " + sourceIpAddr);
        if (sources.containsKey(sourceIpAddr)) {
            sources.remove(sourceIpAddr);
            msg.append(" deleted");
        } else {
            exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
            msg.append(" not found");
        }
        exchange.getIn().setBody(Collections.singletonMap("Status", msg.toString()));

        log.info(msg.toString());
    }

    public Boolean validateStartPoll() {
        if (!getReadySources().isEmpty()) {
            polltimer.restart();
            return true;
        }
        return false;
    }

    public String logEndPoll() {
        long polltime = polltimer.stop();
        gaugeSources.submit("gauge.snmp.polltime", (double)polltime);
        DoubleSummaryStatistics stats = getReadySources().stream().collect(Collectors.summarizingDouble(SnmpSource::getPollResponse));
        gaugeSources.submit("gauge.snmp.response.min", stats.getMin());
        gaugeSources.submit("gauge.snmp.response.max", stats.getMax());
        gaugeSources.submit("gauge.snmp.response.avg", stats.getAverage());
        gaugeSources.submit("gauge.snmp.sources.ready", (double)getReadySources().size());
        gaugeSources.submit("gauge.snmp.sources.down", (double)getDownSources().size());
        counterSources.increment("counter.snmp.poll");
        int totalCounters = getReadySources().stream().collect(Collectors.summingInt(s -> s.getInterfaces().size()));
        gaugeSources.submit("gauge.snmp.counters", (double)totalCounters);
        double cps = (totalCounters * 1000 / polltime);
        gaugeSources.submit("gauge.snmp.cps", cps);
        return String.format("completed in %d ms, collected %d counters from %d sources (%.2f cps)", polltime, totalCounters, getReadySources().size(), cps);
    }

}

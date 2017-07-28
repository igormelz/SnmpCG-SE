package org.openfs.snmpcg;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.stereotype.Service;
import org.openfs.snmpcg.model.SnmpInterface;
import org.openfs.snmpcg.model.SnmpSource;
import org.openfs.snmpcg.model.SnmpSourceStatus;

@Service("snmpSources")
public class SourceInventoryService {

	private static final Logger log = LoggerFactory.getLogger(SourceInventoryService.class);

	@Value("${snmpcg.snmpCommunity:public}")
	private String community;

	@Value("${snmpcg.snmpTimeout:5}")
	private int timeout;

	@Value("${snmpcg.snmpRetries:3}")
	private int retries;

	@Value("${snmpcg.persistFileName:none}")
	private String persistFileName;

	@Value("${snmpcg.cdrTimeStampFormat:yyyy-MM-dd HH:mm:ss}")
	private SimpleDateFormat timeStampFormat;

	@Value("${snmpcg.cdrFieldSeparator:;}")
	private String fieldSeparator;

	private Map<String, SnmpSource> sources = new ConcurrentHashMap<String, SnmpSource>();

	private final static Pattern IPADDR_PATTERN = Pattern.compile("\\d+.\\d+.\\d+.\\d+");
	
	private StopWatch polltimer = new StopWatch();
	private CounterService counterSources;
	private GaugeService gaugeSources;
	
	@Autowired
	public SourceInventoryService(GaugeService gaugeService, CounterService counterService) {
		this.counterSources = counterService;
		this.gaugeSources = gaugeService;
	}
	
	protected SnmpSource addSource(String ipaddr, String community) {
		if (!IPADDR_PATTERN.matcher(ipaddr).matches()) {
			log.error("bad format ip addr:", ipaddr);
			return null;
		}
		Address targetAddress = GenericAddress.parse("udp:" + ipaddr + "/161");
		return addSource(ipaddr, targetAddress, community);
	}

	protected SnmpSource addSource(String ipaddr, Address targetAddress, String community) {
		CommunityTarget target = createTarget(targetAddress, community,	retries, timeout);
		SnmpSource source = new SnmpSource(ipaddr, target);
		return sources.put(ipaddr, source);
	}

	@Handler
	public void addSource(Exchange exchange) {
	
		String host = exchange.getIn().getHeader("source", String.class);
		String sourceCommunity = this.community;	
		StringBuilder sb = new StringBuilder("source " + host);

		if (sources.containsKey(host)) {
			exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
			sb.append(" allready exists");
			exchange.getIn().setBody(Collections.singletonMap("Status", sb.toString()));
			return;
		} 
			
		if (exchange.getIn().getHeader("CamelHttpQuery") != null) {
				sourceCommunity = exchange.getIn().getHeader("CamelHttpQuery",
						String.class);
		} else if (exchange.getIn().getBody() != null) {
				@SuppressWarnings("unchecked")
				Map<String, String> mb = (Map<String, String>) exchange.getIn()
						.getBody(Map.class);
				if (mb != null && mb.containsKey("Community")) {
					sourceCommunity = mb.get("Community");
				}
		}
	
		addSource(host, sourceCommunity);
		sb.append(" add to next poll with community:").append(sourceCommunity);
		exchange.getIn().setBody(Collections.singletonMap("Status", sb.toString()));		
		log.info(sb.toString());
	}

	protected CommunityTarget createTarget(Address targetAddress,
			String community, int retries, int timeout) {
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString(community));
		target.setVersion(SnmpConstants.version2c);
		target.setAddress(targetAddress);
		target.setRetries(retries);
		target.setTimeout(timeout * 1000L);
		return target;
	}

	@Handler
	public List<SnmpSource> getReadySources() {
		return sources.values().stream().filter(info -> info.getStatus().isUp()).collect(Collectors.toList());
	}

	@Handler
	public List<SnmpSource> getDownSources() {
		return sources.values().stream().filter(source -> source.getStatus().isDown()).collect(Collectors.toList());
	}

	public void parse(String source, String delimiter) {

		if (source == null || source.isEmpty()) {
			return;
		}

		// parse ip<delimiter>community
		String[] values = source.trim().split(delimiter, 2);
		String parsedIpAddr = values[0];
		String parsedCommunity = (values.length > 1 && !values[1].isEmpty()) ? values[1]
				: community;

		if (!IPADDR_PATTERN.matcher(parsedIpAddr).matches()) {
			log.error("wrong IPADDR for source:" + parsedIpAddr);
			return;
		}

		// warn if source duplicate
		if (sources.containsKey(parsedIpAddr)) {
			log.warn("duplicate ipaddr: " + parsedIpAddr);
			return;
		}

		addSource(parsedIpAddr, parsedCommunity);
		log.info("parsed source {}, community [{}]", parsedIpAddr, parsedCommunity);
	}

	/**
	 * processing REST /api/sources?queryString
	 * 
	 * @param exchange
	 * @return ?up - list success ?down - list down ?size - up,down
	 */
	@Handler
	public void getSources(Exchange exchange) {

		// process query parameter status
		String status = exchange.getIn().getHeader("status", String.class);
		if (status != null && SnmpSourceStatus.isMember(status)) {
			SnmpSourceStatus qStatus = SnmpSourceStatus.valueOf(status
					.toUpperCase());
			exchange.getIn().setBody(sources.values().stream()
					.filter(e -> e.getStatus() == qStatus).map(mapSource)
					.collect(Collectors.toList()));
			return;
		}

		// process query string
		String queryString = exchange.getIn().getHeader("CamelHttpQuery",
				String.class);
		if (queryString != null && !queryString.isEmpty()) {
			int i = queryString.indexOf("&");
			if (i > 0) {
				queryString = queryString.substring(0, i);
			}

			if ("stats".equalsIgnoreCase(queryString)) {
				exchange.getIn().setBody(sources.values().stream()
						.collect(Collectors.groupingBy(SnmpSource::getStatus, Collectors.counting())));
				return;
			}
		}

		// return list sources
		exchange.getIn().setBody(sources.values().stream()
				.map(mapSource).collect(Collectors.toList()));
	}

	Function<SnmpSource, Map<String, Object>> mapSource = source -> {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("Ip", source.getIpAddress());
		map.put("Status", source.getStatus());
		map.put("sysUptime", source.getSysUptime());
		map.put("sysName", source.getSysName());
		map.put("sysDescr", source.getSysDescr());
		map.put("snmpCommunity", source.getTarget().getCommunity().toString());
		map.put("ifNumber", source.getIftable().size());
		map.put("pollTime", source.getPollTime());
		long counter_up = source.getInterfaces().stream().filter(SnmpInterface::isUp).count();
		map.put("statusUpCounter", counter_up);
		map.put("statusDownCounter", source.getIftable().size() - counter_up);
		map.put("chargeableCounter",source.getInterfaces().stream().filter(SnmpInterface::isChargeable).count());
		map.put("traceCounter",source.getInterfaces().stream().filter(SnmpInterface::isTrace).count());
		map.put("pollResponse", source.getPollResponse());
		return map;
	};

	@Handler
	public void getInterfaces(Exchange exchange) {
		List<Map<String, Object>> ifList = sources.values().stream().map(mapInterface).flatMap(List::stream).collect(Collectors.toList());
		
		String filter = exchange.getIn().getHeader("chargeable", String.class);
		if (filter != null) {
			exchange.getIn().setBody(ifList.stream().filter(e->(Boolean)e.get("chargeable")).collect(Collectors.toList()));
			return;	
		}
		
		filter = exchange.getIn().getHeader("trace", String.class);
		if (filter != null) {
			exchange.getIn().setBody(ifList.stream().filter(e->(Boolean)e.get("trace")).collect(Collectors.toList()));
			return;	
		}
		
		filter = exchange.getIn().getHeader("stats", String.class);
		if (filter != null) {
			Map<String,Long> answer = new HashMap<String,Long>(3);
			answer.put("traceCount", ifList.stream().filter(e->(Boolean)e.get("trace")).count());
			answer.put("chargeableCount", ifList.stream().filter(e->(Boolean)e.get("chargeable")).count());
			answer.put("ifnumberCount", (long)ifList.size());
			exchange.getIn().setBody(answer);
			return;	
		}
		
		
		exchange.getIn().setBody(ifList);
	}
			
	Function<SnmpSource, List<Map<String, Object>>> mapInterface = source -> {
		List<Map<String, Object>> answer = source.getInterfaces().stream()
				.map(e -> {
					Map<String, Object> iface = new HashMap<String, Object>();
					iface.put("Ip", source.getIpAddress());
					iface.put("sysName", source.getSysName());
					iface.put("pollTime", source.getPollTime());
					iface.put("pollDuration", source.getPollDuration());
					iface.put("ifindex", e.getIfIndex());
					iface.put("ifdescr", e.getIfDescr());
					iface.put("ifalias", e.getIfAlias());
					iface.put("chargeable", e.isChargeable());
					iface.put("trace", e.isTrace());
					iface.put("up", e.isUp());
					iface.put("pollInOctets", e.getPollInOctets());
					iface.put("pollOutOctets", e.getPollOutOctets());
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
				exchange.getIn().setBody(ifTable.stream().filter(i->i.isChargeable()).collect(Collectors.toList()));
			} else {
				exchange.getIn().setBody(ifTable.stream().filter(i->!i.isChargeable()).collect(Collectors.toList()));
			}
			return;
		}
		// filter trace=on (off)
		filter = exchange.getIn().getHeader("trace", String.class);
		if (filter != null) {
			if ("on".equalsIgnoreCase(filter)) {
				exchange.getIn().setBody(ifTable.stream().filter(i->i.isTrace()).collect(Collectors.toList()));
			} else {
				exchange.getIn().setBody(ifTable.stream().filter(i->!i.isTrace()).collect(Collectors.toList()));
			}
			return;
		}
		// filter status = up (down)
		filter = exchange.getIn().getHeader("status", String.class);
		if (filter != null) {
			if ("up".equalsIgnoreCase(filter)) {
				exchange.getIn().setBody(ifTable.stream().filter(i->i.isUp()).collect(Collectors.toList()));
			} else {
				exchange.getIn().setBody(ifTable.stream().filter(i->i.isDown()).collect(Collectors.toList()));
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
		getReadySources().stream().forEach(source ->{
			source.getInterfaces().stream().filter(SnmpInterface::isTrace).forEach(ifEntry->{
				sb.append(timeStampFormat.format(source.getPollTime())).append(fieldSeparator);
				sb.append(source.getIpAddress()).append(fieldSeparator);
				sb.append(ifEntry.getIfIndex()).append(fieldSeparator);
				sb.append(ifEntry.getIfDescr()).append(fieldSeparator);
				sb.append(ifEntry.getIfName()).append(fieldSeparator);
				sb.append(ifEntry.getIfAlias().replace(fieldSeparator.charAt(0),'.')).append(fieldSeparator);
				sb.append(ifEntry.getIfAdminStatus()).append(fieldSeparator);
				sb.append(ifEntry.getIfOperStatus()).append(fieldSeparator);
				sb.append(ifEntry.getIfInOctets()).append(fieldSeparator);
				sb.append(ifEntry.getIfOutOctets()).append(fieldSeparator);
				sb.append(source.getSysUptime()).append(fieldSeparator);
				sb.append(System.lineSeparator());
			});
		});
		exchange.getIn().setBody(sb.toString());
		long numRecords = getReadySources().stream().collect(Collectors.summingLong(source->source.getInterfaces().stream().filter(SnmpInterface::isTrace).count()));
		exchange.getIn().setHeader("countTraceRecords", numRecords );
		gaugeSources.submit("gauge.snmp.sources.traceRecords",(double)numRecords);
	}
	
	@Handler
	public void exportChargingDataRecords(Exchange exchange) {
		if (getReadySources().isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		getReadySources().stream().forEach(source ->{
			source.getInterfaces().stream().filter(SnmpInterface::isChargeable).forEach(ifEntry->{
				sb.append(source.getIpAddress()).append(fieldSeparator);
				sb.append(ifEntry.getIfIndex()).append(fieldSeparator);
				sb.append(ifEntry.getIfDescr()).append(fieldSeparator);
				sb.append(ifEntry.getIfName()).append(fieldSeparator);
				sb.append(ifEntry.getIfAlias().replace(fieldSeparator.charAt(0),'.')).append(fieldSeparator);
				sb.append(ifEntry.getPollInOctets()).append(fieldSeparator);
				sb.append(ifEntry.getPollOutOctets()).append(fieldSeparator);
				sb.append(timeStampFormat.format(source.getPollTime())).append(fieldSeparator);
				sb.append(source.getPollDuration()).append(fieldSeparator);
				sb.append((ifEntry.isUp())?1:0);
				sb.append(System.lineSeparator());
			});
		});
		exchange.getIn().setBody(sb.toString());
		long numRecords = getReadySources().stream().collect(Collectors.summingLong(source->source.getInterfaces().stream().filter(SnmpInterface::isChargeable).count()));
		exchange.getIn().setHeader("countChargingDataRecords", numRecords);
		gaugeSources.submit("gauge.snmp.sources.chargingRecords",(double)numRecords);
	}

	Function<SnmpSource, List<Object>> listChargingData = source -> {
		List<Object> answer = new ArrayList<Object>();
		answer.add(source.getIpAddress());
		source.getIftable().values().stream().filter(e -> e.isChargeable())
			.forEach(e -> {
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
		return getReadySources().stream().map(listChargingData)
				.collect(Collectors.toList());
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
	public void updateSourceInterface(Exchange exchange) { 
		String sourceIpAddr = exchange.getIn().getHeader("source", String.class);
		SnmpSource source = sources.get(sourceIpAddr);
		if (source == null) {
			exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
			exchange.getIn().setBody(null);
			return;
		}
		
		if (exchange.getIn().getBody() != null) {
			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) exchange.getIn().getBody(Map.class);
			if (data != null && data.containsKey("ifDescr")) {
				Map<String, Object> answer = new HashMap<String, Object>(1);
				
				if (data.get("ifDescr") instanceof String) {
					SnmpInterface ifEntry = source.getIftable().get(data.get("ifDescr"));
					if (ifEntry == null) {
						exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
						exchange.getIn().setBody(null);
						return;
					}
				
					if (data.containsKey("trace")) {
						ifEntry.setTrace((Boolean)data.get("trace"));
						answer.put("Status", "interface " + ifEntry.getIfDescr() + " tracing " + ifEntry.isTrace());
						exchange.getIn().setBody(answer);
						return;
					}
				
					if (data.containsKey("chargeable")) {
						ifEntry.setChargeable((Boolean)data.get("chargeable"));
						answer.put("Status", "interface " + ifEntry.getIfDescr() + " charging " + ifEntry.isChargeable());
						exchange.getIn().setBody(answer);
						return;
					}
				} else if (data.get("ifDescr") instanceof List) {
					@SuppressWarnings("unchecked")
					List<String> batchIfList = (List<String>)data.get("ifDescr");
					if (data.containsKey("trace")) {
						batchIfList.stream().forEach(ifdescr -> {
							if (source.getIftable().containsKey(ifdescr)) {
								source.getIftable().get(ifdescr).setTrace((Boolean)data.get("trace"));
							}
						});
						exchange.getIn().setBody(Collections.singletonMap("Status","Total " + batchIfList.size() + " interfaces charging " + (Boolean)data.get("trace")));
						return;
					}
					if (data.containsKey("chargeable")) {
						batchIfList.stream().forEach(ifdescr -> {
							if (source.getIftable().containsKey(ifdescr)) {
								source.getIftable().get(ifdescr).setChargeable((Boolean)data.get("chargeable"));
							}
						});
						exchange.getIn().setBody(Collections.singletonMap("Status", "Total " + batchIfList.size() + " interfaces charging " + (Boolean)data.get("chargeable")));
						return;
					}
				}
			}
		}
	}
	
	@Handler
	public void removeSource(Exchange exchange) {
		String sourceIpAddr = exchange.getIn()
				.getHeader("source", String.class);
		StringBuilder msg = new StringBuilder("source " + sourceIpAddr);
		if (sources.containsKey(sourceIpAddr)) {
			sources.remove(sourceIpAddr);
			msg.append(" deleted");
		} else {
			exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
			msg.append(" not found");
		}
		exchange.getIn().setBody(Collections.singletonMap("Status", msg.toString()));
		
		// save changes to recovery
		setRecoveryState();
		
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
		DoubleSummaryStatistics  stats = getReadySources().stream().collect(Collectors.summarizingDouble(SnmpSource::getPollResponse));
		gaugeSources.submit("gauge.snmp.response.min",stats.getMin());
		gaugeSources.submit("gauge.snmp.response.max",stats.getMax());
		gaugeSources.submit("gauge.snmp.response.avg",stats.getAverage());
		gaugeSources.submit("gauge.snmp.sources.ready", (double)getReadySources().size());
		gaugeSources.submit("gauge.snmp.sources.down", (double)getDownSources().size());
		counterSources.increment("counter.snmp.poll");
		int totalCounters = getReadySources().stream().collect(Collectors.summingInt(s->s.getInterfaces().size()));
		return String.format("completed in %d ms, collected %d counters from %d sources (%.2f cps)", 
				polltime, totalCounters, getReadySources().size(), (double)(totalCounters*1000/polltime));
	}
	
	@SuppressWarnings("unchecked")
	@PostConstruct
	protected void getRecoveryState() {

		if (persistFileName == null || persistFileName.equalsIgnoreCase("none"))
			return;

		File recoveryFile = new File(persistFileName);
		if (recoveryFile.exists() && recoveryFile.canRead()
				&& sources.isEmpty()) {
			try (FileInputStream fis = new FileInputStream(recoveryFile);
					ObjectInput input = new ObjectInputStream(
							new BufferedInputStream(fis));) {
				sources = (Map<String, SnmpSource>) input.readObject();
			} catch (ClassNotFoundException ex) {
				log.error("get recovery", ex);
				return;
			} catch (IOException e) {
				log.error("error read recoveryFile " + persistFileName, e);
				return;
			}
			log.info("read recoveryState from file: " + persistFileName);
		}
	}

	/**
	 * write sources to recovery file
	 */
	@Handler
	public synchronized void setRecoveryState() {

		if (persistFileName == null || persistFileName.equalsIgnoreCase("none"))
			return;

		if (sources.isEmpty())
			return;

		File recoveryFile = new File(persistFileName);
		File tmpFile = new File(persistFileName + ".tmp");
		if (tmpFile.exists()) {
			log.warn("remove abnormal temp file {}", tmpFile.getName());
			tmpFile.delete();
		}

		try (FileOutputStream fos = new FileOutputStream(tmpFile);
				ObjectOutput output = new ObjectOutputStream(
						new BufferedOutputStream(fos));) {
			output.writeObject(sources);
			output.flush();
			fos.getFD().sync();
			output.close();
			if (!FileUtil.renameFile(tmpFile, recoveryFile, true)) {
				log.error("can not rename temp file");
			}
		} catch (IOException e) {
			log.error("set recovery exception:", e);
			return;
		}
		log.info("store recoveryState to file: " + persistFileName);
	}

}

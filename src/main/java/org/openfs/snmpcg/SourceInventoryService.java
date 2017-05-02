package org.openfs.snmpcg;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.openfs.snmpcg.model.SnmpSource;
import org.openfs.snmpcg.model.SnmpSourceStatus;

@Service("sources")
public class SourceInventoryService {

	private static final Logger log = LoggerFactory
			.getLogger(SourceInventoryService.class);

	@Value("${snmpcg.snmp.community:public}")
	private String community;

	@Value("${snmpcg.snmp.timeout:3}")
	private int timeout;

	@Value("${snmpcg.snmp.retries:3}")
	private int retries;

	@Value("${snmpcg.persistFileName:none}")
	private String persistFileName;

	@Value("${snmpcg.TimeStampFormat:yyyyMMddHHmmss}")
	private SimpleDateFormat timeStampFormat;

	private final Map<String, SnmpSource> sources = new ConcurrentHashMap<String, SnmpSource>();

	protected SnmpSource addSource(String ipaddr, String community) {
		Address targetAddress = GenericAddress.parse("udp:" + ipaddr + "/161");
		return addSource(ipaddr, targetAddress, community);
	}

	protected SnmpSource addSource(String ipaddr, Address targetAddress,
			String community) {
		CommunityTarget target = createTarget(targetAddress, community,
				retries, timeout);
		SnmpSource source = new SnmpSource(ipaddr, target);
		return sources.put(ipaddr, source);
	}

	public List<SnmpSource> getSourcesByStatus(SnmpSourceStatus status) {
		List<SnmpSource> answer = sources.values().stream()
				.filter(info -> info.getStatus() == status)
				.collect(Collectors.toList());
		return answer;
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
		return getSourcesByStatus(SnmpSourceStatus.SUCCESS);
	}

	@Handler
	public List<SnmpSource> getDownSources() {
		List<SnmpSource> answer = sources.values().stream()
			// filter success and no snmp
			.filter(source -> source.getStatus() != SnmpSourceStatus.SUCCESS)
			.filter(source -> source.getStatus() != SnmpSourceStatus.NO_PDU)
			.collect(Collectors.toList());
		return answer;
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

		if (!Pattern.matches("\\d+.\\d+.\\d+.\\d+", parsedIpAddr)) {
			log.error("wrong IPADDR for source:" + parsedIpAddr);
			return;
		}

		// warn if source duplicate
		if (sources.containsKey(parsedIpAddr)) {
			log.warn("duplicate ipaddr: " + parsedIpAddr);
			return;
		}

		addSource(parsedIpAddr, parsedCommunity);
		log.info("parsed source {}, community [{}]", parsedIpAddr,
				parsedCommunity);
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
			List<Map<String, Object>> answer = sources.values().stream()
					.filter(e -> e.getStatus() == qStatus).map(mapSource)
					.collect(Collectors.toList());
			exchange.getIn().setBody(answer);
			return;
		}

		// process query string
		String queryString = exchange.getIn().getHeader("CamelHttpQuery",String.class);
		if (queryString != null && !queryString.isEmpty()) {
			int i = queryString.indexOf("&");
			if (i > 0) {
				queryString = queryString.substring(0, i);
			}

			if ("stats".equalsIgnoreCase(queryString)) {
				Map<SnmpSourceStatus, Long> stats = sources.values().stream()
					.collect(Collectors.groupingBy(SnmpSource::getStatus, Collectors.counting()));
				exchange.getIn().setBody(stats);
				return;
			}
		}

		// return list sources
		List<Map<String, Object>> answer = sources.values().stream()
			.map(mapSource).collect(Collectors.toList());
		exchange.getIn().setBody(answer);
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
		return map;
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
		exchange.getIn().setBody(sources.get(source).getInterfaces());
	}

	Function<SnmpSource, String> printCounters = source -> {
		return source.getIftable().values().stream()
				.filter(e -> e.isPolling() && e.isTrace())
				.map(e -> {
					return String.format("%s,%d,%s,%s,%s,%d,%d,%d,%d,%s", 
							source.getIpAddress(),
							e.getIfIndex(),
							e.getIfDescr(),
							e.getIfName(),
							e.getIfAlias(),
							e.getIfAdminStatus(),
							e.getIfOperStatus(),
							e.getIfInOctets().getValue(),
							e.getIfOutOctets().getValue(), 
							timeStampFormat.format(source.getPollTime()));
				}).collect(Collectors.joining("\n"));
	};

	@Handler
	public String exportCounters() {
		if (getReadySources().isEmpty()) {
			return null;
		}
		String exportData = getReadySources().stream().map(printCounters)
				.collect(Collectors.joining());
		return exportData.isEmpty() ? null : exportData;
	}

	Function<SnmpSource, String> printChargingData = source -> {
		return source.getIftable().values().stream()
				// print polling and interface is up
				.filter(e -> e.isPolling() && e.getIfAdminStatus() == 1 && e.getIfOperStatus() == 1)
				.map(e -> {
					return String.format("%s,%d,%s,%s,%s,%d,%d,%d,%s,%d",
							source.getIpAddress(),
							e.getIfIndex(),
							e.getIfDescr(),
							e.getIfName(),
							e.getIfAlias(),
							e.getIfAdminStatus(),
							e.getPollInOctets(),
							e.getPollOutOctets(),
							timeStampFormat.format(source.getPollTime()),
							source.getPollDuration());
				}).collect(Collectors.joining("\n"));
	};

	@Handler
	public String exportChargingData() {
		if (getReadySources().isEmpty()) {
			return null;
		}
		return getReadySources().stream().map(printChargingData)
				.collect(Collectors.joining("\n"));
	}

	/**
	 * rest handler to get source information
	 * 
	 * @param sourceIpAddr
	 * @return Map source values to be converted to JSON object
	 */
	@Handler
	public Map<String, Object> getSource(String sourceIpAddr) {
		return mapSource.apply(sources.get(sourceIpAddr));
	}

	@Handler
	public void addSource(Exchange exchange) {

		String host = exchange.getIn().getHeader("source", String.class);
		String sourceCommunity = this.community;

		StringBuilder sb = new StringBuilder("source " + host);

		if (sources.containsKey(host)) {

			exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
			sb.append(" allready exists");

		} else {

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
			sb.append(" add to poll with community:").append(community);
		}
		Map<String, String> answer = new HashMap<String, String>(1);
		answer.put("Status", sb.toString());
		exchange.getIn().setBody(answer);
		log.info("Admin UI: {}", sb.toString());
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
		Map<String, String> answer = new HashMap<String, String>();
		answer.put("Status", msg.toString());
		exchange.getIn().setBody(answer);
		log.info("Admin UI: {}", msg.toString());
	}

	@PostConstruct
	protected void getRecoveryState() {

		if (persistFileName == null || persistFileName.equalsIgnoreCase("none"))
			return;

		File recoveryFile = new File(persistFileName);
		if (recoveryFile.exists() && recoveryFile.canRead()
				&& sources.isEmpty()) {
			try (Stream<String> stream = Files
					.lines(Paths.get(persistFileName))) {
				stream.forEach(line -> {
					// split record ip|community|status|sysName
					String elements[] = line.split("\\|");
					if (elements.length > 1 && !elements[0].isEmpty()
							&& !elements[1].isEmpty()) {
						addSource(elements[0], elements[1]);
					}
				});
			} catch (IOException e) {
				log.error("error read recoveryFile " + persistFileName, e);
				return;
			}
			log.info("read recoveryState from " + persistFileName);
		}
	}

	/**
	 * write sources to recovery file
	 */
	@Handler
	public void setRecoveryState() {

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

		try {
			PrintStream os = new PrintStream(tmpFile);
			sources.values().stream().forEach(s -> os.println(s));
			os.flush();
			os.close();

			if (!FileUtil.renameFile(tmpFile, recoveryFile, true)) {
				log.warn("can not rename temp file");
			}
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		log.info("store recoveryState to " + persistFileName);
	}

}

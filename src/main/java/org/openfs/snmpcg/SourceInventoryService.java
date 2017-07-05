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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.openfs.snmpcg.model.SnmpInterface;
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

	@Value("${snmpcg.cdr.TimeStampFormat:yyyy-MM-dd HH:mm:ss}")
	private SimpleDateFormat timeStampFormat;

	@Value("${snmpcg.cdr.FieldSeparator:;}")
	private String fieldSeparator;

	private Map<String, SnmpSource> sources = new ConcurrentHashMap<String, SnmpSource>();

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
		return sources.values().stream()
				.filter(info -> info.getStatus().isUp())
				.collect(Collectors.toList());
	}

	@Handler
	public List<SnmpSource> getDownSources() {
		return sources.values().stream()
				.filter(source -> source.getStatus().isDown())
				.collect(Collectors.toList());
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
		String queryString = exchange.getIn().getHeader("CamelHttpQuery",
				String.class);
		if (queryString != null && !queryString.isEmpty()) {
			int i = queryString.indexOf("&");
			if (i > 0) {
				queryString = queryString.substring(0, i);
			}

			if ("stats".equalsIgnoreCase(queryString)) {
				Map<SnmpSourceStatus, Long> stats = sources
						.values()
						.stream()
						.collect(
								Collectors.groupingBy(SnmpSource::getStatus,
										Collectors.counting()));
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
		long counter_up = source.getInterfaces().stream()
				.filter(SnmpInterface::isUp).count();
		map.put("pollStatusUp", counter_up);
		map.put("pollStatusDown", source.getIftable().size() - counter_up);
		return map;
	};

	@Handler
	public void getChargingInterfaces(Exchange exchange) {
		exchange.getIn().setBody(sources.values().stream().map(mapChargingData).flatMap(List::stream)
				.collect(Collectors.toList()));
	}

	Function<SnmpSource, List<Map<String, Object>>> mapChargingData = source -> {
		List<Map<String, Object>> answer = source.getInterfaces()
				.stream()
				.filter(e -> e.isPolling() && e.getIfAdminStatus() == 1
						&& e.getIfOperStatus() == 1)
				.map(e -> {
					Map<String, Object> iface = new HashMap<String, Object>();
					iface.put("Ip", source.getIpAddress());
					iface.put("sysName", source.getSysName());
					iface.put("ifindex", e.getIfIndex());
					iface.put("ifdescr", e.getIfDescr());
					iface.put("ifalias", e.getIfAlias());
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

		// process query parameter status
		String status = exchange.getIn().getHeader("pollStatus", String.class);
		if (status != null) {
			if ("up".equalsIgnoreCase(status)) {
				exchange.getIn().setBody(
						sources.get(source)
								.getInterfaces()
								.stream()
								.filter(e -> e.getIfAdminStatus() == 1
										&& e.getIfOperStatus() == 1)
								.collect(Collectors.toList()));
			} else {
				exchange.getIn().setBody(
						sources.get(source)
								.getInterfaces()
								.stream()
								.filter(e -> (e.getIfAdminStatus() != 1 || e
										.getIfOperStatus() != 1))
								.collect(Collectors.toList()));
			}
			return;
		}
		exchange.getIn().setBody(sources.get(source).getInterfaces());
	}

	Function<SnmpSource, String> formatTraceRecord = source -> {
		return source
				.getInterfaces()
				.stream()
				.filter(e -> e.isTrace())
				.map(e -> {
					StringBuilder sb = new StringBuilder();
					sb.append(timeStampFormat.format(source.getPollTime()))
							.append(fieldSeparator);
					sb.append(source.getIpAddress()).append(fieldSeparator);
					sb.append(e.getIfIndex()).append(fieldSeparator);
					sb.append(e.getIfDescr()).append(fieldSeparator);
					sb.append(e.getIfName()).append(fieldSeparator);
					sb.append(
							e.getIfAlias().replace(fieldSeparator.charAt(0),
									'.')).append(fieldSeparator);
					sb.append(e.getIfAdminStatus()).append(fieldSeparator);
					sb.append(e.getIfOperStatus()).append(fieldSeparator);
					sb.append(e.getIfInOctets()).append(fieldSeparator);
					sb.append(e.getIfOutOctets()).append(fieldSeparator);
					sb.append(source.getSysUptime()).append(fieldSeparator);
					return sb.toString();
				}).collect(Collectors.joining("\n"));
	};

	Function<SnmpSource, String> formatChargingDataRecord = source -> {
		return source.getInterfaces()
				.stream()
				// print polling and interface is up
				.filter(SnmpInterface::isUp)
				.map(e -> {
					StringBuilder sb = new StringBuilder();
					sb.append(source.getIpAddress()).append(fieldSeparator);
					sb.append(e.getIfIndex()).append(fieldSeparator);
					sb.append(e.getIfDescr()).append(fieldSeparator);
					sb.append(e.getIfName()).append(fieldSeparator);
					sb.append(
							e.getIfAlias().replace(fieldSeparator.charAt(0),
									'.')).append(fieldSeparator);
					sb.append(e.getPollInOctets()).append(fieldSeparator);
					sb.append(e.getPollOutOctets()).append(fieldSeparator);
					sb.append(timeStampFormat.format(source.getPollTime()))
							.append(fieldSeparator);
					sb.append(source.getPollDuration());
					return sb.toString();
				}).collect(Collectors.joining("\n"));
	};

	@Handler
	public String exportTraceRecords() {
		if (getReadySources().isEmpty()) {
			return null;
		}
		String exportData = getReadySources().stream().map(formatTraceRecord)
				.collect(Collectors.joining("\n"));
		return exportData.isEmpty() ? null : exportData;
	}

	@Handler
	public String exportChargingDataRecords() {
		if (getReadySources().isEmpty()) {
			return null;
		}
		return getReadySources().stream().map(formatChargingDataRecord)
				.collect(Collectors.joining("\n"));
	}

	Function<SnmpSource, List<Object>> listChargingData = source -> {
		List<Object> answer = new ArrayList<Object>();
		answer.add(source.getIpAddress());
		source.getIftable()
				.values()
				.stream()
				.filter(e -> e.isPolling() && e.getIfAdminStatus() == 1
						&& e.getIfOperStatus() == 1).forEach(e -> {
					answer.add(e.getIfIndex());
					answer.add(e.getIfDescr());
					answer.add(e.getIfName());
					answer.add(e.getIfAlias());
					answer.add(e.getPollInOctets());
					answer.add(e.getPollOutOctets());
					answer.add(timeStampFormat.format(source.getPollTime()));
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
			sb.append(" add to poll with community:").append(sourceCommunity);
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

		// save changes to recovery
		setRecoveryState();
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

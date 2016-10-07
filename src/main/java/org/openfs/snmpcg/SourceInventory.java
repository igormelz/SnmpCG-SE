package org.openfs.snmpcg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.smi.IpAddress;

import org.openfs.snmpcg.sources.PollStatus;
import org.openfs.snmpcg.sources.SnmpStatus;
import org.openfs.snmpcg.utils.SnmpTargetFactory;

public class SourceInventory {

	private static final Logger log = LoggerFactory
			.getLogger(SourceInventory.class);
	private String community = "public";
	private int timeout = 5;
	private int retries = 3;
	private String recoveryFileName;

	// cache sources (ip:status)
	// private ConcurrentHashMap<String, SnmpStatus> srcData = new
	// ConcurrentHashMap<String, SnmpStatus>(300);
	private ConcurrentHashMap<String, Map<String, Object>> srcData = new ConcurrentHashMap<String, Map<String, Object>>();

	public void setCommunity(String community) {
		this.community = community;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public void setRetries(int retries) {
		this.retries = retries;
	}

	public void setRecoveryFileName(String f) {
		recoveryFileName = f;
	}

	/**
	 * parsed sources from file
	 * 
	 * @param ex
	 */
	@Handler
	public void initSrcData(Exchange ex) {
		String src = ex.getIn().getBody(String.class);
		String lines[] = src.split("\n");

		List<CommunityTarget> answer = new ArrayList<CommunityTarget>();
		StringBuilder sb = new StringBuilder();

		for (String line : lines) {

			// skip empty
			if ((line = line.trim()).length() == 0)
				continue;

			// split as ip|community
			String [] device = line.split("\\|",2);

			
			// validate ipaddr
			IpAddress ip = new IpAddress(); // 0.0.0.0
			// if (!ip.parseAddress(line)) {
			if (!ip.parseAddress(device[0])) {
				log.warn("source (" + device[0] + ") incorrect ipaddr");
				sb.append(device[0]).append("|").append("ERROR: invalid ipaddr")
						.append("\n");
				continue;
			}

			// check if src in cache
			if (srcData.containsKey(ip.toString())) {
				log.warn("source (" + device[0] + ") already exists");
				sb.append(device[0]).append("|").append("WARN: already exists").append("\n");
				continue;
			}

			// put to cache
			// srcData.put(ip.toString(), SnmpStatus.UNKNOWN);
			Map<String, Object> status = new HashMap<String, Object>();
			status.put("Status", SnmpStatus.UNKNOWN);
			status.put("HostName", ip.getInetAddress().getHostName());
			// put to validate
			if(device.length == 2 && !"".equalsIgnoreCase(device[1])) 		
				status.put("Community", device[1]);
			else 
				status.put("Community", community);
			
			srcData.put(ip.toString(), status);
			sb.append(device[0]).append("|").append("INFO: success parsed")
					.append("\n");

			// put to validate
			if(device.length == 2 && !"".equalsIgnoreCase(device[1])) 
				answer.add(SnmpTargetFactory.createTarget(ip.getInetAddress(), device[1], retries, timeout));
			else 
				answer.add(SnmpTargetFactory.createTarget(ip.getInetAddress(), community, retries, timeout));

		}
		ex.getIn().setHeader(CamelHeaders.SIZE, answer.size());
		ex.getIn().setHeader(CamelHeaders.LIST, answer);
		ex.getIn().setBody(sb.toString());
	}

	/**
	 * collect running source to poll counter
	 * 
	 * @param ex
	 */
	@Handler
	public void getUpSources(Exchange ex) {
		List<CommunityTarget> answer = new ArrayList<CommunityTarget>();
		for (String host : srcData.keySet())
			if (srcData.get(host).get("Status") == SnmpStatus.SUCCESS) {
				IpAddress ip = new IpAddress(host);
				// answer.add(SnmpTargetFactory.createTarget(ip.getInetAddress(),community, retries, timeout));
				answer.add(SnmpTargetFactory.createTarget(ip.getInetAddress(), srcData.get(host).get("Community").toString(), retries, timeout));
			}

		ex.getIn().setHeader(CamelHeaders.SIZE, answer.size());
		ex.getIn().setHeader(CamelHeaders.LIST, answer);
	}

	/**
	 * collect source to validate status
	 * 
	 * @param ex
	 */
	@Handler
	public void getDownSources(Exchange ex) {
		List<CommunityTarget> answer = new ArrayList<CommunityTarget>();
		for (String host : srcData.keySet())
			if (srcData.get(host).get("Status") != SnmpStatus.SUCCESS) {
				IpAddress ip = new IpAddress(host);
				//answer.add(SnmpTargetFactory.createTarget(ip.getInetAddress(),community, retries, timeout));
				answer.add(SnmpTargetFactory.createTarget(ip.getInetAddress(), srcData.get(host).get("Community").toString(), retries, timeout));
			}
		ex.getIn().setHeader(CamelHeaders.SIZE, answer.size());
		ex.getIn().setHeader(CamelHeaders.LIST, answer);
	}

	@Handler
	public boolean isEmptySourceList(@Header(CamelHeaders.LIST) List<?> srcList) {
		return srcList.isEmpty();
	}

	/**
	 * validate uptime & update status
	 * 
	 * @param host
	 * @param status
	 * @param sysUptime
	 */
	@Handler
	public void validateSnmpStatus(@Header(CamelHeaders.HOST) String host,
			@Header(CamelHeaders.STATUS) SnmpStatus status,
			@Header(CamelHeaders.UPTIME) long sysUptime) {

		if (srcData.containsKey(host)) {

			// check if reboot
			if (srcData.get(host).get("Status") == SnmpStatus.SUCCESS
					&& status == SnmpStatus.SUCCESS
					&& srcData.get(host).containsKey("Uptime")) {

				long last = (Long) srcData.get(host).get("Uptime");
				if (sysUptime < last) {
					log.warn("source (" + host
							+ ") rebooted or overflow timer: last uptime ("
							+ last + ") current uptime (" + sysUptime + ")");
				}
			}

			// update host:status
			srcData.get(host).put("Status", status);

			// update host:uptime if success
			if (status == SnmpStatus.SUCCESS)
				srcData.get(host).put("Uptime", sysUptime);
		}
	}

	/**
	 * print validate log to file
	 * 
	 * @param msg
	 * @throws Exception
	 */
	@Handler
	public void logValidate(Exchange msg) throws Exception {

		@SuppressWarnings("unchecked")
		Map<String, PollStatus> iftable = msg.getIn().getBody(Map.class);

		StringBuilder sb = new StringBuilder();
		for (String host : iftable.keySet()) {
			// Map<String, Object> last = srcData.get(host);

			sb.append(host).append("|");
			if (iftable.get(host) != null) {
				sb.append("UP").append("|");

				PollStatus item = iftable.get(host);

				// update uptime:
				// last.put("Uptime", item.getSysUptime());

				sb.append(item.getIfNumer()).append("|")
						.append(item.getSysUptime());
			} else {
				sb.append("DOWN").append("|");
			}
			sb.append("\n");
		}
		msg.getIn().setBody(sb.toString());
		setRecovery();
	}

	/**
	 * processing REST /api/sources?queryString optional queryString processed
	 * return: ?up - list success ?down - list down ?size - up,down
	 * 
	 * @param ex
	 */
	@Handler
	public void restGetSources(Exchange ex) {
		String queryString = ex.getIn().getHeader("CamelHttpQuery",
				String.class);

		if ("stats".equalsIgnoreCase(queryString)) {
			Map<String, Integer> size = new HashMap<String, Integer>();
			for (SnmpStatus st : SnmpStatus.values()) {
				int counter = 0;

				for (String src : srcData.keySet()) {
					if (srcData.get(src).get("Status") != st)
						continue;
					counter++;
				}
				size.put(st.name(), counter);
			}
			ex.getIn().setBody(size);

		} else if (SnmpStatus.isMember(queryString)) {
			SnmpStatus qStatus = SnmpStatus.valueOf(queryString.toUpperCase());
			List<Object> answer = new ArrayList<Object>();
			for (String ip : srcData.keySet())
				if (srcData.get(ip).get("Status") == qStatus) {
					Map<String, Object> entry = new HashMap<String, Object>();
					entry.put("Ip", ip);
					entry.putAll(srcData.get(ip));
					// entry.put("Status",((SnmpStatus)
					// srcData.get(ip).get("Status")).name());
					answer.add(entry);
				}
			// answer.add(src);
			ex.getIn().setBody(answer);

		} else {
			// [{"Ip":"192.169.103.8",
			// "Status":"UNKNOWN"},{"Ip":"192.168.103.149", "Status":"SUCCESS"}]
			List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();
			for (String ip : srcData.keySet()) {
				Map<String, Object> entry = new HashMap<String, Object>();
				entry.put("Ip", ip);
				entry.putAll(srcData.get(ip));
				// for (String k : srcData.get(ip).keySet()) entry.put(k,
				// srcData.get(ip).get(k).toString());
				// entry.put("Status",((SnmpStatus)
				// srcData.get(ip).get("Status")).name());
				answer.add(entry);
			}
			ex.getIn().setBody(answer);
			// ex.getIn().setBody(srcData);
		}
	}

	@Handler
	public void restGetSource(Exchange ex) {
		String ip = ex.getIn().getHeader("source", String.class);
		// List<Object> answer = new ArrayList<Object>();
		Map<String, String> entry = new HashMap<String, String>();
		if (srcData.containsKey(ip)) {
			// Map<String, String> entry = new HashMap<String, String>();
			entry.put("Ip", ip);
			SnmpStatus status = (SnmpStatus) srcData.get(ip).get("Status");
			entry.put("Status", status.name());
			if (status == SnmpStatus.SUCCESS
					&& srcData.get(ip).containsKey("Uptime"))
				entry.put("Uptime", srcData.get(ip).get("Uptime").toString());
			// answer.add(entry);
			// answer.add(srcData.get(host));
		}
		ex.getIn().setBody(entry);
	}

	@Handler
	public void restAddSource(Exchange ex) {
		String host = ex.getIn().getHeader("source", String.class);
		IpAddress ip = new IpAddress(); // 0.0.0.0
		StringBuilder sb = new StringBuilder();
		if (!ip.parseAddress(host))
			sb.append("source ").append(host).append(" invalid ipaddr");
		else if (srcData.containsKey(ip.toString()))
			sb.append("source ").append(host).append(" allready exists");
		else {
			Map<String, Object> status = new HashMap<String, Object>(1);
			status.put("Status", SnmpStatus.UNKNOWN);
			status.put("HostName", ip.getInetAddress().getHostName());
			srcData.put(ip.toString(), status);
			// srcData.put(ip.toString(), SnmpStatus.SUCCESS);
			sb.append("source ").append(host).append(" add to list");
		}
		Map<String, String> answer = new HashMap<String, String>();
		answer.put("Status", sb.toString());
		ex.getIn().setBody(answer);
	}

	@Handler
	public void restDelSource(Exchange ex) {
		String host = ex.getIn().getHeader("source", String.class);
		StringBuilder sb = new StringBuilder();
		if (srcData.containsKey(host)) {
			srcData.remove(host);
			sb.append("source ").append(host).append(" deleted");
		} else
			sb.append("source ").append(host).append(" not found");
		Map<String, String> answer = new HashMap<String, String>();
		answer.put("Status", sb.toString());
		ex.getIn().setBody(answer);
	}

	public void getRecoveryState() {
		File recoveryFile = new File(recoveryFileName);
		if (recoveryFile.exists() && recoveryFile.canRead()) {
			try {
				BufferedReader input = new BufferedReader(new FileReader(
						recoveryFile));
				String line = input.readLine();
				while (line != null) {
					String elements[] = line.split("\\|");
					String ip = elements[0];
					int e = 0;
					Map<String, Object> hmap = new HashMap<String, Object>();
					while (e < elements.length - 1) {
						++e;
						String pair[] = elements[e].split("=");
						if ("Uptime".equalsIgnoreCase(pair[0]))
							hmap.put("Uptime", Long.parseLong(pair[1]));
						else if ("Status".equalsIgnoreCase(pair[0])) 
							hmap.put("Status", SnmpStatus.valueOf(pair[1].toUpperCase()));
						else
							hmap.put(pair[0], pair[1]);
					}
					srcData.put(ip, hmap);
					line = input.readLine();
				}
				input.close();
				log.info("read recovery from " + recoveryFileName);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	/**
	 * save cache to file
	 */
	private void setRecovery() {
		if (recoveryFileName == null)
			return;

		File recoveryFile = new File(recoveryFileName);
		try {
			PrintStream os = new PrintStream(recoveryFile);
			StringBuilder sb = new StringBuilder();
			for (String ip : srcData.keySet()) {
				sb.append(ip);
				for (String k : srcData.get(ip).keySet()) {
					sb.append("|").append(k).append("=")
							.append(srcData.get(ip).get(k));
				}
				sb.append("\n");
			}
			os.print(sb.toString());
			os.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		}
	}

	/*
	 * @Handler public void getParsedSources(Exchange ex) {
	 * List<CommunityTarget> answer = new ArrayList<CommunityTarget>(); for
	 * (IpAddress host : srcData.keySet()) if (srcData.get(host) ==
	 * SnmpStatus.UNKNOWN) { answer.add(SnmpTargetFactory.createTarget(host,
	 * community, retries, timeout)); } ex.getIn().setHeader(CamelHeaders.SIZE,
	 * answer.size()); ex.getIn().setHeader(CamelHeaders.LIST, answer); }
	 */

}

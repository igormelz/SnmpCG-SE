package org.openfs.snmpcg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openfs.snmpcg.counters.PollCounter;
import org.openfs.snmpcg.counters.PollData;
import org.openfs.snmpcg.counters.PollDelta;
import org.openfs.snmpcg.utils.CsvField;
import org.openfs.snmpcg.utils.CsvUtils;


public class SourceCounters {

	private static final Logger log = LoggerFactory
			.getLogger(SourceCounters.class);

	private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
	private CsvField[] logCounterHeaders;
	private CsvField[] cdrCounterHeaders;
	private String recoveryFileName;
	// private Map<String, Map<CsvField,Object>> cache = new
	// ConcurrentHashMap<String, Map<CsvField,Object>>(10000);
	private Map<String, Map<String, Map<CsvField, Object>>> cache = new ConcurrentHashMap<String, Map<String, Map<CsvField, Object>>>();

	public void setDateTimeFormat(String dateTimeFormat) {
		sdf = new SimpleDateFormat(dateTimeFormat);
	}

	public void setLogFields(String logFields) {
		logCounterHeaders = CsvUtils.parseCsvString(logFields);
	}

	public void setRecoveryFileName(String f) {
		recoveryFileName = f;
	}

	public void setCdrFields(String cdrFields) {
		cdrCounterHeaders = CsvUtils.parseCsvString(cdrFields);
	}

	public void setinfluxURL(String u) {
		if (u.startsWith("http")) {
		} else {
		}
	}

	public void setinfluxUser(String u) {
	}

	public void setinfluxPass(String u) {
	}

	@Handler
	public void processListDelta(Exchange msg) throws Exception {

		if (msg.getIn().getBody() == null)
			return;

		@SuppressWarnings("unchecked")
		List<PollData> itemList = msg.getIn().getBody(List.class);
		List<PollDelta> answer = new ArrayList<PollDelta>();

		for (PollData item : itemList) {

			PollDelta delta = new PollDelta(item);
			Map<String, Map<CsvField, Object>> ipcache;

			if (cache.containsKey(item.getIpAddress())) {
				ipcache = cache.get(item.getIpAddress());
				if (ipcache.containsKey(item.getSnmpDescr())) {
					Map<CsvField, Object> last = ipcache.get(item
							.getSnmpDescr());
					if ((Long) last.get(CsvField.sysUpTime) < item
							.getSnmpUptime())
						delta.calcDelta(last);
				}
				ipcache.put(item.getSnmpDescr(), item.getCache());
			} else {
				ipcache = new HashMap<String, Map<CsvField, Object>>();
				ipcache.put(item.getSnmpDescr(), item.getCache());
				cache.put(item.getIpAddress(), ipcache);
			}
			answer.add(delta);
		}
		// return PollDelta
		// think in vs out
		msg.getIn().setBody(answer);
	}

	@Handler
	public void logListDelta(Exchange msg) throws Exception {

		if (msg.getIn().getBody() == null)
			return;

		@SuppressWarnings("unchecked")
		List<List<PollDelta>> iftable = msg.getIn().getBody(List.class);

		// CsvField[] csvCounterHeaders = CsvUtils.parseCsvString(csv);
		// SimpleDateFormat sdf = new SimpleDateFormat(dt);

		List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();

		for (List<PollDelta> delta : iftable) {
			for (PollDelta item : delta) {
				Map<CsvField, Object> entry = new HashMap<CsvField, Object>();
				entry.put(CsvField.Device, item.getIpAddress());
				entry.put(CsvField.ifIndex, item.getSnmpIndex());
				entry.put(CsvField.ifDescr, item.getSnmpDescr());
				entry.put(CsvField.ifInOctets, item.getSnmpIn());
				entry.put(CsvField.ifOutOctets, item.getSnmpOut());
				entry.put(CsvField.StartTime, sdf.format(item.getStartTime()));
				entry.put(CsvField.EndTime, sdf.format(item.getEndTime()));
				entry.put(CsvField.sysUpTime, item.getSnmpUptime());

				answer.add(CsvUtils.createCsvRecord(entry, cdrCounterHeaders));
			}
		}
		msg.getIn().setBody(answer);
		msg.getIn().setHeader(CamelHeaders.SIZE, answer.size());

		setRecovery();
	}

	/**
	 * calculate snmp records as delta current poll in msg & cached poll
	 * 
	 * @param msg
	 * @throws Exception
	 */
	@Handler
	public void processDelta(Exchange msg) throws Exception {

		if (msg.getIn().getBody() == null)
			return;

		PollData item = msg.getIn().getBody(PollData.class);

		// skip empty descr
		if ("".equals(item.getSnmpDescr()))
			return;

		PollDelta answer = new PollDelta(item);
		Map<String, Map<CsvField, Object>> ipcache;

		if (cache.containsKey(item.getIpAddress())) {
			ipcache = cache.get(item.getIpAddress());
			if (ipcache.containsKey(item.getSnmpDescr())) {
				Map<CsvField, Object> last = ipcache.get(item.getSnmpDescr());
				if ((Long) last.get(CsvField.sysUpTime) < item.getSnmpUptime())
					answer.calcDelta(last);
			}
			ipcache.put(item.getSnmpDescr(), item.getCache());
		} else {
			ipcache = new HashMap<String, Map<CsvField, Object>>();
			ipcache.put(item.getSnmpDescr(), item.getCache());
			cache.put(item.getIpAddress(), ipcache);
		}
		// return PollDelta
		// think in vs out
		msg.getIn().setBody(answer);
	}

	/**
	 * create csv delta record
	 * 
	 * @param csv
	 * @param dt
	 * @param msg
	 * @throws Exception
	 */
	@Handler
	public void logDelta(Exchange msg) throws Exception {

		if (msg.getIn().getBody() == null)
			return;

		@SuppressWarnings("unchecked")
		List<PollDelta> iftable = msg.getIn().getBody(List.class);

		// CsvField[] csvCounterHeaders = CsvUtils.parseCsvString(csv);
		// SimpleDateFormat sdf = new SimpleDateFormat(dt);

		List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();

		for (PollDelta item : iftable) {
			Map<CsvField, Object> entry = new HashMap<CsvField, Object>();
			entry.put(CsvField.Device, item.getIpAddress());
			entry.put(CsvField.ifIndex, item.getSnmpIndex());
			entry.put(CsvField.ifDescr, item.getSnmpDescr());
			entry.put(CsvField.ifInOctets, item.getSnmpIn());
			entry.put(CsvField.ifOutOctets, item.getSnmpOut());
			entry.put(CsvField.StartTime, sdf.format(item.getStartTime()));
			entry.put(CsvField.EndTime, sdf.format(item.getEndTime()));
			entry.put(CsvField.sysUpTime, item.getSnmpUptime());


			answer.add(CsvUtils.createCsvRecord(entry, cdrCounterHeaders));
		}
		msg.getIn().setBody(answer);
		msg.getIn().setHeader(CamelHeaders.SIZE, answer.size());

		setRecovery();
	}

	/**
	 * incoming msg is List<List>
	 * 
	 * @param msg
	 * @throws Exception
	 */
	@Handler
	public void logListCounters(Exchange msg) throws Exception {

		if (msg.getIn().getBody() == null)
			return;

		@SuppressWarnings("unchecked")
		List<List<PollData>> pollLists = msg.getIn().getBody(List.class);

		List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();

		for (List<PollData> pollList : pollLists) {
			if (!pollList.isEmpty()) {
				for (PollData item : pollList) {
					Map<CsvField, Object> entry = new HashMap<CsvField, Object>();
					entry.put(CsvField.Device, item.getIpAddress());
					entry.put(CsvField.ifIndex, item.getSnmpIndex());
					entry.put(CsvField.ifDescr, item.getSnmpDescr());
					entry.put(CsvField.ifInOctets, item.getSnmpIn());
					entry.put(CsvField.ifOutOctets, item.getSnmpOut());
					entry.put(CsvField.pollTime, sdf.format(item.getPollTime()));
					entry.put(CsvField.sysUpTime, item.getSnmpUptime());

					answer.add(CsvUtils.createCsvRecord(entry,
							logCounterHeaders));
				}
			}
		}
		msg.getIn().setBody(answer);
		msg.getIn().setHeader(CamelHeaders.SIZE, answer.size());
	}

	/**
	 * Create CSV record from PollData
	 * 
	 * @param csv
	 * @param dt
	 * @param msg
	 * @throws Exception
	 */
	@Handler
	public void logCounters(Exchange msg) throws Exception {

		if (msg.getIn().getBody() == null)
			return;

		@SuppressWarnings("unchecked")
		List<PollData> iftable = msg.getIn().getBody(List.class);

		List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();

		for (PollData item : iftable) {
			if (item == null) {
				log.warn("Recv null PollData");
			} else {
				Map<CsvField, Object> entry = new HashMap<CsvField, Object>();
				entry.put(CsvField.Device, item.getIpAddress());
				entry.put(CsvField.ifIndex, item.getSnmpIndex());
				entry.put(CsvField.ifDescr, item.getSnmpDescr());
				entry.put(CsvField.ifInOctets, item.getSnmpIn());
				entry.put(CsvField.ifOutOctets, item.getSnmpOut());
				entry.put(CsvField.pollTime, sdf.format(item.getPollTime()));
				entry.put(CsvField.sysUpTime, item.getSnmpUptime());

				answer.add(CsvUtils.createCsvRecord(entry, logCounterHeaders));
			}
		}
		msg.getIn().setBody(answer);
		msg.getIn().setHeader(CamelHeaders.SIZE, answer.size());
	}

	@Handler
	public void restClearIpCache(Exchange ex) {
		String host = ex.getIn().getHeader("source", String.class);
		// StringBuilder sb = new StringBuilder();
		if (cache.containsKey(host)) // {
			cache.remove(host);
		// sb.append("cache ").append(host).append(" deleted");
		// } else
		// sb.append("cache ").append(host).append(" not found");
		// ex.getIn().setBody(sb.toString());
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
					Map<String, Map<CsvField, Object>> dmap = new HashMap<String, Map<CsvField, Object>>();
					while (e < elements.length - 1) {
						String descr = elements[++e];
						Map<CsvField, Object> entry = new HashMap<CsvField, Object>();
						for (int j = 0; j < 4; j++) {
							++e;
							String pair[] = elements[e].split("=");
							if ("pollTime".equalsIgnoreCase(pair[0]))
								entry.put(CsvField.pollTime,
										Long.parseLong(pair[1]));
							else if ("sysUpTime".equalsIgnoreCase(pair[0]))
								entry.put(CsvField.sysUpTime,
										Long.parseLong(pair[1]));
							else if ("ifInOctets".equalsIgnoreCase(pair[0])) {
								String values[] = pair[1].split(":");
								entry.put(
										CsvField.ifInOctets,
										new PollCounter(Long
												.parseLong(values[0]), Integer
												.parseInt(values[1])));
							} else if ("ifOutOctets".equalsIgnoreCase(pair[0])) {
								String values[] = pair[1].split(":");
								entry.put(
										CsvField.ifOutOctets,
										new PollCounter(Long
												.parseLong(values[0]), Integer
												.parseInt(values[1])));
							}
						}
						dmap.put(descr, entry);
					}
					cache.put(ip, dmap);
					line = input.readLine();
				}
				input.close();
				log.info("read recovery from " + recoveryFileName);
			} catch (IOException e) {
				e.printStackTrace();
				return;
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
			for (String ip : cache.keySet()) {
				sb.append(ip).append("|");
				for (String descr : cache.get(ip).keySet()) {
					sb.append(descr).append("|");
					for (CsvField f : cache.get(ip).get(descr).keySet()) {
						sb.append(f).append("=")
								.append(cache.get(ip).get(descr).get(f))
								.append("|");

					}
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

	/**
	 * processing REST /api/counters/
	 * 
	 * @param ex
	 */
	@Handler
	public void restGetCache(Exchange ex) {
		List<Map<String, Object>> answer = new ArrayList<Map<String, Object>>();
		for (String host : cache.keySet()) {
			Map<String, Object> entry = new HashMap<String, Object>();
			entry.put("Ip", host);
			entry.put("CacheSize", cache.get(host).size());
			answer.add(entry);
		}
		ex.getIn().setBody(answer);
	}

	@Handler
	public void restGetIpCache(Exchange ex) {
		String host = ex.getIn().getHeader("ip", String.class);
		if (cache.containsKey(host)) {
			List<Map<CsvField, Object>> answer = new ArrayList<Map<CsvField, Object>>();
			Map<String, Map<CsvField, Object>> ipcache = cache.get(host);
			for (String ifdescr : ipcache.keySet()) {
				Map<CsvField, Object> last = ipcache.get(ifdescr);
				last.put(CsvField.ifDescr, ifdescr);
				answer.add(last);
			}
			ex.getIn().setBody(answer);
		}
	}
}

package org.openfs.snmpcg;

import org.apache.camel.Body;
import org.apache.camel.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.stereotype.Service;
import org.openfs.snmpcg.model.SnmpCounter;
import org.openfs.snmpcg.model.SnmpInterface;
import org.openfs.snmpcg.model.SnmpSource;
import org.openfs.snmpcg.model.SnmpSourceStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service("snmpService")
public class SnmpService {

	private static final Logger log = LoggerFactory.getLogger("SnmpService");

	private static final long COUNTER32_MAX_VALUE = 4294967295L;

	private final static OID COUNTER_OIDS[] = new OID[] {
			// sysUpTime[0]
			new OID("1.3.6.1.2.1.1.3"),
			// ifDescr[1]
			new OID(".1.3.6.1.2.1.2.2.1.2"),
			// IfInOctets[2]
			new OID(".1.3.6.1.2.1.2.2.1.10."),
			// IfHCIn64[3]
			new OID(".1.3.6.1.2.1.31.1.1.1.6."),
			// IfOutOctest[4]
			new OID(".1.3.6.1.2.1.2.2.1.16."),
			// IfHCOut64[5]
			new OID(".1.3.6.1.2.1.31.1.1.1.10."),
			// ifAdminStatus[6]
			new OID(".1.3.6.1.2.1.2.2.1.7"),
			// ifOperStatus[7]
			new OID(".1.3.6.1.2.1.2.2.1.8"),
			// ifName[8]
			new OID("1.3.6.1.2.1.31.1.1.1.1"),
			// ifAlias[9]
			new OID("1.3.6.1.2.1.31.1.1.1.18") };

	private final static OID STATUS_OIDS[] = new OID[] {
			// sysUpTime[0]
			new OID(".1.3.6.1.2.1.1.3"),
			// sysDescr[1]
			new OID(".1.3.6.1.2.1.1.1"),
			// sysName[2]
			new OID(".1.3.6.1.2.1.1.5"),
			// sysLocation[3]
			new OID(".1.3.6.1.2.1.1.6"),
			// ifNumber[4]
			new OID(".1.3.6.1.2.1.2.1"),
			// -- IFTABLE --
			// ifDescr[5]
			new OID(".1.3.6.1.2.1.2.2.1.2"),
			// ifAdminStatus[6]
			new OID(".1.3.6.1.2.1.2.2.1.7"),
			// ifOperStatus[7]
			new OID(".1.3.6.1.2.1.2.2.1.8"),
			// ifName[8]
			new OID("1.3.6.1.2.1.31.1.1.1.1"),
			// ifAlias[9]
			new OID("1.3.6.1.2.1.31.1.1.1.18") };

	private GaugeService gaugePollResponse;

	@Autowired
	public SnmpService(GaugeService gaugeService) {
		this.gaugePollResponse = gaugeService;
	}

	@Handler
	public void pollStatus(@Body SnmpSource source) throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("source {}: poll status", source.getIpAddress());
		}
		
		long startPollTime = System.currentTimeMillis();
		List<TableEvent> events = getTable(source, STATUS_OIDS);

		// update pollTime
		long endPollTime = System.currentTimeMillis();
		source.setPollTime(endPollTime);
		source.setPollResponse((endPollTime - startPollTime));

		// return if no response
		if (events == null) {
			return;
		}

		// get source info
		VariableBinding vbs[] = events.get(0).getColumns();

		// process sysUpTime
		String uptime = vbs[0].getVariable().toString();
		long sysUptime = events.get(0).getColumns()[0].getVariable().toLong();

		if (source.getSysUptime() != 0 && source.getSysUptime() > sysUptime) {
			log.warn("source {}: was rebooted. Reset all counter",
					source.getIpAddress());
			// reset Counters and skip calcDelta on next poll
			source.resetSnmpInterfaceCounters();
			source.setSkipDelta(true);
		}
		source.setSysUptime(vbs[0].getVariable().toLong());

		// update system info
		source.setSysDescr(vbs[1].getVariable().toString());
		source.setSysName(vbs[2].getVariable().toString());
		source.setSysLocation(vbs[3].getVariable().toString());

		// validate ifNumber
		if (vbs[4] == null
				|| (vbs[4] != null && vbs[4].getVariable().toInt() == 0)) {
			log.warn("source {}: has no interfaces", source.getIpAddress());
			source.setStatus(SnmpSourceStatus.NO_IFTABLE);
			return;
		}
		int ifNumber = vbs[4].getVariable().toInt();

		// set success status
		source.setStatus(SnmpSourceStatus.SUCCESS);

		// process ifEntry
		events.subList(1, events.size())
				.stream()
				.filter(event -> event != null && !event.isError())
				.forEach(
						event -> {

							if (event.getColumns() == null
									|| event.getColumns().length < 6) {
								log.warn("source {}: no ifTable in response",
										source.getIpAddress());
								return;
							}

							// validate ifDescr
							if (event.getColumns()[5] == null) {
								log.warn("source {}: no ifDescr for index:{}",
										source.getIpAddress(), event.getIndex()
												.get(0));
								return;
							}

							// get ifEntry
							SnmpInterface ifEntry = source
									.getSnmpInterface(event.getColumns()[5]
											.getVariable().toString());

							updateIfEntry(ifEntry, event);

						});
		log.info("source {}: SUCCESS, uptime:{}, ifNumber:{}[{}]",
				source.getIpAddress(), uptime, events.size() - 1, ifNumber);
	}

	@Handler
	public void pollCounters(@Body SnmpSource source) throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("source {}: poll counters", source.getIpAddress());
		}

		long startPollTime = System.currentTimeMillis();
		
		// get ifTable
		List<TableEvent> events = getTable(source, COUNTER_OIDS);

		// update pollTime
		long endPollTime = System.currentTimeMillis();
		source.setPollTime(endPollTime);
		source.setPollResponse((endPollTime - startPollTime));
		
		// update metric
		gaugePollResponse.submit("gauge.poll.response." + source.getIpAddress(),
				(double) (endPollTime - startPollTime));

		// return if no events was received
		if (events == null)
			return;

		// process sysUpTime
		long sysUptime = events.get(0).getColumns()[0].getVariable().toLong();
		if (source.getSysUptime() > sysUptime) {
			log.warn("source {}: was rebooted between pool. Reset all counter",
					source.getIpAddress());
			source.resetSnmpInterfaceCounters();
			source.setSkipDelta(true);
		} else {
			// update duration
			source.setPollDuration(sysUptime - source.getSysUptime());
		}

		// update sysUptime
		source.setSysUptime(sysUptime);

		// keep poll ifTable
		List<String> processedIF = new ArrayList<String>(events.size());

		// process ifEntry
		events.subList(1, events.size())
				.stream()
				.filter(event -> event != null && !event.isError())
				.forEach(event -> {
					VariableBinding vb[] = event.getColumns();

					// validate ifDescr
						if (vb == null || vb.length < 1 || vb[1] == null) {
							log.warn("source {}: no ifDescr for index:{}",
									source.getIpAddress(), event.getIndex()
											.get(0));
							return;
						}

						// get ifEntry
						String ifdescr = vb[1].getVariable().toString();
						// get ifEntry
						SnmpInterface ifEntry = source
								.getSnmpInterface(ifdescr);
						// add to pollIf
						processedIF.add(ifdescr);

						updateIfEntry(ifEntry, event);

						// get ifInOctets, ifOutOctets
						SnmpCounter bytes_in = getCounterValue(vb[2], vb[3]);
						SnmpCounter bytes_out = getCounterValue(vb[4], vb[5]);

						// calculate delta counters
						if (ifEntry.isUp() && !source.isSkipDelta()) {
							ifEntry.setPollInOctets(calcDeltaCounter(
									source.getIpAddress(), ifdescr, bytes_in,
									ifEntry.getIfInOctets()));
							ifEntry.setPollOutOctets(calcDeltaCounter(
									source.getIpAddress(), ifdescr, bytes_out,
									ifEntry.getIfOutOctets()));
						}

						// update counter values
						ifEntry.setIfInOctets(bytes_in);
						ifEntry.setIfOutOctets(bytes_out);
					});

		// reset skipDelta
		if (source.isSkipDelta()) {
			source.setSkipDelta(false);
		}

		if (log.isDebugEnabled()) {
			log.debug("source {}: poll processed: uptime:{}, ifNumber:{}",
					source.getIpAddress(), events.get(0).getColumns()[0]
							.getVariable().toString(), events.size() - 1);
		}

		// validate source ifTable
		List<String> toremove = source.getIftable().keySet().stream()
				.filter(ifdescr -> !processedIF.contains(ifdescr))
				.collect(Collectors.toList());
		// remove not existing interfaces
		if (toremove != null && !toremove.isEmpty()) {
			for (String ifdescr : toremove) {
				// source.removeSnmpInterace(ifdescr);
				log.warn("source {}: not found in response ifdescr: {}",
						source.getIpAddress(), ifdescr);
			}
		}
	}

	private List<TableEvent> getTable(SnmpSource source, OID[] oids)
			throws Exception {
		Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
		snmp.listen();

		TableUtils tUtils = new TableUtils(snmp, new DefaultPDUFactory());
		List<TableEvent> events = tUtils.getTable(source.getTarget(), oids,
				null, null);

		snmp.close();

		// validate timeout
		if (events.size() == 1 && events.get(0).isError()) {
			log.error("source {}: {}", source.getIpAddress(), events.get(0)
					.getErrorMessage());
			source.setStatus(SnmpSourceStatus.TIMEOUT);
			return null;
		}

		// validate NO PDU
		if (events == null || events.isEmpty()) {
			source.setStatus(SnmpSourceStatus.NO_PDU);
			log.error("source {}: no responsePDU (null)", source.getIpAddress());
			return null;
		}

		return events;
	}

	private SnmpCounter getCounterValue(VariableBinding vb32,
			VariableBinding vb64) {

		SnmpCounter counter = new SnmpCounter();

		if (vb32 != null && vb64 != null) {
			if (vb64.getVariable().toLong() > vb32.getVariable().toLong()) {
				counter.setValue(vb64.getVariable().toLong());
				counter.setType(64);
			} else {
				counter.setValue(vb32.getVariable().toLong());
			}
		} else if (vb64 != null) {
			counter.setValue(vb64.getVariable().toLong());
			counter.setType(64);
		} else if (vb32 != null) {
			counter.setValue(vb32.getVariable().toLong());
		}
		return counter;
	}

	private long calcDeltaCounter(String sourceIpAddr, String ifDescr,
			SnmpCounter pollCounter, SnmpCounter lastCounter) {

		if (pollCounter.getValue() > lastCounter.getValue()) {
			return pollCounter.getValue() - lastCounter.getValue();
		}

		if (pollCounter.getValue() == 0 && lastCounter.getValue() > 0) {
			log.warn(
					"source {}: ifdescr:{} - fake overflow counter: current={} last={}",
					sourceIpAddr, ifDescr, pollCounter, lastCounter);
			return 0L;
		}

		if (pollCounter.getValue() < lastCounter.getValue()
				&& pollCounter.getType() == lastCounter.getType()) {
			if (pollCounter.getType() == 32) {
				log.warn(
						"source {}: ifdescr:{} - overflow counter: current={} last={}",
						sourceIpAddr, ifDescr, pollCounter, lastCounter);
				return COUNTER32_MAX_VALUE + pollCounter.getValue()
						- lastCounter.getValue();
			} else {
				log.warn(
						"source {}: ifdescr:{} - overflow 64 bit counter: current={} last={}",
						sourceIpAddr, ifDescr, pollCounter, lastCounter);
				return 0L;
			}
		}

		return 0L;
	}

	private void updateIfEntry(SnmpInterface ifEntry, TableEvent event) {

		// update ifindex
		ifEntry.setIfIndex(event.getIndex().get(0));

		// update adminStatus
		if (event.getColumns()[6] != null) {
			ifEntry.setIfAdminStatus(event.getColumns()[6].getVariable()
					.toInt());
		}

		// update operStatus
		if (event.getColumns()[7] != null) {
			ifEntry.setIfOperStatus(event.getColumns()[7].getVariable().toInt());
		}

		// update ifName
		if (event.getColumns()[8] != null && ifEntry.getIfName() == null) {
			ifEntry.setIfName(event.getColumns()[8].getVariable().toString());
		}

		// update ifAlias
		if (event.getColumns()[9] != null && ifEntry.getIfAlias() == null) {
			ifEntry.setIfAlias(event.getColumns()[9].getVariable().toString());
		}

	}
}
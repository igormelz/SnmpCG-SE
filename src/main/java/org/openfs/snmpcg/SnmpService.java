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
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.stereotype.Service;
import org.openfs.snmpcg.model.SnmpCounter;
import org.openfs.snmpcg.model.SnmpInterface;
import org.openfs.snmpcg.model.SnmpSource;
import org.openfs.snmpcg.model.SnmpSourceStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service("snmpService")
public class SnmpService {

	private static final Logger log = LoggerFactory.getLogger("SnmpService");

	private static final long COUNTER32_MAX_VALUE = 4294967295L;

	private static final OID sysUpTimeOID = new OID(".1.3.6.1.2.1.1.3");
	private static final OID ifDescrOID = new OID(".1.3.6.1.2.1.2.2.1.2");
	private static final OID sysDescrOID = new OID(".1.3.6.1.2.1.1.1");
	private static final OID sysNameOID = new OID(".1.3.6.1.2.1.1.5");
	private static final OID sysLocationOID = new OID(".1.3.6.1.2.1.1.6");
	private static final OID ifNumberOID = new OID(".1.3.6.1.2.1.2.1");
	private static final OID IfInOctetsOID = new OID(".1.3.6.1.2.1.2.2.1.10.");
	private static final OID IfOutOctestOID = new OID(".1.3.6.1.2.1.2.2.1.16.");
	private static final OID ifAdminStatusOID = new OID(".1.3.6.1.2.1.2.2.1.7");
	private static final OID ifOperStatusOID = new OID(".1.3.6.1.2.1.2.2.1.8");
	private static final OID ifNameOID = new OID("1.3.6.1.2.1.31.1.1.1.1");
	private static final OID ifAliasOID = new OID("1.3.6.1.2.1.31.1.1.1.18");
	private static final OID ifHCInOctetsOID = new OID(".1.3.6.1.2.1.31.1.1.1.6");
	private static final OID ifHCOutOctetsOID = new OID(".1.3.6.1.2.1.31.1.1.1.10");

	private final static OID COUNTER_OIDS[] = new OID[] { sysUpTimeOID,
			ifDescrOID, IfInOctetsOID, ifHCInOctetsOID, IfOutOctestOID,
			ifHCOutOctetsOID, ifAdminStatusOID, ifOperStatusOID, ifNameOID,
			ifAliasOID };

	private final static OID STATUS_OIDS[] = new OID[] { sysUpTimeOID,
			sysDescrOID, sysNameOID, sysLocationOID, ifNumberOID, ifDescrOID,
			ifAdminStatusOID, ifOperStatusOID, ifNameOID, ifAliasOID };

	private CounterService counterService;
	private Snmp snmp;
	
	@Autowired
	public SnmpService(CounterService counterService) throws IOException {
		this.counterService = counterService;
		snmp = new Snmp(new DefaultUdpTransportMapping());
		snmp.listen();
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
		validateSkipDelta(source, vbs[0].getVariable().toLong());
		source.setSysUptime(vbs[0].getVariable().toLong());

		// update system info
		source.setSysDescr(vbs[1].getVariable().toString());
		source.setSysName(vbs[2].getVariable().toString());
		source.setSysLocation(vbs[3].getVariable().toString());

		// validate ifNumber
		if (vbs[4] == null
				|| (vbs[4] != null && vbs[4].getVariable().toInt() == 0)) {
			log.warn("source {}: has no interfaces", source.getIpAddress());
			counterService.increment("counter.snmp.logWarn");
			source.setStatus(SnmpSourceStatus.NO_IFTABLE);
			return;
		}

		// set success status
		source.setStatus(SnmpSourceStatus.SUCCESS);

		// process ifEntry
		events.subList(1, events.size())
				.stream()
				.filter(event -> event != null && !event.isError())
				.forEach(
						event -> {

							if (event.getColumns() == null || event.getColumns().length < 6) {
								log.warn("source {}: no ifTable in response", source.getIpAddress());
								counterService.increment("counter.snmp.logWarn");
								return;
							}

							// validate ifDescr
							if (event.getColumns()[5] == null) {
								log.warn("source {}: no ifDescr for index:{}", source.getIpAddress(), event.getIndex().get(0));
								counterService.increment("counter.snmp.logWarn");
								return;
							}

							// get ifEntry
							SnmpInterface ifEntry = source.getSnmpInterface(event.getColumns()[5].getVariable().toString());

							updateIfEntry(ifEntry, event);
							
						});
		log.info("source {}: SUCCESS, uptime:{}, ifNumber:{}", source.getIpAddress(), uptime, events.size() - 1);
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

		// return if no events was received
		if (events == null)
			return;

		// process sysUpTime
		long sysUptime = events.get(0).getColumns()[0].getVariable().toLong();
		if (!validateSkipDelta(source,sysUptime)) {
			source.setPollDuration(sysUptime - source.getSysUptime());
		}
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
							log.warn("source {}: no ifDescr for index:{}", source.getIpAddress(), event.getIndex().get(0));
							counterService.increment("counter.snmp.logWarn");
							return;
						}

						// get ifEntry
						String ifdescr = vb[1].getVariable().toString();
						SnmpInterface ifEntry = source.getSnmpInterface(ifdescr);

						updateIfEntry(ifEntry, event);
							
						// get ifInOctets, ifOutOctets
						SnmpCounter bytes_in = getCounterValue(vb[2], vb[3]);
						SnmpCounter bytes_out = getCounterValue(vb[4], vb[5]);
							
						// calculate delta counters
						if (!source.isSkipDelta() && ifEntry.isUp()) {
							ifEntry.setPollInOctets(calcDeltaCounter(
									source.getIpAddress(), ifdescr, bytes_in,
									ifEntry.getIfInOctets()));
							ifEntry.setPollOutOctets(calcDeltaCounter(
									source.getIpAddress(), ifdescr, bytes_out,
									ifEntry.getIfOutOctets()));
						}
							
						// save counter values
						ifEntry.setIfInOctets(bytes_in);
						ifEntry.setIfOutOctets(bytes_out);
						
						// add to processed list
						processedIF.add(ifdescr);
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
		// process not existing interfaces
		if (toremove != null && !toremove.isEmpty()) {
			for (String ifdescr : toremove) {
				log.warn("source {}: not found in response ifdescr: {}", source.getIpAddress(), ifdescr);
				if (source.getSnmpInterface(ifdescr).isMarked()) {
					source.removeSnmpInterace(ifdescr);
					log.info("source {}: remove interface ifdescr: {}",	source.getIpAddress(), ifdescr);
				} else {
					source.getSnmpInterface(ifdescr).setMarked();
				}
			}
		}
	}

	private List<TableEvent> getTable(SnmpSource source, OID[] oids)
			throws Exception {

		TableUtils tUtils = new TableUtils(snmp, new DefaultPDUFactory());
		List<TableEvent> events = tUtils.getTable(source.getTarget(), oids,
				null, null);

		// validate timeout
		if (events.size() == 1 && events.get(0).isError()) {
			log.error("source {}: {}", source.getIpAddress(), events.get(0).getErrorMessage());
			source.setStatus(SnmpSourceStatus.TIMEOUT);
			counterService.increment("counter.snmp.logError");
			return null;
		}

		// validate NO PDU
		if (events == null || events.isEmpty()) {
			source.setStatus(SnmpSourceStatus.NO_PDU);
			log.error("source {}: no responsePDU (null)", source.getIpAddress());
			counterService.increment("counter.snmp.logError");
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
			log.warn("source {}: ifdescr:{} - fake overflow counter: current={} last={}", sourceIpAddr, ifDescr, pollCounter, lastCounter);
			counterService.increment("counter.snmp.logWarn");
			return 0L;
		}

		if (pollCounter.getValue() < lastCounter.getValue()
				&& pollCounter.getType() == lastCounter.getType()) {
			if (pollCounter.getType() == 32) {
				log.warn("source {}: ifdescr:{} - overflow counter: current={} last={}", sourceIpAddr, ifDescr, pollCounter, lastCounter);
				counterService.increment("counter.snmp.logWarn");
				return COUNTER32_MAX_VALUE + pollCounter.getValue() - lastCounter.getValue();
			} else {
				log.warn("source {}: ifdescr:{} - overflow 64 bit counter: current={} last={}",	sourceIpAddr, ifDescr, pollCounter, lastCounter);
				counterService.increment("counter.snmp.logWarn");
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
		if (event.getColumns()[9] != null) {
			ifEntry.setIfAlias(event.getColumns()[9].getVariable().toString());
		}
	}
	
	private boolean validateSkipDelta(SnmpSource source, long sysUptime) {
		if (source.getSysUptime() != 0 && source.getSysUptime() > sysUptime) {
			log.warn("source {}: was rebooted. Reset all counter", source.getIpAddress());
			counterService.increment("counter.snmp.logWarn");
			// reset Counters and skip calcDelta on next poll
			source.resetSnmpInterfaceCounters();
			source.setSkipDelta(true);
			return true;
		}
		return false;
	}
}
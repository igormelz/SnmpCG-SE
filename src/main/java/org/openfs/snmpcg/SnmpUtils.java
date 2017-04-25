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
import org.openfs.snmpcg.model.SnmpCounter;
import org.openfs.snmpcg.model.SnmpInterface;
import org.openfs.snmpcg.model.SnmpSource;
import org.openfs.snmpcg.model.SnmpSourceStatus;

import java.util.List;

public class SnmpUtils {

	private static final Logger log = LoggerFactory.getLogger("SnmpService");

	private static final long COUNTER32_MAX_VALUE = 4294967295L;

	private final static OID COUNTER_OIDS[] = new OID[] {
			// sysUpTime[0]
			new OID("1.3.6.1.2.1.1.3"),
			// ifDescr[1]
			new OID(".1.3.6.1.2.1.2.2.1.2"),
			// ifAdminStatus[2]
			new OID(".1.3.6.1.2.1.2.2.1.7"),
			// ifOperStatus[3]
			new OID(".1.3.6.1.2.1.2.2.1.8"),
			// IfInOctets[4]
			new OID(".1.3.6.1.2.1.2.2.1.10."),
			// IfHCIn64[5]
			new OID(".1.3.6.1.2.1.31.1.1.1.6."),
			// IfOutOctest[6]
			new OID(".1.3.6.1.2.1.2.2.1.16."),
			// IfHCOut64[7]
			new OID(".1.3.6.1.2.1.31.1.1.1.10."), };

	private final static OID STATUS_OIDS[] = new OID[] {
			// sysUpTime[0]
			new OID(".1.3.6.1.2.1.1.3"),
			// sysDescr[1]
			new OID(".1.3.6.1.2.1.1.1"),
			// sysName[2]
			new OID(".1.3.6.1.2.1.1.5"),
			// sysLocation[3]
			new OID(".1.3.6.1.2.1.1.6"),
			// ifDescr[4]
			new OID(".1.3.6.1.2.1.2.2.1.2"),
			// ifAdminStatus[5]
			new OID(".1.3.6.1.2.1.2.2.1.7"),
			// ifOperStatus[6]
			new OID(".1.3.6.1.2.1.2.2.1.8"),
			// IfInOctets[7]
			new OID(".1.3.6.1.2.1.2.2.1.10."),
			// IfHCIn64[8]
			new OID(".1.3.6.1.2.1.31.1.1.1.6."),
			// IfOutOctest[9]
			new OID(".1.3.6.1.2.1.2.2.1.16."),
			// IfHCOut64[10]
			new OID(".1.3.6.1.2.1.31.1.1.1.10."),
			// ifName[11]
			new OID("1.3.6.1.2.1.31.1.1.1.1"),
			// ifAlias[12]
			new OID("1.3.6.1.2.1.31.1.1.1.18") };

	@Handler
	public void pollStatus(@Body SnmpSource source) throws Exception {
		List<TableEvent> events = getTable(source, STATUS_OIDS);
		if (events == null)
			return;

		// update pollTime
		source.setPollTime(System.currentTimeMillis());

		// get sysInfo
		VariableBinding vbs[] = events.get(0).getColumns();
		String uptime = vbs[0].getVariable().toString();
		source.setSysUptime(vbs[0].getVariable().toLong());
		source.setSysDescr(vbs[1].getVariable().toString());
		source.setSysName(vbs[2].getVariable().toString());
		source.setSysLocation(vbs[3].getVariable().toString());
		source.setStatus(SnmpSourceStatus.SUCCESS);

		// process ifEntry
		events.subList(1, events.size())
				.stream()
				.filter(event -> event.getColumns()[4] != null)
				.forEach(event -> {
					VariableBinding vb[] = event.getColumns();
					String ifdescr = vb[4].getVariable().toString();
					SnmpInterface ifEntry = source.getSnmpInterface(ifdescr);

						// update adminStatus
						if (vb[5] != null) {
							ifEntry.setIfAdminStatus(vb[5].getVariable()
									.toInt());
						}

						// update operStatus
						if (vb[6] != null) {
							ifEntry.setIfOperStatus(vb[6].getVariable()
									.toInt());
						}

						// update ifName
						if (vb[11] != null && ifEntry.getIfName() == null) {
							ifEntry.setIfName(vb[11].getVariable().toString());
						}

						// update ifAlias
						if (vb[12] != null && ifEntry.getIfAlias() == null) {
							ifEntry.setIfAlias(vb[12].getVariable().toString());
						}

						// init counters
						if (ifEntry.getIfAdminStatus() == 1) {
							ifEntry.setIfInOctets(getCounterValue(vb[7], vb[8]));
							ifEntry.setIfOutOctets(getCounterValue(vb[9], vb[10]));
						}
					});
		log.info("source {} uptime:{} ifNumber:{}", source.getIpAddress(),
				uptime, events.size() - 1);
	}

	@Handler
	public void pollCounters(@Body SnmpSource source) throws Exception {

		List<TableEvent> events = getTable(source, COUNTER_OIDS);
		if (events == null)
			return;
		// update pollTime
		source.setPollTime(System.currentTimeMillis());

		// process sysUpTime
		long sysUptime = events.get(0).getColumns()[0].getVariable().toLong();
		if (source.getSysUptime() > sysUptime) {
			log.warn("source {}: was rebooted between pool. Reset all counter");
			source.resetSnmpInterfaceCounters();
		}

		// update sysUptime
		source.setPollDuration(sysUptime - source.getSysUptime());
		source.setSysUptime(sysUptime);

		// process ifEntry
		events.subList(1, events.size())
				.stream()
				.filter(event -> event.getColumns()[1] != null)
				.forEach(
						event -> {
							VariableBinding vb[] = event.getColumns();
							String ifdescr = vb[1].getVariable().toString();
							SnmpInterface ifEntry = source.getSnmpInterface(ifdescr);

							if (vb[2] != null) {
								ifEntry.setIfAdminStatus(vb[2].getVariable().toInt());
							}

							if (vb[3] != null) {
								ifEntry.setIfOperStatus(vb[3].getVariable().toInt());
							}
							
							if (ifEntry.getIfAdminStatus() == 1) {
								SnmpCounter pollInOctets = getCounterValue(vb[4], vb[5]);
								SnmpCounter pollOutOctets = getCounterValue(vb[6], vb[7]);
								// calc delta
								ifEntry.setPollInOctets(calcDeltaCounter(
										source.getIpAddress(), ifdescr,
										pollInOctets, ifEntry.getIfInOctets()));
								ifEntry.setPollOutOctets(calcDeltaCounter(
										source.getIpAddress(), ifdescr,
										pollOutOctets, ifEntry.getIfOutOctets()));
								// update
								ifEntry.setIfInOctets(pollInOctets);
								ifEntry.setIfOutOctets(pollOutOctets);
							}
						});

		log.info("source {} uptime:{} ifNumber:{}", source.getIpAddress(),
				events.get(0).getColumns()[0].getVariable().toString(),
				events.size() - 1);
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
					"source {} ifdescr {} fake overflow counter: current poll {}, last value {}",
					sourceIpAddr, ifDescr, pollCounter, lastCounter);
			return 0L;
		}

		if (pollCounter.getValue() < lastCounter.getValue()
				&& pollCounter.getType() == lastCounter.getType()) {
			if (pollCounter.getType() == 32) {
				log.warn(
						"overflow counter {}:{} current poll {} last value {}",
						sourceIpAddr, ifDescr, pollCounter, lastCounter);
				return COUNTER32_MAX_VALUE + pollCounter.getValue()
						- lastCounter.getValue();
			} else {
				log.warn(
						"source {} ifdescr {} overflow counter: current poll {} last value {}",
						sourceIpAddr, ifDescr, pollCounter, lastCounter);
				return 0L;
			}
		}

		return 0L;
	}

}
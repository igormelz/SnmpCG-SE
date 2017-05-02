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
import java.util.function.Consumer;

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
			// ifName[7]
			new OID("1.3.6.1.2.1.31.1.1.1.1"),
			// ifAlias[8]
			new OID("1.3.6.1.2.1.31.1.1.1.18") 
			};

	@Handler
	public void pollStatus(@Body SnmpSource source) throws Exception {

		log.info("source {}: poll status", source.getIpAddress());

		List<TableEvent> events = getTable(source, STATUS_OIDS);

		// update pollTime
		source.setPollTime(System.currentTimeMillis());

		// return if no response
		if (events == null) {
			return;
		}

		// get source info 
		VariableBinding vbs[] = events.get(0).getColumns();
		String uptime = vbs[0].getVariable().toString();
		source.setSysUptime(vbs[0].getVariable().toLong());
		source.setSysDescr(vbs[1].getVariable().toString());
		source.setSysName(vbs[2].getVariable().toString());
		source.setSysLocation(vbs[3].getVariable().toString());
		source.setStatus(SnmpSourceStatus.SUCCESS);

		// process ifEntry
		events.subList(1, events.size()).stream()
				.filter(event -> event != null)
				.forEach(new Consumer<TableEvent>() {
					int i = 1;
					public void accept(TableEvent event) {

						VariableBinding vb[] = event.getColumns();

						if (vb == null || vb.length <5)
							return;

						if (event.getColumns()[4] == null)
							return;
						
						String ifdescr = vb[4].getVariable().toString();
						SnmpInterface ifEntry = source.getSnmpInterface(ifdescr);

						// update ifindex 
						ifEntry.setIfIndex(i++);
						
						// update adminStatus
						if (vb[5] != null) {
							ifEntry.setIfAdminStatus(vb[5].getVariable()
									.toInt());
						}

						// update operStatus
						if (vb[6] != null) {
							ifEntry.setIfOperStatus(vb[6].getVariable().toInt());
						}

						// update ifName
						if (vb[7] != null && ifEntry.getIfName() == null) {
							ifEntry.setIfName(vb[7].getVariable().toString());
						}

						// update ifAlias
						if (vb[8] != null && ifEntry.getIfAlias() == null) {
							ifEntry.setIfAlias(vb[8].getVariable().toString());
						}
					}
				});
		log.info("source {}: status:SUCCESS, uptime:{}, ifNumber:{}", source.getIpAddress(),
				uptime, events.size() - 1);
	}

	@Handler
	public void pollCounters(@Body SnmpSource source) throws Exception {

		log.info("source {}: poll counters", source.getIpAddress());
		List<TableEvent> events = getTable(source, COUNTER_OIDS);

		// update pollTime
		source.setPollTime(System.currentTimeMillis());

		if (events == null)
			return;

		// process sysUpTime
		long sysUptime = events.get(0).getColumns()[0].getVariable().toLong();
		if (source.getSysUptime() > sysUptime) {
			log.warn("source {}: was rebooted between pool. Reset all counter",
					source.getIpAddress());
			// source.resetSnmpInterfaceCounters();
			// set default duration
			source.setPollDuration(0L);
		}

		// process ifEntry
		events.subList(1, events.size())
				.stream()
				.filter(event -> event != null)
				.forEach(
						event -> {
							VariableBinding vb[] = event.getColumns();
							
							if (vb == null || vb.length < 1 || vb[1] == null)
								return;
							
							String ifdescr = vb[1].getVariable().toString();
							SnmpInterface ifEntry = source.getSnmpInterface(ifdescr);
							
							// update AdminStatus
							if (vb[2] != null) {
								ifEntry.setIfAdminStatus(vb[2].getVariable().toInt());
							}
							
							// update OperStatus
							if (vb[3] != null) {
								ifEntry.setIfOperStatus(vb[3].getVariable().toInt());
							}
							
							// update counters if interface is up
							if (ifEntry.getIfAdminStatus() == 1 && ifEntry.getIfOperStatus() == 1) {
								
								// get bytes_in, bytes_out
								SnmpCounter pollInOctets = getCounterValue(vb[4], vb[5]);
								SnmpCounter pollOutOctets = getCounterValue(vb[6], vb[7]);
								
								// calculate delta counters for next success poll
								if (source.getPollDuration() != 0L) {
									ifEntry.setPollInOctets(calcDeltaCounter(source.getIpAddress(), ifdescr, pollInOctets, ifEntry.getIfInOctets()));
									ifEntry.setPollOutOctets(calcDeltaCounter(source.getIpAddress(), ifdescr, pollOutOctets, ifEntry.getIfOutOctets()));
								}
								
								// keep counter values
								ifEntry.setIfInOctets(pollInOctets);
								ifEntry.setIfOutOctets(pollOutOctets);
							}
						});

		// calc duration in timeticks
		source.setPollDuration(sysUptime - source.getSysUptime());
		
		// update sysUptime
		source.setSysUptime(sysUptime);

		log.info("source {}: poll processed: uptime:{}, ifNumber:{}", source.getIpAddress(),
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
			log.warn("source {}: ifdescr:{} - fake overflow counter: current={} last={}",
					sourceIpAddr, ifDescr, pollCounter, lastCounter);
			return 0L;
		}

		if (pollCounter.getValue() < lastCounter.getValue()
				&& pollCounter.getType() == lastCounter.getType()) {
			if (pollCounter.getType() == 32) {
				log.warn("source {}: ifdescr:{} - overflow counter: current={} last={}",
						sourceIpAddr, ifDescr, pollCounter, lastCounter);
				return COUNTER32_MAX_VALUE + pollCounter.getValue()
						- lastCounter.getValue();
			} else {
				log.warn("source {}: ifdescr:{} - overflow 64 bit counter: current={} last={}",
						sourceIpAddr, ifDescr, pollCounter, lastCounter);
				return 0L;
			}
		}

		return 0L;
	}

}
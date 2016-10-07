package org.openfs.snmpcg.counters;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openfs.snmpcg.utils.CsvField;

public final class PollDelta {

	private static final Logger log = LoggerFactory.getLogger(PollDelta.class);
	private static final long COUNTER32_MAX_VALUE = 4294967295L;

	private String snmp_host;
	private String snmp_index;
	private String snmp_descr;
	private PollCounter snmp_in;
	private PollCounter snmp_out;
	private long startTime;
	private long endTime;
	private long sysUptime;

	public PollDelta(PollData item) {
		// create null
		snmp_host = item.getIpAddress();
		snmp_index = item.getSnmpIndex();
		snmp_descr = item.getSnmpDescr();
		//
		snmp_in = item.getSnmpIn();
		snmp_out = item.getSnmpOut();
		//
		startTime = item.getPollTime();
		endTime = item.getPollTime();
		//
		sysUptime = item.getSnmpUptime();
	}

	public void calcDelta(Map<CsvField, Object> last) {
		sysUptime -= (Long) last.get(CsvField.sysUpTime);
		snmp_in = calcDeltaCounter(snmp_in,
				(PollCounter) last.get(CsvField.ifInOctets));
		snmp_out = calcDeltaCounter(snmp_out,
				(PollCounter) last.get(CsvField.ifOutOctets));
		startTime = (Long) last.get(CsvField.pollTime);
	}

	protected PollCounter calcDeltaCounter(PollCounter item, PollCounter last) {

		if (item.getValue() > last.getValue())
			return new PollCounter(item.getValue() - last.getValue(), 64);

		if (item.getValue() == 0 && last.getValue() > 0) {
			log.warn("Fake overflow delta counter for source (" + snmp_host
					+ ":" + snmp_descr + ") current poll (" + item
					+ ") last poll (" + last + ")");
			return new PollCounter(0L, item.getType());
		}

		if (item.getValue() < last.getValue()) {
			if (item.getType() == 32 && last.getType() == 32) {
				log.info("Overflow counter for source (" + snmp_host + ":"
						+ snmp_descr + ") current poll (" + item
						+ ") last poll (" + last + ")");
				return new PollCounter(COUNTER32_MAX_VALUE + item.getValue()
						- last.getValue(), 32);
			} else if (item.getType() == 64 && last.getType() == 64) {
				log.warn("Overflow counter64 for source (" + snmp_host + ":"
						+ snmp_descr + ") current poll (" + item
						+ ") last poll (" + last + ")");
				return new PollCounter(0L, 64);
			}
		}

		return new PollCounter(0L, item.getType());

	}

	public void setStartTime(long v) {
		startTime = v;
	}

	public void setSysUptime(long v) {
		sysUptime = v - sysUptime;
	}

	public long getSnmpIn() {
		return (startTime == endTime) ? 0L : snmp_in.getValue();
	}

	public long getSnmpOut() {
		return (startTime == endTime) ? 0L : snmp_out.getValue();
	}

	public long getSnmpUptime() {
		return sysUptime;
	}

	public String getSnmpIndex() {
		return snmp_index;
	}

	public String getSnmpDescr() {
		return snmp_descr;
	}

	public String getIpAddress() {
		return snmp_host;
	}

	public long getStartTime() {
		return startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public String toString() {
		return snmp_index + ":" + snmp_descr + ":" + snmp_in + ":" + snmp_out
				+ ":" + startTime + ":" + endTime + ":" + sysUptime;
	}
}

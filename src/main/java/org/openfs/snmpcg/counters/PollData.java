package org.openfs.snmpcg.counters;

import java.util.HashMap;
import java.util.Map;

import org.openfs.snmpcg.utils.CsvField;

public final class PollData {

	private String snmp_host;
	private String snmp_index;
	private String snmp_descr;
	private long createTime;
	private long sysUptime;
	private int adminStatus;

	private PollCounter snmp_in = new PollCounter(0, 32);
	private PollCounter snmp_out = new PollCounter(0, 32);
	
	public PollData(String host, String index, long st, long p, String descr) {
		snmp_host = host;
		snmp_index = index;
		snmp_descr = descr;
		createTime = p;
		sysUptime = st;
	}

	public void setAdminStatus(int s) {
		adminStatus = s;
	}
	
	public int getAdminStatus() {
		return adminStatus;
	}
	
	public void setSnmpIn(long v, int t) {
		snmp_in = new PollCounter(v, t);
	}

	public void setSnmpOut(long v, int t) {
		snmp_out = new PollCounter(v, t);
	}

	public PollCounter getSnmpIn() {
		return snmp_in;
	}

	public PollCounter getSnmpOut() {
		return snmp_out;
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
		return snmp_host.toString();
	}

	public Map<CsvField, Object> getCache() {
		Map<CsvField, Object> entry = new HashMap<CsvField, Object>();
		entry.put(CsvField.ifInOctets, snmp_in);
		entry.put(CsvField.ifOutOctets, snmp_out);
		entry.put(CsvField.pollTime, createTime);
		entry.put(CsvField.sysUpTime, sysUptime);
		return entry;
	}

	public long getPollTime() {
		return createTime;
	}

	public String toString() {
		return snmp_host + ":" + snmp_index + ":" + snmp_descr + ":" + snmp_in + ":" + snmp_out
				+ ":" + createTime + ":" + sysUptime + adminStatus;
	}
}

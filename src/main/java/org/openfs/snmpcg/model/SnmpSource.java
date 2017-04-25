package org.openfs.snmpcg.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.snmp4j.CommunityTarget;

public final class SnmpSource {
	private final String ipAddress;
	private final CommunityTarget target;
	private SnmpSourceStatus status = SnmpSourceStatus.UNKNOWN;
	private long sysUptime;
	private String sysDescr;
	private String sysName;
	private String sysLocation;
	private final Map<String,SnmpInterface> iftable = new HashMap<String,SnmpInterface>();
	private long pollTime;
	private long pollDuration;
	
	public SnmpSource(String ipAddress, CommunityTarget target) {
		this.ipAddress = ipAddress;
		this.target = target;
	}
	
	public SnmpSourceStatus getStatus() {
		return status;
	}

	public void setStatus(SnmpSourceStatus status) {
		this.status = status;
	}

	public CommunityTarget getTarget() {
		return target;
	}
	
	@Override
	public String toString() {
		return String.format("%s|%s|%s|%s"
				,ipAddress
				,target.getCommunity().toString()
				,status
				,sysName
				);
	}

	public String getSysDescr() {
		return sysDescr;
	}

	public void setSysDescr(String sysDescr) {
		this.sysDescr = sysDescr;
	}

	public String getSysName() {
		return sysName;
	}

	public void setSysName(String sysName) {
		this.sysName = sysName;
	}

	public long getSysUptime() {
		return sysUptime;
	}

	public void setSysUptime(long sysUptime) {
		this.sysUptime = sysUptime;
	}
	
	public String getSysLocation() {
		return sysLocation;
	}

	public void setSysLocation(String sysLocation) {
		this.sysLocation = sysLocation;
	}

	public void resetSnmpInterfaceCounters() {
		iftable.values().forEach(entry -> entry.resetCounters());
	}
	
	public SnmpInterface getSnmpInterface(String ifdescr) {
		SnmpInterface entry = iftable.get(ifdescr);
		if( entry == null ) {
			entry = new SnmpInterface(ifdescr);
			iftable.putIfAbsent(ifdescr, entry);
		}
		return entry;
	}
	
	public Map<String,SnmpInterface> getIftable() {
		return iftable;
	}
	
	public List<SnmpInterface> getInterfaces() {
		return iftable.values().stream()
				.filter(e -> e.isPolling())
				.collect(Collectors.toList());
	}
	
	public long getPollTime() {
		return pollTime;
	}

	public void setPollTime(long pollTime) {
		this.pollTime = pollTime;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public long getPollDuration() {
		return pollDuration;
	}

	public void setPollDuration(long pollDuration) {
		this.pollDuration = pollDuration;
	}

}

package org.openfs.snmpcg.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.snmp4j.CommunityTarget;

public final class SnmpSource implements Serializable {
	private static final long serialVersionUID = 5914824756678341630L;
	private final String ipAddress;
	private String status = SnmpSourceStatus.UNKNOWN;
	private long sysUptime;
	private String sysDescr;
	private String sysName;
	private String sysLocation;
	private final Map<String,SnmpInterface> iftable = new HashMap<String,SnmpInterface>();
	private long pollTime;
	private final String community;
	private final int retries;
	private final int timeout;
	private CommunityTarget target;
	private long pollDuration;
	private boolean skipDelta = true;
	private long pollResponse;
	
	public SnmpSource(String ipAddress, String community, int retries, int timeout) {
		this.ipAddress = ipAddress;
		this.community = community;
		this.timeout = timeout;
		this.retries = retries;
		this.target = SnmpCommunityTarget.createTarget(ipAddress, community, retries, timeout);
	}
	
	public String getStatus() {
		return status;
	}

	public CommunityTarget getTarget() {
		if (target == null) {
			target = SnmpCommunityTarget.createTarget(ipAddress, community, retries, timeout);
		}
		return target;
	}

	public String getSysDescr() {
		return sysDescr;
	}

	public String getSysLocation() {
		return sysLocation;
	}

	public SnmpInterface getSnmpInterface(String ifdescr) {
		SnmpInterface entry = iftable.get(ifdescr);
		if( entry == null ) {
			entry = new SnmpInterface(ifdescr);
			iftable.putIfAbsent(ifdescr, entry);
		}
		return entry;
	}

	public void removeSnmpInterace(String ifdescr) {
		if (iftable.containsKey(ifdescr)) {
			iftable.remove(ifdescr);
		}
	}
	
	public Map<String,SnmpInterface> getIftable() {
		return iftable;
	}

	public List<SnmpInterface> getInterfaces() {
		return iftable.values().stream()
				.collect(Collectors.toList());
	}

	public void setStatus(String status) {
		this.status = status;
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
	
	public void setSysLocation(String sysLocation) {
		this.sysLocation = sysLocation;
	}

	public void resetSnmpInterfaceCounters() {
		iftable.values().forEach(SnmpInterface::resetCounters);
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

	public boolean isSkipDelta() {
		return skipDelta;
	}

	public void setSkipDelta(boolean skipDelta) {
		this.skipDelta = skipDelta;
	}

	public long getPollResponse() {
		return pollResponse;
	}
	
	public void setPollResponse(long t) {
		pollResponse = t;
	}
}

package org.openfs.snmpcg.model;

import java.io.Serializable;

public final class SnmpInterface implements Serializable {
	private static final long serialVersionUID = 2654773100327667716L;
	private final String ifDescr;
	private int ifIndex;
	private String ifName;
	private String ifAlias;
	private int ifAdminStatus;
	private int ifOperStatus;
	private boolean polling = true;
	private boolean trace = false;
	private SnmpCounter ifInOctets = new SnmpCounter();
	private SnmpCounter ifOutOctets = new SnmpCounter();
	private long pollInOctets;
	private long pollOutOctets;
	transient private boolean marked = false;

	public SnmpInterface(String ifDescr) {
		this.ifDescr = ifDescr;
	}

	public int getIfAdminStatus() {
		return ifAdminStatus;
	}

	public void setIfAdminStatus(int ifAdminStatus) {
		// reset counters and set polling off
		if (ifAdminStatus != 1) {
			resetCounters();
		}
		this.ifAdminStatus = ifAdminStatus;
	}

	public void resetCounters() {
		ifInOctets.reset();
		ifOutOctets.reset();
		pollInOctets = 0L;
		pollOutOctets = 0L;
	}
	
	public boolean isPolling() {
		return polling;
	}

	public void setPolling(boolean polling) {
		// set trace off if not polling IF
		if (!polling && isTrace()) {
			trace = false;
		}
		this.polling = polling;
	}

	public SnmpCounter getIfInOctets() {
		return ifInOctets;
	}

	public void setIfInOctets(SnmpCounter ifInOctets) {
		this.ifInOctets = ifInOctets;
	}

	public SnmpCounter getIfOutOctets() {
		return ifOutOctets;
	}

	public void setIfOutOctets(SnmpCounter ifOutOctets) {
		this.ifOutOctets = ifOutOctets;
	}

	public String getIfName() {
		return ifName;
	}

	public void setIfName(String ifName) {
		this.ifName = ifName;
	}

	public String getIfAlias() {
		return ifAlias;
	}

	public void setIfAlias(String ifAlias) {
		this.ifAlias = ifAlias;
	}

	public String getIfDescr() {
		return ifDescr;
	}

	public long getPollInOctets() {
		return pollInOctets;
	}

	public void setPollInOctets(long pollInOctets) {
		this.pollInOctets = pollInOctets;
	}

	public long getPollOutOctets() {
		return pollOutOctets;
	}

	public void setPollOutOctets(long pollOutOctets) {
		this.pollOutOctets = pollOutOctets;
	}

	public boolean isTrace() {
		return trace;
	}

	public void setTrace(boolean trace) {
		this.trace = trace;
	}

	public int getIfOperStatus() {
		return ifOperStatus;
	}

	public void setIfOperStatus(int ifOperStatus) {
		this.ifOperStatus = ifOperStatus;
	}

	public int getIfIndex() {
		return ifIndex;
	}

	public void setIfIndex(int ifIndex) {
		this.ifIndex = ifIndex;
	}

	public boolean isUp() {
		return ifAdminStatus == 1 && ifOperStatus == 1;
	}

	public boolean isDown() {
		return !isUp();
	}
	
	public boolean isMarked() {
		return marked;
	}

	public void setMarked() {
		this.marked = true;
	}
}

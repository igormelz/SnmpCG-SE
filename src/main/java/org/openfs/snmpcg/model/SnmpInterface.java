package org.openfs.snmpcg.model;

import java.io.Serializable;

public final class SnmpInterface implements Serializable {
	private static final long serialVersionUID = 2654773100327667716L;
	private final String ifDescr;
	transient private int ifIndex;
	transient private String ifName;
	transient private String ifAlias;
	transient private int ifAdminStatus;
	transient private int ifOperStatus;
	private boolean chargeable = true;
	private boolean trace = false;
	private SnmpCounter ifInOctets = new SnmpCounter();
	private SnmpCounter ifOutOctets = new SnmpCounter();
	transient private long pollInOctets;
	transient private long pollOutOctets;
	transient private boolean marked = false;

	public SnmpInterface(String ifDescr) {
		this.ifDescr = ifDescr;
	}

	public int getIfAdminStatus() {
		return ifAdminStatus;
	}

	public void setIfAdminStatus(int ifAdminStatus) {
		// reset counters
		if (ifAdminStatus != 1) {
			resetPollCounters();
		}
		this.ifAdminStatus = ifAdminStatus;
	}

	public void resetCounters() {
		ifInOctets.reset();
		ifOutOctets.reset();
		resetPollCounters();
	}
	
	public void resetPollCounters() {
		pollInOctets = 0L;
		pollOutOctets = 0L;
	}
	
	public boolean isChargeable() {
		return chargeable;
	}

	public void setChargeable(boolean polling) {
		this.chargeable = polling;
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
		// reset counters
		if (ifOperStatus != 1) {
			resetPollCounters();
		}
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

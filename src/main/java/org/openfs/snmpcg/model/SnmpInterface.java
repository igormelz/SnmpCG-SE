package org.openfs.snmpcg.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public final class SnmpInterface implements Serializable {
    private static final long serialVersionUID = 2654773100327667716L;
    private final String ifDescr;
    private int ifIndex;
    private String ifName;
    private String ifAlias;
    private int ifAdminStatus;
    private int ifOperStatus;
    private boolean chargeable = false;
    private boolean trace = false;
    private SnmpCounter ifInOctets = new SnmpCounter();
    private SnmpCounter ifOutOctets = new SnmpCounter();
    private long pollInOctets;
    private long pollOutOctets;
    private boolean marked = false;
    private final Map<String, String> tags = new HashMap<String, String>();
    // ingress = 1 ; egress = 0
    private int chargeFlow = SnmpConstants.EGRESS;

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

    public void setChargeable(boolean chargeable) {
        this.chargeable = chargeable;
        if (!chargeable) {
            //tags.clear();
            // clear trace
            setTrace(false);
            // reset portType
            chargeFlow = SnmpConstants.EGRESS;
        }
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

    public void setMarked(boolean mark) {
        // reset counters
        if (mark) {
            resetPollCounters();
        }
        this.marked = mark;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void addTags(Map<String, String> tag) {
        tags.clear();
        tags.putAll(tag);
    }

    public void removeTags() {
        tags.clear();
    }


    public int getChargeFlow() {
        return chargeFlow;
    }

    public void setChargeFlow(int chargeFlow) {
        this.chargeFlow = chargeFlow;
    }
    
    public int getPortStatus() {
        return ifAdminStatus + ifOperStatus;
    }
}

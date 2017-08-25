package org.openfs.snmpcg.model;

import org.snmp4j.CommunityTarget;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OctetString;

public final class SnmpCommunityTarget {

    public static CommunityTarget createTarget(String ipaddr, String community, int retries, int timeout) {
        Address targetAddress = GenericAddress.parse("udp:" + ipaddr + "/161");
        return createTarget(targetAddress, community, retries, timeout);
    }

    public static CommunityTarget createTarget(Address targetAddress, String community, int retries, int timeout) {
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString(community));
        target.setVersion(SnmpConstants.version2c);
        target.setAddress(targetAddress);
        target.setRetries(retries);
        target.setTimeout(timeout * 1000L);
        return target;
    }
}

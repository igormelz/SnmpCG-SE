package org.openfs.snmpcg.utils;

import java.net.InetAddress;

import org.snmp4j.CommunityTarget;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;

public final class SnmpTargetFactory {

	public static CommunityTarget createTarget(InetAddress host, String community, int retries, int timeout) {
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString(community));
		target.setVersion(SnmpConstants.version2c);
		target.setAddress(new UdpAddress(host,161));
		target.setRetries(retries);
		target.setTimeout(timeout * 1000L);
		return target;
	}
	
}

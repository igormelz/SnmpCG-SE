package org.openfs.snmpcg.model;

/**
 * Created by ivanov_as on 13.02.2015.
 */
public enum SnmpSourceStatus {
	SUCCESS(0, "Success snmp response"), 
	TIMEOUT(1, "No snmp response (timeout)"), 
	NO_PDU(2, "No responsePDU (null)"), 
	OTHER_ERROR(3, "Response error"), 
	UNKNOWN(4, "Unknown"),
	NO_IFTABLE(5, "has no interfaces");

	private final int code;
	private final String message;

	SnmpSourceStatus(int code, String message) {
		this.code = code;
		this.message = message;
	}

	public int code() {
		return this.code;
	}

	public String message() {
		return this.message;
	}

	public boolean isUp() {
		return this.code == 0;
	}
	
	public boolean isDown() {
		return this.code != 0 && this.code != 2; 
	}
	
	static public boolean isMember(String aName) {
		SnmpSourceStatus[] aTags = SnmpSourceStatus.values();
		for (SnmpSourceStatus aTag : aTags)
			if (aTag.name().equalsIgnoreCase(aName))
				return true;
		return false;
	}

}

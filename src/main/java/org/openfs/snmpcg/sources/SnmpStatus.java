package org.openfs.snmpcg.sources;

/**
 * Created by ivanov_as on 13.02.2015.
 */
public enum SnmpStatus {
	SUCCESS(0, "Success snmp response"), TIMEOUT(1,
			"No snmp response (timeout)"), NO_PDU(2, "No responsePDU (null)"), OTHER_ERROR(
			3, "Response error"), UNKNOWN(4, "Unknown");

	private final int code;
	private final String message;

	SnmpStatus(int code, String message) {
		this.code = code;
		this.message = message;
	}

	public int code() {
		return this.code;
	}

	public String message() {
		return this.message;
	}

	static public boolean isMember(String aName) {
		SnmpStatus[] aTags = SnmpStatus.values();
		for (SnmpStatus aTag : aTags)
			if (aTag.name().equalsIgnoreCase(aName))
				return true;
		return false;
	}

}

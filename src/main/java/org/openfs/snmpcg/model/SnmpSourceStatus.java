package org.openfs.snmpcg.model;

import java.io.Serializable;

public final class SnmpSourceStatus implements Serializable {
	private static final long serialVersionUID = 5475789411767783737L;
	public final static String SUCCESS="SUCCESS"; 
	public final static String TIMEOUT="TIMEOUT"; 
	public final static String NO_PDU="NO PDU"; 
	public final static String OTHER_ERROR="OTHER ERROR"; 
	public final static String UNKNOWN="UNKNOWN";
	public final static String NO_IFTABLE="NO IFTABLE";
}

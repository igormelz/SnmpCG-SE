package org.openfs.snmpcg.model;

public final class SnmpCounter {
	private long value;
	private int type; // -1, 32,64

	public SnmpCounter() {
		value = 0L;
		type = 32;
	}
	
	public SnmpCounter(long v) {
		value = v;
		type = 32;
	}
	
	public SnmpCounter(long v, int t) {
		value = v;
		type = t;
	}
	
	public String toString() {
		return value + ":" + type;   
	}
	
	public long getValue() {
		return value;
	}
	
	public int getType() {
		return type;
	}

	public void setValue(long v) {
		value = v;
	}
	
	public void reset() {
		value = 0L;
		type = 32;
	}
	
	public void setType(int t) {
		if(t == 32 || t == 64) {
			type = t;
		}
	}
	
}

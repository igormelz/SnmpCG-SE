package org.openfs.snmpcg.counters;

public final class PollCounter {
	private long value;
	private int type; // -1, 32,64

	public PollCounter(long v, int t) {
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
/*
public boolean isType32() {
		return (type == 32);
	}
	
	public boolean isType64() {
		return (type == 64);
	}
*/
}

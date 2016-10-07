package org.openfs.snmpcg.sources;

public final class PollStatus {

	private int ifNumber;
	private long sysUptime;
	private long createTime = System.currentTimeMillis();
	
	public PollStatus() {
	}

	public void setIfNumber(int i) {
		ifNumber = i;
	}

	public void setSysUptime(long v) {
		sysUptime = v;
	}

	public long getSysUptime() {
		return sysUptime;
	}

	public int getIfNumer() {
		return ifNumber;
	}

	public long getPollTime() {
		return createTime;
	}

}

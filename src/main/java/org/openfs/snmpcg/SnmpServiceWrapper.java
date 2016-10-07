package org.openfs.snmpcg;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;

import org.openfs.snmpcg.counters.PollData;
import org.openfs.snmpcg.sources.PollStatus;
import org.openfs.snmpcg.sources.SnmpStatus;

import java.util.ArrayList;
import java.util.List;

public class SnmpServiceWrapper {

	private static final Logger log = LoggerFactory.getLogger("SnmpService");
	private static final String ifdescr = "1.3.6.1.2.1.2.2.1.2"; 
	private static final String ifNumber = "1.3.6.1.2.1.2.1.0";
	private static final String sysUptime = "1.3.6.1.2.1.1.3.0";
	private static final String sysUptimeT = "1.3.6.1.2.1.1.3";
	private static final String in32 = ".1.3.6.1.2.1.2.2.1.10.";
	private static final String out32 = ".1.3.6.1.2.1.2.2.1.16.";
	private static final String in64 = ".1.3.6.1.2.1.31.1.1.1.6.";
	private static final String out64 = ".1.3.6.1.2.1.31.1.1.1.10.";
	private static final String operStatus = ".1.3.6.1.2.1.2.2.1.8";
	private static final String adminStatus = ".1.3.6.1.2.1.2.2.1.7";

	private OID pollCounterOIDs[] = new OID[] {
	/* 0 */new OID(sysUptimeT),
	/* 1 */new OID(ifdescr),
	/* 2 */new OID(in32),
	/* 3 */new OID(out32),
	/* 4 */new OID(in64),
	/* 5 */new OID(out64) };

	private boolean isInSubTree(int[] rootoid, PDU pdu) {
		OID objID = pdu.get(0).getOid();
		if (objID == null) {
			return false;
		}

		int oid[] = objID.toIntArray();
		if (oid == null) {
			return false;
		}
		if (oid.length < rootoid.length) {
			return false;
		}

		for (int i = 0; i < rootoid.length; i++) {
			if (oid[i] != rootoid[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * collect snmp status (sysUpTime,ifNumber)
	 * 
	 * @param msg
	 * @throws Exception
	 */
	@Handler
	public void getSnmpStatus(Exchange msg) throws Exception {

		// processing headers
		if (msg.getIn().getBody() == null) {
			msg.getOut().setHeader(Exchange.ROUTE_STOP, "true");
			return;
		}

		Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
		snmp.listen();

		CommunityTarget target = msg.getIn().getBody(CommunityTarget.class);
		IpAddress ipAddress = new IpAddress(target.getAddress().toByteArray());
		msg.getOut().setHeader(CamelHeaders.HOST, ipAddress.toString());

		PDU pdu = new PDU();
		pdu.setType(PDU.GET);
		pdu.add(new VariableBinding(new OID(sysUptime))); /* 0 */
		pdu.add(new VariableBinding(new OID(ifNumber))); /* 1 */

		ResponseEvent response = snmp.get(pdu, target);

		SnmpStatus responseStatus = SnmpStatus.SUCCESS;
		PDU responsePDU = null;
		
		if (response == null) {
			responseStatus = SnmpStatus.TIMEOUT;
		} else {
			responsePDU = response.getResponse();
		}

		if ((responseStatus == SnmpStatus.SUCCESS) && (responsePDU == null)) {
			responseStatus = SnmpStatus.NO_PDU;
			Exception e = response.getError();
			if (e != null) 
				log.error("" + e);
		}

		if ((responseStatus == SnmpStatus.SUCCESS)
				&& (responsePDU.getErrorStatus() != PDU.noError)) {
			responseStatus = SnmpStatus.OTHER_ERROR;
			log.error("Snmp error description: " + responsePDU.getErrorStatusText());
		}

		if (responseStatus != SnmpStatus.SUCCESS) {
			// log.error(responseStatus.message() + ". Host:" + host);
			msg.getOut().setHeader(CamelHeaders.STATUS, responseStatus);
			msg.getOut().setHeader(CamelHeaders.UPTIME, 0L);
			snmp.close();
			return;
		}

		// for (VariableBinding vb : responsePDU.getVariableBindings())
		// log.info(vb.getVariable().toString());

		//List<String> answer = new ArrayList<String>();
		//answer.add(""+ responsePDU.getVariableBindings().get(0).getVariable().toLong());
		//answer.add(""+ responsePDU.getVariableBindings().get(1).getVariable().toInt());
		
		PollStatus answer = new PollStatus(); 
		answer.setIfNumber(responsePDU.getVariableBindings().get(1).getVariable().toInt());
		long st = responsePDU.getVariableBindings().get(0).getVariable().toLong();
		answer.setSysUptime(st);
		
		msg.getOut().setHeader(CamelHeaders.UPTIME, st);
		msg.getOut().setHeader(CamelHeaders.STATUS, SnmpStatus.SUCCESS);
		msg.getOut().setBody(answer);
		snmp.close();
	}

	/**
	 * get ifInOctets/ifOutOctets (Counter32) from host return arrayList<list:
	 * ipaddr|ifdescr|index|in32|out32|pollTime|sysUpTime set headers: snmp_host
	 * = msg.host snmp_status = snmp.status (success, fail, timeout)
	 * 
	 */
	@Handler
	public void getSnmpCounters(Exchange msg) throws Exception {

		Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
		snmp.listen();

		CommunityTarget target = msg.getIn().getBody(CommunityTarget.class);
		IpAddress ipAddress = new IpAddress(target.getAddress().toByteArray());
		msg.getOut().setHeader(CamelHeaders.HOST, ipAddress.toString());

		TableUtils tUtils = new TableUtils(snmp, new DefaultPDUFactory());
		List<TableEvent> events = tUtils.getTable(target, pollCounterOIDs,
				null, null);

		long st = 0L;
		long p = System.currentTimeMillis();
		SnmpStatus responseStatus = SnmpStatus.SUCCESS;
		List<PollData> iftable = new ArrayList<PollData>();

		for (TableEvent event : events) {

			if (event.isError()) {
				log.warn(event.getErrorMessage() + " source:" + ipAddress);
				responseStatus = SnmpStatus.TIMEOUT;
				// msg.getOut().setHeader(SnmpListHeaders.SNMP_HOST_STATUS,);
				// snmp.close();
				// return;
				break;
			}

			String index = event.getIndex().toString();
			if ("0".equalsIgnoreCase(index)) {
				st = event.getColumns()[0].getVariable().toLong();
			} else {
				VariableBinding vb[] = event.getColumns();
				if (vb[1] != null) {
					// ifDescr not null
					PollData pollData = new PollData(ipAddress.toString(), index, st, p,
							vb[1].getVariable().toString());

					// in value, type
					if (vb[2] != null && vb[4] != null) {
						if (vb[2].getVariable().toLong() > vb[4].getVariable()
								.toLong())
							pollData.setSnmpIn(vb[2].getVariable().toLong(), 32);
						else
							pollData.setSnmpIn(vb[4].getVariable().toLong(), 64);
					} else if (vb[4] != null) {
						pollData.setSnmpIn(vb[4].getVariable().toLong(), 64);
					} else if (vb[2] != null) {
						pollData.setSnmpIn(vb[2].getVariable().toLong(), 32);
					}

					// out value, type
					if (vb[3] != null && vb[5] != null) {
						// compare & set
						if (vb[3].getVariable().toLong() > vb[5].getVariable()
								.toLong())
							pollData.setSnmpOut(vb[3].getVariable().toLong(),
									32);
						else
							pollData.setSnmpOut(vb[5].getVariable().toLong(),
									64);

					} else if (vb[5] != null) {
						pollData.setSnmpIn(vb[5].getVariable().toLong(), 64);
					} else if (vb[3] != null) {
						pollData.setSnmpOut(vb[3].getVariable().toLong(), 32);
					}
					iftable.add(pollData);
				}
			}
		}
		
		msg.getOut().setHeader(CamelHeaders.UPTIME, st);
		msg.getOut().setHeader(CamelHeaders.STATUS, responseStatus);
		msg.getOut().setHeader(CamelHeaders.SIZE, iftable.size());
		msg.getOut().setBody(iftable);
		snmp.close();
	}

	/**
	 * get ifDescr for host in msg.
	 * 
	 * return is arrayList of string: ipaddr | index | ifdescr set headers:
	 * snmp_host = msg.host snmp_status = snmp.status (success, fail, timeout)
	 * 
	 * @param msg
	 * @throws Exception
	 */
	@Handler
	public void snmpGetNext(Exchange msg) throws Exception {

		// processing headers
		if (msg.getIn().getBody() == null)
			return;

		// Create TransportMapping and Listen
		Snmp snmp = new Snmp(new DefaultUdpTransportMapping());

		// read hostString from body
		CommunityTarget target = msg.getIn().getBody(CommunityTarget.class);
		String ip = target.getAddress().toString();
		String ipAddress = ip.substring(0, ip.indexOf('/'));
		msg.getOut().setHeader(CamelHeaders.HOST, ipAddress);

		// Create the PDU object
		PDU pdu = new PDU();
		pdu.setType(PDU.GETNEXT);
		pdu.add(new VariableBinding(new OID(ifdescr)));

		int rootoid[] = new OID(ifdescr).toIntArray();

		snmp.listen();

		List<String> answer = new ArrayList<String>();
		while (true) {

			ResponseEvent response = snmp.getNext(pdu, target);
			SnmpStatus responseStatus = SnmpStatus.SUCCESS;
			PDU responsePDU = null;
			if (response == null) {
				responseStatus = SnmpStatus.TIMEOUT;
			} else {
				responsePDU = response.getResponse();
			}

			if ((responseStatus == SnmpStatus.SUCCESS) && (responsePDU == null)) {
				responseStatus = SnmpStatus.NO_PDU;
			}

			if ((responseStatus == SnmpStatus.SUCCESS)
					&& (responsePDU.getErrorStatus() != PDU.noError)) {
				responseStatus = SnmpStatus.OTHER_ERROR;
				log.error("Snmp error description: "
						+ responsePDU.getErrorStatusText());
			}

			if (responseStatus != SnmpStatus.SUCCESS) {
				log.error(responseStatus.message() + ". Host:" + ipAddress);
				msg.getOut().setHeader(CamelHeaders.STATUS, responseStatus);
				snmp.close();
				return;
			}

			if (!isInSubTree(rootoid, responsePDU))
				break;

			VariableBinding b = responsePDU.get(0);
			// ipaddr | descr | index
			answer.add(ipAddress + "|" + b.getVariable().toString() + "|"
					+ b.getOid().toString().substring(ifdescr.length() + 1));

			pdu = new PDU();
			pdu.setType(PDU.GETNEXT);
			pdu.add(b);
		}
		msg.getOut().setHeader(CamelHeaders.STATUS, SnmpStatus.SUCCESS);
		msg.getOut().setBody(answer);
		snmp.close();
	}
}
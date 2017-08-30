package org.openfs.snmpcg;

import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.component.hazelcast.HazelcastConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.Snmp;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TableEvent;
import org.snmp4j.util.TableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.stereotype.Service;
import org.openfs.snmpcg.model.SnmpCounter;
import org.openfs.snmpcg.model.SnmpInterface;
import org.openfs.snmpcg.model.SnmpSource;
import org.openfs.snmpcg.model.SnmpConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service("snmpPoll")
public class SnmpPoll {
    private static final Logger log = LoggerFactory.getLogger(SnmpPoll.class);
    private static final long COUNTER32_MAX_VALUE = 4294967295L;
    private static final OID sysUpTimeOID = new OID(".1.3.6.1.2.1.1.3");
    private static final OID ifDescrOID = new OID(".1.3.6.1.2.1.2.2.1.2");
    private static final OID sysDescrOID = new OID(".1.3.6.1.2.1.1.1");
    private static final OID sysObjectIDOID = new OID(".1.3.6.1.2.1.1.2");
    private static final OID sysNameOID = new OID(".1.3.6.1.2.1.1.5");
    private static final OID sysLocationOID = new OID(".1.3.6.1.2.1.1.6");
    private static final OID ifNumberOID = new OID(".1.3.6.1.2.1.2.1");
    private static final OID IfInOctetsOID = new OID(".1.3.6.1.2.1.2.2.1.10");
    private static final OID IfOutOctestOID = new OID(".1.3.6.1.2.1.2.2.1.16");
    private static final OID ifAdminStatusOID = new OID(".1.3.6.1.2.1.2.2.1.7");
    private static final OID ifOperStatusOID = new OID(".1.3.6.1.2.1.2.2.1.8");
    private static final OID ifNameOID = new OID("1.3.6.1.2.1.31.1.1.1.1");
    private static final OID ifAliasOID = new OID("1.3.6.1.2.1.31.1.1.1.18");
    private static final OID ifHCInOctetsOID = new OID(".1.3.6.1.2.1.31.1.1.1.6");
    private static final OID ifHCOutOctetsOID = new OID(".1.3.6.1.2.1.31.1.1.1.10");
    private final static OID COUNTER_OIDS[] = new OID[] {sysUpTimeOID, ifDescrOID, IfInOctetsOID, ifHCInOctetsOID, IfOutOctestOID, ifHCOutOctetsOID, ifAdminStatusOID,
                                                         ifOperStatusOID, ifNameOID, ifAliasOID};
    private final static OID STATUS_OIDS[] = new OID[] {sysUpTimeOID, sysDescrOID, sysObjectIDOID, sysNameOID, sysLocationOID, ifNumberOID, ifDescrOID, ifAdminStatusOID,
                                                        ifOperStatusOID, ifNameOID, ifAliasOID};
    
    @Value("#{'${snmpcg.snmpVlanOids}'.split(';')}")
    private List<String> snmpVlanOids = new ArrayList<String>();
    
    private CounterService counterService;
    private Snmp snmp;

    @Autowired
    public SnmpPoll(CounterService counterService) throws IOException {
        this.counterService = counterService;
        snmp = new Snmp(new DefaultUdpTransportMapping());
        snmp.listen();
    }

    @Handler
    public void pollStatus(Exchange exchange) throws Exception {
        SnmpSource source = exchange.getIn().getBody(SnmpSource.class);
        // set Hazelcast Key
        exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, source.getIpAddress());

        if (log.isDebugEnabled()) {
            log.debug("source: {} poll status", source.getIpAddress());
        }

        long startPollTime = System.currentTimeMillis();
        List<TableEvent> events = getTable(source, STATUS_OIDS);

        // update pollTime
        long endPollTime = System.currentTimeMillis();
        source.setPollTime(endPollTime);
        source.setPollResponse((endPollTime - startPollTime));

        // return if no response
        if (events == null) {
            return;
        }

        // get source info
        VariableBinding vbs[] = events.get(0).getColumns();

        // process sysUpTime
        String uptime = vbs[0].getVariable().toString();
        validateSkipDelta(source, vbs[0].getVariable().toLong());
        source.setSysUptime(vbs[0].getVariable().toLong());

        // update system info
        source.setSysDescr(vbs[1].getVariable().toString());
        source.setSysObjectID(vbs[2].getVariable().toString());
        source.setSysName(vbs[3].getVariable().toString());
        source.setSysLocation(vbs[4].getVariable().toString());

        // validate ifNumber
        if (vbs[5] == null || (vbs[5] != null && vbs[5].getVariable().toInt() == 0)) {
            log.warn("source: {} has no interfaces", source.getIpAddress());
            counterService.increment("counter.snmp.logWarn");
            source.setStatus(SnmpConstants.NO_IFTABLE);
            return;
        }

        // set success status
        source.setStatus(SnmpConstants.SUCCESS);

        // proc auto vlanOids
        updateVlanOid(source);

        // process ifEntry
        events.subList(1, events.size()).stream().filter(event -> event != null && !event.isError()).forEach(event -> {

            if (event.getColumns() == null || event.getColumns().length < 6) {
                log.warn("source: {} no ifTable in response", source.getIpAddress());
                counterService.increment("counter.snmp.logWarn");
                return;
            }

            // validate ifDescr
            if (event.getColumns()[6] == null) {
                log.warn("source: {} no ifDescr for index: {}", source.getIpAddress(), event.getIndex().get(0));
                counterService.increment("counter.snmp.logWarn");
                return;
            }

            // get ifEntry
            SnmpInterface ifEntry = source.getSnmpInterface(event.getColumns()[6].getVariable().toString());

            // update ifName,ifAlias, ...
            updateIfEntry(ifEntry, event, 7);

        });
        log.info("source: {} update status: SUCCESS, uptime: {}, ifNumber: {}", source.getIpAddress(), uptime, events.size() - 1);
    }

    @Handler
    public void pollCounters(Exchange exchange) throws Exception {
        SnmpSource source = exchange.getIn().getBody(SnmpSource.class);
        if (log.isDebugEnabled()) {
            log.debug("source: {} poll counters", source.getIpAddress());
        }

        // set Hazelcast Key
        exchange.getIn().setHeader(HazelcastConstants.OBJECT_ID, source.getIpAddress());

        long startPollTime = System.currentTimeMillis();

        // validate vlanOID
        OID vlanOID = null;
        if (!source.getTags().isEmpty() && source.getTags().get("VLAN_OID") != null && !source.getTags().get("VLAN_OID").isEmpty()) {
            vlanOID = new OID(source.getTags().get("VLAN_OID"));
        }

        // get ifTable
        List<TableEvent> events = getTable(source, getCounterOIDs(vlanOID));

        // update pollTime
        long endPollTime = System.currentTimeMillis();
        source.setPollTime(endPollTime);
        source.setPollResponse((endPollTime - startPollTime));

        // return if no events was received
        if (events == null)
            return;

        // process sysUpTime
        long sysUptime = events.get(0).getColumns()[0].getVariable().toLong();
        if (!validateSkipDelta(source, sysUptime)) {
            source.setPollDuration(sysUptime - source.getSysUptime());
        }
        source.setSysUptime(sysUptime);

        // keep poll ifTable
        List<String> processedIF = new ArrayList<String>(events.size());

        // process ifEntry
        events.subList(1, events.size()).stream().filter(event -> event != null && !event.isError()).forEach(event -> {
            VariableBinding vb[] = event.getColumns();

            // validate ifDescr
            if (vb == null || vb.length < 1 || vb[1] == null) {
                log.warn("source: {} no ifDescr for index: {}", source.getIpAddress(), event.getIndex().get(0));
                counterService.increment("counter.snmp.logWarn");
                return;
            }

            // get ifEntry
            String ifdescr = vb[1].getVariable().toString();
            SnmpInterface ifEntry = source.getSnmpInterface(ifdescr);

            updateIfEntry(ifEntry, event, 6);

            // update vlanID
            if (vb.length > COUNTER_OIDS.length && vb[COUNTER_OIDS.length] != null) {
                String vlanid = vb[COUNTER_OIDS.length].getVariable().toString();
                // auto charge up iface for first time
                if (!ifEntry.getTags().containsKey("VLAN_ID")) {
                    if (ifEntry.isUp()) {
                        ifEntry.setChargeable(true);
                        log.info("source: {} interface ifdescr: {} set autocharge for vlan: {}", source.getIpAddress(), ifdescr, vlanid);
                    }
                } else if (!vlanid.equals(ifEntry.getTags().get("VLAN_ID"))) {
                    log.info("source: {} interface ifdescr: {} change vlan: {} to {}", source.getIpAddress(), ifdescr, ifEntry.getTags().get("VLAN_ID"), vlanid);
                }
                ifEntry.getTags().put("VLAN_ID", vlanid);
            }

            // get ifInOctets, ifOutOctets
            SnmpCounter bytes_in = getCounterValue(vb[2], vb[3]);
            SnmpCounter bytes_out = getCounterValue(vb[4], vb[5]);

            // calculate delta counters
            if (!source.isSkipDelta() && ifEntry.isUp()) {
                ifEntry.setPollInOctets(calcDeltaCounter(source.getIpAddress(), ifdescr, bytes_in, ifEntry.getIfInOctets()));
                ifEntry.setPollOutOctets(calcDeltaCounter(source.getIpAddress(), ifdescr, bytes_out, ifEntry.getIfOutOctets()));
            }

            // save counter values
            ifEntry.setIfInOctets(bytes_in);
            ifEntry.setIfOutOctets(bytes_out);

            // add to processed list
            processedIF.add(ifdescr);
        });

        // reset skipDelta
        if (source.isSkipDelta()) {
            source.setSkipDelta(false);
        }

        if (log.isDebugEnabled()) {
            log.debug("source: {} uptime: {}, ifNumber: {}", source.getIpAddress(), events.get(0).getColumns()[0].getVariable().toString(), events.size() - 1);
        }

        // validate source ifTable
        List<String> toremove = source.getIftable().keySet().stream().filter(ifdescr -> !processedIF.contains(ifdescr)).collect(Collectors.toList());
        // process not existing interfaces
        if (toremove != null && !toremove.isEmpty()) {
            for (String ifdescr : toremove) {
                log.warn("source: {} not found in response ifdescr: {}", source.getIpAddress(), ifdescr);
                counterService.increment("counter.snmp.logWarn");
                if (source.getSnmpInterface(ifdescr).isMarked()) {
                    source.removeSnmpInterace(ifdescr);
                    log.info("source: {} removed interface ifdescr: {}", source.getIpAddress(), ifdescr);
                } else {
                    source.getSnmpInterface(ifdescr).setMarked(true);
                }
            }
        }
        // reset marked for processed interfaces
        for (String ifdescr : processedIF) {
            if (source.getSnmpInterface(ifdescr).isMarked()) {
                source.getSnmpInterface(ifdescr).setMarked(false);
            }
        }
    }

    private List<TableEvent> getTable(SnmpSource source, OID[] oids) throws Exception {

        TableUtils tUtils = new TableUtils(snmp, new DefaultPDUFactory());
        List<TableEvent> events = tUtils.getTable(source.getTarget(), oids, null, null);

        // validate timeout
        if (events.size() == 1 && events.get(0).isError()) {
            log.error("source: {} {}", source.getIpAddress(), events.get(0).getErrorMessage());
            source.setStatus(SnmpConstants.TIMEOUT);
            counterService.increment("counter.snmp.logError");
            return null;
        }

        // validate NO PDU
        if (events == null || events.isEmpty()) {
            source.setStatus(SnmpConstants.NO_PDU);
            log.error("source: {} no responsePDU (null)", source.getIpAddress());
            counterService.increment("counter.snmp.logError");
            return null;
        }

        return events;
    }

    private SnmpCounter getCounterValue(VariableBinding vb32, VariableBinding vb64) {

        SnmpCounter counter = new SnmpCounter();

        if (vb32 != null && vb64 != null) {
            if (vb64.getVariable().toLong() > vb32.getVariable().toLong()) {
                counter.setValue(vb64.getVariable().toLong());
                counter.setType(64);
            } else {
                counter.setValue(vb32.getVariable().toLong());
            }
        } else if (vb64 != null) {
            counter.setValue(vb64.getVariable().toLong());
            counter.setType(64);
        } else if (vb32 != null) {
            counter.setValue(vb32.getVariable().toLong());
        }
        return counter;
    }

    private long calcDeltaCounter(String sourceIpAddr, String ifDescr, SnmpCounter pollCounter, SnmpCounter lastCounter) {

        if (pollCounter.getValue() > lastCounter.getValue()) {
            return pollCounter.getValue() - lastCounter.getValue();
        }

        if (pollCounter.getValue() == 0 && lastCounter.getValue() > 0) {
            log.warn("source: {} fake counter overflow on ifdescr '{}' (recv={}, last={})", sourceIpAddr, ifDescr, pollCounter, lastCounter);
            counterService.increment("counter.snmp.logWarn");
            return 0L;
        }

        if (pollCounter.getValue() < lastCounter.getValue() && pollCounter.getType() == lastCounter.getType()) {
            if (pollCounter.getType() == 32) {
                log.info("source: {} overflow 32bit counter on ifdescr '{}' (recv={}, last={})", sourceIpAddr, ifDescr, pollCounter, lastCounter);
                counterService.increment("counter.snmp.logInfo");
                return COUNTER32_MAX_VALUE + pollCounter.getValue() - lastCounter.getValue();
            } else {
                log.warn("source: {} overflow 64bit counter on ifdescr '{}' (recv={}, last={})", sourceIpAddr, ifDescr, pollCounter, lastCounter);
                counterService.increment("counter.snmp.logWarn");
                return 0L;
            }
        }
        return 0L;
    }

    private void updateIfEntry(SnmpInterface ifEntry, TableEvent event, int pos) {

        // update ifindex
        ifEntry.setIfIndex(event.getIndex().get(0));

        // update adminStatus
        if (event.getColumns()[pos] != null) {
            ifEntry.setIfAdminStatus(event.getColumns()[pos].getVariable().toInt());
        }

        // update operStatus
        if (event.getColumns()[pos + 1] != null) {
            ifEntry.setIfOperStatus(event.getColumns()[pos + 1].getVariable().toInt());
        }

        // update ifName
        if (event.getColumns()[pos + 2] != null) {
            ifEntry.setIfName(event.getColumns()[pos + 2].getVariable().toString());
        }

        // update ifAlias
        if (event.getColumns()[pos + 3] != null) {
            ifEntry.setIfAlias(event.getColumns()[pos + 3].getVariable().toString());
        }
    }

    private boolean validateSkipDelta(SnmpSource source, long sysUptime) {
        if (source.getSysUptime() != 0 && source.getSysUptime() > sysUptime) {
            log.warn("source: {} rebooted. Reset all counters", source.getIpAddress());
            counterService.increment("counter.snmp.logWarn");
            // reset Counters and skip calcDelta on next poll
            source.resetSnmpInterfaceCounters();
            source.setSkipDelta(true);
            return true;
        }
        return false;
    }

    private OID[] getCounterOIDs(OID vlanOID) {
        if (vlanOID != null) {
            OID[] counterOIDs = Arrays.copyOf(COUNTER_OIDS, COUNTER_OIDS.length + 1);
            counterOIDs[COUNTER_OIDS.length] = vlanOID;
            return counterOIDs;
        }
        return COUNTER_OIDS;
    }

    private void updateVlanOid(SnmpSource source) {
        final String enterprise = "1.3.6.1.4.1";

        if (source.getTags().get("VLAN_OID") == null) {
            int pos0 = source.getSysObjectID().indexOf(enterprise);
            char vendorId = source.getSysObjectID().substring(pos0 + enterprise.length() + 1).charAt(0);
            for (String value : snmpVlanOids) {
                int pos1 = value.indexOf(enterprise);
                if (vendorId == value.substring(pos1 + enterprise.length() + 1).charAt(0)) {
                    source.getTags().put("VLAN_OID", value);
                    log.info("source: {} set vlan_oid: {}", source.getIpAddress(),value);
                }
            }
        }
    }
}

# snmpCG
SNMP Charging Gateway 

## Features 
* Built-in [REST API](rest.md) to CRUD sources
* Run as single JavaApp for SNMP collect in/out counters from sources and out delta counters as csv CallDetailRecord
* Logging all events and validate conditions i.e. status sources, overflow counters
* Keep track    

## Release Notes

### 4.0.3:
* fix url in web 
* move snmpcg.xml to conf dir 

### 4.0.2:
* *snmpcg.config*: __REMOVE__ change [delimiter] to [log.delimiter] and [cdr.delimiter]
* *snmpcg.config*: do not use [initSrcDir] properties - fixed to in 
* add persist recovery data for sources and counter as text files in etc dirs: router.dat and cache.dat. If delete this, then no recovery state (initial)     

### 4.0.1:
* *snmpcg.config*: change [delimiter] to [log.delimiter] and [cdr.delimiter]   
* *snmpcg.config*: add [cdr.compress]. now supported __gzip__ or __none__

### 4.0.0:
-  

## Roadmap
1. extend REST API to work with community string (*pending*)
2. extend REST API to work on interfaces 
4. [LINUX] add influx datastore for keep and draw charts 
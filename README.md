# SnmpCG: SNMP Charging Gateway 

Dynamic SNMP collector with scheduled poll counters and generate charging data records (CDR) on delta counters value.  

## Features
* High performance collecting
* Built-in [REST API](rest-api.txt) for managing and provisioning the source list
* Logging events polling counters and validate source status
* Handle exceptions for counters overflow, reboot routers, restore after timeouts 
* Tracing counters for selected interface 

## Release Notes

### 5.0.0:
* refactoring to spring-boot application
* redesign Admin UI

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
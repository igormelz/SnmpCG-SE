package org.openfs.snmpcg;

import java.io.File;

import org.apache.camel.spring.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class Collector {

	private static final Logger log = LoggerFactory.getLogger(Collector.class);
	private Main main = new Main();
	private String confFile = "conf/snmpcg.xml";
	
	public static void main(String[] args) {
		Collector client = new Collector(args);
		System.exit(0);

	}

	private void parseArguments(String[] args) throws Exception {
		// -config file 
		for (int i = 0; i < args.length; i += 1) {
			if ("-config".equalsIgnoreCase(args[i]) && ++i < args.length)
				confFile = args[i];
		}
		File c = new File(confFile);
		if(!c.exists() && !c.isFile())
			throw new Exception("config file not found");
	}
	
	public Collector(String[] args) {
		try {
			parseArguments(args);
			runService();
		} catch (Exception e) {
			log.error("FileService", e);
			System.err.println("Error: Unknown exeption " + e);
			System.exit(1);
		}
	}

	private void runService() throws Exception {
		main.setFileApplicationContextUri("file:"+confFile);
		main.run();
	}

}

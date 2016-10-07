package org.openfs.snmpcg.utils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CsvUtils {

	public static Map<String, Object> createCsvRecord(
			Map<CsvField, Object> entry, CsvField[] headers) {

		Map<String, Object> record = new LinkedHashMap<String, Object>(
				headers.length);
		for (CsvField header : headers) {
			if (entry.containsKey(header))
				record.put(header.name(), entry.get(header));
		}
		return record;
	}

	public static CsvField[] parseCsvString(String csvString) {
		String[] csvs = csvString.split(",");
		CsvField[] headers = new CsvField[csvs.length];
		int i = 0;
		for (String c : csvs) {
			for (CsvField f : CsvField.values())
				if (c.equalsIgnoreCase(f.name())) {
					headers[i] = f;
					break;
				}
			i++;
		}
		return headers;
	}
}

package com.googlecode.dummyjdbc.statement.impl;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class InMemoryCSV {
	
	/**
	 * 
	 */
	static Map<String,String> values = new HashMap<String,String>();
		
	static void register(String testID, String value) {
		values.put(testID.toLowerCase().trim(), value.trim());
	}

	@SuppressWarnings("resource")
	static void register(String testID, InputStream valueStream) {
		// search for "end of stream" => read all the stream into the string !
		Scanner s = new Scanner(valueStream).useDelimiter("\\A");
	    String value = s.hasNext() ? s.next() : "";
	    s.close();
	    register(testID,value);
	}

	static void clear() {
		values.clear();
	}
	
	static String get(String testID) {
		return values.get(testID.toLowerCase().trim());
	}
}

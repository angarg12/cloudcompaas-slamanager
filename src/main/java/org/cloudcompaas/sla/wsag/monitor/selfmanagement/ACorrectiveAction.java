package org.cloudcompaas.sla.wsag.monitor.selfmanagement;

import java.util.HashMap;
import java.util.Map;

/**
 * @author angarg12
 *
 */
abstract public class ACorrectiveAction implements ICorrectiveAction {
	private static Map<String,Long> actionsTimestamp = new HashMap<String,Long>();
	// Defines a default value of 5. If any extending class wants to modify it, it must
	// be redeclared on it.
	protected int threshold = 5; 
						
	
	protected static synchronized void putActionTimestamp(String action, long timestamp){
		actionsTimestamp.put(action, timestamp);
	}
	
	protected static synchronized Long getActionTimestamp(String action){
		return actionsTimestamp.get(action);
	}
	
	public int getThreshold(){
		return threshold;
	}
}

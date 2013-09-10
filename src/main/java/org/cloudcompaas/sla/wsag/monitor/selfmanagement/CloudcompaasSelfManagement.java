package org.cloudcompaas.sla.wsag.monitor.selfmanagement;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.accounting.IAccountingContext;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermType;

/**
 * @author angarg12
 *
 */
public class CloudcompaasSelfManagement implements ISelfManagement {
	CloudcompaasAgreement agreement;
	Collection<ICorrectiveAction> correctiveActions;
	Map<String,Integer> consecutiveViolationCount = new HashMap<String,Integer>();
	
	public CloudcompaasSelfManagement(CloudcompaasAgreement agreement_){
		agreement = agreement_;
		/** TODO: Use a configuration file for loading correctiveActions, not using 
		 * this poor hardcoded method.
		 */
		correctiveActions = new Vector<ICorrectiveAction>();
		correctiveActions.add(new CloudcompaasReplicasCorrectiveAction());
		correctiveActions.add(new CloudcompaasUpscaleCorrectiveAction());
		correctiveActions.add(new CloudcompaasLUpscaleCorrectiveAction());
		correctiveActions.add(new CloudcompaasLDownscaleCorrectiveAction());
	}

	public void guaranteeNotDetermined(GuaranteeTermType guarantee) {
		consecutiveViolationPut(guarantee.getName(), 0);
	}
	
	public void guaranteeFulfilled(IAccountingContext context) {
		consecutiveViolationPut(context.getGuarantee().getName(), 0);
	}

	public void guaranteeViolated(IAccountingContext context) {
		consecutiveViolationPut(context.getGuarantee().getName(), consecutiveViolationGet(context.getGuarantee().getName())+1);
		
		Iterator<ICorrectiveAction> i = correctiveActions.iterator();
		while(i.hasNext()){
			ICorrectiveAction action = i.next();
			//System.out.println(action.getActionName()+" "+context.getGuarantee().getName());
			if(action.getActionName().equals(context.getGuarantee().getName())){
				//System.out.println("violation "+consecutiveViolationGet(context.getGuarantee().getName())+" out of "+action.getThreshold());
				if(consecutiveViolationGet(context.getGuarantee().getName()) >= action.getThreshold()){
					CorrectiveActionLauncher launcher = new CorrectiveActionLauncher(context, action);
					launcher.start();
					//consecutiveViolationCount.put(state.getName(), 0);
				}
			}
		}
	}	
    
	private void consecutiveViolationPut(String guaranteeName, int value){
		consecutiveViolationCount.put(guaranteeName, value);
	}
	
	private int consecutiveViolationGet(String guaranteeName){
		Integer consecutiveCount = consecutiveViolationCount.get(guaranteeName);
		if(consecutiveCount == null){
			consecutiveViolationPut(guaranteeName,0);
			return 0;
		}else{
			return consecutiveCount;
		}
	}
	
    private class CorrectiveActionLauncher extends Thread {
    	IAccountingContext context;
    	ICorrectiveAction action;
    	
    	public CorrectiveActionLauncher(IAccountingContext context_, ICorrectiveAction action_){
    		context = context_;
    		action = action_;
    	}
    	
    	public void run(){
            try {
            	action.apply(context);
    		} catch (Exception e) {
				// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }
}

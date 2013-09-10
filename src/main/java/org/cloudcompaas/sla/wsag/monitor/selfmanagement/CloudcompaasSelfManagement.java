/*******************************************************************************
 * Copyright (c) 2013, Andrés García García All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * (1) Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * (2) Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * 
 * (3) Neither the name of the Universitat Politècnica de València nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/
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

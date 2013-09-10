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

import java.util.Map;

import org.apache.xmlbeans.XmlObject;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.CCPaaSJexlContext;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.accounting.IAccountingContext;

/**
 * @author angarg12
 *
 */
public class CloudcompaasLUpscaleCorrectiveAction extends ACorrectiveAction {
	static final int COOLDOWN = 120000;
	String actionName = "L-UPSCALE";
	
	public String getActionName() {
		return actionName;
	}
	
	public void apply(IAccountingContext context) {
		try{
			CloudcompaasAgreement agreement = (CloudcompaasAgreement) context.getProperties().get("agreement");
			String actionId = agreement.getAgreementId()+"_LELASTICITY"; 
	
			
			if(getActionTimestamp(actionId) != null && (System.currentTimeMillis()-getActionTimestamp(actionId)) < COOLDOWN){
					return;
			}
			
			putActionTimestamp(actionId, System.currentTimeMillis());

			int replicasToDeploy = 1;
			@SuppressWarnings("unchecked")
			Map<String, Object> variables = (Map<String, Object>) context.getProperties().get("variables");
			CCPaaSJexlContext jexlContext = (CCPaaSJexlContext) variables.get("context");
			// We do a kind of 'hackish' way of checking if the guarantee is violated 'too much'
			// to perform a burst upscale. However the formal concept of 'violating little' or
			// 'violating much' does not exists, informally refers to wether the parameters 
			// barely went over the defined threshold or they went much further.
			/*
	        String exprLit = XmlString.Factory.parse(context.getGuarantee().getServiceLevelObjective().getKPITarget().getCustomServiceLevel().getDomNode()).getStringValue()+"*3";

            JexlEngine jexlengine = new JexlEngine();
	        Expression expr = jexlengine.createExpression( exprLit );

	        if(expr.evaluate(jexlContext) instanceof Boolean){
	        	if((Boolean)expr.evaluate(jexlContext) == false){
	        		replicasToDeploy = 2;
	        	}
	        }
	        exprLit = XmlString.Factory.parse(context.getGuarantee().getServiceLevelObjective().getKPITarget().getCustomServiceLevel().getDomNode()).getStringValue()+"*6";
	        expr = jexlengine.createExpression( exprLit );
	        if(expr.evaluate(jexlContext) instanceof Boolean){
	        	if((Boolean)expr.evaluate(jexlContext) == false){
	        		replicasToDeploy = 3;
	        	}
	        }
	        */
			for(int i = 0; i < context.getGuarantee().sizeOfServiceScopeArray(); i++){
            	XmlObject[] xpath = agreement.getXMLObject().selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
    					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
    					"//ws:ServiceDescriptionTerm/ccpaas:User/@ccpaas:Name");
            	
            	if(xpath == null || xpath.length < 1){
            		throw new Exception("Could not retrieve username.");
            	}

            	RESTComm comm = new RESTComm("Orchestrator");
            	comm.setUrl(agreement.getAgreementId()+
            			"/"+context.getGuarantee().getServiceScopeArray(i).getServiceName()+
            			"/ServiceTermState/Metadata/Replicas/RangeValue/Exact");
            	comm.setContentType("text/plain");
        		comm.put(String.valueOf(replicasToDeploy));
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}

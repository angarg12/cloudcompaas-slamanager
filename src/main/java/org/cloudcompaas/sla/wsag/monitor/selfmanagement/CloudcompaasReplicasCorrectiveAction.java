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

import org.apache.xmlbeans.XmlInteger;
import org.apache.xmlbeans.XmlObject;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.accounting.IAccountingContext;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateType;

/**
 * @author angarg12
 *
 */
public class CloudcompaasReplicasCorrectiveAction extends ACorrectiveAction {
	static final int COOLDOWN = 260000;
	String actionName = "REPLICAS";
	
	public String getActionName() {
		return actionName;
	}
	
	public void apply(IAccountingContext context) {
		try{
			CloudcompaasAgreement agreement = (CloudcompaasAgreement) context.getProperties().get("agreement");
			String actionId = agreement.getAgreementId()+"_"+context.getGuarantee().getName(); 
			if(getActionTimestamp(actionId) != null && (System.currentTimeMillis()-getActionTimestamp(actionId)) < COOLDOWN){
					return;
			}
			
			putActionTimestamp(actionId, System.currentTimeMillis());
			
			Integer exact = null;
			Integer lowerBound = null;
			Integer upperBound = null;
			Integer current = null;
			for(int i = 0; i < context.getGuarantee().sizeOfServiceScopeArray(); i++){
		        String xpath = "declare namespace ws='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
		        "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
		        "//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = '" + context.getGuarantee().getServiceScopeArray(i).getServiceName() + "']/ccpaas:Metadata" +
		        "/ccpaas:Replicas/ccpaas:RangeValue/ccpaas:Exact";
		
				XmlObject[] result = agreement.getXMLObject().selectPath(xpath);
				
				if (result.length == 0) {
					 xpath = "declare namespace ws='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
				        "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
				        "//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = '" + context.getGuarantee().getServiceScopeArray(i).getServiceName() + "']/ccpaas:Metadata" +
				        "/ccpaas:Replicas/ccpaas:RangeValue/ccpaas:Range/ccpaas:LowerBound";
						
					 result = agreement.getXMLObject().selectPath(xpath);
						
					if (result.length == 0) {
						return;
					}
					lowerBound = Integer.parseInt(XmlInteger.Factory.parse(result[0].getDomNode()).getStringValue());
					
					 xpath = "declare namespace ws='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
				        "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
				        "//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = '" + context.getGuarantee().getServiceScopeArray(i).getServiceName() + "']/ccpaas:Metadata" +
				        "/ccpaas:Replicas/ccpaas:RangeValue/ccpaas:Range/ccpaas:UpperBound";
						
					 result = agreement.getXMLObject().selectPath(xpath);
						
					if (result.length == 0) {
						return;
					}
					upperBound = Integer.parseInt(XmlInteger.Factory.parse(result[0].getDomNode()).getStringValue());
				}else{
					exact = Integer.parseInt(XmlInteger.Factory.parse(result[0].getDomNode()).getStringValue());
				}
				
				xpath = "declare namespace ws='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
		        "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
		        "//ws:ServiceTermState[" +
		        "//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = '" + context.getGuarantee().getServiceScopeArray(i).getServiceName() + "']/ccpaas:Metadata/../@ws:Name=@ws:termName]/ws:State";
		
				result = agreement.getXMLObject().selectPath(xpath);
				
				if(result.length == 0) {
					return;
				}
				
				ServiceTermStateType termState = ServiceTermStateType.Factory.parse(result[0].getDomNode()); 
				if(termState.getState().equals(ServiceTermStateDefinition.READY) == false){
					return;
				}
				
				xpath = "declare namespace ws='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
		        "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
		        "//ws:ServiceTermState[" +
		        "//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = '" + context.getGuarantee().getServiceScopeArray(i).getServiceName() + "']/ccpaas:Metadata/../@ws:Name=@ws:termName]/ccpaas:Metadata" +
		        "/ccpaas:Replicas/ccpaas:RangeValue/ccpaas:Exact";
		
				result = agreement.getXMLObject().selectPath(xpath);
				
				if(result.length == 0) {
					return;
				}
				current = Integer.parseInt(XmlInteger.Factory.parse(result[0].getDomNode()).getStringValue());

            	XmlObject[] xpathSelection = agreement.getXMLObject().selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
    					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
    					"//ws:ServiceDescriptionTerm/ccpaas:User/@ccpaas:Name");
            	
            	if(xpathSelection == null || xpathSelection.length < 1){
            		throw new Exception("Could not retrieve username.");
            	}

            	RESTComm comm = new RESTComm("Orchestrator");
            	String baseUrl = "/"+agreement.getAgreementId()+
            			"/"+context.getGuarantee().getServiceScopeArray(i).getServiceName()+
            			"/ServiceTermState/Metadata/Replicas/RangeValue/Exact";
            	comm.setUrl(baseUrl);
            	// im pretty sure this can be optimized and shortened in some way, 
            	// if not at least factoring the code into smaller functions
				if(exact != null){
					if(current < exact){
						// deploy instances
						int difference = exact-current;
						comm.setContentType("text/plain");
						comm.put(String.valueOf(difference));
					} else if(current > exact){
						// undeploy instances
						int difference = current-exact;
						baseUrl += "/"+difference;
						comm.setUrl(baseUrl);
		            	comm.delete();
					}
				}else{
					if(current < lowerBound){
						// deploy instances
						int difference = lowerBound-current;
						comm.setContentType("text/plain");
						comm.put(String.valueOf(difference));
					} else if(current > upperBound){
						// undeploy instances
						int difference = current-upperBound;
						baseUrl += "/"+difference;
						comm.setUrl(baseUrl);
		            	comm.delete();
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}

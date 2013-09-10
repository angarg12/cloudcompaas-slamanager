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

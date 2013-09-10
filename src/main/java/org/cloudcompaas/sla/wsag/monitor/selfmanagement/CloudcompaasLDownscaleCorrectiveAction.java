package org.cloudcompaas.sla.wsag.monitor.selfmanagement;

import org.apache.xmlbeans.XmlObject;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.accounting.IAccountingContext;

/**
 * @author angarg12
 *
 */
public class CloudcompaasLDownscaleCorrectiveAction extends ACorrectiveAction {
	static final int COOLDOWN = 30000;
	String actionName = "L-DOWNSCALE";

	public CloudcompaasLDownscaleCorrectiveAction(){
		threshold = 15; 
	}
	
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
            			"/ServiceTermState/Metadata/Replicas/RangeValue/Exact/1");
        		comm.delete();
			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}

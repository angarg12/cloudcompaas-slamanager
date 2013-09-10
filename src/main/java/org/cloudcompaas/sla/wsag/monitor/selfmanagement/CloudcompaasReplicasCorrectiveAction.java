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

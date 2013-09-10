package org.cloudcompaas.sla.wsag.monitor.serviceterm;

import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.XMLWrapper;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateType;

/**
 * @author angarg12
 *
 */
public class CloudcompaasVirtualContainerMonitoringHandler  implements IServiceTermMonitoringHandler {
	CloudcompaasAgreement agreementInstance;
	
	public CloudcompaasVirtualContainerMonitoringHandler(CloudcompaasAgreement agreementInstance_){
		agreementInstance = agreementInstance_;
	}
	
	public void monitor(IMonitoringContext monitoringContext) throws Exception {
    	ServiceDescriptionTermType[] terms = agreementInstance.getTerms().getAll().getServiceDescriptionTermArray();
    	ServiceTermStateType state = null;
    	
    	for(int i = 0; i < terms.length; i++){
    		try{
	    		if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
	    			state = monitoringContext.getServiceTermStateByName(terms[i].getName());
	    		}
    		}catch(Exception e){}
    	}
    	
    	if(state == null){
			return;
    	}
    	RESTComm comm = new RESTComm("Catalog");
    	comm.setUrl("vr_instance/search?local_sdt_id="+state.getTermName()+"&id_sla="+agreementInstance.getAgreementId());
    	XMLWrapper wrap = comm.get();

		if(wrap.get("//vr_instance").length > 0){
	    	state.setState(ServiceTermStateDefinition.READY);
		}
	}
}

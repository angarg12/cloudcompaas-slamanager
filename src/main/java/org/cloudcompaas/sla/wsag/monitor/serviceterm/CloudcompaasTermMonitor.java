package org.cloudcompaas.sla.wsag.monitor.serviceterm;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;

/**
 * @author angarg12
 *
 */
public class CloudcompaasTermMonitor {
	CloudcompaasAgreement agreementInstance;
	
	public CloudcompaasTermMonitor(CloudcompaasAgreement agreementInstance_){
		agreementInstance = agreementInstance_;
	}
	
    /* (non-Javadoc)
     * @see org.ogf.graap.wsag.server.api.IServiceTermMonitoringHandler#monitor(org.ogf.graap.wsag.server.api.IMonitoringContext)
     */
    public void monitor(IMonitoringContext monitoringContext) throws Exception {
    	IMonitoringContext clonedContext = (IMonitoringContext) monitoringContext.clone();
        //
        // let the monitoring handler update the states using the current context 
        //
        for (int i = 0; i < monitoringContext.getMonitoringHandler().length; i++) {
            IServiceTermMonitoringHandler handler = null;
            try {
                handler = monitoringContext.getMonitoringHandler()[i];
                handler.monitor(clonedContext);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        
		Properties properties = new Properties();
    	RESTComm comm = new RESTComm("Catalog");
    	for(int i = 0; i < clonedContext.getServiceTermStates().length; i++){
    		properties.put("state", clonedContext.getServiceTermStates()[i].xmlText());
    		
    		String payload;
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		properties.storeToXML(baos, null);
    		payload = baos.toString();

        	comm.setUrl("service_description_terms/search?id_sla="+agreementInstance.getAgreementId()+"&local_sdt_id="+clonedContext.getServiceTermStates()[i].getTermName());
        	comm.setContentType("text/xml");
        	comm.put(payload);
    	}

		agreementInstance.setServiceTermStates(clonedContext.getServiceTermStates());
    }
}
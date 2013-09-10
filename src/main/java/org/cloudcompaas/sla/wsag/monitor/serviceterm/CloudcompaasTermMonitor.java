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

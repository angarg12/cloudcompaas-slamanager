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

import org.apache.xmlbeans.GDuration;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.schemas.graap.wsAgreement.AgreementStateDefinition;

/**
 * @author angarg12
 *
 */
public class CloudcompaasServiceTermJob extends Thread {
    private IMonitoringContext monitoringContext;
    private long sleeptime = 0;
    private boolean active = true;
    private CloudcompaasTermMonitor termMonitor; 
    CloudcompaasAgreement agreementInstance;
    
    public CloudcompaasServiceTermJob(IMonitoringContext monitoringContext_, String expression, CloudcompaasAgreement agreementInstance_){
    	GDuration duration = new GDuration(expression);
    	monitoringContext = monitoringContext_;
    	sleeptime += duration.getSecond()*1000;
    	sleeptime += duration.getMinute()*1000*60;
    	sleeptime += duration.getHour()*1000*60*60;
    	sleeptime += duration.getDay()*1000*60*60*24;
    	sleeptime += duration.getMonth()*1000*60*60*24*30;
    	sleeptime += duration.getYear()*1000*60*60*24*365;
    	agreementInstance = agreementInstance_;
    	termMonitor = new CloudcompaasTermMonitor(agreementInstance_);
    }

	public void run() {
        while(active){
            try {
	        	agreementInstance.pull();
				
	        	//System.out.println(sleeptime+" "+agreementInstance.getState().getState()+" "+agreementInstance.getState().getState().equals(AgreementStateDefinition.TERMINATED));
	        	if(agreementInstance.getState().getState().equals(AgreementStateDefinition.COMPLETE) ||
	        			agreementInstance.getState().getState().equals(AgreementStateDefinition.TERMINATED) ||
	        			agreementInstance.getState().getState().equals(AgreementStateDefinition.REJECTED)){
	                //
	                // if the agreement is in complete or terminated state, 
	                // end the monitoring job
	                //
	                return;
	            }
	
	        	termMonitor.monitor(monitoringContext);
	        	Thread.sleep(sleeptime);
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
        }
	}
	
	public void finalize(){
		active = false;
	}
}

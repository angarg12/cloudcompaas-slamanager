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
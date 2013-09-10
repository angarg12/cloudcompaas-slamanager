package org.cloudcompaas.sla.wsag.monitor.guaranteeterm;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import org.apache.xmlbeans.GDuration;
import org.apache.xmlbeans.XmlObject;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.CloudcompaasList;
import org.cloudcompaas.common.util.XMLBinding;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.monitoring.IGuaranteeEvaluator;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.schemas.graap.wsAgreement.AgreementStateDefinition;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermStateType;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermType;
import org.ogf.schemas.graap.wsAgreement.ServicePropertiesType;
import org.ogf.schemas.graap.wsAgreement.ServiceSelectorType;
import org.ogf.schemas.graap.wsAgreement.VariableType;

/**
 * @author angarg12
 *
 */
public class CloudcompaasGuaranteeTermJob extends Thread {
    List<GuaranteeTermType> guarantees;
    IGuaranteeEvaluator evaluator;
    long sleeptime = 0;
    boolean active = true;
    IMonitoringContext monitoringContext;
    CloudcompaasAgreement agreementInstance;
    
    public CloudcompaasGuaranteeTermJob(IMonitoringContext monitoringContext_, List<GuaranteeTermType> guarantees_, String expression, CloudcompaasAgreement agreementInstance_){
    	GDuration duration = new GDuration(expression);
    	guarantees = guarantees_;
    	sleeptime += duration.getSecond()*1000;
    	sleeptime += duration.getMinute()*1000*60;
    	sleeptime += duration.getHour()*1000*60*60;
    	sleeptime += duration.getDay()*1000*60*60*24;
    	sleeptime += duration.getMonth()*1000*60*60*24*30;
    	sleeptime += duration.getYear()*1000*60*60*24*365;
    	monitoringContext = monitoringContext_;
    	agreementInstance = agreementInstance_;
    }
    
	public void run() {
		try{
        	evaluator = new CloudcompaasGuaranteeEvaluator(agreementInstance);
	        while(active){
		        Thread.sleep(sleeptime);
	        	if(agreementInstance.getState().getState().equals(AgreementStateDefinition.COMPLETE) ||
	        			agreementInstance.getState().getState().equals(AgreementStateDefinition.TERMINATED) ||
	        			agreementInstance.getState().getState().equals(AgreementStateDefinition.REJECTED)){
	        		return;
	        	}
            	Iterator<GuaranteeTermType> i = guarantees.iterator();
                List<GuaranteeTermStateType> guaranteesStates = new Vector<GuaranteeTermStateType>();
	            //
	            // Now evaluate the guarantee term state 
	            //
	            while(i.hasNext()){
	                	GuaranteeTermType guarantee = i.next();
	                try {
	                	GuaranteeTermStateType guaranteeState = updateGuaranteeTermState(guarantee);
	                	guaranteesStates.add(guaranteeState);
	                } catch (Exception e) {
	                	e.printStackTrace();
	                    //log.error("Update guarantee term states failed for term "+guarantee.getName()+". Reason: "+e.getMessage());
	                }
	            }
	    		
	    		Iterator<GuaranteeTermStateType> it = guaranteesStates.iterator();
	    		Properties properties = new Properties();
	        	RESTComm comm = new RESTComm("Catalog");
	        	while(it.hasNext()){
	        		GuaranteeTermStateType state = it.next();
	        		properties.put("state", state.xmlText());
	        		
	        		String payload;
	        		ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        		properties.storeToXML(baos, null);
	        		payload = baos.toString();

		        	comm.setUrl("guarantee_terms/search?id_sla="+agreementInstance.getAgreementId()+"&local_guarantee_id="+state.getTermName());
		        	comm.setContentType("text/xml");
		        	comm.put(payload);
	        	}
                //
                // update the agreement property document at once 
                //
	            agreementInstance.setGuaranteeTermStates(guaranteesStates.toArray(new GuaranteeTermStateType[0]));
	        }
		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
    private GuaranteeTermStateType updateGuaranteeTermState(GuaranteeTermType guarantee) throws Exception {     
        Map<String, Object> variableMap = new HashMap<String, Object>();

        variableMap.put("list", new CloudcompaasList());
        for (int i = 0; i < guarantee.getServiceScopeArray().length; i++) {
            //
            // for each service we should only have one instance 
            // of service properties defined 
            //
            ServiceSelectorType scope        = guarantee.getServiceScopeArray(i);
            ServicePropertiesType properties = loadServiceProperties(scope.getServiceName());
            
            VariableType[] variables = properties.getVariableSet().getVariableArray();

            for (int j = 0; j < variables.length; j++) {
                try {
	                String xpath         = variables[j].getLocation();
	                String variableName  = variables[j].getName();
	                String metric 		 = variables[j].getMetric();
	
	                //
	                // TODO: implement proper deserialization mechanism
	                //
                    //System.out.println(variableName);
                    //System.out.println(xpath);
                    XmlObject[] values = agreementInstance.getXMLObject().selectPath(xpath);

                    if(values.length == 1){
                        Object variableValue = XMLBinding.bind(metric, values[0]); 
                        // and put data in the variable map
                        if (!variableMap.containsKey(variableName)) {
                            variableMap.put(variableName, variableValue);
                        }
                    } else if(values.length > 1){
                    	Object[] variableValue = new Object[values.length];
                    	for(int k = 0; k < values.length; k++){
                    		variableValue[k] = XMLBinding.bind(metric, values[k]);  
                    	}
                        // and put data in the variable map
                        if (!variableMap.containsKey(variableName)) {
                            variableMap.put(variableName, variableValue);
                        }
                    }
                } catch (Exception ex) {
                	ex.printStackTrace();

                    //System.out.println(agreementInstance.getXMLObject().xmlText());
                    //log.warn(MessageFormat.format("Could not resolve variable {0} for [{1}:{2}]. Resolve value to null.", new Object[] { variableName, properties.getServiceName(), properties.getName() }));
                }
                 
            }
        }        
        return evaluator.evaluate(guarantee, variableMap);
    }
    
    // MAY need to be synchronyzed
    private ServicePropertiesType loadServiceProperties(String serviceName) throws Exception {
        String xpath = "declare namespace wsag='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
                       "//wsag:Terms/wsag:All/wsag:ServiceProperties[ @wsag:ServiceName = '" + serviceName + "']";
        
        XmlObject[] result = agreementInstance.getXMLObject().selectPath(xpath);
        
        if (result.length == 0) {
            //log.warn(MessageFormat.format("No service properties found with name {0}. Possible SLA design issue.", new Object[] { serviceName  }));
            return null;
        }

        if (result.length > 1) {
            //log.warn(MessageFormat.format("Multiple service properties found with name {0}. Possible SLA design issue.", new Object[] { serviceName }));
        }
        
        return (ServicePropertiesType) result[0];
    }
	
	public void finalize(){
		active = false;
	}
}
package org.cloudcompaas.sla.wsag.monitor.serviceterm;

import java.io.ByteArrayInputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.XMLWrapper;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.cloudcompaas.sla.wsag.monitor.CloudcompaasMonitor;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDocument;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 * @author angarg12
 *
 */
public class CloudcompaasMetadataMonitoringHandler  implements IServiceTermMonitoringHandler {
	CloudcompaasAgreement agreementInstance;
	
	public CloudcompaasMetadataMonitoringHandler(CloudcompaasAgreement agreementInstance_){
		agreementInstance = agreementInstance_;
	}
	
	public void monitor(IMonitoringContext monitoringContext) throws Exception {
    	ServiceDescriptionTermType[] terms = agreementInstance.getTerms().getAll().getServiceDescriptionTermArray();
    	ServiceTermStateType state = null;
    	
    	for(int i = 0; i < terms.length; i++){
    		try{
	    		if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:Metadata")){
	    			state = monitoringContext.getServiceTermStateByName(terms[i].getName());
	    		}
    		}catch(Exception e){}
    	}
    	
    	if(state == null){
			return;
    	}
    	
    	try{
	    	state.setState(ServiceTermStateDefinition.READY);
	    	ServiceTermStateDocument stsd = ServiceTermStateDocument.Factory.newInstance();
	    	stsd.setServiceTermState(state);
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			builderFactory.setNamespaceAware(true);
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document doc = builder.parse(new InputSource(new ByteArrayInputStream(stsd.xmlText().getBytes("utf-8"))));
			
			//the monitoring data is associated with the virtual machine term, so we must retrieve its id and query the system with it
			String currentTermServiceName = null;
			String virtualMachineTermName = null;
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getName().equals(state.getTermName())){
					currentTermServiceName = terms[i].getServiceName();
				}
			}
			for(int i = 0; i < terms.length; i++){
				if(terms[i].getServiceName().equals(currentTermServiceName)
						&& terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
					virtualMachineTermName = terms[i].getName();
				}
			}
			
        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("/monitoring_information/search?local_sdt_id="+virtualMachineTermName+"&id_sla="+agreementInstance.getAgreementId());
    		XMLWrapper wrap = comm.get();
			
    		String[] epr = wrap.get("//epr");
    		String[] timestamp = wrap.get("//timestamp");
    		
    		Map<String, Date> lastReports = new HashMap<String, Date>();
			DateFormat format = new SimpleDateFormat("yyyy'-'MM'-'dd HH:mm:ss.SSS");
    		for(int i = 0; i < epr.length; i++){
				Date storedReport = lastReports.get(epr[i]);
				Date lastReport = format.parse(timestamp[i]);
				if(storedReport == null || storedReport.getTime() < lastReport.getTime()){
					lastReports.put(epr[i], lastReport);
				}
    		}
			
			int deployedCount = 0;
			Iterator<String> it = lastReports.keySet().iterator();
			while(it.hasNext()){
				String key = it.next();
				Date storedReport = lastReports.get(key);
				Date now = new Date();
				long difference = now.getTime() - storedReport.getTime();
				// monitoring poll is 2 min, so we tolerate a 3 min difference
				if(difference > CloudcompaasMonitor.TOLERANCE){
					// if monitoring information is too old, is because that replica is not alive anymore
					continue;
				}
				deployedCount++;
			}
			Text text = doc.createTextNode(String.valueOf(deployedCount));
			Element el1 = doc.createElementNS("http://www.grycap.upv.es/cloudcompaas", "Exact");
			el1.appendChild(text);
	    	Element el2 = doc.createElementNS("http://www.grycap.upv.es/cloudcompaas", "RangeValue");
			el2.appendChild(el1);
			el1 = doc.createElementNS("http://www.grycap.upv.es/cloudcompaas", "Replicas");
			el1.appendChild(el2);
			el2 = doc.createElementNS("http://www.grycap.upv.es/cloudcompaas", "Metadata");
			el2.appendChild(el1);
			
			doc.getFirstChild().appendChild(el2);
			stsd = ServiceTermStateDocument.Factory.parse(doc.getFirstChild());

			XmlOptions xmlOptions = new XmlOptions();
			xmlOptions.setDocumentType(state.schemaType());
			state.set(XmlObject.Factory.parse(stsd.getServiceTermState().xmlText(), xmlOptions));
    	}catch(Exception e){
			e.printStackTrace();
			state.setState(ServiceTermStateDefinition.NOT_READY);
			throw e;
    	}
	}
}

/*
 * SampleAgreement
 */
package org.cloudcompaas.sla.wsag.agreement;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.XMLWrapper;
import org.cloudcompaas.sla.wsag.SLAManager;
import org.cloudcompaas.sla.wsag.monitor.CloudcompaasMonitor;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasMetadataMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasUserMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasVirtualContainerMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasVirtualMachineMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasVirtualServiceMonitoringHandler;
import org.ogf.schemas.graap.wsAgreement.AgreementPropertiesDocument;
import org.ogf.schemas.graap.wsAgreement.AgreementPropertiesType;
import org.ogf.schemas.graap.wsAgreement.AgreementStateDefinition;
import org.ogf.schemas.graap.wsAgreement.AgreementStateType;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermStateType;
import org.ogf.schemas.graap.wsAgreement.ServiceReferenceType;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateType;
import org.ogf.schemas.graap.wsAgreement.TerminateInputType;
import org.ogf.graap.wsag.api.exceptions.ResourceUnavailableException;
import org.ogf.graap.wsag.api.exceptions.ResourceUnknownException;
import org.ogf.graap.wsag.api.types.AbstractAgreementType;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;
import org.w3c.dom.Element;
import es.upv.grycap.cloudcompaas.EndpointReferenceDocument;

/**
 * SampleAgreement 
 * Modified by angarg12
 *
 * @author Oliver Waeldrich
 *
 */
public class CloudcompaasAgreement extends AbstractAgreementType {
	SLAManager parent;
	public static final String CLOUDCOMPAAS_AGREEMENT = "org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement";
	
    public CloudcompaasAgreement(SLAManager parent_) {
        super();
        parent = parent_;
    }

    public CloudcompaasAgreement(AgreementPropertiesType properties) {
        super();
        this.agreementProperties = properties;
    }
    
    public void create() throws Exception {
        AgreementStateType state = AgreementStateType.Factory.newInstance();
        state.setState(AgreementStateDefinition.PENDING);
        setState(state);
        Element el = getContext().getDomNode().getOwnerDocument().createElementNS("http://schemas.ggf.org/graap/2007/03/ws-agreement", "InitializationTime");

		DateFormat format = new SimpleDateFormat("yyyy'-'MM'-'dd HH:mm:ss.SSS");
    	el.appendChild(getContext().getDomNode().getOwnerDocument().createTextNode(format.format(new Date())));
    	
    	getContext().getDomNode().appendChild(el);

    	register();

		ServiceTermStateType termState = ServiceTermStateType.Factory.newInstance();
    	Properties properties = new Properties();
    	RESTComm comm = new RESTComm("Catalog");
    	comm.setUrl("service_description_terms");
		comm.setContentType("text/xml");
		for(int i = 0; i < getTerms().getAll().getServiceDescriptionTermArray().length; i++){
	    	properties.put("local_sdt_id", getTerms().getAll().getServiceDescriptionTermArray()[i].getName());
			termState.setState(ServiceTermStateDefinition.NOT_READY);
			termState.setTermName(getTerms().getAll().getServiceDescriptionTermArray()[i].getName());
	    	properties.put("state", termState.xmlText());
	    	properties.put("id_sla", getAgreementId());
	    	
			String payload;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			properties.storeToXML(baos, null);
			payload = baos.toString();
			
			comm.post(payload);
		}

		GuaranteeTermStateType guaranteeState = GuaranteeTermStateType.Factory.newInstance();
    	comm.setUrl("guarantee_terms");
    	
    	properties = new Properties();
		for(int i = 0; i < getTerms().getAll().getGuaranteeTermArray().length; i++){
	    	properties.put("local_guarantee_id", getTerms().getAll().getGuaranteeTermArray()[i].getName());
			guaranteeState.setState(GuaranteeTermStateDefinition.NOT_DETERMINED);
			guaranteeState.setTermName(getTerms().getAll().getGuaranteeTermArray()[i].getName());
	    	properties.put("state", guaranteeState.xmlText());
	    	properties.put("id_sla", getAgreementId());
	    	
			String payload;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			properties.storeToXML(baos, null);
			payload = baos.toString();
			
			comm.post(payload);
		}
		
        CreateAgreement cagr = new CreateAgreement(this, parent);
        cagr.start();
    }

	public void terminate(TerminateInputType arg0) {

    	Element el = getContext().getDomNode().getOwnerDocument().createElementNS("http://schemas.ggf.org/graap/2007/03/ws-agreement","TerminationTime");

		DateFormat format = new SimpleDateFormat("yyyy'-'MM'-'dd HH:mm:ss.SSS");
    	el.appendChild(getContext().getDomNode().getOwnerDocument().createTextNode(format.format(new Date())));
    	
    	getContext().getDomNode().appendChild(el);
        try {
			update();
		} catch (Exception e) {
			//throw new ResourceUnavailableException(e);
		}
		TerminateAgreement terminate = new TerminateAgreement(this, parent);
		terminate.start();
	}

	public void reject()
			throws ResourceUnknownException, ResourceUnavailableException {
		try {
			pull();
		} catch (Exception e1) {
			throw new ResourceUnknownException(e1);
		}
    	if(getState().getState().equals(AgreementStateDefinition.PENDING)){
			AgreementStateType state = AgreementStateType.Factory.newInstance();
	        state.setState(AgreementStateDefinition.REJECTED);
	        setState(state);
    	} else {
			throw new ResourceUnavailableException("The agreement could not be rejected because it is in a wrong state: "+getState());
    	}

    	Element el = getContext().getDomNode().getOwnerDocument().createElementNS("http://schemas.ggf.org/graap/2007/03/ws-agreement","RejectionTime");

		DateFormat format = new SimpleDateFormat("yyyy'-'MM'-'dd HH:mm:ss.SSS");
    	el.appendChild(getContext().getDomNode().getOwnerDocument().createTextNode(format.format(new Date())));
    	
    	getContext().getDomNode().appendChild(el);
    	
        try {
			update();
		} catch (Exception e) {
			throw new ResourceUnavailableException(e);
		}
		RejectAgreement reject = new RejectAgreement(this, parent);
		reject.start();
	}
	
	private void setServiceTermState(String termname, String termstate) throws Exception {
		try{
		ServiceTermStateType[] stst = agreementProperties.getServiceTermStateArray();
		boolean contains = false;
		
		XmlObject xmlstate = XmlObject.Factory.parse(termstate);
		for(int i = 0; i < stst.length; i++){
			if(stst[i].getTermName() != null && stst[i].getTermName().equals(termname)){
				stst[i].set(xmlstate);
				contains = true;
				break;
			}
		}
		
		if(contains == false){
			ServiceTermStateType state = agreementProperties.addNewServiceTermState();
			state.setTermName(termname);
			state.set(xmlstate);
			ServiceTermStateType[] newstst =  new ServiceTermStateType[stst.length+1];
			for(int i = 0; i < stst.length; i++){
				newstst[i] = stst[i];
			}
			newstst[stst.length] = state;
			agreementProperties.setServiceTermStateArray(newstst);
		}
		}catch(Exception e){
			e.printStackTrace();
			throw e;
		}
	}
	
	private void setGuaranteeTermState(String termname, String termstate) throws Exception {
		GuaranteeTermStateType[] stst = agreementProperties.getGuaranteeTermStateArray();
		boolean contains = false;

		XmlObject xmlstate = XmlObject.Factory.parse(termstate);
		for(int i = 0; i < stst.length; i++){
			if(stst[i].getTermName() != null && stst[i].getTermName().equals(termname)){
				stst[i].set(xmlstate);
				contains = true;
				break;
			}
		}

		if(contains == false){
			GuaranteeTermStateType state = agreementProperties.addNewGuaranteeTermState();
			state.setTermName(termname);
			state.set(xmlstate);
			GuaranteeTermStateType[] newstst =  new GuaranteeTermStateType[stst.length+1];
			for(int i = 0; i < stst.length; i++){
				newstst[i] = stst[i];
			}
			newstst[stst.length] = state;
			agreementProperties.setGuaranteeTermStateArray(newstst);
		}
	}
	
    public void register() throws Exception {
    	Properties properties = new Properties();
    	properties.put("state", getState().xgetState().getStringValue());
    	//properties.put("xmlsla", reducedXmlText());
    	
		String payload;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		properties.storeToXML(baos, null);
		payload = baos.toString();
		
    	RESTComm comm = new RESTComm("Catalog");
    	comm.setUrl("sla");
    	comm.setContentType("text/xml");
		XMLWrapper wrap = comm.post(payload);
		
		setAgreementId(wrap.getFirst("//id_sla"));
		updateXml();
    }
    
    public void persist() throws Exception {
    	Properties properties = new Properties();
    	properties.put("state", getState().xgetState().getStringValue());
    	properties.put("xmlsla", reducedXmlText());
    	
		String payload;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		properties.storeToXML(baos, null);
		payload = baos.toString();
		
    	RESTComm comm = new RESTComm("Catalog");
    	comm.setUrl("sla/"+getAgreementId());
    	comm.setContentType("text/xml");
		comm.put(payload);
    }
    
    public void updateState() throws Exception {
    	RESTComm comm = new RESTComm("Catalog");
    	comm.setUrl("sla/"+getAgreementId()+"/state");
    	comm.setContentType("text/plain");
		comm.put(getState().xgetState().getStringValue());
    }
    
    public void updateXml() throws Exception {
    	RESTComm comm = new RESTComm("Catalog");
    	comm.setUrl("sla/"+getAgreementId()+"/xmlsla");
    	comm.setContentType("text/plain");
		comm.put(reducedXmlText().replace("'", "''"));
    }
    
    public void update() throws Exception {
    	updateState();
    	updateXml();
    }
    
    public String xmlText(){
    	AgreementPropertiesDocument agreementDoc = AgreementPropertiesDocument.Factory.newInstance();
    	try{
		    // store agreement properties
		    AgreementPropertiesType props = agreementDoc.addNewAgreementProperties();
		    props.setAgreementId(getAgreementId());
		    props.setName(getName());
		    props.setContext(getContext());
		    props.setTerms(getTerms());
		    props.setAgreementState(getState());
		    props.setServiceTermStateArray(getServiceTermStates());
		    props.setGuaranteeTermStateArray(getGuaranteeTermStates());
    	}catch(Exception e){
    		e.printStackTrace();
    		return null;
    	}
	    XmlOptions options = new XmlOptions();
	    options.setLoadStripWhitespace();
	    options.setLoadTrimTextBuffer();
	    return agreementDoc.xmlText(options);
    }
    
    public String reducedXmlText(){
    	AgreementPropertiesDocument agreementDoc = AgreementPropertiesDocument.Factory.newInstance();
    	try{
		    // store agreement properties
		    AgreementPropertiesType props = agreementDoc.addNewAgreementProperties();
		    props.setAgreementId(getAgreementId());
		    props.setName(getName());
		    props.setContext(getContext());
		    props.setTerms(getTerms());
    	}catch(Exception e){
    		e.printStackTrace();
    		return null;
    	}
	    XmlOptions options = new XmlOptions();
	    options.setLoadStripWhitespace();
	    options.setLoadTrimTextBuffer();
	    return agreementDoc.xmlText(options);
    }
    
    public void pull() throws Exception {
    	RESTComm comm = new RESTComm("Catalog");
    	comm.setUrl("sla/"+getAgreementId());
    	XMLWrapper wrap = comm.get();

		String slastate = wrap.getFirst("//state").trim();
    	AgreementStateType state = AgreementStateType.Factory.newInstance();
        if(slastate.compareToIgnoreCase("COMPLETE") == 0){
        	state.setState(AgreementStateDefinition.COMPLETE);
	    	setState(state);
        }
        if(slastate.compareToIgnoreCase("OBSERVED") == 0){
        	state.setState(AgreementStateDefinition.OBSERVED);
	    	setState(state);
        }
        if(slastate.compareToIgnoreCase("OBSERVEDANDTERMINATING") == 0){
        	state.setState(AgreementStateDefinition.OBSERVED_AND_TERMINATING);
	    	setState(state);
        }
        if(slastate.compareToIgnoreCase("PENDING") == 0){
        	state.setState(AgreementStateDefinition.PENDING);
	    	setState(state);
        }
        if(slastate.compareToIgnoreCase("PENDINGANDTERMINATING") == 0){
        	state.setState(AgreementStateDefinition.PENDING_AND_TERMINATING);
	    	setState(state);
        }
        if(slastate.compareToIgnoreCase("REJECTED") == 0){
        	state.setState(AgreementStateDefinition.REJECTED);
	    	setState(state);
        }
        if(slastate.compareToIgnoreCase("TERMINATED") == 0){
        	state.setState(AgreementStateDefinition.TERMINATED);
	    	setState(state);
        }
		AgreementPropertiesDocument respDoc = AgreementPropertiesDocument.Factory.parse(wrap.getFirst("//xmlsla"));

	    setName(respDoc.getAgreementProperties().getName());
	    setContext(respDoc.getAgreementProperties().getContext());
	    setTerms(respDoc.getAgreementProperties().getTerms());
	    
    	comm.setUrl("service_description_terms/search?id_sla="+getAgreementId());
    	wrap = comm.get();

		for(int i = 0; i < wrap.get("//local_sdt_id").length; i++){
			setServiceTermState(wrap.get("//local_sdt_id")[i], wrap.get("//state")[i]);
		}
		
    	comm.setUrl("guarantee_terms/search?id_sla="+getAgreementId());
    	wrap = comm.get();
    	
		for(int i = 0; i < wrap.get("//local_guarantee_id").length; i++){
			setGuaranteeTermState(wrap.get("//local_guarantee_id")[i], wrap.get("//state")[i]);
		}

		Map<String,ServiceReferenceType> serviceRefsMap = new HashMap<String,ServiceReferenceType>();
		comm = new RESTComm("Catalog");
    	comm.setUrl("/monitoring_information/search?id_sla="+getAgreementId());
		wrap = comm.get();
		
		String[] epr = wrap.get("//epr");
		String[] timestamp = wrap.get("//timestamp");
		String[] localSdtId = wrap.get("//local_sdt_id");
		
		Map<String, Map<String,Date>> lastReports = new HashMap<String, Map<String,Date>>();
		DateFormat format = new SimpleDateFormat("yyyy'-'MM'-'dd HH:mm:ss.SSS");
		for(int i = 0; i < epr.length; i++){
			Map<String, Date> sdtIds = lastReports.get(epr[i]);
			if(sdtIds == null){
				sdtIds = new HashMap<String, Date>(); 
				lastReports.put(epr[i], sdtIds);
			}
			Date storedReport = sdtIds.get(localSdtId[i]);
			Date lastReport = format.parse(timestamp[i]);
			if(storedReport == null || storedReport.getTime() < lastReport.getTime()){
				sdtIds.put(localSdtId[i], lastReport);
			}
		}
		
		Iterator<String> eprIt = lastReports.keySet().iterator();
		while(eprIt.hasNext()){
			String eprKey = eprIt.next();
			Map<String, Date> eprReports = lastReports.get(eprKey);
			Iterator<String> sdtIdIt = eprReports.keySet().iterator();
			while(sdtIdIt.hasNext()){
				String sdtIdKey = sdtIdIt.next();
				Date storedReport = eprReports.get(sdtIdKey);
				Date now = new Date();
				long difference = now.getTime() - storedReport.getTime();
				// monitoring poll is 2 min, so we tolerate a 3 min difference
				if(difference > CloudcompaasMonitor.TOLERANCE){
					// if monitoring information is too old, is because that replica is not alive anymore
					continue;
				}
				String serviceName = null;
				
				for(int k = 0; k < getTerms().getAll().sizeOfServiceDescriptionTermArray(); k++){
					if(getTerms().getAll().getServiceDescriptionTermArray(k).getName().equals(sdtIdKey)){
						serviceName = getTerms().getAll().getServiceDescriptionTermArray(k).getServiceName();
						break;
					}
				}
				
				if(serviceName == null){
					continue;
				}
				
				ServiceReferenceType srt = serviceRefsMap.get(serviceName);
				if(srt == null){
					srt = getTerms().getAll().addNewServiceReference();
			        srt.setName("References");
			        srt.setServiceName(serviceName);
			        serviceRefsMap.put(serviceName, srt);
				}
				
				EndpointReferenceDocument eprdoc = EndpointReferenceDocument.Factory.newInstance();
				eprdoc.addNewEndpointReference().setServiceDescriptionTerm(sdtIdKey);
				eprdoc.getEndpointReference().addElement(eprKey);
				srt.getDomNode().appendChild(
						srt.getDomNode().getOwnerDocument().importNode(
								eprdoc.getDomNode().getFirstChild()
								,true));
			}
		}
    }
    
    public class CreateAgreement extends Thread {
    	CloudcompaasAgreement agreementInstance;
    	SLAManager parent;
    	
    	public CreateAgreement(CloudcompaasAgreement agreementInstance_, SLAManager parent_){
    		agreementInstance = agreementInstance_;
    		parent = parent_;
    	}
    	
    	public void run(){
            try {
            	XmlObject[] xpath = agreementInstance.getXMLObject().selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
    					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
    					"//ws:ServiceDescriptionTerm/ccpaas:User/@ccpaas:Name");
            	
            	if(xpath == null || xpath.length < 1){
            		throw new Exception("Could not retrieve username.");
            	}
            	
            	RESTComm comm = new RESTComm("Orchestrator");
        		comm.put(agreementInstance.getAgreementId());
            	
    			AgreementStateType state = AgreementStateType.Factory.newInstance();
    	        state.setState(AgreementStateDefinition.OBSERVED);
    	        setState(state);
    	        
    			CloudcompaasMonitor monitor = new CloudcompaasMonitor(agreementInstance);
    	        IServiceTermMonitoringHandler ctm = new CloudcompaasVirtualMachineMonitoringHandler(agreementInstance);
    	    	monitor.addMonitoringHandler(ctm);
    	        ctm = new CloudcompaasVirtualContainerMonitoringHandler(agreementInstance);
    	    	monitor.addMonitoringHandler(ctm);
    	        ctm = new CloudcompaasVirtualServiceMonitoringHandler(agreementInstance);
    	    	monitor.addMonitoringHandler(ctm);
    	        ctm = new CloudcompaasUserMonitoringHandler(agreementInstance);
    	    	monitor.addMonitoringHandler(ctm);
    	        ctm = new CloudcompaasMetadataMonitoringHandler(agreementInstance);
    	    	monitor.addMonitoringHandler(ctm);
    			monitor.startMonitoring();
    	        
    			agreementInstance.update();
    		} catch (Exception e) {
    			e.printStackTrace();
    			try {
    				agreementInstance.reject();
				} catch (Exception ee) {
					// TODO Auto-generated catch block
					ee.printStackTrace();
				}
    		}
    	}
    }
    
    public class TerminateAgreement extends Thread {
    	CloudcompaasAgreement agreementInstance;
    	SLAManager parent;
    	
    	public TerminateAgreement(CloudcompaasAgreement agreement_, SLAManager parent_){
    		agreementInstance = agreement_;
    		parent = parent_;
    	}
    	
    	public void run(){
            try {
            	XmlObject[] xpath = agreementInstance.getXMLObject().selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
    					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
    					"//ws:ServiceDescriptionTerm/ccpaas:User/@ccpaas:Name");
            	
            	if(xpath == null || xpath.length < 1){
            		throw new Exception("Could not retrieve username.");
            	}
            	        	
            	RESTComm comm = new RESTComm("Orchestrator");
            	comm.setUrl(agreementInstance.getAgreementId());
        		comm.delete();
            	
            	comm = new RESTComm("Catalog");
            	comm.setUrl("service_description_terms/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("guarantee_terms/search?id_sla="+getAgreementId());
            	comm.delete();
            	/* The responsability of deleting the service instances (and monitoring
            	 * information for that matter) should rest in each module, and not in
            	 * the SLAManager.
            	comm.setUrl("service_instance/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("vr_instance/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("vm_instance/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("monitoring_information/search?id_sla="+getAgreementId());
            	comm.delete();
        		*/
        		AgreementStateType state = AgreementStateType.Factory.newInstance();
    	        state.setState(AgreementStateDefinition.TERMINATED);
    	        setState(state);
    	        for(int i = 0; i < agreementInstance.getTerms().getAll().sizeOfServiceReferenceArray(); i++){
    	        	agreementInstance.getTerms().getAll().removeServiceReference(i);
    	        }
    	        agreementInstance.update();
    		} catch (Exception e) {
				// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }
    
    public class RejectAgreement extends Thread {
    	CloudcompaasAgreement agreementInstance;
    	SLAManager parent;
    	
    	public RejectAgreement(CloudcompaasAgreement agreement_, SLAManager parent_){
    		agreementInstance = agreement_;
    		parent = parent_;
    	}
    	
    	public void run(){
            try {
            	XmlObject[] xpath = agreementInstance.getXMLObject().selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
    					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
    					"//ws:ServiceDescriptionTerm/ccpaas:User/@ccpaas:Name");
            	
            	if(xpath == null || xpath.length < 1){
            		throw new Exception("Could not retrieve username.");
            	}

            	RESTComm comm = new RESTComm("Orchestrator");
            	comm.setUrl(agreementInstance.getAgreementId());
        		comm.delete();          	    
        		
            	comm = new RESTComm("Catalog");
            	comm.setUrl("service_description_terms/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("guarantee_terms/search?id_sla="+getAgreementId());
            	comm.delete();
            	/* The responsability of deleting the service instances (and monitoring
            	 * information for that matter) should rest in each module, and not in
            	 * the SLAManager.
            	comm.setUrl("service_instance/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("vr_instance/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("vm_instance/search?id_sla="+getAgreementId());
            	comm.delete();
            	comm.setUrl("monitoring_information/search?id_sla="+getAgreementId());
            	comm.delete();
        		*/
        		AgreementStateType state = AgreementStateType.Factory.newInstance();
    	        state.setState(AgreementStateDefinition.REJECTED);
    	        setState(state);
    	        for(int i = 0; i < agreementInstance.getTerms().getAll().sizeOfServiceReferenceArray(); i++){
    	        	agreementInstance.getTerms().getAll().removeServiceReference(i);
    	        }
    	        agreementInstance.update();
    		} catch (Exception e) {
				// TODO Auto-generated catch block
    			e.printStackTrace();
    		}
    	}
    }
}

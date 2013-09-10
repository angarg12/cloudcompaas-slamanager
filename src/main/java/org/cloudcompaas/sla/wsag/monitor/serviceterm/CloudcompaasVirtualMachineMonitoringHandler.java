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

import java.io.ByteArrayInputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.XMLWrapper;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDocument;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/**
 * @author angarg12
 *
 */
public class CloudcompaasVirtualMachineMonitoringHandler implements IServiceTermMonitoringHandler {
	CloudcompaasAgreement agreementInstance;
	
	public CloudcompaasVirtualMachineMonitoringHandler(CloudcompaasAgreement agreementInstance_){
		agreementInstance = agreementInstance_;
	}
	
	public void monitor(IMonitoringContext monitoringContext) throws Exception {
    	ServiceDescriptionTermType[] terms = agreementInstance.getTerms().getAll().getServiceDescriptionTermArray();
    	ServiceTermStateType state = null;

    	for(int i = 0; i < terms.length; i++){
    		if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
    			state = monitoringContext.getServiceTermStateByName(terms[i].getName());
    		}
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
			
	    	RESTComm comm = new RESTComm("Catalog");
	    	comm.setUrl("monitoring_information/search?local_sdt_id="+state.getTermName()+"&id_sla="+agreementInstance.getAgreementId());
	    	XMLWrapper wrap = comm.get();
		
	    	// first we will group all monitoring information per EPR
	    	Map<String, Map<String, String>> monitoringInformation = new HashMap<String, Map<String, String>>();
			for(int i = 0; i < wrap.get("//monitoring_information").length; i++){
				DateFormat format = new SimpleDateFormat("yyyy'-'MM'-'dd HH:mm:ss.SSS");
				Date lastReport = format.parse(wrap.get("//timestamp")[i]);
				Date now = new Date();
				long difference = now.getTime() - lastReport.getTime();
				// monitoring poll is 2 min, so we tolerate a 3 min difference
				if(difference > 1000*60*3){
					// if monitoring information is too old, is because that replica is not alive anymore
					continue;
				}
				// put all the monitoring information for the same EPR in a map
				if(monitoringInformation.get(wrap.get("//epr")[i]) == null){
					monitoringInformation.put(wrap.get("//epr")[i], new HashMap<String, String>());
					monitoringInformation.get(wrap.get("//epr")[i]).put("TIMESTAMP",wrap.get("//timestamp")[i]);
				}
				monitoringInformation.get(wrap.get("//epr")[i])
					.put(wrap.get("//metric_name")[i],wrap.get("//metric_value")[i]);
			}
			
			Set<String> miKeySet = monitoringInformation.keySet();
			Iterator<String> i = miKeySet.iterator();
			// iterate through the eprs
			while(i.hasNext()){
				String epr = i.next();
				Set<String> eprKeySet = monitoringInformation.get(epr).keySet();
				Iterator<String> j = eprKeySet.iterator();
				// create a new replica element that will hold the monitoring information
				Element el = doc.createElementNS("http://www.grycap.upv.es/cloudcompaas", "Replica");
		    	el.setAttributeNS("http://www.grycap.upv.es/cloudcompaas", "Id", epr);
				doc.getFirstChild().appendChild(el);
				// iterate through the monitoring information for that epr
				while(j.hasNext()){
					String metricName = j.next();
					// create a new element for each monitoring information
					Element metric = el.getOwnerDocument().createElementNS("http://www.grycap.upv.es/cloudcompaas", metricName);
					metric.appendChild(el.getOwnerDocument().createTextNode(monitoringInformation.get(epr).get(metricName)));
					el.appendChild(metric);
				}
			}

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

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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlString;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.XMLWrapper;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDocument;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateType;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

/**
 * @author angarg12
 *
 */
public class CloudcompaasUserMonitoringHandler  implements IServiceTermMonitoringHandler {
	CloudcompaasAgreement agreementInstance;
	
	public CloudcompaasUserMonitoringHandler(CloudcompaasAgreement agreementInstance_){
		agreementInstance = agreementInstance_;
	}
	
	public void monitor(IMonitoringContext monitoringContext) throws Exception {
	   	ServiceDescriptionTermType[] terms = agreementInstance.getTerms().getAll().getServiceDescriptionTermArray();
    	ServiceTermStateType state = null;
    	
    	for(int i = 0; i < terms.length; i++){
    		try{
	    		if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:User")){
	    			state = monitoringContext.getServiceTermStateByName(terms[i].getName());
	    		}
    		}catch(Exception e){}
    	}
    	
    	if(state == null){
			return;
    	}
    	
    	try{
		    state.setState(ServiceTermStateDefinition.READY);
		    
		    String xpath = "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
	    	"declare namespace ws='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
	    	"//ws:Terms/ws:All/ws:ServiceDescriptionTerm/ccpaas:User/@ccpaas:Name";
	
			XmlObject[] result = agreementInstance.getXMLObject().selectPath(xpath);
			
			if (result.length == 0) {
				//No username found, oops!
				return;
			}

			String username = XmlString.Factory.parse(result[0].getDomNode().getFirstChild()).getStringValue();
	    	RESTComm comm = new RESTComm("Catalog");
	    	comm.setUrl("user/search?name="+username);
	    	XMLWrapper wrap = comm.get();
		    			
	    	ServiceTermStateDocument stsd = ServiceTermStateDocument.Factory.newInstance();
	    	stsd.setServiceTermState(state);
			DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
			builderFactory.setNamespaceAware(true);
			DocumentBuilder builder = builderFactory.newDocumentBuilder();
			Document doc = builder.parse(new InputSource(new ByteArrayInputStream(stsd.xmlText().getBytes("utf-8"))));
			Text text = doc.createTextNode(wrap.getFirst("//current_credits"));
			Element el1 = doc.createElementNS("http://www.grycap.upv.es/cloudcompaas", "UserCredits");
			el1.appendChild(text);
	    	Element el2 = doc.createElementNS("http://www.grycap.upv.es/cloudcompaas", "User");
			el2.appendChild(el1);
			text = doc.createTextNode(username);
			Attr att = doc.createAttributeNS("http://www.grycap.upv.es/cloudcompaas", "Name");
			att.appendChild(text);
			el2.getAttributes().setNamedItemNS(att);
			
			doc.getFirstChild().appendChild(el2);
			stsd = ServiceTermStateDocument.Factory.parse(doc.getFirstChild());
	
			XmlOptions xmlOptions = new XmlOptions();
			xmlOptions.setDocumentType(state.schemaType());
			state.set(XmlObject.Factory.parse(stsd.getServiceTermState().xmlText(), xmlOptions));
    	}catch(Exception e){
		    state.setState(ServiceTermStateDefinition.NOT_READY);
		    e.printStackTrace();
    	}
	}
}

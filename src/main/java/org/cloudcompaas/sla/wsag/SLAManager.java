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
package org.cloudcompaas.sla.wsag;

import java.io.File;
import java.util.List;
import java.util.Properties;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.wink.common.annotations.Scope;
import org.apache.wink.common.http.HttpStatus;
import org.apache.xmlbeans.XmlOptions;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.components.Component;
import org.cloudcompaas.common.components.Register;
import org.cloudcompaas.common.security.server.UserCache;
import org.cloudcompaas.common.util.XMLDOMComparator;
import org.cloudcompaas.common.util.XMLWrapper;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.cloudcompaas.sla.wsag.monitor.CloudcompaasMonitor;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasMetadataMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasUserMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasVirtualContainerMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasVirtualMachineMonitoringHandler;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasVirtualServiceMonitoringHandler;
import org.ogf.graap.wsag.api.AgreementOffer;
import org.ogf.graap.wsag.api.types.AgreementOfferType;
import org.ogf.graap.wsag.server.engine.TemplateValidator;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;
import org.ogf.schemas.graap.wsAgreement.AgreementOfferDocument;
import org.ogf.schemas.graap.wsAgreement.AgreementStateDefinition;
import org.ogf.schemas.graap.wsAgreement.AgreementStateType;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermType;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;
import org.ogf.schemas.graap.wsAgreement.ServicePropertiesType;
import org.ogf.schemas.graap.wsAgreement.TemplateDocument;
import org.ogf.schemas.graap.wsAgreement.TerminateInputType;

/**
 * @author angarg12
 *
 */
@Scope(Scope.ScopeType.SINGLETON)
@Path("/agreement")
public class SLAManager extends Component implements ISLAManager {
	protected TemplateValidator validator;
	
	public SLAManager() throws Exception {
		Properties properties = new Properties();
		
		properties.load(getClass().getResourceAsStream("/conf/SLAManager.properties"));
		
        validator = new TemplateValidator();
        
        File f = new File(getClass().getResource("/validator").toURI());
        File[] files = f.listFiles();
        for(int i = 0; i < files.length; i++){
        	validator.getConfiguration().getSchemaImports().addNewSchemaFilename().setStringValue("/validator/"+files[i].getName());
        }
        
		String service = properties.getProperty("service");
		String version = properties.getProperty("version");
		String epr = properties.getProperty("epr");

		Register register = new Register(Thread.currentThread(), service, version, epr);
		register.start();
	}

	@Override
	public void customStartup(){
		try {
			loadStoredAgreements();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void loadStoredAgreements() throws Exception {
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/sla/search?state=Observed&state=ObservedAndTerminating");
		XMLWrapper wrap = comm.get();
		String[] ids = wrap.get("//id_sla");
		for(int i = 0; i < ids.length; i++){
	        CloudcompaasAgreement agreementInstance = new CloudcompaasAgreement(this);
	        agreementInstance.setAgreementId(ids[i]);
	        agreementInstance.pull();

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
			try {
				/** TODO 
				 * I can't exactly remember why did I put this sleep. Maybe was a question of timing.
				 * Or maybe it is not needed anymore. May try to remove it.
				 */
				Thread.sleep(2000);
				monitor.startMonitoring();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
	}

	@GET
	@Path("/template")
    @Produces({MediaType.TEXT_XML})
	public Response getTemplates(@HeaderParam("Authorization") String auth, @QueryParam("include") List<String> include, @QueryParam("exclude") List<String> exclude){
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		
		try{
			SLACompositor slacomp = new SLACompositor(this);
			return Response
			.status(HttpStatus.OK.getCode())
			.entity(slacomp.generateTemplates(include, 
											exclude
											, UserCache.getUsername(auth.replace("Basic ", ""))))
			.build();
		}catch(Exception e){
			return Response
			.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
			.entity(e.getMessage())
			.build();
		}
	}
	
	@POST
    @Consumes({MediaType.TEXT_XML})
	public Response createAgreement(@HeaderParam("Authorization") String auth, String agreementOffer) {
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}

		AgreementOfferDocument offerDoc;
		SLACompositor compo = new SLACompositor(this);
		TemplateDocument offerTemplate;
		XMLDOMComparator xmlcomparator = new XMLDOMComparator();
		try { 
			XmlOptions options = new XmlOptions();
		    options.setLoadStripWhitespace();
		    options.setLoadTrimTextBuffer();
			offerDoc = AgreementOfferDocument.Factory.parse(agreementOffer, options);
			// in order to get the basic token, we need to trim the Basic keyword first.
			offerTemplate = TemplateDocument.Factory.parse(
					compo.templateReconstruction(
							offerDoc.getAgreementOffer().getContext().getTemplateId()
							, UserCache.getUsername(auth.replace("Basic ", "")))
					, options);
		} catch (Exception e) {
			return Response
			.status(HttpStatus.BAD_REQUEST.getCode())
			.entity(e.getMessage()).build();
		} 
        StringBuffer error = new StringBuffer();
        AgreementOffer offer = new AgreementOfferType(offerDoc.getAgreementOffer());
        if (validator.validate(offer, offerTemplate.getTemplate(),error) == false) {
			return Response
			.status(HttpStatus.BAD_REQUEST.getCode())
			.entity("Agreement Offer is not correct respect the template constraints.")
			.build();
        }
		
		// we have to check if terms are set
        if ( (offerDoc.getAgreementOffer().getTerms() == null) ||
             (offerDoc.getAgreementOffer().getTerms().getAll() == null)) {
			return Response
			.status(HttpStatus.BAD_REQUEST.getCode())
			.entity("Offer does not contain any terms.")
			.build();
        }
            
        GuaranteeTermType[] offerGuarantees = offerDoc.getAgreementOffer().getTerms().getAll().getGuaranteeTermArray();
        GuaranteeTermType[] templateGuarantees = offerTemplate.getTemplate().getTerms().getAll().getGuaranteeTermArray();
        if(offerGuarantees.length != templateGuarantees.length){
			return Response
			.status(HttpStatus.BAD_REQUEST.getCode())
			.entity("Agreement Template guarantees have been tampered.")
			.build();
        }else{
        	for(int i = 0; i < offerGuarantees.length; i++){
        		boolean isCorrect = false;
        		// we need an inner loop to make the function correct independently of the
        		// order.
        		for(int j = 0; j < templateGuarantees.length; j++){
        			if(offerGuarantees[i].getName().equals(templateGuarantees[j].getName())){
        				if(xmlcomparator.compare(offerGuarantees[i].getDomNode(),templateGuarantees[j].getDomNode())){
        					isCorrect = true;
        					break;
                		}
        			}
        		}
        		if(isCorrect == false){
        			return Response
        			.status(HttpStatus.BAD_REQUEST.getCode())
        			.entity("Agreement Template guarantees have been tampered.")
        			.build();
        		}
        	}
        }
        
        ServicePropertiesType[] offerProperties = offerDoc.getAgreementOffer().getTerms().getAll().getServicePropertiesArray();
        ServicePropertiesType[] templateProperties = offerTemplate.getTemplate().getTerms().getAll().getServicePropertiesArray();
        if(offerProperties.length != templateProperties.length){
			return Response
			.status(HttpStatus.BAD_REQUEST.getCode())
			.entity("Agreement Template properties have been tampered.")
			.build();
        }else{
        	for(int i = 0; i < offerProperties.length; i++){
        		boolean isCorrect = false;
        		// we need an inner loop to make the function correct independtly of the
        		// order.
        		for(int j = 0; j < templateProperties.length; j++){
        			if(offerProperties[i].getName().equals(templateProperties[j].getName())){
        				if(xmlcomparator.compare(offerProperties[i].getDomNode(),templateProperties[j].getDomNode())){
        					isCorrect = true;
        					break;
                		}
        			}
        		}
        		if(isCorrect == false){
        			return Response
        			.status(HttpStatus.BAD_REQUEST.getCode())
        			.entity("Agreement Template properties have been tampered.")
        			.build();
        		}
        	}
        }
        
        // now we get the SDTs
        ServiceDescriptionTermType[] offerSdts = offerDoc.getAgreementOffer().getTerms().getAll().getServiceDescriptionTermArray();
        ServiceDescriptionTermType[] templateSdts = offerTemplate.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();

        if(offerSdts.length != templateSdts.length){
			return Response
			.status(HttpStatus.BAD_REQUEST.getCode())
			.entity("Agreement Template ServiceDescriptionTerms have been tampered.")
			.build();
        }
        	
        for(int i = 0; i < offerSdts.length; i++){
        	if(offerSdts[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:User")){
        		boolean isCorrect = false;
        		for(int j = 0; j < templateSdts.length; j++){
	        		if(xmlcomparator.compare(offerSdts[i].getDomNode().getFirstChild(), templateSdts[j].getDomNode().getFirstChild()) == true){
	        			isCorrect = true;
	        			break;
	        		}
        		}
        		if(isCorrect == false){
        			return Response
        			.status(HttpStatus.BAD_REQUEST.getCode())
        			.entity("User information has been tampered.")
        			.build();
        		}
        	}
        }
        CloudcompaasAgreement agreement = new CloudcompaasAgreement(this);

        agreement.setContext(offerDoc.getAgreementOffer().getContext());
        agreement.setTerms(offerDoc.getAgreementOffer().getTerms());
        agreement.setName(offerDoc.getAgreementOffer().getName());

		String agreementIdXml;
		try{
			agreement.create();
			agreementIdXml = "<id_agreement>"+agreement.getAgreementId()+"</id_agreement>";
		}catch(Exception e){
			return Response
			.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
			.entity(e.getMessage())
			.build();
		}
        return Response
        .status(HttpStatus.OK.getCode())
        .entity(agreementIdXml)
        .build();
	}

	@GET
	@Path("{id}")
    @Produces({MediaType.TEXT_XML})
	public Response retrieveAgreement(@HeaderParam("Authorization") String auth, @PathParam("id") String agreementId){

		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		CloudcompaasAgreement agreement = new CloudcompaasAgreement(this);
		agreement.setAgreementId(agreementId);

		try{
			agreement.pull();
		}catch(Exception e){
			return Response
			.status(HttpStatus.NOT_FOUND.getCode())
			.build();
		}
		return Response
		.status(HttpStatus.OK.getCode())
		.entity(agreement.xmlText())
		.build();
	}

	@DELETE
	@Path("{id}")
	public Response finalizeAgreement(@HeaderParam("Authorization") String auth, @PathParam("id") String agreementId) {
		if(auth == null || securityHandler.authenticate(auth) == false){
			return Response
			.status(HttpStatus.UNAUTHORIZED.getCode())
			.build();
		}
		
		CloudcompaasAgreement agreement = new CloudcompaasAgreement(this);
		agreement.setAgreementId(agreementId);
		try {
			agreement.pull();
		} catch (Exception e1) {
			return Response
			.status(HttpStatus.NOT_FOUND.getCode())
			.build();
		}
    	if(agreement.getState().getState().equals(AgreementStateDefinition.PENDING)){
			AgreementStateType state = AgreementStateType.Factory.newInstance();
	        state.setState(AgreementStateDefinition.PENDING_AND_TERMINATING);
	        agreement.setState(state);
    	} else if(agreement.getState().getState().equals(AgreementStateDefinition.OBSERVED)){
			AgreementStateType state = AgreementStateType.Factory.newInstance();
	        state.setState(AgreementStateDefinition.OBSERVED_AND_TERMINATING);
	        agreement.setState(state);
    	} else {
			return Response
			.status(HttpStatus.FORBIDDEN.getCode())
			.entity("The agreement could not be finalized because it is in a wrong state: "+agreement.getState())
			.build();
    	}
    	
		TerminateInputType tit = TerminateInputType.Factory.newInstance();
		agreement.terminate(tit);
		return Response
		.status(HttpStatus.OK.getCode())
		.build();
	}
}

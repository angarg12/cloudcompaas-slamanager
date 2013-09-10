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

import java.util.UUID;

import org.apache.xmlbeans.XmlObject;
import org.ogf.schemas.graap.wsAgreement.ServiceDescriptionTermType;
import org.ogf.schemas.graap.wsAgreement.ServicePropertiesType;
import org.ogf.schemas.graap.wsAgreement.TemplateDocument;
import org.ogf.schemas.graap.wsAgreement.VariableType;
import org.w3c.dom.NodeList;

import es.upv.grycap.cloudcompaas.MetadataDocument;
import es.upv.grycap.cloudcompaas.OperatingSystemDocument;
import es.upv.grycap.cloudcompaas.OrganizationDocument;
import es.upv.grycap.cloudcompaas.PhysicalResourceDocument;
import es.upv.grycap.cloudcompaas.ServiceDocument;
import es.upv.grycap.cloudcompaas.ServiceVersionDocument;
import es.upv.grycap.cloudcompaas.SoftAddOnDocument;
import es.upv.grycap.cloudcompaas.SoftResourceDocument;
import es.upv.grycap.cloudcompaas.UserDocument;
import es.upv.grycap.cloudcompaas.VirtualContainerDocument;
import es.upv.grycap.cloudcompaas.VirtualMachineDocument;
import es.upv.grycap.cloudcompaas.VirtualRuntimeDocument;

/**
 * @author angarg12
 *
 */
public class SLAMerger {
	
	public static void main(String[] args){
		String baseTemplate = "<ws:Template ws:TemplateId=\"\" " +
				"xmlns:ws=\"http://schemas.ggf.org/graap/2007/03/ws-agreement\" " +
				"xmlns:ccpaas=\"http://www.grycap.upv.es/cloudcompaas\" " +
				"xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
				"xmlns:wsag4j=\"http://schemas.scai.fraunhofer.de/2008/11/wsag4j/engine\">" +
				"<ws:Name>SAMPLE</ws:Name><ws:Context><ws:ServiceProvider>AgreementResponder</ws:ServiceProvider><ws:TemplateId></ws:TemplateId><ws:TemplateName>SAMPLE</ws:TemplateName></ws:Context>" +
				"<ws:Terms><ws:All></ws:All></ws:Terms><ws:CreationConstraints/></ws:Template>";
		String metadatatemplate = "<ccpaas:Metadata xmlns:ccpaas=\"http://www.grycap.upv.es/cloudcompaas\"><ccpaas:Replicas><ccpaas:RangeValue><ccpaas:Exact>1</ccpaas:Exact></ccpaas:RangeValue></ccpaas:Replicas></ccpaas:Metadata>";
		String guaranteetemplate = "<ws:GuaranteeTerm ws:Name=\"UPSCALE\" ws:Obligated=\"ServiceProvider\"  xmlns:ws=\"http://schemas.ggf.org/graap/2007/03/ws-agreement\"><ws:ServiceScope ws:ServiceName=\"SAMPLE\"/><ws:QualifyingCondition>MAX_REPLICAS gt ACT_REPLICAS</ws:QualifyingCondition><ws:ServiceLevelObjective><ws:KPITarget><ws:KPIName>CPUAVG</ws:KPIName><ws:CustomServiceLevel>list.avg(CPUPERC) le 90</ws:CustomServiceLevel></ws:KPITarget></ws:ServiceLevelObjective><ws:BusinessValueList><ws:Penalty><ws:AssessmentInterval><ws:TimeInterval>PT2M</ws:TimeInterval></ws:AssessmentInterval><ws:ValueExpression>5</ws:ValueExpression></ws:Penalty><ws:Reward><ws:AssessmentInterval><ws:TimeInterval>PT2M</ws:TimeInterval></ws:AssessmentInterval><ws:ValueExpression>ACT_REPLICAS*10</ws:ValueExpression></ws:Reward></ws:BusinessValueList><ws:ServiceProperties ws:Name=\"Service_Properties_1\" ws:ServiceName=\"SAMPLE\" xmlns:ws=\"http://schemas.ggf.org/graap/2007/03/ws-agreement\"><ws:VariableSet><ws:Variable ws:Name=\"MAX_REPLICAS\" ws:Metric=\"xsd:int\"><ws:Location>declare namespace ccpaas=''http://www.grycap.upv.es/cloudcompaas''; declare namespace ws=''http://schemas.ggf.org/graap/2007/03/ws-agreement'';//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = ''SAMPLE'']/ccpaas:Metadata/ccpaas:Replicas/ccpaas:RangeValue/ccpaas:Range/ccpaas:UpperBound or //ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = ''SAMPLE'']/ccpaas:Metadata/ccpaas:Replicas/ccpaas:RangeValue/ccpaas:Exact</ws:Location></ws:Variable><ws:Variable ws:Name=\"ACT_REPLICAS\" ws:Metric=\"xsd:int\"><ws:Location>declare namespace ccpaas=''http://www.grycap.upv.es/cloudcompaas'';declare namespace ws=''http://schemas.ggf.org/graap/2007/03/ws-agreement'';//ws:ServiceTermState[//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = ''SAMPLE'']/ccpaas:Metadata/../@ws:Name=@ws:termName]/ccpaas:Metadata/ccpaas:Replicas/ccpaas:RangeValue/ccpaas:Exact</ws:Location></ws:Variable><ws:Variable ws:Name=\"CPUPERC\" ws:Metric=\"xs:string\"><ws:Location>declare namespace ccpaas=''http://www.grycap.upv.es/cloudcompaas''; declare namespace ws=''http://schemas.ggf.org/graap/2007/03/ws-agreement'';//ws:ServiceTermState[//ws:Terms/ws:All/ws:ServiceDescriptionTerm[@ws:ServiceName = ''SAMPLE'']/ccpaas:VirtualMachine/../@ws:Name=@ws:termName]/ccpaas:Replica/ccpaas:CPU_USED_PERC</ws:Location></ws:Variable></ws:VariableSet></ws:ServiceProperties><ccpaas:Metadata xmlns:ccpaas=\"http://www.grycap.upv.es/cloudcompaas\"><ccpaas:Metrics><ccpaas:Metric ccpaas:Name=\"CPU_USED_PERC\"><ccpaas:Level>iaas</ccpaas:Level></ccpaas:Metric></ccpaas:Metrics></ccpaas:Metadata></ws:GuaranteeTerm>";
		try {
			String mergedtemplate = new SLAMerger().merge(baseTemplate,metadatatemplate);
			mergedtemplate = new SLAMerger().merge(mergedtemplate,guaranteetemplate);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public String merge(String first, String second)throws Exception {
		TemplateDocument template = null;
		if(first.startsWith("<ws:Template ")){
			template = TemplateDocument.Factory.parse(first);
		}
		
		if(second.startsWith("<ccpaas:Organization ")){
			OrganizationDocument organization = OrganizationDocument.Factory.parse(second);
			return merge(template,organization).xmlText();
		}else if(second.startsWith("<ccpaas:User ")){
			/*
			OrganizationDocument organization = null;
			UserDocument user = UserDocument.Factory.parse(second);
			if(template == null){
				organization = OrganizationDocument.Factory.parse(first);
				return merge(organization,user).xmlText();
			}else{
				ServiceDescriptionTermType[] terms = template.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
				for(int i = 0; i < terms.length; i++){
					if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:User")){
						organization = OrganizationDocument.Factory.parse(terms[i].getDomNode().getFirstChild());
						terms[i].set(merge(organization,user));
						return template.xmlText();
					}
				}
			}*/
			// Will directly merge with the template, skipping the organization
			UserDocument user = UserDocument.Factory.parse(second);
			return merge(template,user).xmlText();
		}else if(second.startsWith("<ccpaas:Metadata ")){
			MetadataDocument metadata = MetadataDocument.Factory.parse(second);
			return merge(template,metadata).xmlText();
		}else if(second.startsWith("<ccpaas:Service ")){
			ServiceDocument service = ServiceDocument.Factory.parse(second);
			return merge(template,service).xmlText();
		}else if(second.startsWith("<ccpaas:ServiceVersion ")){
			ServiceDocument service = ServiceDocument.Factory.parse(first);
			ServiceVersionDocument serviceversion = ServiceVersionDocument.Factory.parse(second);
			return merge(service,serviceversion).xmlText();
		}else if(second.startsWith("<ccpaas:VirtualContainer ")){
			VirtualContainerDocument virtualcontainer = VirtualContainerDocument.Factory.parse(second);
			return merge(template,virtualcontainer).xmlText();
		}else if(second.startsWith("<ccpaas:VirtualRuntime ")){
			VirtualContainerDocument virtualcontainer = null;
			VirtualRuntimeDocument virtualruntime = VirtualRuntimeDocument.Factory.parse(second);
			if(template == null){
				virtualcontainer = VirtualContainerDocument.Factory.parse(first);
				return merge(virtualcontainer,virtualruntime).xmlText();
			}else{
				ServiceDescriptionTermType[] terms = template.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
				for(int i = 0; i < terms.length; i++){
					if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
						virtualcontainer = VirtualContainerDocument.Factory.parse(terms[i].getDomNode().getFirstChild());
						template.getTemplate().getTerms().getAll().removeServiceDescriptionTerm(i);
						return merge(template,merge(virtualcontainer,virtualruntime)).xmlText();
					}
				}
			}
		}else if(second.startsWith("<ccpaas:OperatingSystem ")){
			VirtualMachineDocument virtualmachine = null;
			OperatingSystemDocument os = OperatingSystemDocument.Factory.parse(second);
			if(template == null){
				virtualmachine = VirtualMachineDocument.Factory.parse(first);
				return merge(virtualmachine,os).xmlText();
			}else{
				ServiceDescriptionTermType[] terms = template.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
				for(int i = 0; i < terms.length; i++){
					if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
						virtualmachine = VirtualMachineDocument.Factory.parse(terms[i].getDomNode().getFirstChild());
						template.getTemplate().getTerms().getAll().removeServiceDescriptionTerm(i);
						return merge(template,merge(virtualmachine,os)).xmlText();
					}
				}
			}
		}else if(second.startsWith("<ccpaas:SoftResource ")){
			VirtualRuntimeDocument virtualruntime = null;
			SoftResourceDocument softresource = SoftResourceDocument.Factory.parse(second);
			if(template == null){
				virtualruntime = VirtualRuntimeDocument.Factory.parse(first);
				return merge(virtualruntime,softresource).xmlText();
			}else{
				ServiceDescriptionTermType[] terms = template.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
				for(int i = 0; i < terms.length; i++){
					if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
						virtualruntime = VirtualRuntimeDocument.Factory.parse(terms[i].getDomNode().getFirstChild());
						template.getTemplate().getTerms().getAll().removeServiceDescriptionTerm(i);
						return merge(template.xmlText(),merge(virtualruntime,softresource).xmlText());
					}
				}
			}
		}else if(second.startsWith("<ccpaas:SoftAddOn ")){
			SoftResourceDocument softresource = SoftResourceDocument.Factory.parse(first);
			SoftAddOnDocument softaddon = SoftAddOnDocument.Factory.parse(second);
			return merge(softresource,softaddon).xmlText();
		}else if(second.startsWith("<ccpaas:VirtualMachine ")){
			VirtualMachineDocument virtualmachine = VirtualMachineDocument.Factory.parse(second);
			return merge(template,virtualmachine).xmlText();
		}else if(second.startsWith("<ccpaas:PhysicalResource ")){
			VirtualMachineDocument virtualmachine = VirtualMachineDocument.Factory.parse(first);
			PhysicalResourceDocument physicalresource = PhysicalResourceDocument.Factory.parse(second);
			return merge(virtualmachine,physicalresource).xmlText();
		} else if(second.startsWith("<ws:GuaranteeTerm ")){
			XmlObject guarantee = XmlObject.Factory.parse(second);
			return mergeGuarantee(template,guarantee).xmlText();
		} 
		return null;
	}
	
	public TemplateDocument merge(TemplateDocument first, OrganizationDocument second)throws Exception {
		ServiceDescriptionTermType sdt = first.getTemplate().getTerms().getAll().addNewServiceDescriptionTerm();
		NodeList nl = second.getOrganization().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					NodeList nnll = nl.item(i).getChildNodes();
					if(nnll != null){
						while(nnll.getLength() > 0){
							first.getTemplate().getCreationConstraints().getDomNode().appendChild(first.getTemplate().getDomNode().getOwnerDocument().importNode(nnll.item(0), true));
							nl.item(i).removeChild(nnll.item(0));
						}
					}
					second.getOrganization().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = mergeGuarantee(first,guarantee);
					
					second.getOrganization().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		sdt.set(second);
		sdt.setName(UUID.randomUUID().toString());
		sdt.setServiceName("SAMPLE");
		return first;
	}

	public TemplateDocument merge(TemplateDocument first, UserDocument second)throws Exception {
		ServiceDescriptionTermType sdt = first.getTemplate().getTerms().getAll().addNewServiceDescriptionTerm();
		NodeList nl = second.getUser().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					NodeList nnll = nl.item(i).getChildNodes();
					if(nnll != null){
						while(nnll.getLength() > 0){
							first.getTemplate().getCreationConstraints().getDomNode().appendChild(first.getTemplate().getDomNode().getOwnerDocument().importNode(nnll.item(0), true));
							nl.item(i).removeChild(nnll.item(0));
						}
					}
					second.getUser().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = mergeGuarantee(first,guarantee);
					
					second.getUser().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		sdt.set(second);
		sdt.setName(UUID.randomUUID().toString());
		sdt.setServiceName("SAMPLE");
		return first;
	}

	public TemplateDocument merge(TemplateDocument first, MetadataDocument second)throws Exception {		
		NodeList nl = second.getMetadata().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					NodeList nnll = nl.item(i).getChildNodes();
					if(nnll != null){
						while(nnll.getLength() > 0){
							first.getTemplate().getCreationConstraints().getDomNode().appendChild(first.getTemplate().getDomNode().getOwnerDocument().importNode(nnll.item(0), true));
							nl.item(i).removeChild(nnll.item(0));
						}
					}
					second.getMetadata().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = mergeGuarantee(first,guarantee);
					
					second.getMetadata().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		ServiceDescriptionTermType[] terms = first.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
		ServiceDescriptionTermType sdt = null;
		
		for(int i = 0; i < terms.length; i++){
			if(terms[i].getDomNode().getFirstChild().getNodeName().equals("ccpaas:Metadata")){
				sdt = terms[i];
				sdt.getDomNode().getFirstChild().appendChild(sdt.getDomNode().getOwnerDocument().importNode(second.getDomNode().getFirstChild().getFirstChild(),true));
				break;
			}
		}
		if(sdt == null){
			sdt = first.getTemplate().getTerms().getAll().addNewServiceDescriptionTerm();
			sdt.set(second);
			sdt.setName(UUID.randomUUID().toString());
			sdt.setServiceName("SAMPLE");
		}
		return first;
	}
	
	public OrganizationDocument merge(OrganizationDocument first, UserDocument second) throws Exception {
		if(first.getOrganization().getUser() != null){
			throw new Exception("The organization already has a user.");
		}
		NodeList nl = second.getUser().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					first.getOrganization().getDomNode().appendChild(first.getOrganization().getDomNode().getOwnerDocument().importNode(nl.item(i), true));
					second.getUser().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = OrganizationDocument.Factory.parse(mergeGuarantee(first,guarantee).xmlText());
					
					second.getUser().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		first.getOrganization().addNewUser().set(second.getUser());
		return first;
	}
	
	public TemplateDocument merge(TemplateDocument first, ServiceDocument second) throws Exception {
		ServiceDescriptionTermType sdt = first.getTemplate().getTerms().getAll().addNewServiceDescriptionTerm();
		NodeList nl = second.getService().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					NodeList nnll = nl.item(i).getChildNodes();
					if(nnll != null){
						while(nnll.getLength() > 0){
							first.getTemplate().getCreationConstraints().getDomNode().appendChild(first.getTemplate().getDomNode().getOwnerDocument().importNode(nnll.item(0), true));
							nl.item(i).removeChild(nnll.item(0));
						}
					}
					second.getService().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = mergeGuarantee(first,guarantee);
					
					second.getService().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		sdt.set(second);
		sdt.setName(UUID.randomUUID().toString());
		sdt.setServiceName("SAMPLE");
		return first;
	}

	public ServiceDocument merge(ServiceDocument first, ServiceVersionDocument second) throws Exception {
		if(first.getService().getServiceVersion() != null){
			throw new Exception("The service already has a serviceversion.");			
		}
		NodeList nl = second.getServiceVersion().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					first.getService().getDomNode().appendChild(first.getService().getDomNode().getOwnerDocument().importNode(nl.item(i), true));
					second.getServiceVersion().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = ServiceDocument.Factory.parse(mergeGuarantee(first,guarantee).xmlText());
					
					second.getServiceVersion().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		first.getService().addNewServiceVersion().set(second.getServiceVersion());
		return first;
	}

	public TemplateDocument merge(TemplateDocument first, VirtualContainerDocument second) throws Exception {
		ServiceDescriptionTermType sdt = first.getTemplate().getTerms().getAll().addNewServiceDescriptionTerm();
		NodeList nl = second.getVirtualContainer().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					NodeList nnll = nl.item(i).getChildNodes();
					if(nnll != null){
						while(nnll.getLength() > 0){
							first.getTemplate().getCreationConstraints().getDomNode().appendChild(first.getTemplate().getDomNode().getOwnerDocument().importNode(nnll.item(0), true));
							nl.item(i).removeChild(nnll.item(0));
						}
					}
					second.getVirtualContainer().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = mergeGuarantee(first,guarantee);
					
					second.getVirtualContainer().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		sdt.set(second);
		sdt.setName(UUID.randomUUID().toString());
		sdt.setServiceName("SAMPLE");
		return first;
	}

	public VirtualContainerDocument merge(VirtualContainerDocument first, VirtualRuntimeDocument second) throws Exception {
		NodeList nl = second.getVirtualRuntime().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					first.getVirtualContainer().getDomNode().appendChild(first.getVirtualContainer().getDomNode().getOwnerDocument().importNode(nl.item(i), true));
					second.getVirtualRuntime().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = VirtualContainerDocument.Factory.parse(mergeGuarantee(first,guarantee).xmlText());
					
					second.getVirtualRuntime().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		first.getVirtualContainer().addNewVirtualRuntime().set(second.getVirtualRuntime());
		return first;
	}

	public VirtualMachineDocument merge(VirtualMachineDocument first, OperatingSystemDocument second) throws Exception {
		if(first.getVirtualMachine().getOperatingSystem() != null){
			throw new Exception("The service already has an operatingsystem.");			
		}
		NodeList nl = second.getOperatingSystem().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					first.getVirtualMachine().getDomNode().appendChild(first.getVirtualMachine().getDomNode().getOwnerDocument().importNode(nl.item(i), true));
					second.getOperatingSystem().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = VirtualMachineDocument.Factory.parse(mergeGuarantee(first,guarantee).xmlText());
					
					second.getOperatingSystem().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		first.getVirtualMachine().addNewOperatingSystem().set(second.getOperatingSystem());
		return first;
	}

	public VirtualRuntimeDocument merge(VirtualRuntimeDocument first, SoftResourceDocument second) throws Exception {
		NodeList nl = second.getSoftResource().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					first.getVirtualRuntime().getDomNode().appendChild(first.getVirtualRuntime().getDomNode().getOwnerDocument().importNode(nl.item(i), true));
					second.getSoftResource().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = VirtualRuntimeDocument.Factory.parse(mergeGuarantee(first,guarantee).xmlText());
					
					second.getSoftResource().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		first.getVirtualRuntime().addNewSoftResource().set(second.getSoftResource());
		return first;
	}

	public SoftResourceDocument merge(SoftResourceDocument first, SoftAddOnDocument second) throws Exception {
		NodeList nl = second.getSoftAddOn().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					first.getSoftResource().getDomNode().appendChild(first.getSoftResource().getDomNode().getOwnerDocument().importNode(nl.item(i), true));
					second.getSoftAddOn().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = SoftResourceDocument.Factory.parse(mergeGuarantee(first,guarantee).xmlText());
					
					second.getSoftAddOn().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		first.getSoftResource().addNewSoftAddOn().set(second.getSoftAddOn());
		return first;
	}

	public TemplateDocument merge(TemplateDocument first, VirtualMachineDocument second) throws Exception {
		ServiceDescriptionTermType sdt = first.getTemplate().getTerms().getAll().addNewServiceDescriptionTerm();
		NodeList nl = second.getVirtualMachine().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					NodeList nnll = nl.item(i).getChildNodes();
					if(nnll != null){
						while(nnll.getLength() > 0){
							first.getTemplate().getCreationConstraints().getDomNode().appendChild(first.getTemplate().getDomNode().getOwnerDocument().importNode(nnll.item(0), true));
							nl.item(i).removeChild(nnll.item(0));
						}
					}
					second.getVirtualMachine().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = mergeGuarantee(first,guarantee);
					
					second.getVirtualMachine().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		sdt.set(second);
		sdt.setName(UUID.randomUUID().toString());
		sdt.setServiceName("SAMPLE");
		return first;
	}

	public VirtualMachineDocument merge(VirtualMachineDocument first, PhysicalResourceDocument second) throws Exception {
		NodeList nl = second.getPhysicalResource().getDomNode().getChildNodes();
		if(nl != null){
			for(int i = 0; i < nl.getLength(); i++){
				if(nl.item(i).getNodeName().equals("ws:CreationConstraints")){
					first.getVirtualMachine().getDomNode().appendChild(first.getVirtualMachine().getDomNode().getOwnerDocument().importNode(nl.item(i), true));
					second.getPhysicalResource().getDomNode().removeChild(nl.item(i));
					i--;
				}
				if(nl.item(i).getNodeName().equals("ws:GuaranteeTerm")){
					XmlObject guarantee = XmlObject.Factory.parse(nl.item(i));
					first = VirtualMachineDocument.Factory.parse(mergeGuarantee(first,guarantee).xmlText());
					
					second.getPhysicalResource().getDomNode().removeChild(nl.item(i));
					i--;
				}
			}
		}
		first.getVirtualMachine().addNewPhysicalResource().set(second.getPhysicalResource());
		return first;
	}
	
	public static TemplateDocument mergeGuarantee(TemplateDocument first, XmlObject second) throws Exception {
		String xpath = "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
				"//ccpaas:Metadata";
		XmlObject[] result = second.selectPath(xpath);
  
		for(int i = 0; i < result.length; i++){
			MetadataDocument metadata = MetadataDocument.Factory.parse(result[i].xmlText().replaceAll("xml-fragment", "ccpaas:Metadata"));
			second.getDomNode().getFirstChild().removeChild(result[i].getDomNode());
			first = new SLAMerger().merge(first,metadata);
		}
		
		xpath = "declare namespace wsag='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
      "//wsag:ServiceProperties";

		result = second.selectPath(xpath);
      
		ServicePropertiesType[] servProps = first.getTemplate().getTerms().getAll().getServicePropertiesArray();

		for(int i = 0; i < result.length; i++){
			ServicePropertiesType spd = ServicePropertiesType.Factory.parse(result[i].xmlText());
			boolean containsServiceProperties = false;
			for(int j = 0; j < servProps.length; j++){
	        	if(servProps[j].getName().equals(spd.getName()) &&
	        		servProps[j].getServiceName().equals(spd.getServiceName())){
	        		containsServiceProperties = true;
	        		VariableType[] guaranteevt = spd.getVariableSet().getVariableArray();
	        		VariableType[] templatevt = servProps[j].getVariableSet().getVariableArray();
	        		for(int k = 0; k < guaranteevt.length; k++){
	        			boolean containsVariable = false;
	        			for(int l = 0; l < templatevt.length; l++){
	        				if(templatevt[l].getName().equals(guaranteevt[k].getName())){
	        					containsVariable = true;
	        					break;
	        				}
	        			}
	        			if(containsVariable == false){
	        				VariableType vt = servProps[j].getVariableSet().addNewVariable();
	        				vt.setName(guaranteevt[k].getName());
	        				vt.setMetric(guaranteevt[k].getMetric());
	        				vt.setLocation(guaranteevt[k].getLocation());
	        			}
	        		}
	        	}
	        }
		  	if(containsServiceProperties == false){
		  		first.getTemplate().getTerms().getAll().addNewServiceProperties().set(result[i]);
		  	}
		  	second.getDomNode().getFirstChild().removeChild(result[i].getDomNode());
		  }
		  xpath = "declare namespace wsag='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
		  "//wsag:GuaranteeTerm";
			
		  result = second.selectPath(xpath);
		  
		  for(int i = 0; i < result.length; i++){
			  first.getTemplate().getTerms().getAll().addNewGuaranteeTerm().set(result[i]);
		  }
		  
		  return first;
	}
	
	public static XmlObject mergeGuarantee(XmlObject first, XmlObject second) throws Exception {
		first.getDomNode().getFirstChild().appendChild(first.getDomNode().getFirstChild().getOwnerDocument().importNode(second.getDomNode().getFirstChild(), true));
		return first;
	}
}

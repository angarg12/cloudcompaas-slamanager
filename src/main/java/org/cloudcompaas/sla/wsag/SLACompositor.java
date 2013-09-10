package org.cloudcompaas.sla.wsag;

import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;
import org.apache.xmlbeans.XmlObject;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.XMLWrapper;
import org.ogf.schemas.graap.wsAgreement.TemplateDocument;
import org.w3c.dom.NodeList;

import es.upv.grycap.cloudcompaas.OperatingSystemDocument;
import es.upv.grycap.cloudcompaas.PhysicalResourceDocument;
import es.upv.grycap.cloudcompaas.ServiceDocument;
import es.upv.grycap.cloudcompaas.ServiceVersionDocument;
import es.upv.grycap.cloudcompaas.SoftAddOnDocument;
import es.upv.grycap.cloudcompaas.SoftResourceDocument;
import es.upv.grycap.cloudcompaas.VirtualContainerDocument;
import es.upv.grycap.cloudcompaas.VirtualMachineDocument;
import es.upv.grycap.cloudcompaas.VirtualRuntimeDocument;

/**
 * @author angarg12
 *
 */
public class SLACompositor {
	Logger log = Logger.getLogger(this.getClass());
	SLAManager parent;
	SLAMerger merger;
	FragmentValidator validator;
	Map<String, Vector<CompositionItem>> table = new HashMap<String, Vector<CompositionItem>>();
	private static final String baseTemplate = "<ws:Template ws:TemplateId=\"\" " +
			"xmlns:ws=\"http://schemas.ggf.org/graap/2007/03/ws-agreement\" " +
			"xmlns:ccpaas=\"http://www.grycap.upv.es/cloudcompaas\" " +
			"xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" " +
			"xmlns:wsag4j=\"http://schemas.scai.fraunhofer.de/2008/11/wsag4j/engine\">" +
			"<ws:Name>SAMPLE</ws:Name><ws:Context><ws:ServiceProvider>AgreementResponder</ws:ServiceProvider><ws:TemplateId></ws:TemplateId><ws:TemplateName>SAMPLE</ws:TemplateName></ws:Context>" +
			"<ws:Terms><ws:All></ws:All></ws:Terms><ws:CreationConstraints/></ws:Template>";
	int steps = 0;
	int length = Integer.MAX_VALUE;

	public SLACompositor(SLAManager parent_){
		parent = parent_;
		merger = new SLAMerger();
		validator = new FragmentValidator();
	}
	
	// The search algorithm will find all the results with the lowest possible 'length'. This may be
	// used in the branch and bound algorithm for pruning.
	public String generateTemplates(List<String> include, List<String> exclude, String username) throws Exception {
        Logger.getLogger(parent.validator.getClass()).setLevel(Level.DEBUG);
		FileAppender appender = new FileAppender(new SimpleLayout(),"C:/Users/angarg12/slamanager.log");
		log.addAppender(appender);
		log.setLevel(Level.INFO);
		
		//Properties properties = new Properties();
		//properties.load(getClass().getResourceAsStream("/conf/log4j.properties"));
		//PropertyConfigurator.configure(properties);

		String templatesXml = "<ws:Templates " +
		"xmlns:ws=\"http://schemas.ggf.org/graap/2007/03/ws-agreement\">";
		try{
			Vector<CompositionItem> results = new Vector<CompositionItem>();
			CompositionItem template = new CompositionItem(baseTemplate, 
					"", 0, false);
			Vector<CompositionItem> solutionspace = generateSolutionSpace(template.getId());
			
			RESTComm comm = new RESTComm("Catalog");
			comm.setUrl("/user/search?name="+username);
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");

			String metadatatemplate = "<ccpaas:Metadata xmlns:ccpaas=\"http://www.grycap.upv.es/cloudcompaas\"><ccpaas:Replicas><ccpaas:RangeValue><ccpaas:Exact>1</ccpaas:Exact></ccpaas:RangeValue></ccpaas:Replicas></ccpaas:Metadata>";
			String mergedtemplate = merger.merge(template.getTemplate(),metadatatemplate);
			template.setTemplate(merger.merge(mergedtemplate,comm.get().getFirst("//template")));

			long before = System.currentTimeMillis();
			Vector<CompositionItem> startingTemplates = trimSolutionSpace(solutionspace, include, exclude, template);

			for(int i = 0; i < startingTemplates.size(); i++){
				startCompose(startingTemplates.get(i), 0,  
						solutionspace, include, exclude, 
					results);
			}
			Iterator<CompositionItem> it = results.iterator();
			while(it.hasNext()){
				CompositionItem res = it.next();
				TemplateDocument templ = TemplateDocument.Factory.parse(res.getTemplate());
				String templateencodedid = templateIdEncoding(res.getId());
				templ.getTemplate().setTemplateId(templateencodedid);
				templ.getTemplate().getContext().setTemplateId(templateencodedid);
				templatesXml += templ.xmlText();
			}
			
			long after = System.currentTimeMillis();
			//System.out.println((after-before)/1000+" secs, "+steps+" steps "+results.size()+" results");
			//log.fatal(steps);
			templatesXml += "</ws:Templates>";
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return templatesXml;
	}

	/* Root call to the compose function. Handle things slightly different. */
	private void startCompose(CompositionItem target, int valueposition, 
			Vector<CompositionItem> searchspace, List<String> include, List<String> exclude, 
			Vector<CompositionItem> solutionspace) throws Exception {
		//log.info("startCompose");
		if(searchspace == null){
			return;
		}
    	if(solutionspace.size() > 0 && length > solutionspace.firstElement().getDistance()){
    		length = solutionspace.firstElement().getDistance();
    	}
    	while(searchspace.size() > valueposition && (searchspace.get(valueposition).getDistance()+target.getDistance()) > length){
    		valueposition++;
    	}
		while(searchspace.size() > valueposition && 
				searchspace.get(valueposition).isTerminal() == true && 
				(searchspace.get(valueposition).getId().contains("_VIRTUALMACHINE-") == true && 
				 target.getId().contains("_VIRTUALMACHINE-") == true ||
				searchspace.get(valueposition).getId().contains("_VIRTUALCONTAINER-") == true &&
				target.getId().contains("_VIRTUALCONTAINER-") == true)){
			valueposition++;
		}
steps++;

		if(searchspace.size() <= valueposition){
			//log.info(steps+" "+target.getId()+":"+target.getDistance()+" "+valueposition+" "+searchspace.size()+" "+solutionspace.size());
			CompositionItem result = new CompositionItem(target.getTemplate(),target.getId(),target.getDistance(), true);

			if(meetExternalRestrictions(include, exclude, result.getTemplate()) == true &&
					meetAbsoluteInternalRestrictions(result.getTemplate()) == true && 
					meetFinalInternalRestrictions(result.getTemplate()) == true){
		        if(validator.validate(result.getTemplate())){
		        	if(result.getDistance() < length){
		        		solutionspace.removeAllElements();
		        	}
	        		solutionspace.add(result);
				}
			}
			return;
		}	
		
		//log.info(steps+" "+target.getId()+":"+target.getDistance()+" "+valueposition+" "+searchspace.size()+" "+solutionspace.size()+" "+searchspace.get(valueposition).getId()+":"+searchspace.get(valueposition).getDistance());
		if(searchspace.get(valueposition).isTerminal() == true){
			//log.info("startCompose - terminal branch");
			// this part of the code is the branch where the template is not added
			startCompose(target, valueposition+1, 
					searchspace, include, exclude, solutionspace);

			// this part of the code is the branch where the template is added
			String addTemplate = null;
			try{
				addTemplate = merger.merge(target.getTemplate(), searchspace.get(valueposition).getTemplate());
			}catch(Exception e){
				e.printStackTrace();
				return;
			}

			if(meetAbsoluteInternalRestrictions(addTemplate) == false || 
					meetFinalInternalRestrictions(addTemplate) == false ||
					(target.getDistance()+searchspace.get(valueposition).getDistance()) > length){
				return;
			}
			CompositionItem result = new CompositionItem(addTemplate, target.getId() + searchspace.get(valueposition).getId(),target.getDistance()+searchspace.get(valueposition).getDistance(), false);
/*
			int length = result.getDistance();
        	if(solutionspace.size() > 0){
        		length = solutionspace.firstElement().getDistance();
        	}
        	if(result.getDistance() > length){
        		return;
        	}*/
			startCompose(result, valueposition+1, 
					searchspace, include, exclude, solutionspace);
		}else{
			//log.info("startCompose - nonterminal branch");
			
			CompositionItem newtarget = new CompositionItem(searchspace.get(valueposition).getTemplate(), searchspace.get(valueposition).getId(), searchspace.get(valueposition).getDistance(), false);
			//searchspace.addAll(valueposition+1, spawnSubSearch(newtarget, include, exclude));

			searchspace.remove(valueposition);
			searchspace.addAll(spawnSubSearch(newtarget, include, exclude));


			for(int i = 0; i < searchspace.size(); i++){
				//log.info("--"+i+" "+searchspace.get(i).getId());
			}
			
			startCompose(target, valueposition, 
					searchspace, include, exclude, solutionspace);
		}
	}
	
	private void compose(CompositionItem target, int valueposition, 
			Vector<CompositionItem> searchspace, List<String> include, List<String> exclude, 
			Vector<CompositionItem> solutionspace) throws Exception {
		//log.info("compose");
		if(searchspace == null){
			return;
		}
    	while(searchspace.size() > valueposition && (searchspace.get(valueposition).getDistance()+target.getDistance()) > length){
    		valueposition++;
    	}
    	/*
    	while(searchspace.size() > valueposition && 
				searchspace.get(valueposition).isTerminal() == true && 
				searchspace.get(valueposition).getId().contains("_VIRTUALMACHINE-") == target.getId().contains("_VIRTUALMACHINE-")){
			System.out.println(searchspace.get(valueposition).getId()+" "+target.getId());
			valueposition++;
		}*/
		steps++;
		
		if(searchspace.size() <= valueposition){
			//log.info(steps+" "+target.getId()+":"+target.getDistance()+" "+valueposition+" "+searchspace.size()+" "+solutionspace.size());

			CompositionItem result = new CompositionItem(target.getTemplate(),target.getId(),target.getDistance(), true);
			
			if(meetAbsoluteInternalRestrictions(result.getTemplate()) == true && 
					meetFinalInternalRestrictions(result.getTemplate()) == true){
				// we can apply the validator to this template ONLY if it is a Virtual Machine. If it is a SLA fragment
				// that does not include a VM, then it will fail the validation even if is a correct fragment.
				if(result.getId().contains("_VIRTUALMACHINE") == false ||
					validator.validate(result.getTemplate()) == true){
					solutionspace.add(result);
				}
			}
			return;
		}	
		//log.info(steps+" "+target.getId()+":"+target.getDistance()+" "+valueposition+" "+searchspace.size()+" "+solutionspace.size()+" "+searchspace.get(valueposition).getId()+":"+searchspace.get(valueposition).getDistance());
		if(searchspace.get(valueposition).isTerminal() == true){
			//log.info("compose - terminal branch");
			// this part of the code is the branch where the template is not added
			compose(target, valueposition+1, 
					searchspace, include, exclude, solutionspace);

			// this part of the code is the branch where the template is added
			String addTemplate = null;
			try{
				addTemplate = merger.merge(target.getTemplate(), searchspace.get(valueposition).getTemplate());
			}catch(Exception e){
				e.printStackTrace();
				return;
			}
			if(meetAbsoluteInternalRestrictions(addTemplate) == false){
				return;
			}
			CompositionItem result = new CompositionItem(addTemplate, target.getId() + searchspace.get(valueposition).getId(),target.getDistance()+searchspace.get(valueposition).getDistance(), false);
			compose(result, valueposition+1, 
					searchspace, include, exclude, solutionspace);
		}else{
			//log.info("compose - nonterminal branch");
			CompositionItem newtarget = new CompositionItem(searchspace.get(valueposition).getTemplate(), searchspace.get(valueposition).getId(), searchspace.get(valueposition).getDistance(), false);
			//searchspace.addAll(valueposition+1, spawnSubSearch(newtarget, include, exclude));
			
			searchspace.remove(valueposition);
			searchspace.addAll(spawnSubSearch(newtarget, include, exclude));

//			for(int i = 0; i < searchspace.size(); i++){
//				log.info("++"+i+" "+searchspace.get(i).getId());
//			}
			
			compose(target, valueposition, 
					searchspace, include, exclude, solutionspace);
		}
	}

	private Vector<CompositionItem> spawnSubSearch(CompositionItem target, List<String> include, List<String> exclude){
		Vector<CompositionItem> newsolutionspace = null; 
		try{
			newsolutionspace = getSolutionSpace(target.getId());
			if(newsolutionspace == null){
				Vector<CompositionItem> newsearchspace = generateSolutionSpace(target.getId());
				newsolutionspace = new Vector<CompositionItem>();
				if(newsearchspace != null){
					Vector<CompositionItem> trimspace = trimSolutionSpace(newsearchspace, include, exclude, target);
					//log.info("TRIMSPACEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE "+trimspace.size());
					
					for(int i = 0; i < trimspace.size(); i++){
						//log.info(trimspace.get(i).getId()+" target "+target.getId());
						compose(trimspace.get(i), 0, newsearchspace, include, exclude, newsolutionspace);
					}
					//compose(target, 0, 
					//	solution, elements, solutionspace, maxdistance);
				}
				putSolutionSpace(target.getId(), newsolutionspace);
			}
		}catch(Exception e){
			//System.out.println(e.getMessage());
		}
		return newsolutionspace;
	}
	
	/*private Vector<CompositionItem> mergeCartesian(CompositionItem first, Vector<CompositionItem> second) throws Exception {
		Vector<CompositionItem> firstSet = new Vector<CompositionItem>();
		firstSet.add(first);
		return mergeCartesian(firstSet, second);
	}*/
	
	private Vector<CompositionItem> mergeCartesian(Vector<CompositionItem> first, Vector<CompositionItem> second) throws Exception {
		Vector<CompositionItem> productSet = new Vector<CompositionItem>();
		for(int i = 0; i < first.size(); i++){
			for(int j = 0; j < second.size(); j++){
				CompositionItem newtarget = new CompositionItem(merger.merge(first.get(i).getTemplate(), second.get(j).getTemplate()), 
						first.get(i).getId() + second.get(j).getId(), 
						first.get(i).getDistance()+second.get(j).getDistance(),
						true);
				productSet.add(newtarget);
			}
		}
		return productSet;
	}
	
	private Vector<CompositionItem> getSolutionSpace(String id) throws Exception {
		//String baseId = id.split("-")[0];
		return table.get(id);
	}
	
	private void putSolutionSpace(String id, Vector<CompositionItem> space) throws Exception {
		//String baseId = id.split("-")[0];
		table.put(id, space);
	}
	
	private Vector<CompositionItem> generateSolutionSpace(String id) throws Exception {		
		String baseId = id.split("-")[0];
		//log.info("generate "+id);
		if(id.equals("")){
			return generateTemplateSolutionSpace();
		}else if(baseId.equals("_SERVICE")){
			return generateServiceSolutionSpace(id.split("-")[1]);
		}else if(baseId.equals("_VIRTUALMACHINE")){
			return generateVirtualMachineSolutionSpace(id.split("-")[1]);
		} else if(baseId.equals("_VIRTUALCONTAINER")){
			return generateVirtualContainerSolutionSpace(id.split("-")[1]);
		}else if(baseId.equals("_VIRTUALRUNTIME")){
			return generateVirtualRuntimeSolutionSpace(id.split("-")[1]);
		}else if(baseId.equals("_SOFTRESOURCE")){
			return generateSoftResourceSolutionSpace(id.split("-")[1]);
		}
		return null;
	}
	
	private Vector<CompositionItem> generateTemplateSolutionSpace() throws Exception {
		Vector<CompositionItem> space = new Vector<CompositionItem>();

		RESTComm comm = new RESTComm("Catalog");
		XMLWrapper wrap;
		String[] ids;
		
		comm.setUrl("/virtualmachine");
		wrap = comm.get();
		ids = wrap.get("//virtualmachine");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/virtualmachine/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_VIRTUALMACHINE-"+ids[i]
			                                  			 , 1
			                                  			 , false);
			space.add(item);
		}
		
		comm.setUrl("/virtualcontainer");
		wrap = comm.get();
		ids = wrap.get("//virtualcontainer");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/virtualcontainer/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_VIRTUALCONTAINER-"+ids[i]
			                                  			 , 1
			                                  			 , false);
			space.add(item);
		}
		
		comm.setUrl("/service");
		wrap = comm.get();
		ids = wrap.get("//service");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/service/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_SERVICE-"+ids[i]
			                                  			 , 1
			                                  			 , false);
			space.add(item);
		}

		comm.setUrl("/guarantee");
		wrap = comm.get();
		ids = wrap.get("//guarantee");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/guarantee/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_GUARANTEE-"+ids[i]
			                                  			 , 1
			                                  			 , true);
			space.add(item);
		}
		
		return space;
	}	
	
	private Vector<CompositionItem> generateServiceSolutionSpace(String serviceid) throws Exception {
		Vector<CompositionItem> space = new Vector<CompositionItem>();
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/serviceversion/search?id_service="+serviceid);
		XMLWrapper wrap = comm.get();
		String[] ids = wrap.get("//id_serviceversion");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/serviceversion/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_SERVICEVERSION-"+ids[i]
			                                  			 , 1
			                                  			 , true);
			space.add(item);
		}
		
		return space;
	}	
	
	private Vector<CompositionItem> generateVirtualMachineSolutionSpace(String virtualmachineid) throws Exception {
		Vector<CompositionItem> space = new Vector<CompositionItem>();
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/operatingsystem");
		XMLWrapper wrap = comm.get();
		String[] ids = wrap.get("//operatingsystem");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/operatingsystem/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_OPERATINGSYSTEM-"+ids[i]
			                                  			 , 1
			                                  			 , true);
			space.add(item);
		}
		
		comm.setUrl("/has_physicalresource/search?id_virtualmachine="+virtualmachineid);
		wrap = comm.get();
		ids = wrap.get("//id_physicalresource");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/physicalresource/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_PHYSICALRESOURCE-"+ids[i]
			                                  			 , 1
			                                  			 , true);
			space.add(item);
		}
		
		return space;
	}	
	
	private Vector<CompositionItem> generateVirtualContainerSolutionSpace(String virtualcontainerid) throws Exception {
		Vector<CompositionItem> space = new Vector<CompositionItem>();
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/has_virtualruntime/search?id_virtualcontainer="+virtualcontainerid);
		XMLWrapper wrap = comm.get();
		String[] ids = wrap.get("//id_virtualruntime");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/virtualruntime/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_VIRTUALRUNTIME-"+ids[i]
			                                  			 , 1
			                                  			 , false);
			space.add(item);
		}
		
		return space;
	}	
	
	private Vector<CompositionItem> generateVirtualRuntimeSolutionSpace(String virtualruntimeid) throws Exception {
		Vector<CompositionItem> space = new Vector<CompositionItem>();
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/has_softresource/search?id_virtualruntime="+virtualruntimeid);
		XMLWrapper wrap = comm.get();
		String[] ids = wrap.get("//id_softresource");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/softresource/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_SOFTRESOURCE-"+ids[i]
			                                  			 , 1
			                                  			 , false);
			space.add(item);
		}
		
		return space;
	}	
	
	private Vector<CompositionItem> generateSoftResourceSolutionSpace(String softresourceid) throws Exception {
		Vector<CompositionItem> space = new Vector<CompositionItem>();
		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/has_softaddon/search?id_softresource="+softresourceid);
		XMLWrapper wrap = comm.get();
		String[] ids = wrap.get("//id_softaddon");
		for(int i = 0; i < ids.length; i++){
			comm.setUrl("/softaddon/"+ids[i]+"/id_sla_template");
			String sla_id = comm.get().getFirst("//id_sla_template");
			comm.setUrl("/sla_template/"+sla_id+"/template");
			CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
			                                  			 , "_SOFTADDON-"+ids[i]
			                                  			 , 1
			                                  			 , true);
			space.add(item);
		}

		return space;
	}

	private Vector<CompositionItem> trimSolutionSpace(Vector<CompositionItem> space, List<String> includes, List<String> excludes, CompositionItem item) throws Exception {
		//log.info("trim "+item.getId());
		if(item.getId().equals("")){
			 return trimTemplateSolutionSpace(space, includes, excludes, item);
		}else if(item.getId().startsWith("_SERVICE-")){
			return trimServiceSolutionSpace(space, includes, excludes, item);
		}else if(item.getId().startsWith("_VIRTUALMACHINE-")){
			return trimVirtualMachineSolutionSpace(space, includes, excludes, item);
		} else if(item.getId().startsWith("_VIRTUALCONTAINER-")){
			return trimVirtualContainerSolutionSpace(space, includes, excludes, item);
		}else if(item.getId().startsWith("_VIRTUALRUNTIME-")){
			return trimVirtualRuntimeSolutionSpace(space, includes, excludes, item);
		}else if(item.getId().startsWith("_SOFTRESOURCE-")){
			return trimSoftResourceSolutionSpace(space, includes, excludes, item);
		}
		return new Vector<CompositionItem>();
	}
	
	private Vector<CompositionItem> trimTemplateSolutionSpace(Vector<CompositionItem> searchspace, List<String> includes, List<String> excludes, CompositionItem item) throws Exception {
		Vector<CompositionItem> newsearchspace = new Vector<CompositionItem>();
		Vector<CompositionItem> newsolutionspace  = new Vector<CompositionItem>();
		newsolutionspace.add(item);
		newsearchspace.addAll(searchspace);
	
		for(int i = 0; i < includes.size(); i++){
			/* We do not add to the solutions the elements (potentially) non terminals, because it cause bugs.
			 * e.g. If you add a Service to the solution, it wont get composed with its service version, and the result will be wrong.
			 * There MAY BE a workaround.
			 * First off this method will return a new set of composition items.
			 * This set will be built as follows:
			 * For every non-terminal item that is to be trimmed:
			 * - The item is expanded. This produces a new set of ci.
			 * - Notice that subsequent items to be added will need to be added to those that has already been produced.
			 */
			if(includes.get(i).contains("ccpaas:VirtualMachine ") || includes.get(i).contains("ccpaas:VirtualContainer ") || includes.get(i).contains("ccpaas:Service ")){
				Iterator<CompositionItem> solutionIterator = newsolutionspace.iterator();
				while(solutionIterator.hasNext()){
					CompositionItem solutionItem = solutionIterator.next();
					Pattern p = Pattern.compile(includes.get(i));
			
					if(p.matcher(solutionItem.getTemplate()).matches() == true){
						continue;
					}
					
					boolean spaceContainsItem = false;
					Iterator<CompositionItem> it = searchspace.iterator();
					while(it.hasNext()){
						CompositionItem ci = it.next();
						if(p.matcher(ci.getTemplate()).matches() == true){
							spaceContainsItem = true;
							newsearchspace.remove(ci);
							
							CompositionItem newtarget = new CompositionItem(ci.getTemplate(), ci.getId(), ci.getDistance(), false);
							
							Vector<CompositionItem> firstSet = new Vector<CompositionItem>();
							firstSet.addAll(newsolutionspace);
							newsolutionspace.remove(solutionItem);
							newsolutionspace.addAll(
									mergeCartesian(firstSet, 
											spawnSubSearch(newtarget, excludes, excludes)));
	//						log.info("************************************************");
	//						log.info(newsolutionspace.size());
	//						for(int j = 0; j < newsolutionspace.size(); j++){
	//							log.info(newsolutionspace.get(j).getId());
	//							log.info(newsolutionspace.get(j).getTemplate());
	//						}
	//						log.info("************************************************");
							//compose(item, 0, , includes, excludes, newsolutionspace);
							break;
						}
					}
					
					if(spaceContainsItem == false){
						throw new Exception("The solution space does not contain the required item: "+includes.get(i));
					}
				}
			}
			
			if(includes.get(i).contains("ws:GuaranteeTerm ") == true){
				Iterator<CompositionItem> solutionIterator = newsolutionspace.iterator();
				while(solutionIterator.hasNext()){
					CompositionItem solutionItem = solutionIterator.next();
					Pattern p = Pattern.compile(includes.get(i));
			
					if(p.matcher(solutionItem.getTemplate()).matches() == true){
						continue;
					}
				
					boolean spaceContainsGuarantee = false;
					Iterator<CompositionItem> it = searchspace.iterator();
					while(it.hasNext()){
						CompositionItem ci = it.next();
						if(p.matcher(ci.getTemplate()).matches() == true){
							spaceContainsGuarantee = true;
							newsearchspace.remove(ci);
						
							solutionItem.setId(solutionItem.getId() + ci.getId());
							solutionItem.setDistance(solutionItem.getDistance()+ci.getDistance());
							solutionItem.setTemplate(merger.merge(solutionItem.getTemplate(), ci.getTemplate()));
						}
					}
	
					if(spaceContainsGuarantee == false){
						throw new Exception("The solution space does not contain the required item: "+includes.get(i));
					}	
				}
			}
			/*
			if(includes[i].startsWith("<ccpaas:VirtualMachine ") == true){
				ServiceDescriptionTermType[] terms = template.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
				boolean containsVM = false;
				for(int j = 0; j < terms.length; j++){
					if(terms[j].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualMachine")){
						VirtualMachineDocument s = VirtualMachineDocument.Factory.parse(terms[j].getDomNode().getFirstChild());
						if(s.xmlText().contains(includes[i]) == true){
							containsVM = true;
							break;
						}
					}
				}
				if(containsVM == true){
					continue;
				}
				
				boolean spaceContainsVM = false;
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(ci.getTemplate().contains(includes[i]) == true){
						spaceContainsVM = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						template = TemplateDocument.Factory.parse(merger.merge(template.xmlText(), ci.getTemplate()));
						break;
					}
				}
				
				if(spaceContainsVM == false){
					throw new Exception("The solution space does not contain the required item: "+includes[i]);
				}
			}
			if(includes[i].startsWith("<ccpaas:VirtualContainer ") == true){
				ServiceDescriptionTermType[] terms = template.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
				boolean containsVC = false;
				for(int j = 0; j < terms.length; j++){
					if(terms[j].getDomNode().getFirstChild().getNodeName().equals("ccpaas:VirtualContainer")){
						VirtualContainerDocument s = VirtualContainerDocument.Factory.parse(terms[j].getDomNode().getFirstChild());
						if(s.xmlText().contains(includes[i]) == true){
							containsVC = true;
							break;
						}
					}
				}
				if(containsVC == true){
					continue;
				}
				
				boolean spaceContainsVC = false;
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(ci.getTemplate().contains(includes[i]) == true){
						spaceContainsVC = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						template = TemplateDocument.Factory.parse(merger.merge(template.xmlText(), ci.getTemplate()));
						newspace.addAll(generateSolutionSpace(ci.getId()));
						break;
					}
				}
				
				if(spaceContainsVC == false){
					throw new Exception("The solution space does not contain the required item: "+includes[i]);
				}
			}
			*/
			/*
			 * HOWEVER we can give a special treatment to virtual services that greatly improves performance. Since there are not forward
			 * or cross references (e.g. no VC refers a VS) we can EXCLUDE from the search space those services that are not required.
			 * NOTICE that this WOULD NOT WORK if there are cross references (a service refers to another one).
			 *
			if(includes.get(i).contains("ccpaas:Service ") == true){
				ServiceDescriptionTermType[] terms = template.getTemplate().getTerms().getAll().getServiceDescriptionTermArray();
				boolean containsService = false;
				Pattern p = Pattern.compile(includes.get(i));
				for(int j = 0; j < terms.length; j++){
					if(p.matcher(terms[j].xmlText()).matches() == true){
						containsService = true;
						break;
					}
				}
				
				Iterator<CompositionItem> it = searchspace.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == false && 
							ci.getTemplate().contains("<ccpaas:Service ")){
						newsearchspace.remove(ci);
					}
				}
				
				if(containsService == true){
					continue;
				}
			}
			*/
		}

		for(int i = 0; i < excludes.size(); i++){
			if(excludes.get(i).contains("ccpaas:VirtualMachine ") == true){
				Iterator<CompositionItem> solutionIterator = newsolutionspace.iterator();
				while(solutionIterator.hasNext()){
					CompositionItem solutionItem = solutionIterator.next();
					Pattern p = Pattern.compile(excludes.get(i));
					if(p.matcher(solutionItem.getTemplate()).matches() == true){
						newsolutionspace.remove(solutionItem);
					}
					
					Iterator<CompositionItem> it = searchspace.iterator();
					while(it.hasNext()){
						CompositionItem ci = it.next();
						if(ci.getId().contains("_VIRTUALMACHINE-") == false){
							continue;
						}
						if(p.matcher(ci.getTemplate()).matches() == true){
							newsearchspace.remove(ci);
						}
					}
				}
			}
			if(excludes.get(i).contains("ccpaas:VirtualContainer ") == true){
				Iterator<CompositionItem> solutionIterator = newsolutionspace.iterator();
				while(solutionIterator.hasNext()){
					CompositionItem solutionItem = solutionIterator.next();
					Pattern p = Pattern.compile(excludes.get(i));
					if(p.matcher(solutionItem.getTemplate()).matches() == true){
						newsolutionspace.remove(solutionItem);
					}
				
					Iterator<CompositionItem> it = searchspace.iterator();
					while(it.hasNext()){
						CompositionItem ci = it.next();
						if(ci.getId().contains("_VIRTUALCONTAINER-") == false){
							continue;
						}
						if(p.matcher(ci.getTemplate()).matches() == true){
							newsearchspace.remove(ci);
						}
					}
				}
			}
			if(excludes.get(i).contains("ccpaas:Service ") == true){
				Iterator<CompositionItem> solutionIterator = newsolutionspace.iterator();
				while(solutionIterator.hasNext()){
					CompositionItem solutionItem = solutionIterator.next();
					Pattern p = Pattern.compile(excludes.get(i));
					if(p.matcher(solutionItem.getTemplate()).matches() == true){
						newsolutionspace.remove(solutionItem);
					}
				
					Iterator<CompositionItem> it = searchspace.iterator();
					while(it.hasNext()){
						CompositionItem ci = it.next();
						if(ci.getId().contains("_SERVICE-") == false){
							continue;
						}
						if(p.matcher(ci.getTemplate()).matches() == true){
							newsearchspace.remove(ci);
						}
					}
				}
			}
			if(excludes.get(i).contains("ws:GuaranteeTerm ") == true){
				Iterator<CompositionItem> solutionIterator = newsolutionspace.iterator();
				while(solutionIterator.hasNext()){
					CompositionItem solutionItem = solutionIterator.next();
					Pattern p = Pattern.compile(excludes.get(i));
					if(p.matcher(solutionItem.getTemplate()).matches() == true){
						newsolutionspace.remove(solutionItem);
					}
					
					Iterator<CompositionItem> it = searchspace.iterator();
					while(it.hasNext()){
						CompositionItem ci = it.next();
						if(ci.getId().contains("_GUARANTEE-") == false){
							continue;
						}
						if(p.matcher(ci.getTemplate()).matches() == true){
							newsearchspace.remove(ci);
						}
					}
				}
			}
		}
		
		searchspace.retainAll(newsearchspace);
		return newsolutionspace;
	}
	
	private Vector<CompositionItem> trimServiceSolutionSpace(Vector<CompositionItem> space, List<String> includes, List<String> excludes, CompositionItem item) throws Exception {
		ServiceDocument service = ServiceDocument.Factory.parse(item.getTemplate());
		Vector<CompositionItem> newspace = new Vector<CompositionItem>();
		Vector<CompositionItem> newsolutionspace  = new Vector<CompositionItem>();
		newspace.addAll(space);

		for(int i = 0; i < includes.size(); i++){
			if(includes.get(i).contains("ccpaas:ServiceVersion ") == true){
				Pattern p = Pattern.compile(includes.get(i));
				if(service.getService().getServiceVersion() != null){
					ServiceVersionDocument sv = ServiceVersionDocument.Factory.parse(service.getService().getServiceVersion().getDomNode());
					if(p.matcher(sv.xmlText()).matches() == true){
						continue;
					}
				}
				
				boolean containsServiceVersion = false;
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						containsServiceVersion = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						item.setDistance(item.getDistance()+ci.getDistance());
						service = ServiceDocument.Factory.parse(merger.merge(service.xmlText(), ci.getTemplate()));
					}
				}
				
				if(containsServiceVersion == false){
					throw new Exception("The solution space does not contain the required item: "+includes.get(i));
				}
			}
		}

		for(int i = 0; i < excludes.size(); i++){
			if(excludes.get(i).contains("ccpaas:ServiceVersion ") == true){
				Pattern p = Pattern.compile(excludes.get(i));
				if(service.getService().getServiceVersion() != null){
					ServiceVersionDocument sv = ServiceVersionDocument.Factory.parse(service.getService().getServiceVersion().getDomNode());
					if(p.matcher(sv.xmlText()).matches() == true){
						space = new Vector<CompositionItem>();
						return newsolutionspace;
					}
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(ci.getId().contains("_SERVICEVERSION-") == false){
						continue;
					}
					if(p.matcher(ci.getTemplate()).matches() == true){
						newspace.remove(ci);
					}
				}
			}
		}
		
		space = newspace;
		item.setTemplate(service.xmlText());
		newsolutionspace.add(item);
		return newsolutionspace;
	}
	
	private Vector<CompositionItem> trimVirtualMachineSolutionSpace(Vector<CompositionItem> space, List<String> includes, List<String> excludes, CompositionItem item) throws Exception {
		VirtualMachineDocument vm = VirtualMachineDocument.Factory.parse(item.getTemplate());
		Vector<CompositionItem> newspace = new Vector<CompositionItem>();
		Vector<CompositionItem> newsolutionspace  = new Vector<CompositionItem>();
		newspace.addAll(space);
		
		for(int i = 0; i < includes.size(); i++){
			if(includes.get(i).contains("ccpaas:OperatingSystem ") == true){
				Pattern p = Pattern.compile(includes.get(i));
				if(vm.getVirtualMachine().getOperatingSystem() != null){
					OperatingSystemDocument os = OperatingSystemDocument.Factory.parse(vm.getVirtualMachine().getOperatingSystem().getDomNode());
					if(p.matcher(os.xmlText()).matches() == true){
						continue;
					}
				}
				
				boolean containsOS = false;
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						containsOS = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						item.setDistance(item.getDistance()+ci.getDistance());
						vm = VirtualMachineDocument.Factory.parse(merger.merge(vm.xmlText(), ci.getTemplate()));
					}
				}
				
				if(containsOS == false){
					throw new Exception("The solution space does not contain the required item: "+includes.get(i));
				}
			}
			if(includes.get(i).contains("ccpaas:PhysicalResource ") == true){
				Pattern p = Pattern.compile(includes.get(i));
				PhysicalResourceDocument pr;
				boolean containsPhysicalResource = false;
				for(int j = 0; j < vm.getVirtualMachine().getPhysicalResourceArray().length;j++){
					pr = PhysicalResourceDocument.Factory.parse(vm.getVirtualMachine().getPhysicalResourceArray()[j].getDomNode());
					if(p.matcher(pr.xmlText()).matches() == true){
						containsPhysicalResource = true;
						break;
					}
				}
				
				if(containsPhysicalResource == true){
					continue;
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						containsPhysicalResource = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						item.setDistance(item.getDistance()+ci.getDistance());
						vm = VirtualMachineDocument.Factory.parse(merger.merge(vm.xmlText(), ci.getTemplate()));
					}
				}
				
				if(containsPhysicalResource == false){
					throw new Exception("The solution space does not contain the required item: "+includes.get(i));
				}
			}
		}
		
		for(int i = 0; i < excludes.size(); i++){
			if(excludes.get(i).contains("ccpaas:OperatingSystem ") == true){
				Pattern p = Pattern.compile(excludes.get(i));
				if(vm.getVirtualMachine().getOperatingSystem() != null){
					OperatingSystemDocument os = OperatingSystemDocument.Factory.parse(vm.getVirtualMachine().getOperatingSystem().getDomNode());
					if(p.matcher(os.xmlText()).matches() == true){
						space = new Vector<CompositionItem>();
						return newsolutionspace;
					}
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(ci.getId().contains("_OPERATINGSYSTEM-") == false){
						continue;
					}
					if(p.matcher(ci.getTemplate()).matches() == true){
						newspace.remove(ci);
					}
				}
			}
			if(excludes.get(i).contains("ccpaas:PhysicalResource ") == true){
				Pattern p = Pattern.compile(excludes.get(i));
				PhysicalResourceDocument pr;
				for(int j = 0; j < vm.getVirtualMachine().getPhysicalResourceArray().length;j++){
					pr = PhysicalResourceDocument.Factory.parse(vm.getVirtualMachine().getPhysicalResourceArray()[j].getDomNode());
					if(p.matcher(pr.xmlText()).matches() == true){
						space = new Vector<CompositionItem>();
						return newsolutionspace;
					}
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(ci.getId().contains("_PHYSICALRESOURCE-") == false){
						continue;
					}
					if(p.matcher(ci.getTemplate()).matches() == true){
						newspace.remove(ci);
					}
				}
			}
		}
		
		item.setTemplate(vm.xmlText());
		newsolutionspace.add(item);
		return newsolutionspace;
	}
	
	private Vector<CompositionItem> trimVirtualContainerSolutionSpace(Vector<CompositionItem> space, List<String> includes, List<String> excludes, CompositionItem item) throws Exception {
		VirtualContainerDocument vc = VirtualContainerDocument.Factory.parse(item.getTemplate());
		Vector<CompositionItem> newspace = new Vector<CompositionItem>();
		Vector<CompositionItem> newsolutionspace  = new Vector<CompositionItem>();
		newspace.addAll(space);
		
		for(int i = 0; i < includes.size(); i++){
			if(includes.get(i).contains("ccpaas:VirtualRuntime ") == true){
				Pattern p = Pattern.compile(includes.get(i));
				VirtualRuntimeDocument vr;
				boolean containsVirtualRuntime = false;
				for(int j = 0; j < vc.getVirtualContainer().getVirtualRuntimeArray().length;j++){
					vr = VirtualRuntimeDocument.Factory.parse(vc.getVirtualContainer().getVirtualRuntimeArray()[j].getDomNode());
					if(p.matcher(vr.xmlText()).matches() == true){
						containsVirtualRuntime = true;
						break;
					}
				}
				
				if(containsVirtualRuntime == true){
					continue;
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						containsVirtualRuntime = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						item.setDistance(item.getDistance()+ci.getDistance());
						vc = VirtualContainerDocument.Factory.parse(merger.merge(vc.xmlText(), ci.getTemplate()));
					}
				}
				
				if(containsVirtualRuntime == false){
					throw new Exception("The solution space does not contain the required item: "+includes.get(i));
				}
			}
		}

		for(int i = 0; i < excludes.size(); i++){
			if(excludes.get(i).contains("ccpaas:VirtualRuntime ") == true){
				Pattern p = Pattern.compile(excludes.get(i));
				VirtualRuntimeDocument vr;
				for(int j = 0; j < vc.getVirtualContainer().getVirtualRuntimeArray().length;j++){
					vr = VirtualRuntimeDocument.Factory.parse(vc.getVirtualContainer().getVirtualRuntimeArray()[j].getDomNode());
					if(p.matcher(vr.xmlText()).matches() == true){
						space = new Vector<CompositionItem>();
						return newsolutionspace;
					}
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						newspace.remove(ci);
					}
				}
			}
		}
		space = newspace;
		item.setTemplate(vc.xmlText());
		newsolutionspace.add(item);
		return newsolutionspace;
	}
	
	private Vector<CompositionItem> trimVirtualRuntimeSolutionSpace(Vector<CompositionItem> space, List<String> includes, List<String> excludes, CompositionItem item) throws Exception {
		VirtualRuntimeDocument vr = VirtualRuntimeDocument.Factory.parse(item.getTemplate());
		Vector<CompositionItem> newspace = new Vector<CompositionItem>();
		Vector<CompositionItem> newsolutionspace  = new Vector<CompositionItem>();
		newspace.addAll(space);
		
		for(int i = 0; i < includes.size(); i++){
			if(includes.get(i).contains("ccpaas:SoftResource ") == true){
				Pattern p = Pattern.compile(includes.get(i));
				SoftResourceDocument srd;
				boolean containsSoftResource = false;
				for(int j = 0; j < vr.getVirtualRuntime().getSoftResourceArray().length;j++){
					srd = SoftResourceDocument.Factory.parse(vr.getVirtualRuntime().getSoftResourceArray()[j].getDomNode());
					if(p.matcher(srd.xmlText()).matches() == true){
						containsSoftResource = true;
						break;
					}
				}
				
				if(containsSoftResource == true){
					continue;
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						containsSoftResource = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						item.setDistance(item.getDistance()+ci.getDistance());
						vr = VirtualRuntimeDocument.Factory.parse(merger.merge(vr.xmlText(), ci.getTemplate()));
					}
				}
				
				if(containsSoftResource == false){
					return newsolutionspace;
				}
			}
		}
		
		for(int i = 0; i < excludes.size(); i++){
			if(excludes.get(i).contains("ccpaas:SoftResource ") == true){
				Pattern p = Pattern.compile(excludes.get(i));
				SoftResourceDocument srd;
				for(int j = 0; j < vr.getVirtualRuntime().getSoftResourceArray().length;j++){
					srd = SoftResourceDocument.Factory.parse(vr.getVirtualRuntime().getSoftResourceArray()[j].getDomNode());
					if(p.matcher(srd.xmlText()).matches() == true){
						space = new Vector<CompositionItem>();
						return newsolutionspace;
					}
				}
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						newspace.remove(ci);
					}
				}
			}
		}
		
		item.setTemplate(vr.xmlText());
		newsolutionspace.add(item);
		return newsolutionspace;
	}
	
	private Vector<CompositionItem> trimSoftResourceSolutionSpace(Vector<CompositionItem> space, List<String> includes, List<String> excludes, CompositionItem item) throws Exception {
		SoftResourceDocument softresource = SoftResourceDocument.Factory.parse(item.getTemplate());
		Vector<CompositionItem> newspace = new Vector<CompositionItem>();
		Vector<CompositionItem> newsolutionspace  = new Vector<CompositionItem>();
		newspace.addAll(space);
		
		for(int i = 0; i < includes.size(); i++){
			if(includes.get(i).contains("ccpaas:SoftAddOn ") == true){
				Pattern p = Pattern.compile(includes.get(i));
				SoftAddOnDocument saod;
				boolean containsAddOn = false;
				for(int j = 0; j < softresource.getSoftResource().getSoftAddOnArray().length;j++){
					saod = SoftAddOnDocument.Factory.parse(softresource.getSoftResource().getSoftAddOnArray()[j].getDomNode());
					if(p.matcher(saod.xmlText()).matches() == true){
						containsAddOn = true;
						break;
					}
				}
				
				if(containsAddOn == true){
					continue;
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						containsAddOn = true;
						newspace.remove(ci);
						item.setId(item.getId() + ci.getId());
						item.setDistance(item.getDistance()+ci.getDistance());
						softresource = SoftResourceDocument.Factory.parse(merger.merge(softresource.xmlText(), ci.getTemplate()));
					}
				}
				
				if(containsAddOn == false){
					return newsolutionspace;
				}
			}
		}
		
		for(int i = 0; i < excludes.size(); i++){
			if(excludes.get(i).contains("ccpaas:SoftAddOn ") == true){
				Pattern p = Pattern.compile(excludes.get(i));
				SoftAddOnDocument saod;
				for(int j = 0; j < softresource.getSoftResource().getSoftAddOnArray().length;j++){
					saod = SoftAddOnDocument.Factory.parse(softresource.getSoftResource().getSoftAddOnArray()[j].getDomNode());
					if(p.matcher(saod.xmlText()).matches() == true){
						space = new Vector<CompositionItem>();
						return newsolutionspace;
					}
				}
				
				Iterator<CompositionItem> it = space.iterator();
				while(it.hasNext()){
					CompositionItem ci = it.next();
					if(p.matcher(ci.getTemplate()).matches() == true){
						newspace.remove(ci);
					}
				}
			}
		}
		space = newspace;
		item.setTemplate(softresource.xmlText());
		newsolutionspace.add(item);
		return newsolutionspace;
	}
	
	private boolean meetExternalRestrictions(List<String> includes, List<String> excludes, String item){

		for(int i = 0; i < includes.size(); i++){
			Pattern p = Pattern.compile(includes.get(i));
			if(p.matcher(item).matches() == false){
				return false;
			}
		}
		
		for(int i = 0; i < excludes.size(); i++){
			Pattern p = Pattern.compile(excludes.get(i));
			if(p.matcher(item).matches() == true){
				return false;
			}
		}
		
		return true;
	}
	
	/*	Absolute internal restrictions are restrictions that must be met even at partial results*/
	private boolean meetAbsoluteInternalRestrictions(String item){
		XmlObject template;
		try {
			template = XmlObject.Factory.parse(item);
			/**
			 * Here we code all the internal restrictions that our domain must meet.
			 * 
			 * 1- A VirtualMachine cannot have two PhysicalResource of the same kind.
			 * 2- A Service must have only 1 ServiceVersion
			 * 3- A VirtualMachine must have only 1 OperatingSystem
			 * 4- A VirtualContainer cannot have two VirtualRuntime of the same kind
			 * 5- A VirtualRuntime cannot have two softresource of the same kind
			 * 6- A Softresource cannot have two softaddon of the same kind
			 * 7- A template cannot have two VirtualMachines.
			 * 8- A template cannot have two Services.
			 * 9- A template cannot have two VirtualContainers.
			 */
			/**** Condition 1 ****/
			XmlObject[] matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:PhysicalResource");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode() &&
							match.getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue().equals(matches[j].getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue())){
						return false;
					}
				}
			}
			/**** Condition 2 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:ServiceVersion");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode()){
						return false;
					}
				}
			}
			/**** Condition 3 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:OperatingSystem");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode()){
						return false;
					}
				}
			}
			/**** Condition 4 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:VirtualRuntime");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode() &&
							match.getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue().equals(matches[j].getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue())){
						return false;
					}
				}
			}
			/**** Condition 5 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:SoftResource");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode() &&
							match.getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue().equals(matches[j].getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue())){
						return false;
					}
				}
			}
			/**** Condition 6 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:SoftAddOn");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode() &&
							match.getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue().equals(matches[j].getDomNode().getAttributes().getNamedItem("ccpaas:Name").getNodeValue())){
						return false;
					}
				}
			}
			/**** Condition 7 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:VirtualMachine");
			if(matches.length > 1){
				return false;
			}
			/**** Condition 8 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:Service");
			if(matches.length > 1){
				return false;
			}
			/**** Condition 9 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:VirtualContainer");
			if(matches.length > 1){
				return false;
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	/* Final internal restrictions are restrictions that can only be checked over
	 * terminal templates. */
	private boolean meetFinalInternalRestrictions(String item){
		XmlObject template;
		try {
			template = XmlObject.Factory.parse(item);
			/**
			 * Here we code all the internal restrictions that our domain must meet.
			 * 
			 * 1 - A Service must have 1 ServiceVersion
			 * 2 - A VirtualMachine must have 1 OperatingSystem
			 * 3 - A VirtualMachine must have at least one PhysicalResource.
			 * 4 - A VirtualContainer must have at least one VirtualRuntime.
			 * 5 - A VirtualContainer must have at least one associated VirtualMachine. // WRONG this condition has been deleted, see reason down
			 * 6 - A Service must have at least one associated VirtualContainer // WRONG this condition has been deleted, see reason down
			 */
			/**** Condition 1 ****/
			XmlObject[] matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:ServiceVersion");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode()){
						return false;
					}
				}
			}
			int serviceversionnumber = matches.length;
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:Service");
			int servicenumber = matches.length;
			if(servicenumber != serviceversionnumber){
				return false;
			}
			/**** Condition 2 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:OperatingSystem");
			for(int i = 0; i < matches.length; i++){
				XmlObject match = matches[i];
				for(int j = 0; j < matches.length; j++){
					if(i != j &&
							match.getDomNode().getParentNode() == matches[j].getDomNode().getParentNode()){
						return false;
					}
				}
			}
			int osnumber = matches.length;
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:VirtualMachine");
			int vmnumber = matches.length;
			if(osnumber != vmnumber){
				return false;
			}
			/**** Condition 3 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:VirtualMachine");
			for(int i = 0; i < matches.length; i++){
				NodeList nl = matches[i].getDomNode().getChildNodes();
				boolean hasphysres = false;
				for(int j = 0; j < nl.getLength(); j++){
					if(nl.item(j).getNodeName().equals("ccpaas:PhysicalResource")){
						hasphysres = true;
						break;
					}
				}
				if(hasphysres == false){
					return false;
				}
			}
			/**** Condition 4 ****/
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:VirtualContainer");
			for(int i = 0; i < matches.length; i++){
				NodeList nl = matches[i].getDomNode().getChildNodes();
				boolean hasvr = false;
				for(int j = 0; j < nl.getLength(); j++){
					if(nl.item(j).getNodeName().equals("ccpaas:VirtualRuntime")){
						hasvr = true;
						break;
					}
				}
				if(hasvr == false){
					return false;
				}
			}
			/**** Condition 5 ****/
			/**
			 * If we introduce this condition, then ordering matters in evaluation. I.e. if VC are evaluated before VM, they will be prunned,
			 * and never considered anymore.
			 * 
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:VirtualContainer");
			for(int i = 0; i < matches.length; i++){
				if(matches[i].getDomNode().getParentNode() == null || matches[i].getDomNode().getParentNode().getAttributes() == null){
					break;
				}
				XmlObject[] correspondingmatch = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
						"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
				"//ws:ServiceDescriptionTerm[@ws:ServiceName='"+matches[i].getDomNode().getParentNode().getAttributes().getNamedItem("ws:ServiceName").getNodeValue()+"']/ccpaas:VirtualMachine");
				if(correspondingmatch.length < 1){
					return false;
				}
			}
			**/
			/**** Condition 6 ****/
			/**
			 * The same reason as condition 5.
			 * 
			matches = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
					"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
			"//ccpaas:Service");
			for(int i = 0; i < matches.length; i++){
				if(matches[i].getDomNode().getParentNode() == null || matches[i].getDomNode().getParentNode().getAttributes() == null){
					break;
				}
				XmlObject[] correspondingmatch = template.selectPath("declare namespace ws = 'http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
						"declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
				"//ws:ServiceDescriptionTerm[@ws:ServiceName='"+matches[i].getDomNode().getParentNode().getAttributes().getNamedItem("ws:ServiceName").getNodeValue()+"']/ccpaas:VirtualContainer");
				if(correspondingmatch.length < 1){
					return false;
				}
			}
			*/
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}
	
	//encodes the id to make it shorter. The encoding is an arbitrary correspondence of symbols
	public String templateIdEncoding(String id){
		String encodedId = "";
		
		id=id.replaceAll("_VIRTUALMACHINE-","_A-");
		id=id.replaceAll("_PHYSICALRESOURCE-","_B-");
		id=id.replaceAll("_VIRTUALCONTAINER-","_C-");
		id=id.replaceAll("_OPERATINGSYSTEM-","_D-");
		id=id.replaceAll("_VIRTUALRUNTIME-","_E-");
		id=id.replaceAll("_SOFTRESOURCE-","_F-");
		id=id.replaceAll("_SOFTADDON-","_G-");
		id=id.replaceAll("_SERVICE-","_H-");
		id=id.replaceAll("_SERVICEVERSION-","_I-");
		id=id.replaceAll("_USER-","_J-");
		id=id.replaceAll("_ORGANIZATION-","_K-");
		id=id.replaceAll("_GUARANTEE-","_L-");

		String[] tokens = id.split("_");
		String currentToken = "";
		if(tokens.length > 0){
			for(int i = 0; i < tokens.length; i++){
				String[] pair = tokens[i].split("-");
				if(pair.length < 2){
					continue;
				}
				if(currentToken.equals(pair[0])){
					encodedId += "-"+pair[1];
				}else{
					currentToken = pair[0];
					encodedId += "_"+tokens[i];
				}
			}
		}
		
		return encodedId;
	}
	
	public String templateReconstruction(String id, String username) throws Exception{
		String[] components = id.split("_");
		Vector<CompositionItem> templates = new Vector<CompositionItem>();
		
		String metadatatemplate = "<ccpaas:Metadata xmlns:ccpaas=\"http://www.grycap.upv.es/cloudcompaas\"><ccpaas:Replicas><ccpaas:RangeValue><ccpaas:Exact>1</ccpaas:Exact></ccpaas:RangeValue></ccpaas:Replicas></ccpaas:Metadata>";
		String mergedtemplate = null;
		mergedtemplate = merger.merge(baseTemplate, metadatatemplate);

		RESTComm comm = new RESTComm("Catalog");
		comm.setUrl("/user/search?name="+username);
		String sla_id = comm.get().getFirst("//id_sla_template");
		comm.setUrl("/sla_template/"+sla_id+"/template");
	
		TemplateDocument template = null;
		template = TemplateDocument.Factory.parse(merger.merge(mergedtemplate,comm.get().getFirst("//template")));
		
		template.getTemplate().setTemplateId(id);
		template.getTemplate().getContext().setTemplateId(id);
		
		templates.add(new CompositionItem(template.xmlText(), "", 0, false));
		for(int i = 0; i < components.length; i++){
			String[] tokens = components[i].split("-");
			for(int j = tokens.length-1; j > 0; j--){
				if(tokens[0].equals("A")){
					comm.setUrl("/virtualmachine/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "A"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("B")){
					comm.setUrl("/physicalresource/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "B"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("C")){
					comm.setUrl("/virtualcontainer/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "C"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("D")){
					comm.setUrl("/operatingsystem/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "D"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("E")){
					comm.setUrl("/virtualruntime/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "E"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("F")){
					comm.setUrl("/softresource/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "F"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("G")){
					comm.setUrl("/softaddon/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "G"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("H")){
					comm.setUrl("/service/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "H"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("I")){
					comm.setUrl("/serviceversion/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "I"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				/*}else if(tokens[0].equals("J")){
					RESTComm comm = new RESTComm("Catalog");
					comm.setUrl("/user/"+tokens[j]+"/id_sla_template");
					String sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "J"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);*/
				}else if(tokens[0].equals("K")){
					comm.setUrl("/organization/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "K"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}else if(tokens[0].equals("L")){
					comm.setUrl("/guarantee/"+tokens[j]+"/id_sla_template");
					sla_id = comm.get().getFirst("//id_sla_template");
					comm.setUrl("/sla_template/"+sla_id+"/template");
					CompositionItem item = new CompositionItem(comm.get().getFirst("//template")
					                                  			 , "L"
					                                  			 , 0
					                                  			 , true);
					templates.add(item);
				}
			}
		}
		
		Map<String,String> parent = new HashMap<String,String>();
		parent.put("J","K"); 
		parent.put("K","");
		parent.put("I","H");
		parent.put("H","");
		parent.put("G","F");
		parent.put("F","E");
		parent.put("E","C");
		parent.put("D","A");
		parent.put("B","A");
		parent.put("A","");
		parent.put("C","");
		parent.put("L","");
		while(templates.size() > 1){
			CompositionItem ci = templates.lastElement();
			for(int i = templates.size()-1; i >= 0; i--){
				if(templates.get(i).getId().equals(parent.get(ci.getId()))){
					CompositionItem merge = new CompositionItem(merger.merge(templates.get(i).getTemplate(), ci.getTemplate()),templates.get(i).getId(),0,false);
					templates.removeElement(templates.get(i));
					templates.remove(ci);
					templates.insertElementAt(merge, i);
					break;
				}
			}
		}

		return templates.firstElement().getTemplate();
	}
}

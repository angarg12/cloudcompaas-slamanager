/*
 * SampleAgreement
 */
package org.cloudcompaas.sla.wsag.monitor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.cloudcompaas.sla.wsag.monitor.guaranteeterm.CloudcompaasGuaranteeTermJob;
import org.cloudcompaas.sla.wsag.monitor.serviceterm.CloudcompaasServiceTermJob;
import org.ogf.schemas.graap.wsAgreement.AgreementContextType;
import org.ogf.schemas.graap.wsAgreement.AgreementStateType;
import org.ogf.schemas.graap.wsAgreement.CompensationType;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermStateType;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermType;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.ServiceTermStateType;
import org.ogf.schemas.graap.wsAgreement.TermTreeType;
import org.ogf.graap.wsag.api.exceptions.ResourceUnavailableException;
import org.ogf.graap.wsag.api.exceptions.ResourceUnknownException;
import org.ogf.graap.wsag.server.api.IAgreementContext;
import org.ogf.graap.wsag.server.api.impl.AgreementContext;
import org.ogf.graap.wsag.server.monitoring.IMonitoringContext;
import org.ogf.graap.wsag.server.monitoring.IServiceTermMonitoringHandler;
import org.ogf.graap.wsag.server.monitoring.MonitoringContext;

/**
 * SampleAgreement
 * Modified by angarg12
 * 
 * @author Oliver Waeldrich
 * 
 */
public class CloudcompaasMonitor {
	private IAgreementContext executionContext;
	private List<IServiceTermMonitoringHandler> monitoringHandler = new Vector<IServiceTermMonitoringHandler>();
	private List<CloudcompaasGuaranteeTermJob> guaranteeTermsJobs = new Vector<CloudcompaasGuaranteeTermJob>();
	private List<CloudcompaasServiceTermJob> serviceTermsJobs = new Vector<CloudcompaasServiceTermJob>();
	private static final String DEFAULT_SCHEDULE = "PT5S";
	private CloudcompaasAgreement agreementInstance;
	Map<String, List<GuaranteeTermType>> guaranteeMapping = new HashMap<String, List<GuaranteeTermType>>();
	public static final int TOLERANCE = 1000*60*3;
	
	/**
	 * Creates a new instance of a monitorable agreement. The agreement object,
	 * for which this MonitorableAgreement is created, implements the methods to
	 * store the terms and the state of the agreement, and to terminate the
	 * agreement.
	 * 
	 * @param agreement
	 *            the agreement object, which should be monitored.
	 */
	public CloudcompaasMonitor(CloudcompaasAgreement agreement) {
		this.agreementInstance = agreement;
		executionContext = new AgreementContext(agreementInstance);
	}

	private IMonitoringContext initializeMonitoringContext() throws Exception {

		IMonitoringContext monitoringContext = new MonitoringContext();

		//
		// add the execution context properties to the monitoring context
		//
		monitoringContext.setProperties(executionContext
				.getExecutionProperties());
		
		//
		// add monitoring handler to monitoring context
		//
		monitoringContext
				.setMonitoringHandler(new IServiceTermMonitoringHandler[0]);

		for (int i = 0; i < monitoringHandler.size(); i++) {
			monitoringContext.addMonitoringHandler(monitoringHandler.get(i));
		}

		//
		// add service term states
		//
		for (int i = 0; i < agreementInstance.getTerms().getAll()
				.getServiceDescriptionTermArray().length; i++) {
			ServiceTermStateType sdtstate = ServiceTermStateType.Factory
					.newInstance();
			sdtstate.setTermName(agreementInstance.getTerms().getAll()
					.getServiceDescriptionTermArray()[i].getName());
			sdtstate.setState(ServiceTermStateDefinition.NOT_READY);
			monitoringContext.addServiceTemState(sdtstate);
		}
		return monitoringContext;
	}

	/**
	 * CURRENTLY WE DO NOT SUPPORT COUNT FOR THE ASSESSMENT INTERVAL
	 * @throws Exception
	 */
	private void initializeGuaranteeTerms() throws Exception {
		/**
		 *  First problem: since reward and penalties for the same guarantee term can have different
		 *  assessment time, we must divide each guarantee in their respective reward and penalty part.
		 */
		GuaranteeTermType[] guarantees = agreementInstance.getTerms().getAll().getGuaranteeTermArray();
		for(int i = 0; i < guarantees.length; i++){
			if(guarantees[i].getBusinessValueList() == null){
				continue;
			}
			GuaranteeTermType cloned;
			if(guarantees[i].getBusinessValueList().getPenaltyArray() != null){
				for(int j = 0; j < guarantees[i].getBusinessValueList().getPenaltyArray().length; j++){
					cloned = GuaranteeTermType.Factory.parse(guarantees[i].xmlText());
					cloned.getBusinessValueList().setPenaltyArray(new CompensationType[]{guarantees[i].getBusinessValueList().getPenaltyArray(j)});
					cloned.getBusinessValueList().setRewardArray(new CompensationType[0]);
					String assessmentinterval = cloned.getBusinessValueList().getPenaltyArray(j).getAssessmentInterval().getTimeInterval().toString();
					List<GuaranteeTermType> listtoadd;
					listtoadd = guaranteeMapping.get(assessmentinterval);
					if(listtoadd == null){
						listtoadd = new Vector<GuaranteeTermType>();
					}
					listtoadd.add(cloned);
					guaranteeMapping.put(assessmentinterval, listtoadd);
				}
			}
			if(guarantees[i].getBusinessValueList().getRewardArray() != null){
				for(int j = 0; j < guarantees[i].getBusinessValueList().getRewardArray().length; j++){
					cloned = GuaranteeTermType.Factory.parse(guarantees[i].xmlText());
					cloned.getBusinessValueList().setPenaltyArray(new CompensationType[0]);
					cloned.getBusinessValueList().setRewardArray(new CompensationType[]{guarantees[i].getBusinessValueList().getRewardArray(j)});
					String assessmentinterval = cloned.getBusinessValueList().getRewardArray(j).getAssessmentInterval().getTimeInterval().toString();
					List<GuaranteeTermType> listtoadd;
					listtoadd = guaranteeMapping.get(assessmentinterval);
					if(listtoadd == null){
						listtoadd = new Vector<GuaranteeTermType>();
					}
					listtoadd.add(cloned);
					guaranteeMapping.put(assessmentinterval, listtoadd);
				}
			}
		}
	}
	
	private synchronized void scheduleTermMonitoringJobs(
			IMonitoringContext monitoringContext) throws Exception {
		CloudcompaasServiceTermJob termsJob = new CloudcompaasServiceTermJob(monitoringContext, DEFAULT_SCHEDULE, agreementInstance);
		serviceTermsJobs.add(termsJob);
		termsJob.start();
	}
	
	private synchronized void scheduleGuranteeMonitoringJobs(
			IMonitoringContext monitoringContext) throws Exception {
		Set<String> keys = guaranteeMapping.keySet();
		Iterator<String> i = keys.iterator();
		while(i.hasNext()){
			String scheduleperiod = i.next();
	
			CloudcompaasGuaranteeTermJob guaranteeJob = new CloudcompaasGuaranteeTermJob(monitoringContext, guaranteeMapping.get(scheduleperiod), scheduleperiod, agreementInstance);
			guaranteeTermsJobs.add(guaranteeJob);
			guaranteeJob.start();
		}
	}

	/**
	 * @return the executionContext
	 */
	public IAgreementContext getExecutionContext() {
		return executionContext;
	}

	/**
	 * @param executionContext
	 *            the executionContext to set
	 */
	public void setExecutionContext(IAgreementContext executionContext) {
		this.executionContext = executionContext;
	}

	/**
	 * 
	 * @param handler
	 *            monitoring handler
	 */
	public void addMonitoringHandler(IServiceTermMonitoringHandler handler) {
		monitoringHandler.add(handler);
	}

	/**
	 * Returns the list of registered monitoring handler.
	 * 
	 * @return the monitoringHandler
	 */
	public IServiceTermMonitoringHandler[] getMonitoringHandler() {
		return monitoringHandler
				.toArray(new IServiceTermMonitoringHandler[monitoringHandler
						.size()]);
	}

	/**
	 * Starts the agreement monitoring process.
	 * 
	 * @throws Exception
	 */
	public void startMonitoring() throws Exception {
		//
		// initialize the monitoring context
		//
		IMonitoringContext monitoringContext = initializeMonitoringContext();
		initializeGuaranteeTerms();
		//
		// schedule the monitoring jobs
		//
		try {
			scheduleTermMonitoringJobs(monitoringContext);
			scheduleGuranteeMonitoringJobs(monitoringContext);
		} catch (Exception e) {
			throw new Exception("Error scheduling monitoring jobs. Reason: "
					+ e.getMessage());
		}
	}

	public void stopMonitoring() throws Exception {
		try {
			Iterator<CloudcompaasServiceTermJob> it1 = serviceTermsJobs.iterator();
			while(it1.hasNext()){
				CloudcompaasServiceTermJob termJob = it1.next();
				termJob.finalize();
			}
			
			Iterator<CloudcompaasGuaranteeTermJob> it2 = guaranteeTermsJobs.iterator();
			while(it2.hasNext()){
				CloudcompaasGuaranteeTermJob guaranteeJob = it2.next();
				guaranteeJob.finalize();
			}
		} catch (Exception e) {
			throw new Exception(
					"Error stoping the agreement monitoring. Reason: "
							+ e.getMessage());
		}
	}
	
	public String getAgreementId() throws ResourceUnknownException,
			ResourceUnavailableException {
		return agreementInstance.getAgreementId();
	}

	public AgreementContextType getContext() throws ResourceUnknownException,
			ResourceUnavailableException {
		return agreementInstance.getContext();
	}

	public GuaranteeTermStateType[] getGuaranteeTermStates()
			throws ResourceUnknownException, ResourceUnavailableException {
		return agreementInstance.getGuaranteeTermStates();
	}

	public String getName() throws ResourceUnknownException,
			ResourceUnavailableException {
		return agreementInstance.getName();
	}

	public ServiceTermStateType[] getServiceTermStates()
			throws ResourceUnknownException, ResourceUnavailableException {
		return agreementInstance.getServiceTermStates();
	}

	public AgreementStateType getState() throws ResourceUnknownException,
			ResourceUnavailableException {
		return agreementInstance.getState();
	}

	public TermTreeType getTerms() throws ResourceUnknownException,
			ResourceUnavailableException {
		return agreementInstance.getTerms();
	}
}

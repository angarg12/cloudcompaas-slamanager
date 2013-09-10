package org.cloudcompaas.sla.wsag.monitor.guaranteeterm;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.cloudcompaas.common.util.CCPaaSJexlContext;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.cloudcompaas.sla.wsag.monitor.accounting.CloudcompaasAccountingSystem;
import org.cloudcompaas.sla.wsag.monitor.selfmanagement.CloudcompaasSelfManagement;
import org.cloudcompaas.sla.wsag.monitor.selfmanagement.ISelfManagement;
import org.ogf.graap.wsag.server.accounting.AccountingContext;
import org.ogf.graap.wsag.server.accounting.IAccountingContext;
import org.ogf.graap.wsag.server.accounting.IAccountingSystem;
import org.ogf.graap.wsag.server.monitoring.IGuaranteeEvaluator;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermStateDefinition;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermStateType;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermType;
import org.ogf.schemas.graap.wsAgreement.ServiceLevelObjectiveType;

/**
 * @author angarg12
 *
 */
public class CloudcompaasGuaranteeEvaluator  implements IGuaranteeEvaluator {
    IAccountingSystem accountingSystem;
    ISelfManagement selfManagement;
    CloudcompaasAgreement agreementInstance;
    
    public CloudcompaasGuaranteeEvaluator(CloudcompaasAgreement agreementInstance_){
    	agreementInstance = agreementInstance_;
    	accountingSystem = new CloudcompaasAccountingSystem();
    	selfManagement = new CloudcompaasSelfManagement(agreementInstance);
    }
    /**
     * @return the accountingSystem
     */
    public IAccountingSystem getAccountingSystem() {
        return accountingSystem;
    }

    /**
     * @param accountingSystem the accountingSystem to set
     */
    public void setAccountingSystem(IAccountingSystem accountingSystem) {
        this.accountingSystem = accountingSystem;
    }

    
    public GuaranteeTermStateType evaluate(GuaranteeTermType guarantee, Map<String, Object> variables) throws Exception {
        //
        // initialize guarantee term state 
        //
        GuaranteeTermStateType state = GuaranteeTermStateType.Factory.newInstance();

        state.setTermName(guarantee.getName());
        state.setState(GuaranteeTermStateDefinition.NOT_DETERMINED);
        
        // Create a JEXL context
        JexlEngine jexlengine = new JexlEngine();
        CCPaaSJexlContext jc = new CCPaaSJexlContext();
        variables.put("context", jc);
        jc.setVars(variables);
        //
        // create Guarantee States 
        //
        if (guarantee.isSetQualifyingCondition()) {
            //
            // First, check whether the guarantee term will be evaluated or not.
            // Therefore we need to check the qualifying condition, if present
            //
            // In the default implementation we assume that the qualifying condition
            // is set as a string value.
            //
            XmlObject qcObject = guarantee.getQualifyingCondition();
            String condition = XmlString.Factory.parse(qcObject.getDomNode()).getStringValue();

            Expression qcExpr = jexlengine.createExpression(condition);
            Object qcExprResult = qcExpr.evaluate(jc);
            if (qcExprResult instanceof Boolean) {
            	//System.out.println(qcExpr.getExpression()+" "+((Boolean)qcExprResult).booleanValue());
                if (((Boolean)qcExprResult).booleanValue()) {
                    //log.info("Qualifying condition of guarantee '"+ guarantee.getName() +"' is fullfilled.");
                    //log.info("Guarantee term will be evaluated.");
                } else {
                    //log.info(MessageFormat.format("Qualifying condition of guarantee ''{0}'' is not fullfilled.", new Object[] { guarantee.getName() }));
                    //log.info("Guarantee term will not be evaluated.");
                	//System.out.println("Qualifying condition of guarantee is not fullfilled.");
                	selfManagement.guaranteeNotDetermined(guarantee);
                    return state;
                }
            } else {
                //log.error(MessageFormat.format("Qualifying condition of guarantee ''{0}'' does not evaluate to boolean value.", new Object[] { guarantee.getName() }));
                //log.error("Guarantee term will not be evaluated.");
            	//System.out.println("Qualifying condition of guarantee does not evaluate to boolean.");
                return state;
            }
        }
        
        ServiceLevelObjectiveType slo = guarantee.getServiceLevelObjective();
        
        XmlObject sloCSL = slo.getKPITarget().getCustomServiceLevel();
        String exprLit = XmlString.Factory.parse(sloCSL.getDomNode()).getStringValue();
        Expression expr = jexlengine.createExpression(exprLit);
        //System.out.println(exprLit);
        //
        // Now evaluate the expression, getting the result
        //

        Object exprResult = expr.evaluate(jc);
        if (exprResult instanceof Boolean) {
            IAccountingContext context = new AccountingContext();
            context.setGuarantee(guarantee);
            context.setEvaluationResult(((Boolean) exprResult).booleanValue());
            Map<String,Object> properties = new HashMap<String,Object>();
            properties.put("agreement", agreementInstance);
            properties.put("obligated", guarantee.getObligated());
            properties.put("variables", variables);
            context.setProperties(properties);
            
            if (context.getEvaluationResult()) {
                state.setState(GuaranteeTermStateDefinition.FULFILLED);
                
                selfManagement.guaranteeFulfilled(context);
                //log.info("Guarantee is evaluated to successful. Issue reward with accounting system.");
                for(int i = 0; i < guarantee.getBusinessValueList().sizeOfRewardArray(); i++){
                	accountingSystem.issueCompensation(guarantee.getBusinessValueList().getRewardArray(i), context);
                }
            } else {
                state.setState(GuaranteeTermStateDefinition.VIOLATED);
                
                //log.info("Guarantee is evaluated to violated. Issue penalty with accounting system.");
                selfManagement.guaranteeViolated(context);
                for(int i = 0; i < guarantee.getBusinessValueList().sizeOfPenaltyArray(); i++){
                	accountingSystem.issueCompensation(guarantee.getBusinessValueList().getPenaltyArray(i), context);
                }
            }
        }
        
        return state;
    }
}
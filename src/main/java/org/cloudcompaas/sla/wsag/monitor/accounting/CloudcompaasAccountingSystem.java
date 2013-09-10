package org.cloudcompaas.sla.wsag.monitor.accounting;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.jexl2.Expression;
import org.apache.commons.jexl2.JexlEngine;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.cloudcompaas.common.communication.RESTComm;
import org.cloudcompaas.common.util.CCPaaSJexlContext;
import org.cloudcompaas.sla.wsag.agreement.CloudcompaasAgreement;
import org.ogf.graap.wsag.server.accounting.IAccountingContext;
import org.ogf.graap.wsag.server.accounting.IAccountingSystem;
import org.ogf.schemas.graap.wsAgreement.CompensationType;
import org.ogf.schemas.graap.wsAgreement.ServiceRoleType;

/**
 * @author angarg12
 *
 */
public class CloudcompaasAccountingSystem  implements IAccountingSystem {
    /* (non-Javadoc)
     * @see org.ogf.graap.wsag.server.api.IAccountingSystem#issueCompensation(org.ogf.schemas.graap.wsAgreement.CompensationType)
     */
    public void issueCompensation(CompensationType compensation, IAccountingContext context) {
        
        String compensationType = (context.getEvaluationResult()) ? "reward" : "penalty";
        CloudcompaasAgreement agreementInstance = (CloudcompaasAgreement) context.getProperties().get("agreement");
        String xpath = "declare namespace ccpaas='http://www.grycap.upv.es/cloudcompaas';" +
        	"declare namespace ws='http://schemas.ggf.org/graap/2007/03/ws-agreement';" +
        	"//ws:Terms/ws:All/ws:ServiceDescriptionTerm/ccpaas:User/@ccpaas:Name";
        ServiceRoleType.Enum obligated = (ServiceRoleType.Enum) context.getProperties().get("obligated");

		XmlObject[] result = agreementInstance.getXMLObject().selectPath(xpath);
		
		if (result.length == 0) {
			//No username found, oops!
			return;
		}
		
		if (result.length > 1) {
			//WTF, more than one user? that sure is messed up.
		}

        try {
        	String username = XmlString.Factory.parse(result[0].getDomNode().getFirstChild()).getStringValue();
        	
        	@SuppressWarnings("unchecked")
        	Map<String, Object> variables = (Map<String, Object>) context.getProperties().get("variables");
            // Create a JEXL context 
        	CCPaaSJexlContext jc = new CCPaaSJexlContext();
            jc.setVars(variables);

            JexlEngine jexlengine = new JexlEngine();
            Expression qcExpr = jexlengine.createExpression( XmlString.Factory.parse(compensation.getValueExpression().getDomNode()).getStringValue() );

            String value = qcExpr.evaluate(jc).toString();
            String unit  = compensation.getValueUnit();
            
            Properties properties = new Properties();
    		
    		if(obligated == null || obligated.equals(ServiceRoleType.SERVICE_PROVIDER)){
	    		if(compensationType.equals("reward")){
	                properties.put("current_credits", "?!current_credits-"+value);
	    		}else{
	                properties.put("current_credits", "?!current_credits+"+value);
	    		}
    		}else if(obligated.equals(ServiceRoleType.SERVICE_CONSUMER)){
	    		if(compensationType.equals("reward")){
	                properties.put("current_credits", "?!current_credits+"+value);
	    		}else{
	                properties.put("current_credits", "?!current_credits-"+value);
	    		}
    		}
    		
    		String payload;
    		ByteArrayOutputStream baos = new ByteArrayOutputStream();
    		properties.storeToXML(baos, null);
    		payload = baos.toString();

        	RESTComm comm = new RESTComm("Catalog");
        	comm.setUrl("user/search?name="+username);
        	comm.setContentType("text/xml");
    		comm.put(payload);
            //log.info(MessageFormat.format("Issue {0}: {1} {2}", new Object[] { compensationType, value, unit }));
        } catch (Exception e) {
        	e.printStackTrace();
            //log.info(MessageFormat.format("Issue {0}: {1} {2}", new Object[] { compensationType, compensation.getValueExpression(), compensation.getValueUnit() }));
        }
    }
}

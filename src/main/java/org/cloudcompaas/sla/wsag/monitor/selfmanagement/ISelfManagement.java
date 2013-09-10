package org.cloudcompaas.sla.wsag.monitor.selfmanagement;

import org.ogf.graap.wsag.server.accounting.IAccountingContext;
import org.ogf.schemas.graap.wsAgreement.GuaranteeTermType;

/**
 * @author angarg12
 *
 */
public interface ISelfManagement {
	
	public void guaranteeNotDetermined(GuaranteeTermType guarantee);
	public void guaranteeFulfilled(IAccountingContext context);
	public void guaranteeViolated(IAccountingContext context);
}

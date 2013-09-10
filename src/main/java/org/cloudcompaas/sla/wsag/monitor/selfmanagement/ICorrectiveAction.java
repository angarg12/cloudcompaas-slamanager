package org.cloudcompaas.sla.wsag.monitor.selfmanagement;

import org.ogf.graap.wsag.server.accounting.IAccountingContext;

/**
 * @author angarg12
 *
 */
public interface ICorrectiveAction {
	public String getActionName();
	public void apply(IAccountingContext context);
	public int getThreshold();
}

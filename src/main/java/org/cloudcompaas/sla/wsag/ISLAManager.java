package org.cloudcompaas.sla.wsag;

import java.util.List;

import javax.ws.rs.core.Response;

/**
 * @author angarg12
 *
 */
public interface ISLAManager {
	public Response getTemplates(String auth, List<String> include, List<String> exclude);
	public Response createAgreement(String auth, String agreementOffer);
	public Response retrieveAgreement(String auth, String agreementId);
	public Response finalizeAgreement(String auth, String agreementId);
}

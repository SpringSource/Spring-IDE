/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry;

import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.cloudfoundry.client.lib.CloudCredentials;
import org.cloudfoundry.client.lib.CloudFoundryClient;
import org.cloudfoundry.client.lib.CloudFoundryOperations;
import org.cloudfoundry.client.lib.domain.CloudSpace;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.operation.IRunnableContext;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops.Operation;

public class CloudFoundryClientFactory {

	/*
	 * System property. Set to "true" if connection pool is to be used. "false"
	 * otherwise or omit as a system property
	 */
	public static final String BOOT_DASH_CONNECTION_POOL = "sts.boot.dash.connection.pool";

	/**
	 * Get the client for an existing {@link CloudFoundryRunTarget}. Note that
	 * this may require the password to be set for that runtarget.
	 *
	 * @param runTarget
	 * @return client if connection was successful.
	 * @throws Exception
	 *             if there was an error connecting, including if password is
	 *             not set or invalid.
	 */
	public CloudFoundryOperations getClient(CloudFoundryRunTarget runTarget) throws Exception {

		CloudFoundryTargetProperties targetProperties = (CloudFoundryTargetProperties) runTarget.getTargetProperties();

		return getClient(targetProperties);
	}

	public CloudFoundryOperations getClient(CloudFoundryTargetProperties targetProperties) throws Exception {
		checkPassword(targetProperties.getPassword(), targetProperties.getUsername());

		Properties properties = System.getProperties();
		// By default disable connection pool (i.e. flag is set to true) unless
		// a property exists that sets
		// USING connection pool to "true" (so, i.e., disable connection pool is
		// false)
		boolean disableConnectionPool = properties == null || !properties.containsKey(BOOT_DASH_CONNECTION_POOL)
				|| !"true".equals(properties.getProperty(BOOT_DASH_CONNECTION_POOL));

		return targetProperties.getSpaceName() != null
				? new CloudFoundryClient(
						new CloudCredentials(targetProperties.getUsername(), targetProperties.getPassword()),
						new URL(targetProperties.getUrl()), targetProperties.getOrganizationName(),
						targetProperties.getSpaceName(), targetProperties.isSelfsigned(), disableConnectionPool)
				: new CloudFoundryClient(
						new CloudCredentials(targetProperties.getUsername(), targetProperties.getPassword()),
						new URL(targetProperties.getUrl()), targetProperties.isSelfsigned(), disableConnectionPool);

	}

	public OrgsAndSpaces getCloudSpaces(final CloudFoundryTargetProperties targetProperties, IRunnableContext context)
			throws Exception {

		OrgsAndSpaces spaces = null;

		Operation<List<CloudSpace>> op = new Operation<List<CloudSpace>>(
				"Connecting to the Cloud Foundry target. Please wait while the list of spaces is resolved...") {
			protected List<CloudSpace> runOp(IProgressMonitor monitor) throws Exception, OperationCanceledException {
				return CloudFoundryClientFactory.this.getClient(targetProperties).getSpaces();
			}
		};

		List<CloudSpace> actualSpaces = op.run(context, true);
		if (actualSpaces != null && !actualSpaces.isEmpty()) {
			spaces = new OrgsAndSpaces(actualSpaces);
		}

		return spaces;
	}

	public static void checkPassword(String password, String id) throws MissingPasswordException {
		if (password == null) {
			throw new MissingPasswordException("No password stored or set for: " + id
					+ ". Please ensure that the password is set in the run target and it is up-to-date.");
		}
	}

}

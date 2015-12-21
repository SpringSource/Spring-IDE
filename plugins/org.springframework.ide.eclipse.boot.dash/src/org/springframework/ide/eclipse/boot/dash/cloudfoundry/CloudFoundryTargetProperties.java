/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry;

import java.util.Map;

import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.TargetProperties;

public class CloudFoundryTargetProperties extends TargetProperties {


	public final static String ORG_PROP = "organization";
	public final static String SPACE_PROP = "space";
	public final static String SELF_SIGNED_PROP = "selfsigned";

	public final static String ORG_GUID = "organization_guid";
	public final static String SPACE_GUID = "space_guid";

	public final static String DISCONNECTED = "disconnected";

	public CloudFoundryTargetProperties(RunTargetType runTargetType) {
		super(runTargetType);
	}

	public CloudFoundryTargetProperties(TargetProperties targetProperties, RunTargetType runTargetType) {
		super(targetProperties.getAllProperties(), runTargetType);
		if (get(RUN_TARGET_ID) == null) {
			put(RUN_TARGET_ID, getId(this));
		}
	}

	public String getSpaceName() {
		return map.get(SPACE_PROP);
	}

	public String getOrganizationName() {
		return map.get(ORG_PROP);
	}

	public String getSpaceGuid() {
		return map.get(SPACE_GUID);
	}

	public String getOrganizationGuid() {
		return map.get(ORG_GUID);
	}

	public boolean isSelfsigned() {
		return map.get(SELF_SIGNED_PROP) != null && Boolean.parseBoolean(map.get(SELF_SIGNED_PROP));
	}

	@Override
	public Map<String, String> getPropertiesToPersist() {
		Map<String, String> map = super.getPropertiesToPersist();
		// Exclude password as password are persisted separately
		map.remove(PASSWORD_PROP);
		return map;
	}

	public static String getId(CloudFoundryTargetProperties cloudProps) {
		return getId(cloudProps.getUsername(), cloudProps.getUrl(), cloudProps.getOrganizationName(),
				cloudProps.getSpaceName());
	}

	public static String getName(CloudFoundryTargetProperties cloudProps) {
		return cloudProps.getOrganizationName() + " : " + cloudProps.getSpaceName() + " - [" + cloudProps.getUrl()
				+ "]";
	}

	public static String getId(String userName, String url, String orgName, String spaceName) {
		return userName + " : " + url + " : " + orgName + " : " + spaceName;
	}
}

/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.environment.ui.live.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.ide.eclipse.beans.ui.live.model.JsonParser;

import com.google.common.collect.ImmutableList;

public class LiveEnvJsonParser2x implements JsonParser<LiveEnvModel> {

	public LiveEnvJsonParser2x() {
	}

	@Override
	public LiveEnvModel parse(String jsonInput) throws Exception {

		JSONObject envObj = toJson(jsonInput);

		ActiveProfiles profiles = parseActiveProfiles(envObj);
		PropertySources propertySources = parseProperties(envObj);

		LiveEnvModel model = new LiveEnvModel(profiles, propertySources);

		return model;
	}

	private ActiveProfiles parseActiveProfiles(JSONObject envObj) {
		Object _profiles = envObj.opt("activeProfiles");

		if (_profiles instanceof JSONArray) {
			JSONArray profilesObj = (JSONArray) _profiles;

			ImmutableList.Builder<Profile> list = ImmutableList.builder();

			for (int i = 0; i < profilesObj.length(); i++) {
				Object object = profilesObj.opt(i);
				if (object instanceof String) {
					list.add(new Profile((String) object));
				}
			}
			ImmutableList<Profile> profiles = list.build();
			return !profiles.isEmpty() ? new ActiveProfiles(profiles) : null;

		}
		return null;
	}

	private PropertySources parseProperties(JSONObject envObj) throws Exception {
		ImmutableList.Builder<PropertySource> allSources = ImmutableList.builder();

		Object sourcesObj = envObj.opt("propertySources");

		if (sourcesObj instanceof JSONArray) {
			JSONArray props = (JSONArray) sourcesObj;
			for (int i = 0; i < props.length(); i++) {
				Object object = props.opt(i);
				if (object instanceof JSONObject) {
					JSONObject propObj = (JSONObject) object;
					String name = propObj.optString("name");
					if (name != null) {
						PropertySource propertySource = new PropertySource(name);
						Object opt2 = propObj.opt("properties");
						List<Property> properties = parseProperties(opt2);
						propertySource.add(properties);

						allSources.add(propertySource);
					}
				}
			}
		}
		ImmutableList<PropertySource> sources = allSources.build();
		return !sources.isEmpty() ? new PropertySources(sources) : null;
	}

	private List<Property> parseProperties(Object opt2) {
		List<Property> properties = new ArrayList<Property>();

		if (opt2 instanceof JSONObject) {
			JSONObject jsonObj = (JSONObject) opt2;
			Iterator<?> keys = jsonObj.keys();
			if (keys != null) {
				while(keys.hasNext()) {
					Object key = keys.next();
					if (key instanceof String) {
						String propKey = (String) key;
						Object valueObj = jsonObj.opt(propKey);
						if (valueObj != null) {
							properties.add(new Property(propKey, getValue(valueObj)));
						}
					}
				}
			}
		}
		return properties;
	}

	private String getValue(Object value) {
		if (value instanceof JSONObject) {
			JSONObject valObj = (JSONObject) value;
			return valObj.optString("value");
		}
		return null;
	}

	protected JSONObject toJson(String json) throws JSONException {
		return new JSONObject(json);
	}
}

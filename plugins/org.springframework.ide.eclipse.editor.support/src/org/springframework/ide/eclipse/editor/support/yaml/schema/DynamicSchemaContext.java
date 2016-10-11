/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.yaml.schema;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * Exposes some 'dynamic' contextual information to schema implementations.
 * <p>
 * This is necessary to enable the impementation of schema features where one
 * part of a yaml structure depends details of another part. A typical example
 * could be 'type' property, so that different values assigned to that property
 * change the structure that is expected for the rest of the node.
 *
 * @author Kris De Volder
 */
public interface DynamicSchemaContext {

	DynamicSchemaContext NULL = new DynamicSchemaContext() {
		@Override
		public Set<String> getDefinedProperties() {
			return ImmutableSet.of();
		}
	};

	/**
	 * Returns the set of property names that are already defined in the current context.
	 * <p>
	 * In Content assist scenarios this is only a 'best effort' result. An empty set may
	 * be returned because either no properties are yet defined, or because the sloppy parser
	 * can not quite make out the yaml structure because yaml text is incomplete or too complex
	 * for its analysis.
	 * <p>
	 * In a validation scenario however, the returned information should precisely reflect what
	 * properties are defined in the surrounding object.
	 */
	Set<String> getDefinedProperties();


}

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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.util.CollectionUtil;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SChildBearingNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SKeyNode;
import org.springframework.ide.eclipse.editor.support.yaml.structure.YamlStructureParser.SNode;

/**
 * Adapts an SNode so it can be used by a YamlSchema as a {@link DynamicSchemaContext}
 *
 * @author Kris De Volder
 */
public class SNodeDynamicSchemaContext extends CachingSchemaContext {

	private SNode contextNode;

	public SNodeDynamicSchemaContext(SNode contextNode) {
		this.contextNode = contextNode;
	}

	@Override
	protected Set<String> computeDefinedProperties() {
		try {
			if (contextNode instanceof SChildBearingNode) {
				List<SNode> children = ((SChildBearingNode)contextNode).getChildren();
				if (CollectionUtil.hasElements(children)) {
					Set<String> keys = new HashSet<>(children.size());
					for (SNode c : children) {
						if (c instanceof SKeyNode) {
							keys.add(((SKeyNode) c).getKey());
						}
					}
					return keys;
				}
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
		return Collections.emptySet();
	}


}

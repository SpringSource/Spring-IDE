/*
 * Copyright 2002-2006 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 

package org.springframework.ide.eclipse.beans.ui.graph.editor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPersistableElement;
import org.springframework.ide.eclipse.beans.core.BeansCorePlugin;
import org.springframework.ide.eclipse.beans.core.internal.model.BeanReference;
import org.springframework.ide.eclipse.beans.core.internal.model.BeansModelUtils;
import org.springframework.ide.eclipse.beans.core.internal.model.BeanReference.BeanType;
import org.springframework.ide.eclipse.beans.core.model.IBean;
import org.springframework.ide.eclipse.beans.core.model.IBeanConstructorArgument;
import org.springframework.ide.eclipse.beans.core.model.IBeanProperty;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfig;
import org.springframework.ide.eclipse.beans.core.model.IBeansConfigSet;
import org.springframework.ide.eclipse.beans.ui.graph.BeansGraphImages;
import org.springframework.ide.eclipse.beans.ui.graph.BeansGraphPlugin;
import org.springframework.ide.eclipse.beans.ui.graph.model.Bean;
import org.springframework.ide.eclipse.core.io.ZipEntryStorage;
import org.springframework.ide.eclipse.core.model.IModelElement;
import org.springframework.util.ObjectUtils;

/**
 * This editor input is used to specify the list of <code>IBean</code>s which
 * should be displayed in the beans graph editor.
 * Therefore a source model model element (<code>IBean</code>,
 * <code>IBeanConfig</code> or <code>IBeanConfigSet</code>) has to be
 * specified.
 * For a given bean it's parent bean (for child beans only), constructor
 * argument values and property values are checked.
 * <code>IBean</code> look-up is done from the specified context
 * (<code>IBeanConfig</code> or <code>IBeanConfigSet</code>).
 * This list of beans is accessible via <code<>getBeans()</code>.
 * This context used for bean look-up is accessible via
 * <code<>getContext()</code>.
 *
 * @author Torsten Juergeleit
 */
public class GraphEditorInput implements IEditorInput, IPersistableElement {

	private IModelElement element;
	private IModelElement context;
	private String name;
	private String toolTip;
	private Map<String, Bean> beans;
	private boolean hasError;

	/**
	 * Creates a list with all beans which are referenced from the model
	 * element defined by given ID.
	 * @param elementID  the model element's ID
	 * @throws IllegalArgumentException  if unsupported model element is
	 * 				 					specified 
	 */
	public GraphEditorInput(String elementID) {
		this(BeansCorePlugin.getModel().getElement(elementID),
				getContext(elementID));
	}

	/**
	 * Creates a list with all beans which are referenced from the model
	 * element defined by given ID.
	 * @param elementID  the model element's ID
	 * @param contextID  the context's ID
	 * @throws IllegalArgumentException  if unsupported model element or
	 * 									context is specified 
	 */
	public GraphEditorInput(String elementID, String contextID) {
		this(BeansCorePlugin.getModel().getElement(elementID),
			 BeansCorePlugin.getModel().getElement(contextID));
	}

	/**
	 * Creates a list with all beans which are referenced from given model
	 * element.
	 * @param element  the model element to build a list of all referenced
	 * 					 beans from
	 * @throws IllegalArgumentException  if unsupported model element is
	 * 				 					specified 
	 */
	public GraphEditorInput(IModelElement element) {
		this(element, getContext(element));
	}
    
	/**
	 * Creates a list with all beans which are referenced from given model
	 * element. Bean look-up is done from the specified context.
	 * 
	 * @param element
	 *            the model element to build a list of all referenced beans from
	 * @param context
	 *            the context the referenced beans are looked-up
	 * @throws IllegalArgumentException
	 *             if unsupported model element or context is specified
	 */
	public GraphEditorInput(IModelElement element, IModelElement context) {
		this.element = element;
		this.context = context;

		// Prepare name and tooltip for given element and context
		if (element instanceof IBeansConfig) {
			String toolTipPrefix = BeansGraphPlugin
					.getResourceString("ShowGraphAction.name.config");
			IBeansConfig config = (IBeansConfig) element;
			IResource resource = config.getElementResource();
			if (resource != null) {
				if (config.isElementArchived()) {
					ZipEntryStorage storage = new ZipEntryStorage(config);
					name = storage.getName();
					toolTip = toolTipPrefix
							+ storage.getZipResource().getProjectRelativePath()
									.toString() + " - "
							+ storage.getFullPath().toString();
				} else {
					name = resource.getName();
					toolTip = toolTipPrefix + resource.getFullPath().toString();
				}
			} else {
				name = BeansGraphPlugin
						.getResourceString("ShowGraphAction.name.undefined");
				toolTip = BeansGraphPlugin
						.getResourceString("ShowGraphAction.name.config")
						+ name;
			}
		} else if (element instanceof IBeansConfigSet) {
			IModelElement parent = ((IBeansConfigSet) element)
					.getElementParent();
			name = element.getElementName();
			toolTip = BeansGraphPlugin
					.getResourceString("ShowGraphAction.name.configSet")
					+ parent.getElementName() + '/' + element.getElementName();
		} else if (element instanceof IBean) {
			name = element.getElementName();
			StringBuffer buffer = new StringBuffer();
			buffer.append(BeansGraphPlugin
					.getResourceString("ShowGraphAction.name.bean"));
			if (context instanceof IBeansConfig) {
				buffer.append(BeansGraphPlugin
						.getResourceString("ShowGraphAction.name.config"));
				buffer.append(context.getElementName());
				buffer.append(": ");
			} else if (context instanceof IBeansConfigSet) {
				buffer.append(BeansGraphPlugin
						.getResourceString("ShowGraphAction.name.configSet"));
				buffer.append(context.getElementParent().getElementName());
				buffer.append('/');
				buffer.append(context.getElementName());
				buffer.append(": ");
			}
			buffer.append(element.getElementName());
			toolTip = buffer.toString();
		} else {
			throw new IllegalArgumentException("Unsupported model element "
					+ element);
		}
		createBeansMap();
	}

	/**
	 * Creates a list with all beans belonging to the specified config / config
	 * set or being referenced from the specified bean.
	 */
	protected void createBeansMap() {
		Set<IBean> list = new LinkedHashSet<IBean>();
		if (element instanceof IBeansConfig) {
			list.addAll(((IBeansConfig) element).getBeans());
		} else if (element instanceof IBeansConfigSet) {
			list.addAll(((IBeansConfigSet) element).getBeans());
		} else if (element instanceof IBean) {
			list.add((IBean) element);
			for (BeanReference beanRef : BeansModelUtils
					.getBeanReferences(element, context, true)) {
				if (beanRef.getType() != BeanType.INNER) {
					list.add(beanRef.getTarget());
				}
			}
		}

		// Marshall all beans into a graph bean node
		beans = new LinkedHashMap<String, Bean>();
		for (IBean bean : list) {
			beans.put(bean.getElementName(), new Bean(bean));
		}
	}

	public String getName() {
		return name;
	}

	public IModelElement getElement() {
		return element;
	}
	
	public IModelElement getContext() {
		return context;
	}

	public Map getBeans() {
		return beans;
	}

	public void setHasError(boolean hasError) {
		this.hasError = hasError;
	}

	public Object getAdapter(Class adapter) {
		if (adapter == IModelElement.class) {
			return getElement();
		}
		return null;
	}

	public boolean exists() {
		return false;
	}

	public ImageDescriptor getImageDescriptor() {
		return BeansGraphImages.DESC_OBJS_SPRING;
	}

	public IPersistableElement getPersistable() {
		return (hasError ? null : this);
	}

	public String getToolTipText() {
		return toolTip;
	}

	public String getFactoryId() {
		return GraphEditorInputFactory.getFactoryId();
	}

	public void saveState(IMemento memento) {
		GraphEditorInputFactory.saveState(memento, this);
	}

	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof GraphEditorInput)) {
			return false;
		}
		GraphEditorInput that = (GraphEditorInput) other;
		if (!ObjectUtils.nullSafeEquals(this.element, that.element))
			return false;
		if (!ObjectUtils.nullSafeEquals(this.context, that.context))
			return false;
		return super.equals(other);
	}

	public int hashCode() {
		int hashCode = ObjectUtils.nullSafeHashCode(element);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(context);
		return 29 * hashCode + super.hashCode();
	}

	private static IModelElement getContext(IModelElement element) {
		if (element instanceof IBean) {
			return element.getElementParent();
		} else if (element instanceof IBeanConstructorArgument
				|| element instanceof IBeanProperty) {
			return element.getElementParent().getElementParent();
		}
		return element;
	}

	private static IModelElement getContext(String elementId) {
		IModelElement element = BeansCorePlugin.getModel()
				.getElement(elementId);
		return getContext(element);
	}
}

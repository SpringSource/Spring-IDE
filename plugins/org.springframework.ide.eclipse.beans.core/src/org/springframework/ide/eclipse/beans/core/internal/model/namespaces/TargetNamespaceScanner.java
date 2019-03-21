/*******************************************************************************
 * Copyright (c) 2011, 2014 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.beans.core.internal.model.namespaces;

import java.io.IOException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.ide.eclipse.beans.core.BeansCorePlugin;
import org.springsource.ide.eclipse.commons.core.SpringCoreUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Scanner to quickly identify the namespace that is declared inside an XSD.
 * @author Martin Lippert
 * @since 2.8.0
 */
public class TargetNamespaceScanner {

	/**
	 * Returns the target namespace URI of the XSD identified by the given
	 * <code>url</code>.
	 */
	public static String getTargetNamespace(URL url) {
		if (url == null) {
			return null;
		}

		ClassLoader ccl = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(TargetNamespaceScanner.class.getClassLoader());
			
			DocumentBuilderFactory factory = SpringCoreUtils.getDocumentBuilderFactory();
			factory.setValidating(false);
			
			factory.setFeature("http://www.xml.org/sax/features/validation", false);
			factory.setFeature("https://apache.org/xml/features/validation/dynamic", false);
			factory.setFeature("https://apache.org/xml/features/validation/schema", false);
			factory.setFeature("https://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			
			DocumentBuilder docBuilder = factory.newDocumentBuilder();
			Document doc = docBuilder.parse(url.openStream());
			
			return doc.getDocumentElement().getAttribute("targetNamespace");
		} catch (SAXException e) {
			BeansCorePlugin.logAsWarning(e);
		} catch (IOException e) {
			BeansCorePlugin.logAsWarning(e);
		} catch (ParserConfigurationException e) {
			BeansCorePlugin.logAsWarning(e);
		}
		finally {
			Thread.currentThread().setContextClassLoader(ccl);
		}
		return null;
	}

}

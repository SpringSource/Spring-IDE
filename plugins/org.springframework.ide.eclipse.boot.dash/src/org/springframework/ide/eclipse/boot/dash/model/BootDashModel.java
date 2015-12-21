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
package org.springframework.ide.eclipse.boot.dash.model;

import org.eclipse.core.runtime.ListenerList;
import org.springframework.ide.eclipse.boot.dash.livexp.ObservableSet;
import org.springframework.ide.eclipse.boot.dash.views.BootDashModelConsoleManager;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;

public abstract class BootDashModel {

	private BootDashViewModel parent;
	private RunTarget target;

	private LiveVariable<RefreshState> refreshState;

	public BootDashModel(RunTarget target, BootDashViewModel parent) {
		super();
		this.target = target;
		this.parent = parent;
		this.refreshState = new LiveVariable<RefreshState>(RefreshState.READY, this);
	}

	public RunTarget getRunTarget() {
		return this.target;
	}

	ListenerList elementStateListeners = new ListenerList();

	public void notifyElementChanged(BootDashElement element) {
		for (Object l : elementStateListeners.getListeners()) {
			((ElementStateListener) l).stateChanged(element);
		}
	}

	ListenerList modelStateListeners = new ListenerList();

	protected final void notifyModelStateChanged() {
		for (Object l : modelStateListeners.getListeners()) {
			((ModelStateListener) l).stateChanged(this);
		}
	}

	abstract public ObservableSet<BootDashElement> getElements();

	abstract public BootDashModelConsoleManager getElementConsoleManager();

	/**
	 * When no longer needed the model should be disposed, otherwise it will
	 * continue listening for changes to the workspace in order to keep itself
	 * in synch.
	 */
	abstract public void dispose();

	/**
	 * Trigger manual model refresh.
	 */
	abstract public void refresh(UserInteractions ui);

	/**
	 * Returns the state of the model
	 * @return
	 */
	public RefreshState getRefreshState() {
		return refreshState.getValue();
	}

	public final void setRefreshState(RefreshState newState) {
		refreshState.setValue(newState);
		notifyModelStateChanged();
	}

	public void addElementStateListener(ElementStateListener l) {
		elementStateListeners.add(l);
	}

	public void removeElementStateListener(ElementStateListener l) {
		elementStateListeners.remove(l);
	}

	public interface ElementStateListener {
		/**
		 * Called when something about the element has changed.
		 * <p>
		 * Note this doesn't get called when (top-level) elements are
		 * added / removed to the model. Only when some property of
		 * the element itself has changed.
		 * <p>
		 * Note: think of the 'children' of an element as a propery of its parent element.
		 * So, if a child is added/removed to/from an element then the element
		 * itself will receive a stateChanged event.
		 */
		void stateChanged(BootDashElement e);
	}

	public interface ModelStateListener {
		void stateChanged(BootDashModel model);
	}

	public void addModelStateListener(ModelStateListener l) {
		modelStateListeners.add(l);
	}

	public void removeModelStateListener(ModelStateListener l) {
		modelStateListeners.remove(l);
	}

	public BootDashViewModel getViewModel() {
		return parent;
	}

}
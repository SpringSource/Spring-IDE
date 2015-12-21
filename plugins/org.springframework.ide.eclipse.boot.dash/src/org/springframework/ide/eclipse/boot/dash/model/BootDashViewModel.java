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

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ide.eclipse.boot.dash.cloudfoundry.DevtoolsUtil;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.debug.DebugStrategyManager;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.debug.DebugSupport;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.debug.ssh.SshDebugSupport;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetType;
import org.springframework.ide.eclipse.boot.util.ProcessTracker;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSet;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;
import org.springsource.ide.eclipse.commons.livexp.util.Filters;

/**
 * @author Kris De Volder
 */
public class BootDashViewModel implements Disposable {

	private LiveSet<RunTarget> runTargets;
	private BootDashModelManager models;
	private Set<RunTargetType> runTargetTypes;
	private RunTargetPropertiesManager manager;
	private ToggleFiltersModel toggleFiltersModel;
	private BootDashElementsFilterBoxModel filterBox;
	private LiveExpression<Filter<BootDashElement>> filter;
	private ProcessTracker devtoolsProcessTracker;
	private List<RunTargetType> orderedRunTargetTypes;
	private Comparator<BootDashModel> modelComparator;
	private DebugStrategyManager cfDebugStrategies;

	/**
	 * Create an 'empty' BootDashViewModel with no run targets. Targets can be
	 * added by adding them to the runTarget's LiveSet.
	 */
	public BootDashViewModel(BootDashModelContext context, RunTargetType... runTargetTypes) {
		runTargets = new LiveSet<RunTarget>(new LinkedHashSet<RunTarget>());
		models = new BootDashModelManager(context, this, runTargets);

		manager = new RunTargetPropertiesManager(context, runTargetTypes);
		List<RunTarget> existingtargets = manager.getStoredTargets();
		runTargets.addAll(existingtargets);
		runTargets.addListener(manager);

		this.orderedRunTargetTypes = Arrays.asList(runTargetTypes);
		this.modelComparator = new BootModelComparator(orderedRunTargetTypes);

		this.runTargetTypes = new LinkedHashSet<RunTargetType>(orderedRunTargetTypes);
		filterBox = new BootDashElementsFilterBoxModel();
		toggleFiltersModel = new ToggleFiltersModel();
		filter = Filters.compose(filterBox.getFilter(), toggleFiltersModel.getFilter());
		devtoolsProcessTracker = DevtoolsUtil.createProcessTracker(this);
		cfDebugStrategies = createCfDebugStrategies();
	}

	protected DebugStrategyManager createCfDebugStrategies() {
		return new DebugStrategyManager(SshDebugSupport.INSTANCE, this);
	}

	public LiveSet<RunTarget> getRunTargets() {
		return runTargets;
	}

	@Override
	public void dispose() {
		models.dispose();
		devtoolsProcessTracker.dispose();
		cfDebugStrategies.dispose();
	}

	public void addElementStateListener(ElementStateListener l) {
		models.addElementStateListener(l);
	}

	public void removeElementStateListener(ElementStateListener l) {
		models.removeElementStateListener(l);
	}

	public LiveExpression<Set<BootDashModel>> getSectionModels() {
		return models.getModels();
	}

	public Set<RunTargetType> getRunTargetTypes() {
		return runTargetTypes;
	}

	public void removeTarget(RunTarget toRemove, UserInteractions userInteraction) {

		if (toRemove != null) {
			RunTarget found = null;
			for (RunTarget existingTarget : runTargets.getValues()) {
				if (existingTarget.getId().equals(toRemove.getId())) {
					found = existingTarget;
					break;
				}
			}
			if (found != null && userInteraction.confirmOperation("Deleting run target: " + found.getName(),
					"Are you sure that you want to delete " + found.getName()
							+ "? All information regarding this target will be permanently removed.")) {
				runTargets.remove(found);
			}
		}
	}

	public void updatePropertiesInStore(RunTargetWithProperties target) {
		// For now, only properties for secure storage can be updated (e.g. credentials for the run target)
		manager.secureStorage(target.getTargetProperties());
	}

	public void updateTargetPropertiesInStore() {
		manager.store(getRunTargets().getValue());
	}

	public ToggleFiltersModel getToggleFilters() {
		return toggleFiltersModel;
	}

	public BootDashElementsFilterBoxModel getFilterBox() {
		return filterBox;
	}

	public LiveExpression<Filter<BootDashElement>> getFilter() {
		return filter;
	}

	public RunTarget getRunTargetById(String targetId) {
		for (BootDashModel m : getSectionModels().getValue()) {
			RunTarget target = m.getRunTarget();
			if (target.getId().equals(targetId)) {
				return target;
			}
		};
		return null;
	}

	public BootDashModel getSectionByTargetId(String targetId) {
		for (BootDashModel m : getSectionModels().getValue()) {
			if (m.getRunTarget().getId().equals(targetId)) {
				return m;
			}
		};
		return null;
	}

	public Comparator<BootDashModel> getModelComparator() {
		return this.modelComparator;
	}

	public DebugSupport getCfDebugSupport() {
		//TODO: DebugSupport is specific to CF, so why is it provided here in the viewModel that encompasses all
		//types of elements?
		//Right now there seems to be no better place for it, but maybe it really belong in the
		// CF RunTargetType.
		return cfDebugStrategies.getStrategy();
	}


}

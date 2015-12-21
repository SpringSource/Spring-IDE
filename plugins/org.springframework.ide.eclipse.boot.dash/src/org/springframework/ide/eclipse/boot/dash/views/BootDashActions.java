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
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.ngrok.NGROKInstallManager;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

public class BootDashActions {

	private final static String PROPERTIES_VIEW_ID = "org.eclipse.ui.views.PropertySheet"; //$NON-NLS-1$

	///// context info //////////////
	private BootDashViewModel model;
	private MultiSelection<BootDashElement> elementsSelection;
	private UserInteractions ui;
	private LiveExpression<BootDashModel> sectionSelection;

	///// actions ///////////////////
	private RunStateAction[] runStateActions;
	private AbstractBootDashElementsAction openConsoleAction;
	private OpenLaunchConfigAction openConfigAction;
	private OpenInBrowserAction openBrowserAction;
	private OpenInPackageExplorer openInPackageExplorerAction;
	private AddRunTargetAction[] addTargetActions;
	private RefreshRunTargetAction refreshAction;
	private RemoveRunTargetAction removeTargetAction;
	private DeleteApplicationsAction deleteApplicationsAction;
	private RestartApplicationOnlyAction restartOnlyAction;
	private RestartWithRemoteDevClientAction restartWithRemoteDevClientAction;
	private OpenCloudAdminConsoleAction openCloudAdminConsoleAction;
	private ReconnectCloudConsoleAction reconnectCloudConsoleAction;
	private ToggleBootDashModelConnection toggleTargetConnectionAction;

	private UpdatePasswordAction updatePasswordAction;
	private ShowViewAction showPropertiesViewAction;
	private ToggleFiltersAction toggleFiltersAction;
	private ExposeAppAction exposeRunAppAction;
	private ExposeAppAction exposeDebugAppAction;

	public BootDashActions(BootDashViewModel model, MultiSelection<BootDashElement> selection, UserInteractions ui) {
		this(
				model,
				selection,
				null,
				ui
		);
	}

	public BootDashActions(BootDashViewModel model, MultiSelection<BootDashElement> selection, LiveExpression<BootDashModel> section, UserInteractions ui) {
		Assert.isNotNull(ui);
		this.model = model;
		this.elementsSelection = selection;
		this.sectionSelection = section;
		this.ui = ui;

		makeActions();
	}

	protected void makeActions() {
		RunStateAction restartAction = new RestartAction(model, elementsSelection, ui, RunState.RUNNING);

		RunStateAction rebugAction = new RedebugAction(model, elementsSelection, ui, RunState.DEBUGGING);

		RunStateAction stopAction = new RunStateAction(model, elementsSelection, ui, RunState.INACTIVE) {
			@Override
			protected boolean currentStateAcceptable(RunState s) {
				// Enable stop button so CF apps can be stopped when "STARTING"
				return s != RunState.INACTIVE;
			}

			@Override
			protected Job createJob() {
				final Collection<BootDashElement> selecteds = elementsSelection.getValue();
				if (!selecteds.isEmpty()) {
					return new Job("Stopping " + selecteds.size() + " Boot Dash Elements") {
						protected IStatus run(IProgressMonitor monitor) {
							monitor.beginTask("Stopping " + selecteds.size() + " Elements", selecteds.size());
							try {
								for (BootDashElement el : selecteds) {
									monitor.subTask("Stopping: " + el.getName());
									try {
										el.stopAsync(ui);
									} catch (Exception e) {
										return BootActivator.createErrorStatus(e);
									}
									monitor.worked(1);
								}
								return Status.OK_STATUS;
							} finally {
								monitor.done();
							}
						}
					};
				}
				return null;
			}
		};
		stopAction.setText("Stop");
		stopAction.setToolTipText("Stop the process(es) associated with the selected elements");
		stopAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/stop.gif"));
		stopAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/stop_disabled.gif"));

		runStateActions = new RunStateAction[] { restartAction, rebugAction, stopAction };

		openConfigAction = new OpenLaunchConfigAction(model, elementsSelection, ui);
		openConsoleAction = new OpenConsoleAction(elementsSelection, model, ui);
		openBrowserAction = new OpenInBrowserAction(model, elementsSelection, ui);
		openInPackageExplorerAction = new OpenInPackageExplorer(elementsSelection, ui);
		addTargetActions = createAddTargetActions();

		deleteApplicationsAction = new DeleteApplicationsAction(elementsSelection, ui);
		restartOnlyAction = new RestartApplicationOnlyAction(elementsSelection, ui);
		reconnectCloudConsoleAction = new ReconnectCloudConsoleAction(elementsSelection, ui);

		if (sectionSelection != null) {
			refreshAction = new RefreshRunTargetAction(sectionSelection, ui);
			removeTargetAction = new RemoveRunTargetAction(sectionSelection, model, ui);
			updatePasswordAction = new UpdatePasswordAction(sectionSelection, model, ui);
			openCloudAdminConsoleAction = new OpenCloudAdminConsoleAction(sectionSelection, ui);
			toggleTargetConnectionAction = new ToggleBootDashModelConnection(sectionSelection, ui);
		}

		showPropertiesViewAction = new ShowViewAction(PROPERTIES_VIEW_ID);

		toggleFiltersAction = new ToggleFiltersAction(model.getToggleFilters(), elementsSelection, ui);

		exposeRunAppAction = new ExposeAppAction(model, elementsSelection, ui, RunState.RUNNING, NGROKInstallManager.getInstance());
		exposeRunAppAction.setText("(Re)start and Expose via ngrok");
		exposeRunAppAction.setToolTipText("Start or restart the process associated with the selected elements and expose it to the outside world via an ngrok tunnel");
		exposeRunAppAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/restart.gif"));
		exposeRunAppAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/restart_disabled.gif"));

		exposeDebugAppAction = new ExposeAppAction(model, elementsSelection, ui, RunState.DEBUGGING, NGROKInstallManager.getInstance());
		exposeDebugAppAction.setText("(Re)debug and Expose via ngrok");
		exposeDebugAppAction.setToolTipText("Start or restart the process associated with the selected elements in debug mode and expose it to the outside world via an ngrok tunnel");
		exposeDebugAppAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/rebug.png"));
		exposeDebugAppAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/rebug_disabled.png"));

		restartWithRemoteDevClientAction = new RestartWithRemoteDevClientAction(model, elementsSelection, ui);

	}

	private AddRunTargetAction[] createAddTargetActions() {
		Set<RunTargetType> targetTypes = model.getRunTargetTypes();
		ArrayList<AddRunTargetAction> actions = new ArrayList<AddRunTargetAction>();
		for (RunTargetType tt : targetTypes) {
			if (tt.canInstantiate()) {
				actions.add(new AddRunTargetAction(tt, model.getRunTargets(), ui));
			}
		}
		return actions.toArray(new AddRunTargetAction[actions.size()]);
	}

	private static final class RestartAction extends RunOrDebugStateAction {
		private RestartAction(BootDashViewModel model, MultiSelection<BootDashElement> selection, UserInteractions ui,
				RunState goalState) {
			super(model, selection, ui, goalState);
			setText("(Re)start");
			setToolTipText("Start or restart the process associated with the selected elements");
			setImageDescriptor(BootDashActivator.getImageDescriptor("icons/restart.gif"));
			setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/restart_disabled.gif"));
		}

		/**
		 * Overrides parent to automatically retarget this action to apply to all the children of an element
		 * (if it has children). This way the action behaves logically if both a parent and some children
		 * are selected (i.e. we don't want to execute the action twice on the explicitly selected children!)
		 */
		@Override
		public Collection<BootDashElement> getTargetElements() {
			Builder<BootDashElement> builder = ImmutableSet.builder();
			addTargetsFor(builder, getSelectedElements());
			return builder.build();
		}

		private void addTargetsFor(Builder<BootDashElement> builder, Collection<BootDashElement> selecteds) {
			for (BootDashElement s : selecteds) {
				addTargetsFor(builder, s);
			}
		}

		private void addTargetsFor(Builder<BootDashElement> builder, BootDashElement s) {
			ImmutableSet<BootDashElement> children = s.getChildren().getValues();
			if (children.isEmpty()) {
				//No children, add s itself
				builder.add(s);
			} else {
				addTargetsFor(builder, children);
			}
		}
	}

	private static final class RedebugAction extends RunOrDebugStateAction {
		private RedebugAction(BootDashViewModel model, MultiSelection<BootDashElement> selection, UserInteractions ui,
				RunState goalState) {
			super(model, selection, ui, goalState);
			setText("(Re)debug");
			setToolTipText("Start or restart the process associated with the selected elements in debug mode");
			setImageDescriptor(BootDashActivator.getImageDescriptor("icons/rebug.png"));
			setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/rebug_disabled.png"));
		}

		@Override
		protected boolean appliesToElement(BootDashElement e) {
			//Do not enable redebug action for node that has multiple children
			// See https://www.pivotaltracker.com/story/show/110374096
			return e.getChildren().getValues().size()<=1;
		}
	}

	public static class RunOrDebugStateAction extends RunStateAction {

		public RunOrDebugStateAction(BootDashViewModel model, MultiSelection<BootDashElement> selection,
				UserInteractions ui, RunState goalState) {
			super(model, selection, ui, goalState);
			Assert.isLegal(goalState == RunState.RUNNING || goalState == RunState.DEBUGGING);
		}

		@Override
		protected Job createJob() {
			final Collection<BootDashElement> selecteds = getTargetElements();
			if (!selecteds.isEmpty()) {
				return new Job("Restarting " + selecteds.size() + " Dash Elements") {
					@Override
					public IStatus run(IProgressMonitor monitor) {
						monitor.beginTask("Restart Boot Dash Elements", selecteds.size());
						try {
							for (BootDashElement el : selecteds) {
								monitor.subTask("Restarting: " + el.getName());
								try {
									el.restart(goalState, ui);
								} catch (Exception e) {
									return BootActivator.createErrorStatus(e);
								}
								monitor.worked(1);
							}
							return Status.OK_STATUS;
						} finally {
							monitor.done();
						}
					}

				};
			}
			return null;
		}

		/**
		 * Determines the elements that the action will effectively operator on when
		 * it is executed. This is typically the same as the selected elements, but some
		 * actions may want to 'reinterpret' the selected elements.
		 */
		public Collection<BootDashElement> getTargetElements() {
			return getSelectedElements();
		}
	}

	public RunStateAction[] getRunStateActions() {
		return runStateActions;
	}

	public AbstractBootDashElementsAction getOpenBrowserAction() {
		return openBrowserAction;
	}

	public AbstractBootDashElementsAction getOpenConsoleAction() {
		return openConsoleAction;
	}

	public AbstractBootDashElementsAction getOpenInPackageExplorerAction() {
		return openInPackageExplorerAction;
	}

	public OpenLaunchConfigAction getOpenConfigAction() {
		return openConfigAction;
	}

	public AddRunTargetAction[] getAddRunTargetActions() {
		return addTargetActions;
	}

	public IAction getRemoveRunTargetAction() {
		return removeTargetAction;
	}

	/**
	 * @return May be null as it may not be supported on all models.
	 */
	public IAction getRefreshRunTargetAction() {
		return refreshAction;
	}

	/**
	 * @return May be null as it may not be supported on all models.
	 */
	public IAction getDeleteApplicationsAction() {
		return deleteApplicationsAction;
	}

	public IAction getRestartOnlyApplicationAction() {
		return restartOnlyAction;
	}

	public IAction getReconnectCloudConsole() {
		return reconnectCloudConsoleAction;
	}

	public IAction getOpenCloudAdminConsoleAction() {
		return openCloudAdminConsoleAction;
	}

	public IAction getToggleTargetConnectionAction() {
		return toggleTargetConnectionAction;
	}

	/**
	 * @return May be null as it may not be supported on all models.
	 */
	public IAction getUpdatePasswordAction() {
		return updatePasswordAction;
	}

	/**
	 * @return show properties view action instance
	 */
	public IAction getShowPropertiesViewAction() {
		return showPropertiesViewAction;
	}

	public IAction getExposeRunAppAction() {
		return exposeRunAppAction;
	}

	public IAction getExposeDebugAppAction() {
		return exposeDebugAppAction;
	}

	public void dispose() {
		if (runStateActions != null) {
			for (RunStateAction a : runStateActions) {
				a.dispose();
			}
			runStateActions = null;
		}
		if (openConsoleAction != null) {
			openConsoleAction.dispose();
		}
		if (openConfigAction != null) {
			openConfigAction.dispose();
		}
		if (openBrowserAction != null) {
			openBrowserAction.dispose();
		}
		if (addTargetActions != null) {
			for (AddRunTargetAction a : addTargetActions) {
				a.dispose();
			}
			addTargetActions = null;
		}
		if (toggleFiltersAction != null) {
			toggleFiltersAction.dispose();
			toggleFiltersAction = null;
		}

		if (exposeRunAppAction != null) {
			exposeRunAppAction.dispose();
			exposeRunAppAction = null;
		}

		if (exposeDebugAppAction != null) {
			exposeDebugAppAction.dispose();
			exposeDebugAppAction = null;
		}
	}

	public IAction getToggleFiltersAction() {
		return toggleFiltersAction;
	}

	public RestartWithRemoteDevClientAction getRestartWithRemoteDevClientAction() {
		return restartWithRemoteDevClientAction;
	}

}

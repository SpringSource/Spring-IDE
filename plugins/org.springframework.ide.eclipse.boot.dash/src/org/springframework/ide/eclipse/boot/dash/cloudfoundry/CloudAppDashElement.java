/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;

import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudAppDashElement.CloudAppIdentity;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.OperationTracker.Task;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFApplication;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFApplicationDetail;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.CFInstanceStats;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.ClientRequests;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.HealthChecks;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.client.v2.CFPushArguments;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.console.LogType;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.debug.DebugSupport;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.deployment.CloudApplicationDeploymentProperties;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.deployment.DeploymentProperties;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops.CloudApplicationOperation;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops.Operation;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops.RemoteDevClientStartOperation;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.ops.SetHealthCheckOperation;
import org.springframework.ide.eclipse.boot.dash.metadata.IPropertyStore;
import org.springframework.ide.eclipse.boot.dash.metadata.PropertyStoreApi;
import org.springframework.ide.eclipse.boot.dash.metadata.PropertyStoreFactory;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.Deletable;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens.CancelationToken;
import org.springframework.ide.eclipse.boot.dash.util.LogSink;
import org.springframework.ide.eclipse.boot.dash.views.BootDashModelConsoleManager;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * A handle to a Cloud application. NOTE: This element should NOT hold Cloud
 * application state as it may be discarded and created multiple times for the
 * same app for any reason.
 * <p/>
 * Cloud application state should always be resolved from external sources
 */
public class CloudAppDashElement extends CloudDashElement<CloudAppIdentity> implements Deletable, LogSink {

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	static final private String DEPLOYMENT_MANIFEST_FILE_PATH = "deploymentManifestFilePath"; //$NON-NLS-1$
	private static final String PROJECT_NAME = "PROJECT_NAME";

	private CancelationTokens cancelationTokens;

	private final CloudFoundryRunTarget cloudTarget;
	private final CloudFoundryBootDashModel cloudModel;
	private PropertyStoreApi persistentProperties;

	private final LiveVariable<Throwable> error = new LiveVariable<>();
	private final OperationTracker startOperationTracker = new OperationTracker(()-> this.getName(),error);

	private final LiveVariable<CFApplication> appData = new LiveVariable<>();
	private final LiveVariable<List<CFInstanceStats>> instanceData = new LiveVariable<>();
	private final LiveExpression<RunState> baseRunState = new LiveExpression<RunState>() {

		{
			dependsOn(appData);
			dependsOn(instanceData);
			dependsOn(startOperationTracker.inProgress);
			dependsOn(error);
		}

		@Override
		protected RunState compute() {
			if (error.getValue()!=null) {
				return RunState.UNKNOWN;
			}
			if (startOperationTracker.inProgress.getValue()>0) {
				return RunState.STARTING;
			}
			CFApplication app = appData.getValue();
			List<CFInstanceStats> instances = instanceData.getValue();
			if (instances!=null && app!=null) {
				return ApplicationRunningStateTracker.getRunState(app, instances);
			}
			return RunState.UNKNOWN;
		}
	};

	/**
	 * Used as a temporary override of health-check info fetched from CF. This is cleared when element gets 'proper'
	 * data fetched from CF. This exists so that we can implement 'setHealthCheck' which is called to update
	 * model state after changing the health-check value individually. It makes sense in that case to only update
	 * this one bit of the model state rather than refresh all the data from CF.
	 */
	private final LiveVariable<String> healthCheckOverride = new LiveVariable<>();
	{
		appData.addListener((e, v) -> {
			healthCheckOverride.setValue(null);
		});
	}

	protected void showConsole() {
		try {
			getCloudModel().getElementConsoleManager().showConsole(this);
		} catch (Exception e) {
			Log.log(e);
		}
	}

	public CloudAppDashElement(CloudFoundryBootDashModel model, String appName, IPropertyStore modelStore) {
		super(model, new CloudAppIdentity(appName, model.getRunTarget()));
		this.cancelationTokens = new CancelationTokens();
		this.cloudTarget = model.getRunTarget();
		this.cloudModel = model;
		IPropertyStore backingStore = PropertyStoreFactory.createSubStore("A"+getName(), modelStore);
		this.persistentProperties = PropertyStoreFactory.createApi(backingStore);
		addElementNotifier(baseRunState);
		addElementNotifier(appData);
		addElementNotifier(healthCheckOverride);
		this.addDisposableChild(baseRunState);
	}

	public CloudFoundryBootDashModel getCloudModel() {
		return (CloudFoundryBootDashModel) getBootDashModel();
	}

	@Override
	public void stopAsync(UserInteractions ui) throws Exception {
		cancelOperations();
		String appName = getName();
		getCloudModel().runAsynch("Stopping application " + appName, appName, (IProgressMonitor monitor) -> {
			stop(createCancelationToken(), monitor);
		}, ui);
	}

	public void stop(CancelationToken cancelationToken, IProgressMonitor monitor) throws Exception {
		checkTerminationRequested(cancelationToken, monitor);
		getClient().stopApplication(getName());
		getCloudModel().getElementConsoleManager().terminateConsole(getName());
		refresh();
	}

	@Override
	public void restart(RunState runningOrDebugging, UserInteractions ui) throws Exception {
		cancelOperations();
		CancelationToken cancelationToken = createCancelationToken();
		if (getProject() != null) {
			cloudModel.runAsynch("Restarting, goal state: " + runningOrDebugging, getName(), (IProgressMonitor monitor) -> {
				// Let push and debug resolve deployment properties
				CloudApplicationDeploymentProperties deploymentProperties = null;
				pushAndDebug(deploymentProperties, runningOrDebugging, ui, cancelationToken, monitor);
			}, ui);
		} else {
			cloudModel.runAsynch("Restarting, goal state: " + runningOrDebugging, getName(), (IProgressMonitor monitor) -> {
				restartOnly(ui, cancelationToken, monitor);
			}, ui);
		}
	}

	public DebugSupport getDebugSupport() {
		//In the future we may need to choose between multiple strategies here.
		return getViewModel().getCfDebugSupport();
	}

	public BootDashViewModel getViewModel() {
		return getBootDashModel().getViewModel();
	}

	public void restartWithRemoteClient(UserInteractions ui, CancelationToken cancelationToken) {
		String opName = "Restart Remote DevTools Client for application '" + getName() + "'";
		getCloudModel().runAsynch(opName, getName(), (IProgressMonitor monitor) -> {
			doRestartWithRemoteClient(RunState.RUNNING, ui, cancelationToken, monitor);
		}, ui);
	}

	protected void doRestartWithRemoteClient(RunState runningOrDebugging, UserInteractions ui, CancelationToken cancelationToken, IProgressMonitor monitor)
			throws Exception {

		CloudFoundryBootDashModel model = getCloudModel();
		Map<String, String> envVars = model.getRunTarget().getClient().getApplicationEnvironment(getName());

		if (getProject() == null) {
			ExceptionUtil.coreException(new Status(IStatus.ERROR, BootDashActivator.PLUGIN_ID,
					"Local project not associated to CF app '" + getName() + "'"));
		}

		new SetHealthCheckOperation(this, HealthChecks.HC_NONE, ui, /* confirmChange */true, cancelationToken)
				.run(monitor);

		if (!DevtoolsUtil.isEnvVarSetupForRemoteClient(envVars, DevtoolsUtil.getSecret(getProject()))) {
			// Let the push and debug operation resolve default properties
			CloudApplicationDeploymentProperties deploymentProperties = null;

			pushAndDebug(deploymentProperties , runningOrDebugging, ui, cancelationToken, monitor);
			/*
			 * Restart and push op resets console anyway, no need to reset it
			 * again
			 */
		} else if (getRunState() == RunState.INACTIVE) {
			restartOnly(ui, cancelationToken, monitor);
		}

		new RemoteDevClientStartOperation(model, getName(), runningOrDebugging, cancelationToken).run(monitor);
	}


	public void restartOnly(UserInteractions ui, CancelationToken cancelationToken, IProgressMonitor monitor) throws Exception {
		whileStarting(ui, cancelationToken, monitor, () -> {
			if (!getClient().applicationExists(getName())) {
				throw ExceptionUtil.coreException(
						"Unable to start the application. Application does not exist anymore in Cloud Foundry: "
								+ getName());
			}

			checkTerminationRequested(cancelationToken, monitor);

			log("Starting application: " + getName());
			getClient().restartApplication(getName(), CancelationTokens.merge(cancelationToken, monitor));

			new ApplicationRunningStateTracker(cancelationToken, this).startTracking(monitor);

			CFApplicationDetail updatedInstances = getClient().getApplication(getName());
			setDetailedData(updatedInstances);
		});
	}

	public void restartOnlyAsynch(UserInteractions ui, CancelationToken cancelationToken) {
		String opName = "Restarting application " + getName();
		getCloudModel().runAsynch(opName, getName(), (IProgressMonitor monitor) -> {
			restartOnly(ui, cancelationToken, monitor);
		}, ui);
	}

	public void pushAndDebug(CloudApplicationDeploymentProperties deploymentProperties, RunState runningOrDebugging,
			UserInteractions ui, CancelationToken cancelationToken, IProgressMonitor monitor) throws Exception {
		String opName = "Starting application '" + getName() + "' in "
				+ (runningOrDebugging == RunState.DEBUGGING ? "DEBUG" : "RUN") + " mode";
		DebugSupport debugSupport = getDebugSupport();

		if (runningOrDebugging == RunState.DEBUGGING) {

			if (debugSupport != null && debugSupport.isSupported(this)) {
				Operation<?> debugOp = debugSupport.createOperation(this, opName, ui, cancelationToken);

				push(deploymentProperties, runningOrDebugging, debugSupport, cancelationToken, ui, monitor);
				debugOp.run(monitor);
			} else {
				String title = "Debugging is not supported for '" + getName() + "'";
				String msg = debugSupport.getNotSupportedMessage(this);
				if (msg == null) {
					msg = title;
				}
				ui.errorPopup(title, msg);
				throw ExceptionUtil.coreException(msg);
			}
		} else {
			push(deploymentProperties, runningOrDebugging, debugSupport, cancelationToken, ui, monitor);
		}
	}

	@Override
	public void openConfig(UserInteractions ui) {

	}

	@Override
	public String getName() {
		return delegate.getAppName();
	}

	/**
	 * Returns the project associated with this element or null. If includeNonExistingProjects is
	 * true, then the project is returned even it no longer exists.
	 */
	public IProject getProject(boolean includeNonExistingProjects) {
		String name = getPersistentProperties().get(PROJECT_NAME);
		if (name!=null) {
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
			if (includeNonExistingProjects || project.exists()) {
				return project;
			}
		}
		return null;
	}

	/**
	 * Returns the project associated with this element or null. The project returned is
	 * guaranteed to exist.
	 */
	@Override
	public IProject getProject() {
		return getProject(false);
	}

	/**
	 * Set the project 'binding' for this element.
	 * @return true if the element was changed by this operation.
	 */
	public boolean setProject(IProject project) {
		try {
			PropertyStoreApi props = getPersistentProperties();
			String oldValue = props.get(PROJECT_NAME);
			String newValue = project==null?null:project.getName();
			if (!Objects.equals(oldValue, newValue)) {
				props.put(PROJECT_NAME, newValue);
				return true;
			}
			return false;
		} catch (Exception e) {
			Log.log(e);
			return false;
		}
	}

	@Override
	public RunState getRunState() {
		RunState state = baseRunState.getValue();
		if (state == RunState.RUNNING) {
			DebugSupport debugSupport = getDebugSupport();
			if (debugSupport.isDebuggerAttached(CloudAppDashElement.this)) {
//			if (DevtoolsUtil.isDevClientAttached(this, ILaunchManager.DEBUG_MODE)) {
				state = RunState.DEBUGGING;
			}
		}
		return state;
	}

	/**
	 * This method is mostly meant just for test purposes. The 'baseRunState' is really
	 * part of how this class internally computes runstate. Clients should have no business
	 * using it separate from the runtstate.
	 */
	public LiveExpression<RunState> getBaseRunStateExp() {
		return baseRunState;
	}

	@Override
	public CloudFoundryRunTarget getTarget() {
		return cloudTarget;
	}

	@Override
	public int getLivePort() {
		return 80;
	}

	@Override
	public String getLiveHost() {
		CFApplication app = getSummaryData();
		if (app != null) {
			List<String> uris = app.getUris();
			if (uris != null) {
				for (String uri : uris) {
					return uri;
				}
			}
		}
		return null;
	}

	public Integer getMemory() {
		CFApplication app = getSummaryData();
		if (app != null) {
			return app.getMemory();
		}
		return null;
	}

	public String getHealthCheckHttpEndpoint() {
		CFApplication app = getSummaryData();
		if (app != null) {
			return app.getHealthCheckHttpEndpoint();
		}
		return null;
	}

	public CFApplication getSummaryData() {
		return appData.getValue();
	}

	@Override
	public ILaunchConfiguration getActiveConfig() {
		return null;
	}

	@Override
	public int getActualInstances() {
		CFApplication data = getSummaryData();
		return data != null ? data.getRunningInstances() : 0;
	}

	@Override
	public int getDesiredInstances() {
		CFApplication data = getSummaryData();
		return data != null ? data.getInstances() : 0;
	}

	public String getHealthCheck() {
		String hc = healthCheckOverride.getValue();
		if (hc!=null) {
			return hc;
		}
		CFApplication data = getSummaryData();
		return data!=null ? data.getHealthCheckType() : DeploymentProperties.DEFAULT_HEALTH_CHECK_TYPE;
	}

	/**
	 * Changes the cached health-check value for this model element. Note that this
	 * doesnt *not* change the real value of the health-check.
	 */
	public void setHealthCheck(String hc) {
		this.healthCheckOverride.setValue(hc);
	}


	public UUID getAppGuid() {
		CFApplication app = getSummaryData();
		if (app!=null) {
			return app.getGuid();
		}
		return null;
	}

	@Override
	public PropertyStoreApi getPersistentProperties() {
		return persistentProperties;
	}

	static class CloudAppIdentity {

		private final String appName;
		private final RunTarget runTarget;

		public String toString() {
			return appName + "@" + runTarget;
		};

		CloudAppIdentity(String appName, RunTarget runTarget) {
			this.appName = appName;
			this.runTarget = runTarget;
		}

		public String getAppName() {
			return this.appName;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((appName == null) ? 0 : appName.hashCode());
			result = prime * result + ((runTarget == null) ? 0 : runTarget.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CloudAppIdentity other = (CloudAppIdentity) obj;
			if (appName == null) {
				if (other.appName != null)
					return false;
			} else if (!appName.equals(other.appName))
				return false;
			if (runTarget == null) {
				if (other.runTarget != null)
					return false;
			} else if (!runTarget.equals(other.runTarget))
				return false;
			return true;
		}

	}

	public void log(String message) {
		log(message, LogType.LOCALSTDOUT);
	}

	public void log(String message, LogType logType) {
		try {
			getCloudModel().getElementConsoleManager().writeToConsole(this, message, logType);
		} catch (Exception e) {
			Log.log(e);
		}
	}

	@Override
	public Object getParent() {
		return getBootDashModel();
	}

	protected LiveExpression<URI> getActuatorUrl() {
		LiveExpression<URI> urlExp = getCloudModel().getActuatorUrlFactory().createOrGet(this);
		if (urlExp!=null) {
			return urlExp;
		}
		//only happens when this element is not valid anymore, but return something harmless / usable anyhow
		return LiveExpression.constant(null);
	}

	@Override
	protected Client getRestClient() {
		CloudFoundryTargetProperties props = getTarget().getTargetProperties();
		boolean skipSsl = props.isSelfsigned() || props.skipSslValidation();
		if (skipSsl) {
			try {
				SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(null, new TrustManager[]{new X509TrustManager() {
					public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
					public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
					public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
				}}, new java.security.SecureRandom());
				HostnameVerifier verifier = (a1, a2) -> true;
				return ClientBuilder.newBuilder()
					.sslContext(sslContext)
					.hostnameVerifier(verifier)
					.build();
			} catch (Exception e) {
				Log.log(e);
			}
		}
		//This worked before so lets not try to fix that case.
		return super.getRestClient();
	}

	private javax.net.ssl.SSLContext buildSslContext()  {
		try {
			return new SSLContextBuilder().useSSL().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
		} catch (GeneralSecurityException gse) {
			throw new RuntimeException("An error occurred setting up the SSLContext", gse);
		}
	}


	public IFile getDeploymentManifestFile() {
		String text = getPersistentProperties().get(DEPLOYMENT_MANIFEST_FILE_PATH);
		try {
			return text == null ? null : ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(text));
		} catch (IllegalArgumentException e) {
			Log.log(e);
			return null;
		}
	}

	public void setDeploymentManifestFile(IFile file) {
		try {
			if (file == null) {
				getPersistentProperties().put(DEPLOYMENT_MANIFEST_FILE_PATH, (String) null);
			} else {
				getPersistentProperties().put(DEPLOYMENT_MANIFEST_FILE_PATH, file.getFullPath().toString());
			}
		} catch (Exception e) {
			Log.log(e);
		}
	}

	public void setDetailedData(CFApplicationDetail appDetails) {
		if (appDetails!=null) {
			this.appData.setValue(appDetails);
			this.instanceData.setValue(appDetails.getInstanceDetails());
		} else {
			this.appData.setValue(null);
			this.instanceData.setValue(null);
		}
	}

	public List<CFInstanceStats> getInstanceData() {
		return this.instanceData.getValue();
	}

	public void setError(Throwable t) {
		error.setValue(t);
	}

	public CancelationToken createCancelationToken() {
		return cancelationTokens.create();
	}

	public void cancelOperations() {
		cancelationTokens.cancelAll();
	}

	/**
	 * Print a message to the console associated with this application.
	 */
	public void print(String msg) {
		print(msg, LogType.LOCALSTDOUT);
	}

	/**
	 * Print a message to the console associated with this application.
	 */
	public void printError(String string) {
		print(string, LogType.LOCALSTDERROR);
	}

	/**
	 * Print a message to the console associated with this application.
	 */
	public void print(String msg, LogType type) {
		try {
			BootDashModelConsoleManager consoles = getCloudModel().getElementConsoleManager();
			consoles.writeToConsole(this, msg+"\n", type);
		} catch (Exception e) {
			Log.log(e);
		}
	}

	/**
	 * Attempt to refresh the data associated with this app in the model. Returns the
	 * refreshed element if this was succesful, null if the element was deleted (because during the
	 * refresh we discovered it not longer exists) and if something failed trying to refresh the element.
	 */
	public CloudAppDashElement refresh() throws Exception {
		debug("Refreshing element: "+this.getName());
		CFApplicationDetail data = getClient().getApplication(getName());
		if (data==null) {
			//Looks like element no longer exist in CF so remove it from the model
			CloudFoundryBootDashModel model = getCloudModel();
			model.removeApplication(getName());
			return null;
		}
		getCloudModel().updateApplication(data);
		return this;
	}

	private ClientRequests getClient() {
		return getTarget().getClient();
	}

	public void push(CloudApplicationDeploymentProperties deploymentProperties, RunState runningOrDebugging,
			DebugSupport debugSupport, CancelationToken cancelationToken, UserInteractions ui, IProgressMonitor monitor)
			throws Exception {

		boolean isDebugging = runningOrDebugging == RunState.DEBUGGING;
		whileStarting(ui, cancelationToken, monitor, () -> {
			// Refresh app data and check that the application (still) exists in
			// Cloud Foundry
			// This also ensures that the 'diff change dialog' will pick up on
			// the latest changes.
			// TODO: should this refresh be moved closer to the where we
			// actually compute the diff?
			CloudAppDashElement updatedApp = this.refresh();
			if (updatedApp == null) {
				ExceptionUtil.coreException(new Status(IStatus.ERROR, BootDashActivator.PLUGIN_ID,
						"No Cloud Application found for '" + getName() + "'"));
			}
			IProject project = getProject();
			if (project == null) {
				ExceptionUtil.coreException(new Status(IStatus.ERROR, BootDashActivator.PLUGIN_ID,
						"Local project not associated to CF app '" + getName() + "'"));
			}

			checkTerminationRequested(cancelationToken, monitor);

			DeploymentProperties properties = deploymentProperties == null
					? getCloudModel().resolveDeploymentProperties(updatedApp, ui, monitor) : deploymentProperties;

			// Update JAVA_OPTS env variable with Remote DevTools Client secret
			DevtoolsUtil.setupEnvVarsForRemoteClient(properties.getEnvironmentVariables(),
					DevtoolsUtil.getSecret(project));
			if (debugSupport != null) {
				if (isDebugging) {
					debugSupport.setupEnvVars(properties.getEnvironmentVariables());
				} else {
					debugSupport.clearEnvVars(properties.getEnvironmentVariables());
				}
			}

			checkTerminationRequested(cancelationToken, monitor);

			CFPushArguments pushArgs = properties.toPushArguments(getCloudModel().getCloudDomains(monitor));

			getClient().push(pushArgs, CancelationTokens.merge(cancelationToken, monitor));

			log("Application pushed to Cloud Foundry: " + getName());
		});
	}

	public void whileStarting(UserInteractions ui, CancelationToken cancelationToken, IProgressMonitor monitor, Task task) throws Exception {
		showConsole();
		startOperationTracker.whileExecuting(ui, cancelationToken, monitor, task);
		refresh();
	}

	public void checkTerminationRequested(CancelationToken cancelationToken, IProgressMonitor mon)
			throws OperationCanceledException {
		if (mon != null && mon.isCanceled() || cancelationToken != null && cancelationToken.isCanceled()) {
			throw new OperationCanceledException();
		}
	}

	@Override
	public void delete(UserInteractions ui) {
		CloudFoundryBootDashModel model = getCloudModel();
		CloudAppDashElement cloudElement = this;
		cloudElement.cancelOperations();
		CancelationToken cancelToken = cloudElement.createCancelationToken();
		CloudApplicationOperation operation = new CloudApplicationOperation("Deleting: " + cloudElement.getName(), model,
				cloudElement.getName(), cancelToken) {

			@Override
			protected void doCloudOp(IProgressMonitor monitor) throws Exception, OperationCanceledException {
				// Delete from CF first. Do it outside of synch block to avoid
				// deadlock
				model.getRunTarget().getClient().deleteApplication(appName);
				model.getElementConsoleManager().terminateConsole(cloudElement.getName());
				model.removeApplication(cloudElement.getName());
				cloudElement.setProject(null);
			}
		};

		// Allow deletions to occur concurrently with any other application
		// operation
		operation.setSchedulingRule(null);
		getCloudModel().runAsynch(operation, ui);
	}

	@Override
	public EnumSet<RunState> supportedGoalStates() {
		return CloudFoundryRunTarget.RUN_GOAL_STATES;
	}

}

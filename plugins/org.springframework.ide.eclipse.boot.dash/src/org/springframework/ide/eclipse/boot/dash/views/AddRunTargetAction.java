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

import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetType;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSet;

public class AddRunTargetAction extends AbstractBootDashAction {

	private LiveSet<RunTarget> targets;
	private RunTargetType runTargetType;

	public AddRunTargetAction(RunTargetType runTargetType, LiveSet<RunTarget> targets, UserInteractions ui) {
		super(ui);
		this.runTargetType = runTargetType;
		this.targets = targets;
		this.setText("Add a "+runTargetType.getName()+" Target");
		this.setToolTipText("Configure a connection to "+runTargetType.getName()+" and add it as a new section to the Boot Dashboard");
		this.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/add_target.png"));
		setEnabled(true);
		setVisible(true);
	}

	@Override
	public void run() {
		runTargetType.openTargetCreationUi(targets);
	}

}

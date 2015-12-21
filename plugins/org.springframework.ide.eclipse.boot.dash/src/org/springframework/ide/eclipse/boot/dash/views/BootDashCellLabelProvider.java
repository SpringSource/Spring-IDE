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

import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.StyledCellLabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.graphics.Image;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.RefreshState;
import org.springframework.ide.eclipse.boot.dash.util.ColumnViewerAnimator;
import org.springframework.ide.eclipse.boot.dash.util.Stylers;
import org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn;

/**
 * @author Kris De Volder
 */
public class BootDashCellLabelProvider extends StyledCellLabelProvider {

	protected final BootDashColumn forColum;
	private ColumnViewerAnimator animator;
	private BootDashLabels bdeLabels;

	private ColumnViewer tv;

	public BootDashCellLabelProvider(ColumnViewer tv, BootDashColumn target, Stylers stylers) {
		this.tv = tv;
		this.forColum = target;
		this.bdeLabels = new BootDashLabels(stylers);
	}

	@Override
	public void update(ViewerCell cell) {
		Object e = cell.getElement();
		Image[] imgs = bdeLabels.getImageAnimation(e, forColum);
		StyledString label = bdeLabels.getStyledText(e, forColum);
		cell.setText(label.getString());
		cell.setStyleRanges(label.getStyleRanges());
		animate(cell, imgs);
	}

	private void animate(ViewerCell cell, Image[] images) {
		if (animator==null) {
			animator = new ColumnViewerAnimator(tv);
		}
		animator.setAnimation(cell, images);
	}

	@Override
	public void dispose() {
		super.dispose();
		bdeLabels.dispose();
		if (animator!=null) {
			animator.dispose();
			animator = null;
		}
	}

	@Override
	public String getToolTipText(Object element) {
		if (element instanceof BootDashModel) {
			RefreshState state = ((BootDashModel) element).getRefreshState();
			if (state.getId() == RefreshState.ERROR.getId()) {
				return state.getMessage();
			}
		}
		return null;
	}
}

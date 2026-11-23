package com.vaadin.starter.bakery.testbench;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.vaadin.starter.bakery.testbench.elements.components.DashboardLCounterLabelElement;
import com.vaadin.starter.bakery.testbench.elements.ui.DashboardViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement;
import com.vaadin.testing.unified.UnifiedPage;

public class DashboardViewIT extends AbstractIT<DashboardViewElement> {

	@Override
	protected DashboardViewElement openView(UnifiedPage page) {
		StorefrontViewElement storefront = openLoginView(page).login("admin@vaadin.com", "admin");
		return storefront.getMenu().navigateToDashboard();
	}

	@Test
	public void checkRowsCount(UnifiedPage page) {
		setup(page);
		DashboardViewElement dashboardPage = openView(page);
		Assertions.assertEquals(4, dashboardPage.getBoard().getRows().size());
	}

	@Test
	public void checkCounters(UnifiedPage page) {
		setup(page);
		DashboardViewElement dashboardPage = openView(page);
		int numLabels = dashboardPage.getBoard().getRows().get(0).$(DashboardLCounterLabelElement.class).all().size();
		Assertions.assertEquals(4, numLabels);
	}

}

package com.vaadin.starter.bakery.testbench;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.vaadin.starter.bakery.testbench.elements.components.DashboardLCounterLabelElement;
import com.vaadin.starter.bakery.testbench.elements.ui.DashboardViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;

public class DashboardViewIT extends AbstractIT<DashboardViewElement> {

	@Override
	protected DashboardViewElement openView(UnifiedPage page) {
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		StorefrontViewElement storefront = openLoginView(page).login("admin@vaadin.com", "admin");
		return storefront.getMenu().navigateToDashboard();
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void checkRowsCount(UnifiedPage page) throws Exception {
		setup(page);
		DashboardViewElement dashboardPage = openView(page);
		Assertions.assertEquals(4, dashboardPage.getBoard().getRows().size());
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void checkCounters(UnifiedPage page) throws Exception {
		setup(page);
		DashboardViewElement dashboardPage = openView(page);
		int numLabels = dashboardPage.getBoard().getRows().get(0).$(DashboardLCounterLabelElement.class).all().size();
		Assertions.assertEquals(4, numLabels);
	}

}

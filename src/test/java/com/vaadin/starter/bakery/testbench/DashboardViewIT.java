package com.vaadin.starter.bakery.testbench;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.vaadin.starter.bakery.testbench.elements.components.DashboardLCounterLabelElement;
import com.vaadin.starter.bakery.testbench.elements.ui.DashboardViewElement;
import com.vaadin.starter.bakery.ui.views.dashboard.DashboardView;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

// Escape Hatch kept: the board, its rows and the counter labels are in DashboardView's LitTemplate
// (client-side vaadin-board, @Id-mapped DashboardCounterLabel): no Testing Tool's Unified lookup finds them
@RequiresTestingTool(TestingTool.SELENIUM)
@WithMockUser(username = "admin@vaadin.com", roles = "admin")
public class DashboardViewIT extends AbstractIT {

	private static DashboardViewElement openView(UnifiedPage page) {
		page.navigate(DashboardView.class);
		return TestBenchEscapeHatch.of(page).$(DashboardViewElement.class).first();
	}

	@Test
	@DisplayName("The dashboard shows four rows of charts and counters")
	public void checkRowsCount(UnifiedPage page) {
		DashboardViewElement dashboardPage = openView(page);
		Assertions.assertEquals(4, dashboardPage.getBoard().getRows().size());
	}

	@Test
	@DisplayName("The dashboard's first row shows four order counters")
	public void checkCounters(UnifiedPage page) {
		DashboardViewElement dashboardPage = openView(page);
		int numLabels = dashboardPage.getBoard().getRows().get(0).$(DashboardLCounterLabelElement.class).all().size();
		Assertions.assertEquals(4, numLabels);
	}

}

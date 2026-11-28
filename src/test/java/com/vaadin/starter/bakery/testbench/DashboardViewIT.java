package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import com.vaadin.starter.bakery.ui.views.dashboard.DashboardCounterLabel;
import com.vaadin.starter.bakery.ui.views.dashboard.DashboardView;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

@WithUserDetails("admin@vaadin.com")
public class DashboardViewIT extends AbstractIT {

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	@DisplayName("The dashboard lays its content out in four board rows")
	public void checkRowsCount(UnifiedPage page) {
		openDashboard(page);

		// Escape Hatch kept: the board rows exist only in the template's shadow DOM (no @Id, no server-side
		// component); no Unified lookup enters a shadow root, and findDescendants(..) on the template finds none.
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		assertEquals(4, testBench.$("dashboard-view").first().$("vaadin-board-row").all().size());
	}

	@Test
	@DisplayName("The dashboard shows four order counters")
	public void checkCounters(UnifiedPage page) {
		openDashboard(page);

		assertThat(page.findTemplate(DashboardView.class).findComponent(DashboardCounterLabel.class)).hasCount(4);
	}

	private static void openDashboard(UnifiedPage page) {
		page.step("Open the dashboard from the menu", () -> {
			page.navigate("");
			page.find(UnifiedTabs.class).selectTab("Dashboard");
			assertThat(page).isAt(DashboardView.class);
		});
	}
}

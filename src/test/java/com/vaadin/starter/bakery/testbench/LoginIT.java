package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.starter.bakery.testbench.elements.ui.DashboardViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.LoginViewElement;
import com.vaadin.starter.bakery.ui.views.dashboard.DashboardView;
import com.vaadin.starter.bakery.ui.views.login.LoginView;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

public class LoginIT extends AbstractIT {

	@Test
	@DisplayName("A barista signs in with email and password and lands on the storefront")
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void loginWorks(UnifiedPage page) {
		page.navigate(LoginView.class);
		// Escape Hatch kept: LoginOverlay has no Wrapper and its form fields exist only in the browser
		LoginViewElement loginView = TestBenchEscapeHatch.of(page).$(LoginViewElement.class).first();
		Assertions.assertEquals("Email", loginView.getUsernameLabel());
		loginView.login("barista@vaadin.com", "barista");

		// isAt(StorefrontView.class) fails on Selenium for a route template with an alias (findings F10)
		assertThat(page.find(UnifiedTabs.class)).hasSelectedTab("Storefront");
	}

	@Test
	@DisplayName("A barista logs out from the menu and sees the login view")
	@WithMockUser(username = "barista@vaadin.com", roles = "barista")
	// the app logs out with Page.setLocation(..), which Browserless doesn't follow
	@RequiresBrowser
	public void logout(UnifiedPage page) {
		page.navigate("storefront");

		// selectTab(..) fails when selecting the tab navigates away (findings F11): click the tab instead
		page.findComponent(Tab.class).withAccessibleName("Logout").element().click();

		assertThat(page).isAt(LoginView.class);
	}

	@Test
	@DisplayName("Signing in after opening the dashboard lands on the dashboard")
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void loginToNotDefaultUrl(UnifiedPage page) {
		page.navigate(DashboardView.class);
		// Escape Hatch kept: LoginOverlay has no Wrapper and its form fields exist only in the browser
		LoginViewElement loginView = TestBenchEscapeHatch.of(page).$(LoginViewElement.class).first();
		loginView.login("admin@vaadin.com", "admin", DashboardViewElement.class);

		assertThat(page).isAt(DashboardView.class);
	}

	@Test
	@DisplayName("A signed-in user who opens the login view is sent to the storefront")
	@WithMockUser(username = "admin@vaadin.com", roles = "admin")
	public void openLoginAfterLoggedIn(UnifiedPage page) {
		page.navigate(LoginView.class);

		// isAt(StorefrontView.class) fails on Selenium for a route template with an alias (findings F10)
		assertThat(page.find(UnifiedTabs.class)).hasSelectedTab("Storefront");
	}

}

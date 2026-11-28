package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.starter.bakery.ui.views.dashboard.DashboardView;
import com.vaadin.starter.bakery.ui.views.login.LoginView;
import com.vaadin.starter.bakery.ui.views.storefront.StorefrontView;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedLogin;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextField;

// These tests check the login form itself, which posts to Spring Security's /login: a browser is needed.
@RequiresBrowser
public class LoginIT extends AbstractIT {

	@Test
	@DisplayName("A barista signs in through the login form and lands on the storefront")
	public void loginWorks(UnifiedPage page) {
		page.navigate(LoginView.class);
		UnifiedLogin login = page.find(UnifiedLogin.class);
		assertThat(login).isOpen();
		assertThat(page.find(UnifiedTextField.class).withLabel("Email")).exists();

		login.login("barista@vaadin.com", "barista");

		assertThat(page).isAt(StorefrontView.class);
	}

	@Test
	@DisplayName("A signed-in barista logs out from the menu and is back at the login")
	public void logout(UnifiedPage page) {
		signIn(page, "barista@vaadin.com", "barista");
		assertThat(page).isAt(StorefrontView.class);

		page.find(UnifiedTabs.class).selectTab("Logout");

		assertThat(page).isAt(LoginView.class);
	}

	@Test
	@DisplayName("Opening the dashboard signed out asks to sign in, then shows the dashboard")
	public void loginToNotDefaultUrl(UnifiedPage page) {
		page.navigate(DashboardView.class);
		assertThat(page).isAt(LoginView.class);

		page.find(UnifiedLogin.class).login("admin@vaadin.com", "admin");

		assertThat(page).isAt(DashboardView.class);
	}

	@Test
	@DisplayName("Opening the login while signed in shows the storefront instead")
	public void openLoginAfterLoggedIn(UnifiedPage page) {
		signIn(page, "admin@vaadin.com", "admin");

		page.navigate(LoginView.class);

		assertThat(page).isAt(StorefrontView.class);
		assertThat(page.find(UnifiedLogin.class)).doesNotExist();
	}

	private static void signIn(UnifiedPage page, String user, String password) {
		page.step("Sign in as " + user, () -> {
			page.navigate(LoginView.class);
			page.find(UnifiedLogin.class).login(user, password);
		});
	}
}

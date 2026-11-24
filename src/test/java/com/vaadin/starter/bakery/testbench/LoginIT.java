package com.vaadin.starter.bakery.testbench;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.vaadin.starter.bakery.testbench.elements.ui.DashboardViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.LoginViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

public class LoginIT extends AbstractIT<LoginViewElement> {

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void loginWorks(UnifiedPage page) throws Exception {
		setup(page);
		LoginViewElement loginView = openLoginView(page);
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		Assertions.assertEquals("Email", loginView.getUsernameLabel());
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		loginView.login("barista@vaadin.com", "barista");
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void logout(UnifiedPage page) throws Exception {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		setup(page);
		LoginViewElement loginView = openLoginView(page);
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		StorefrontViewElement storefront = loginView.login("barista@vaadin.com", "barista");
		storefront.getMenu().logout();
		// TODO unified: TestBench Escape Hatch: getDriver()
		Assertions.assertTrue(testBench.getDriver().getCurrentUrl().endsWith("login"));
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void loginToNotDefaultUrl(UnifiedPage page) throws Exception {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		setup(page);
		// TODO unified: TestBench Escape Hatch: getDriver()
		LoginViewElement loginView = openLoginView(page, testBench.getDriver(), APP_URL + "dashboard");
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		DashboardViewElement dashboard = loginView.login("admin@vaadin.com", "admin", DashboardViewElement.class);
		Assertions.assertNotNull(dashboard);
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void openLoginAfterLoggedIn(UnifiedPage page) throws Exception {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		setup(page);
		loginToNotDefaultUrl(page);
		// Navigating to /login after user is logged in will forward to storefront view
		// TODO unified: navigation to APP_URL + "login": not a local application URL the recipe can read; use page.navigate("<path>")
		// TODO unified: TestBench Escape Hatch: getDriver()
		testBench.getDriver().get(APP_URL + "login");
		// TODO unified: TestBench Escape Hatch: $(StorefrontViewElement.class)
		testBench.$(StorefrontViewElement.class).onPage().waitForFirst();
		// TODO unified: TestBench Escape Hatch: $(LoginViewElement.class)
		Assertions.assertTrue(testBench.$(LoginViewElement.class).all().isEmpty());
	}

	@Override
	protected LoginViewElement openView(UnifiedPage page) {
		return openLoginView(page);
	}

}
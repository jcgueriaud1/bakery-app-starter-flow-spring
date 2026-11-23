package com.vaadin.starter.bakery.testbench;

import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;

import com.vaadin.starter.bakery.testbench.elements.ui.LoginViewElement;
import com.vaadin.starter.bakery.ui.utils.BakeryConst;
import com.vaadin.testbench.IPAddress;
import com.vaadin.testbench.TestBenchDriverProxy;
import com.vaadin.testbench.TestBenchElement;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.UnifiedUITest;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

@UnifiedUITest
public abstract class AbstractIT<E extends TestBenchElement> {
	public String APP_URL = "http://localhost:8080/";

	static {
		// Let notifications persist longer during tests
		BakeryConst.NOTIFICATION_DURATION = 10000;
	}

	void setup(UnifiedPage page) throws Exception {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                getDriver().manage().window().setSize(new Dimension(1024, 800));
		// TODO unified: TestBench Escape Hatch: getRunLocallyBrowser()
		if (testBench.getRunLocallyBrowser() == null) {
			APP_URL = "http://" + IPAddress.findSiteLocalAddress() + ":8080/";
		}
	}

	@Override
	public TestBenchDriverProxy getDriver() {
		return (TestBenchDriverProxy) super.getDriver();
	}

	protected LoginViewElement openLoginView(UnifiedPage page) {
		return openLoginView(page, getDriver(), APP_URL);
	}

	protected LoginViewElement openLoginView(UnifiedPage page, WebDriver driver, String url) {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		driver.get(url);
		// TODO unified: TestBench Escape Hatch: $(LoginViewElement.class)
		return testBench.$(LoginViewElement.class).waitForFirst();
	}

	protected abstract E openView();

}

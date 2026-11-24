package com.vaadin.starter.bakery.testbench;

import org.openqa.selenium.Dimension;

import com.vaadin.starter.bakery.testbench.elements.ui.LoginViewElement;
import com.vaadin.starter.bakery.ui.utils.BakeryConst;
import com.vaadin.testbench.TestBenchDriverProxy;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

// TODO unified: the tests ran against a server started outside the test; they now start the app themselves, on a random port and with its test configuration (@UnifiedSpringBootTest); the build's spring-boot:start/stop executions can be removed (recipe option springBootTest)
@UnifiedSpringBootTest
public abstract class AbstractIT {
	static {
		// Let notifications persist longer during tests
		BakeryConst.NOTIFICATION_DURATION = 10000;
	}

	void setup(UnifiedPage page) throws Exception {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
                // TODO unified: TestBench Escape Hatch: getDriver()
                testBench.getDriver().manage().window().setSize(new Dimension(1024, 800));
	}

	public TestBenchDriverProxy getDriver(UnifiedPage page) {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		// TODO unified: TestBench Escape Hatch: super.getDriver()
		return (TestBenchDriverProxy) testBench.getDriver();
	}

	protected LoginViewElement openLoginView(UnifiedPage page) {
		return openLoginView(page, "");
	}

	protected LoginViewElement openLoginView(UnifiedPage page, String path) {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		page.navigate(path);
		// TODO unified: TestBench Escape Hatch: $(LoginViewElement.class)
		return testBench.$(LoginViewElement.class).waitForFirst();
	}

}

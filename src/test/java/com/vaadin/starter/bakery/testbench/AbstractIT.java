package com.vaadin.starter.bakery.testbench;

import org.openqa.selenium.Dimension;

import com.vaadin.starter.bakery.testbench.elements.ui.LoginViewElement;
import com.vaadin.starter.bakery.ui.utils.BakeryConst;
import com.vaadin.testbench.TestBenchElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

@UnifiedSpringBootTest
// the tests still use the TestBench page objects in elements/
@RequiresTestingTool(TestingTool.SELENIUM)
public abstract class AbstractIT<E extends TestBenchElement> {

	static {
		// Let notifications persist longer during tests
		BakeryConst.NOTIFICATION_DURATION = 10000;
	}

	void setup(UnifiedPage page) {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		// TODO unified: TestBench Escape Hatch: getDriver()
		testBench.getDriver().manage().window().setSize(new Dimension(1024, 800));
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

	protected abstract E openView(UnifiedPage page);

}

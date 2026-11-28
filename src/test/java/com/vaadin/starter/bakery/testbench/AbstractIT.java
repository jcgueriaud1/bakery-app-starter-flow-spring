package com.vaadin.starter.bakery.testbench;

import com.vaadin.starter.bakery.ui.utils.BakeryConst;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

/**
 * Base of the UI tests: every test class shares this one Spring context, which starts the app on a random port
 * (on Playwright and Selenium) or in the JVM (Browserless).
 */
@UnifiedSpringBootTest
public abstract class AbstractIT {

	static {
		// Let notifications persist longer during tests. Since the app now runs in the test's JVM, this takes effect
		// (it didn't against the server the build started).
		BakeryConst.NOTIFICATION_DURATION = 10000;
	}
}

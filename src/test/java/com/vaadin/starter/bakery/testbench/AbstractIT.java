package com.vaadin.starter.bakery.testbench;

import com.vaadin.starter.bakery.ui.utils.BakeryConst;
import com.vaadin.testing.unified.spring.UnifiedSpringBootTest;

/**
 * One Spring context and server for every UI test class.
 */
@UnifiedSpringBootTest
public abstract class AbstractIT {

	static {
		// Let notifications persist longer during tests
		BakeryConst.NOTIFICATION_DURATION = 10000;
	}

}

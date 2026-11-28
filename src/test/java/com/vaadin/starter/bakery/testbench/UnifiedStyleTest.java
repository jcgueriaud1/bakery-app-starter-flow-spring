package com.vaadin.starter.bakery.testbench;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.vaadin.testing.unified.archunit.UnifiedStyleRules;

class UnifiedStyleTest {

	@Test
	@DisplayName("Every Unified UI Test follows the style rules")
	void followsTheUnifiedStyle() {
		UnifiedStyleRules.ALL.check(new ClassFileImporter().importPackages("com.vaadin.starter.bakery"));
	}
}

package com.vaadin.starter.bakery.testbench.wrappers;

import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;

/** Flow's {@code EmailField}, which the Core Set has no Wrapper for (findings F13). */
@UnifiedComponent(EmailField.class)
public interface UnifiedEmailField
		extends UnifiedWrapper, CommonFilters<UnifiedEmailField>, LabelFilter<UnifiedEmailField> {

	default void setValue(String value) {
		element().setValueAsUser(value);
	}

	default String getValue() {
		return element().getProperty("value");
	}
}

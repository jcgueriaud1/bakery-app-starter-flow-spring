package com.vaadin.starter.bakery.testbench.wrappers;

import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;
import com.vaadin.testing.unified.query.LabelFilter;

/** Flow's {@code PasswordField}, which the Core Set has no Wrapper for (findings F13). */
@UnifiedComponent(PasswordField.class)
public interface UnifiedPasswordField
		extends UnifiedWrapper, CommonFilters<UnifiedPasswordField>, LabelFilter<UnifiedPasswordField> {

	default void setValue(String value) {
		element().setValueAsUser(value);
	}

	default String getValue() {
		return element().getProperty("value");
	}
}

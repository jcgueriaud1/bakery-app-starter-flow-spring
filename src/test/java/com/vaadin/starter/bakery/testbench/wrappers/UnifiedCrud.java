package com.vaadin.starter.bakery.testbench.wrappers;

import com.vaadin.flow.component.crud.Crud;
import com.vaadin.testing.unified.UnifiedComponent;
import com.vaadin.testing.unified.UnifiedWrapper;
import com.vaadin.testing.unified.query.CommonFilters;

/**
 * Flow's {@code Crud}, which the Core Set has no Wrapper for (findings F13). Its edit buttons are drawn in the
 * browser, so the tests that use it need a browser.
 */
@UnifiedComponent(Crud.class)
public interface UnifiedCrud extends UnifiedWrapper, CommonFilters<UnifiedCrud> {

	/** Clicks the edit button of the grid row with this index. */
	default void editRow(int row) {
		element().findDescendants("vaadin-crud-edit").get(row).click();
	}

	default boolean isEditorOpen() {
		return Boolean.TRUE.equals(element().getPropertyBoolean("editorOpened"));
	}
}

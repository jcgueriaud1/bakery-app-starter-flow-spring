package com.vaadin.starter.bakery.testbench;

import java.util.Optional;

import org.vaadin.addons.dramafinder.element.GridElement;

import com.microsoft.playwright.Page;
import com.vaadin.starter.bakery.ui.components.SearchBar;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedGrid.UnifiedGridRow;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

/**
 * Lookups and User Tasks shared by the views built on {@code AbstractBakeryCrudView} (Products, Users).
 * <p>
 * These views use Flow's {@code Crud}, which needs a browser: its Save, Cancel and Delete buttons get their text and
 * their click handling from the {@code vaadin-crud} web component, so on Browserless they have no name and clicking
 * them does nothing. Tests using these tasks declare {@code @RequiresBrowser}.
 */
final class CrudTasks {

	private CrudTasks() {
	}

	/** The search bar's "New …" button; its text is set by the template, so it is found by its {@code @Id}. */
	static UnifiedButton newItemButton(UnifiedPage page) {
		return page.findTemplate(SearchBar.class).find(UnifiedButton.class).withId("action");
	}

	// The Crud's confirm dialogs (and the main layout's) have their own hidden Cancel/Delete buttons, hence visible().
	static UnifiedButton saveButton(UnifiedPage page) {
		return page.find(UnifiedButton.class).withText("Save").visible();
	}

	static UnifiedButton cancelButton(UnifiedPage page) {
		return page.find(UnifiedButton.class).withText("Cancel").visible();
	}

	static UnifiedButton deleteButton(UnifiedPage page) {
		return page.find(UnifiedButton.class).withText("Delete").visible();
	}

	/**
	 * Presses the Crud's edit button (the pencil) on a row.
	 * <p>
	 * Escape Hatch kept: the pencil is drawn by the {@code vaadin-crud} renderer in a column without a header. It is
	 * not a Flow component, so no Unified lookup finds it, and {@code row.cell(..)} needs a header. Each browser
	 * Testing Tool clicks it with its own API. The Playwright click doesn't wait for the server: check what the
	 * editor shows before typing into it.
	 */
	static void openForEditing(UnifiedPage page, UnifiedGridRow row) {
		page.step("Edit " + row, () -> {
			int index = row.getIndex();
			int editColumn = page.find(UnifiedGrid.class).getColumnHeaders().size() - 1;
			Optional<Page> playwright = page.tryUnwrap(Page.class);
			if (playwright.isPresent()) {
				GridElement.get(playwright.get()).findCell(index, editColumn).orElseThrow()
						.getCellContentLocator().locator("vaadin-crud-edit").click();
			} else {
				TestBenchEscapeHatch.of(page).$(com.vaadin.flow.component.grid.testbench.GridElement.class).first()
						.getCell(index, editColumn).$("vaadin-crud-edit").first().click();
			}
		});
	}
}

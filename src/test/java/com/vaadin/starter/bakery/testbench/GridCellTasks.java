package com.vaadin.starter.bakery.testbench;

import java.util.Optional;

import org.vaadin.addons.dramafinder.element.GridElement;

import com.microsoft.playwright.Page;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

/**
 * Escape Hatch kept, browsers only: clicks an element that a grid's LitRenderer draws in a cell (Crud's edit
 * pencil, the storefront's order card). Such an element is not a Flow component and its column has no header, so
 * no Unified lookup reaches it ({@code row.cell(..)} needs a header). Each browser Testing Tool clicks it with its
 * own API. A Playwright click doesn't wait for the server: check what the click shows before typing.
 */
final class GridCellTasks {

	private GridCellTasks() {
	}

	/**
	 * @param hostTag the tag of the element whose shadow root holds the grid, or {@code null} for the page's grid
	 * @param innerClass a class of the element's part that listens to the click, or {@code null} for the element
	 */
	static void clickInCell(UnifiedPage page, String hostTag, int row, int column, String elementTag,
			String innerClass) {
		String selector = innerClass == null ? elementTag : elementTag + " ." + innerClass;
		Optional<Page> playwright = page.tryUnwrap(Page.class);
		if (playwright.isPresent()) {
			// Playwright's CSS engine pierces open shadow roots, so the page's first grid is the one we mean
			GridElement.get(playwright.get()).findCell(row, column).orElseThrow()
					.getCellContentLocator().locator(selector).first().click();
			return;
		}
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		com.vaadin.flow.component.grid.testbench.GridElement grid = hostTag == null
				? testBench.$(com.vaadin.flow.component.grid.testbench.GridElement.class).first()
				: testBench.$(hostTag).first().$(com.vaadin.flow.component.grid.testbench.GridElement.class).first();
		var element = grid.getCell(row, column).$(elementTag).first();
		(innerClass == null ? element : element.$("*").withAttributeContainingWord("class", innerClass).first()).click();
	}
}

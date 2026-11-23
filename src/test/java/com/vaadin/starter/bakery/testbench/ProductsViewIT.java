package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.vaadin.starter.bakery.testbench.elements.ui.ProductsViewElement;
import com.vaadin.starter.bakery.ui.views.admin.products.ProductsView;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

// Escape Hatch kept: "New product" is @Id-mapped in the SearchBar LitTemplate, which no Unified lookup finds;
// the Crud's editor state, edit column and its own buttons and confirm dialogs have no Wrapper
@RequiresTestingTool(TestingTool.SELENIUM)
@WithMockUser(username = "admin@vaadin.com", roles = "admin")
public class ProductsViewIT extends AbstractIT {

	private static Random r = new Random();

	private static ProductsViewElement openView(UnifiedPage page) {
		page.navigate(ProductsView.class);
		return TestBenchEscapeHatch.of(page).$(ProductsViewElement.class).onPage().first();
	}

	private static UnifiedTextField productName(UnifiedPage page) {
		return page.find(UnifiedTextField.class).withLabel("Product name");
	}

	private static UnifiedTextField price(UnifiedPage page) {
		return page.find(UnifiedTextField.class).withLabel("Unit price");
	}

	@Test
	@DisplayName("A product renamed twice keeps its row in the grid")
	public void editProductTwice(UnifiedPage page) {
		ProductsViewElement productsPage = openView(page);
		UnifiedGrid products = page.find(UnifiedGrid.class);

		String uniqueName = "Unique cake name " + r.nextInt();
		String initialPrice = "98.76";
		int rowNum = createProduct(page, productsPage, uniqueName, initialPrice);
		productsPage.openRowForEditing(rowNum);

		Assertions.assertTrue(productsPage.getCrud().isEditorOpen());
		String newValue = "New " + uniqueName;
		productName(page).setValue(newValue);

		productsPage.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(productsPage.getCrud().isEditorOpen());
		assertThat(products.row(rowNum)).hasCellText("Product Name", newValue);

		productsPage.openRowForEditing(rowNum);
		newValue = "The " + newValue;
		productName(page).setValue(newValue);

		productsPage.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(productsPage.getCrud().isEditorOpen());
		assertThat(products.row(rowNum)).hasCellText("Product Name", newValue);
	}

	@Test
	@DisplayName("A product's new price is saved and shown when the product is opened again")
	public void editProduct(UnifiedPage page) {
		ProductsViewElement productsPage = openView(page);
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);

		String uniqueName = "Unique cake name " + r.nextInt();
		String initialPrice = "98.76";
		int rowIndex = createProduct(page, productsPage, uniqueName, initialPrice);

		productsPage.openRowForEditing(rowIndex);
		// Escape Hatch kept: the Unified API checks a whole path (hasPath) and the product id isn't known here
		Assertions.assertTrue(testBench.getDriver().getCurrentUrl().matches(".*/products/\\d+$"));

		Assertions.assertTrue(productsPage.getCrud().isEditorOpen());

		assertThat(price(page)).hasValue(initialPrice);

		price(page).setValue("123.45");

		productsPage.getCrud().getEditorSaveButton().click();

		Assertions.assertFalse(productsPage.getCrud().isEditorOpen());

		assertThat(page).hasPath("products");

		productsPage.openRowForEditing(rowIndex);

		assertThat(price(page)).hasValue("123.45");

		// Return initial value
		price(page).setValue(initialPrice);

		productsPage.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(productsPage.getCrud().isEditorOpen());
	}

	@Test
	@DisplayName("Cancelling a new product with changes asks to discard them")
	public void testCancelConfirmationMessage(UnifiedPage page) {
		ProductsViewElement productsPage = openView(page);

		productsPage.getCrud().getNewItemButton().get().click();
		Assertions.assertTrue(productsPage.getCrud().isEditorOpen());
		productName(page).setValue("Some name");
		productsPage.getCrud().getEditorCancelButton().click();
		Assertions.assertEquals(productsPage.getDiscardConfirmDialog().getHeaderText(), "Discard changes");
	}

	private int createProduct(UnifiedPage page, ProductsViewElement productsPage, String name, String price) {
		productsPage.getSearchBar().getCreateNewButton().click();

		Assertions.assertTrue(productsPage.getCrud().isEditorOpen());

		productName(page).setValue(name);
		price(page).setValue(price);

		productsPage.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(productsPage.getCrud().isEditorOpen());

		UnifiedGrid.UnifiedGridRow row = page.find(UnifiedGrid.class).row().withCellText("Product Name", name);
		assertThat(row).exists();
		return row.getIndex();
	}

}

package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import com.vaadin.starter.bakery.ui.views.admin.products.ProductsView;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedGrid.UnifiedGridRow;
import com.vaadin.testing.unified.component.UnifiedTextField;

// Flow's Crud needs a browser (see CrudTasks)
@RequiresBrowser
@WithUserDetails("admin@vaadin.com")
public class ProductsViewIT extends AbstractIT {

	private static final Random r = new Random();

	@Test
	@DisplayName("An admin renames a new product twice and it keeps its place in the grid")
	public void editProductTwice(UnifiedPage page) {
		// given
		page.navigate(ProductsView.class);
		UnifiedGrid products = page.find(UnifiedGrid.class);
		String uniqueName = "Unique cake name " + r.nextInt();
		createProduct(page, uniqueName, "98.76");
		int rowNum = productRow(products, uniqueName).getIndex();

		// when
		String newName = "New " + uniqueName;
		renameProduct(page, uniqueName, newName);

		// then
		assertThat(productName(page).visible()).doesNotExist();
		assertThat(products.row(rowNum)).hasCellText("Product Name", newName);

		// when
		String newerName = "The " + newName;
		renameProduct(page, newName, newerName);

		// then
		assertThat(productName(page).visible()).doesNotExist();
		assertThat(products.row(rowNum)).hasCellText("Product Name", newerName);
	}

	@Test
	@DisplayName("An admin changes a product's price, sees it saved, and changes it back")
	public void editProduct(UnifiedPage page) {
		// given
		page.navigate(ProductsView.class);
		UnifiedGrid products = page.find(UnifiedGrid.class);
		String uniqueName = "Unique cake name " + r.nextInt();
		String initialPrice = "98.76";
		createProduct(page, uniqueName, initialPrice);

		// when
		CrudTasks.openForEditing(page, productRow(products, uniqueName));

		// then
		assertThat(price(page)).isVisible().hasValue(initialPrice);

		// when
		price(page).setValue("123.45");
		CrudTasks.saveButton(page).click();

		// then
		assertThat(price(page).visible()).doesNotExist();
		assertThat(page).hasPath("products");
		assertThat(productRow(products, uniqueName)).hasCellText("Unit Price", "$123.45");

		// when
		CrudTasks.openForEditing(page, productRow(products, uniqueName));

		// then
		assertThat(price(page)).hasValue("123.45");

		// Return initial value
		price(page).setValue(initialPrice);
		CrudTasks.saveButton(page).click();
		assertThat(price(page).visible()).doesNotExist();
	}

	@Test
	@DisplayName("Cancelling a new product with a name typed in asks to discard the changes")
	public void testCancelConfirmationMessage(UnifiedPage page) {
		page.navigate(ProductsView.class);

		CrudTasks.newItemButton(page).click();
		assertThat(productName(page)).isVisible();
		productName(page).setValue("Some name");
		CrudTasks.cancelButton(page).click();

		assertThat(page.find(UnifiedConfirmDialog.class).withHeaderTitle("Discard changes")).isOpen();
	}

	private static UnifiedTextField productName(UnifiedPage page) {
		return page.find(UnifiedTextField.class).withLabel("Product name");
	}

	private static UnifiedTextField price(UnifiedPage page) {
		return page.find(UnifiedTextField.class).withLabel("Unit price");
	}

	private static UnifiedGridRow productRow(UnifiedGrid products, String name) {
		return products.row().withCellText("Product Name", name);
	}

	private static void createProduct(UnifiedPage page, String name, String price) {
		page.step("Create product " + name, () -> {
			CrudTasks.newItemButton(page).click();
			productName(page).setValue(name);
			price(page).setValue(price);
			CrudTasks.saveButton(page).click();
			assertThat(productName(page).visible()).doesNotExist();
			assertThat(page.find(UnifiedGrid.class).row().withCellText("Product Name", name)).exists();
		});
	}

	private static void renameProduct(UnifiedPage page, String name, String newName) {
		page.step("Rename product " + name + " to " + newName, () -> {
			CrudTasks.openForEditing(page, productRow(page.find(UnifiedGrid.class), name));
			// the editor shows the product once the server has loaded it; typing earlier gets overwritten
			assertThat(productName(page)).isVisible().hasValue(name);
			productName(page).setValue(newName);
			CrudTasks.saveButton(page).click();
		});
	}
}

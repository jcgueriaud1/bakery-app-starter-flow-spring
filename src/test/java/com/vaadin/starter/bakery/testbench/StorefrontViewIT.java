package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import java.util.Optional;
import java.util.Random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithUserDetails;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.starter.bakery.backend.data.entity.Order;
import com.vaadin.starter.bakery.ui.components.SearchBar;
import com.vaadin.starter.bakery.ui.views.orderedit.OrderDetails;
import com.vaadin.starter.bakery.ui.views.orderedit.OrderEditor;
import com.vaadin.starter.bakery.ui.views.orderedit.OrderItemEditor;
import com.vaadin.starter.bakery.ui.views.storefront.StorefrontView;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedButton;
import com.vaadin.testing.unified.component.UnifiedDialog;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedIntegerField;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTemplate;
import com.vaadin.testing.unified.component.UnifiedTextField;

/*
 * The storefront, its dialog and the order editor/details are LitTemplates: their components are found by entering
 * each template, by @Id where the text is written in the template's HTML (empty on Browserless).
 */
@WithUserDetails("admin@vaadin.com")
public class StorefrontViewIT extends AbstractIT {

	@Test
	@Disabled
	@DisplayName("Adding one more of an order's first product updates its card")
	public void editOrder(UnifiedPage page) {
		page.navigate(StorefrontView.class);
		int orderIndex = 1;
		openOrder(page, orderIndex);
		orderDetails(page).find(UnifiedButton.class).withId("edit").click();
		int initialCount = amount(page).getValue();

		amount(page).setValue(initialCount + 1);
		orderEditor(page).find(UnifiedButton.class).withId("review").click();
		orderDetails(page).find(UnifiedButton.class).withId("save").click();

		assertThat(page.find(UnifiedNotification.class).withTextContaining("was updated")).isOpen();
		openOrder(page, orderIndex);
		orderDetails(page).find(UnifiedButton.class).withId("edit").click();
		assertThat(amount(page)).hasValue(initialCount + 1);
	}

	@Test
	@DisplayName("The new-order editor and an order's details open and close, also after visiting another view")
	public void testDialogs(UnifiedPage page) {
		page.navigate(StorefrontView.class);
		openAndCloseAllDialogs(page);

		page.find(UnifiedTabs.class).selectTab("Users");
		page.find(UnifiedTabs.class).selectTab("Storefront");

		openAndCloseAllDialogs(page);
	}

	@Test
	@DisplayName("The order editor shows an error for a too long or an empty customer field")
	public void testTextFieldValidation(UnifiedPage page) {
		page.navigate(StorefrontView.class);
		openOrder(page, new Random().nextInt(10));
		orderDetails(page).find(UnifiedButton.class).withId("edit").click();

		UnifiedTextField customerName = orderEditor(page).find(UnifiedTextField.class).withLabel("Customer");
		assertRejectsOverflow(customerName);
		customerName.setValue("");
		assertThat(customerName).hasErrorMessage("must not be blank");
		assertRejectsOverflow(orderEditor(page).find(UnifiedTextField.class).withLabel("Additional Details"));
		assertRejectsOverflow(firstOrderItem(page).find(UnifiedTextField.class).withId("comment"));
	}

	private static void openAndCloseAllDialogs(UnifiedPage page) {
		page.step("Open and close the new-order editor and an order's details", () -> {
			for (int i = 0; i < 2; i++) {
				storefront(page).findTemplate(SearchBar.class).find(UnifiedButton.class).withId("action").click();
				assertThat(dialog(page)).isOpen();
				orderEditor(page).find(UnifiedButton.class).withId("cancel").click();
				assertThat(dialog(page)).isClosed();
			}

			openOrder(page, 0);
			assertThat(orderDetails(page)).isVisible();
			orderDetails(page).find(UnifiedButton.class).withId("cancel").click();
			assertThat(dialog(page)).isClosed();
		});
	}

	private static void assertRejectsOverflow(UnifiedTextField field) {
		field.setValue("A".repeat(256));
		assertThat(field).isInvalid().hasErrorMessage("maximum length is 255 characters");
	}

	/**
	 * Clicks the order's card in the grid.
	 * <p>
	 * Escape Hatch kept: the card is drawn by a LitRenderer, and its click calls the renderer function
	 * {@code cardClick}. In a browser it is clicked through {@link GridCellTasks} (a row click doesn't reach it).
	 * Browserless can't fire a renderer function through the Unified API; {@code cardClick} navigates to the
	 * order's route, so the test goes there.
	 */
	private static void openOrder(UnifiedPage page, int index) {
		page.step("Open order card " + index, () -> {
			UnifiedGrid orders = storefront(page).find(UnifiedGrid.class);
			Optional<Grid> serverSide = orders.tryUnwrap(Grid.class);
			if (serverSide.isPresent()) {
				Order order = (Order) serverSide.get().getDataCommunicator().getItem(index);
				page.navigate("storefront/" + order.getId());
			} else {
				GridCellTasks.clickInCell(page, "storefront-view", index, 0, "order-card", "wrapper");
			}
			assertThat(dialog(page)).isOpen();
		});
	}

	private static UnifiedTemplate storefront(UnifiedPage page) {
		return page.findTemplate(StorefrontView.class);
	}

	private static UnifiedDialog dialog(UnifiedPage page) {
		return storefront(page).find(UnifiedDialog.class);
	}

	private static UnifiedTemplate orderEditor(UnifiedPage page) {
		return dialog(page).findTemplate(OrderEditor.class);
	}

	private static UnifiedTemplate orderDetails(UnifiedPage page) {
		return dialog(page).findTemplate(OrderDetails.class);
	}

	private static UnifiedTemplate firstOrderItem(UnifiedPage page) {
		return orderEditor(page).findTemplate(OrderItemEditor.class).all().get(0);
	}

	private static UnifiedIntegerField amount(UnifiedPage page) {
		return firstOrderItem(page).find(UnifiedIntegerField.class).withId("amount");
	}
}

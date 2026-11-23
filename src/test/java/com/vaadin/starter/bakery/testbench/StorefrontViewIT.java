package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.starter.bakery.testbench.elements.components.OrderCardElement;
import com.vaadin.starter.bakery.testbench.elements.ui.OrderItemEditorElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement.OrderEditorElement;
import com.vaadin.starter.bakery.ui.views.admin.users.UsersView;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedNotification;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

// Escape Hatch kept: the order grid, its order cards, the search bar and the order dialog are @Id-mapped
// in StorefrontView's LitTemplate (cards drawn by a LitRenderer): no Testing Tool's Unified lookup finds them
@RequiresTestingTool(TestingTool.SELENIUM)
@WithMockUser(username = "admin@vaadin.com", roles = "admin")
public class StorefrontViewIT extends AbstractIT {

	private static StorefrontViewElement openView(UnifiedPage page) {
		// navigate(StorefrontView.class) opens "storefront/:orderID" on Selenium (findings F10)
		page.navigate("storefront");
		return storefront(page);
	}

	private static StorefrontViewElement storefront(UnifiedPage page) {
		return TestBenchEscapeHatch.of(page).$(StorefrontViewElement.class).waitForFirst();
	}

	@Test
	@Disabled
	@DisplayName("Adding one item to an order updates the order card")
	public void editOrder(UnifiedPage page) {
		StorefrontViewElement storefrontPage = openView(page);

		int orderIndex = 1;

		OrderCardElement order = storefrontPage.getOrderCard(orderIndex);
		Assertions.assertNotNull(order);
		int initialCount = Integer.parseInt(order.getGoodsCount(0));

		order.click();
		ButtonElement editBtn = storefrontPage.getOrderDetails().getEditButton();
		editBtn.click();
		// Escape Hatch kept: the Unified API checks a whole path (hasPath) and the order id isn't known here
		TestBenchEscapeHatch.of(page)
				.waitUntil(driver -> driver.getCurrentUrl().matches("^(.+)\\/(storefront)\\/[0-9]+\\/(edit)$"));

		OrderEditorElement orderEditor = storefrontPage.getOrderEditor();
		orderEditor.getOrderItemEditor(0).clickAmountFieldPlus();

		orderEditor.review();
		storefrontPage.getOrderDetails().getSaveButton().click();

		assertThat(page.find(UnifiedNotification.class).withTextContaining("was updated")).isOpen();

		order = storefrontPage.getOrderCard(orderIndex);
		Assertions.assertNotNull(order);
		int currentCount = Integer.parseInt(order.getGoodsCount(0));
		Assertions.assertEquals(initialCount + 1, currentCount);

	}

	@Test
	@DisplayName("The new-order and order-details dialogs open and close, also after visiting another view")
	public void testDialogs(UnifiedPage page) {
		StorefrontViewElement storefrontPage = openView(page);
		openAllDialogs(storefrontPage);

		// the menu's tabs hold RouterLinks: selectTab(..) selects a tab without following its link (findings F12)
		page.findComponent(RouterLink.class).withAccessibleName("Users").element().click();
		assertThat(page).isAt(UsersView.class);
		page.findComponent(RouterLink.class).withAccessibleName("Storefront").element().click();
		assertThat(page.find(UnifiedTabs.class)).hasSelectedTab("Storefront");

		openAllDialogs(storefront(page));
	}

	private void openAllDialogs(StorefrontViewElement storefrontPage) {
		storefrontPage.getSearchBar().getCreateNewButton().click();
		Assertions.assertTrue(storefrontPage.getDialog().get().isOpen());
		storefrontPage.getOrderEditor().cancel();
		Assertions.assertFalse(storefrontPage.getDialog().get().isOpen());

		storefrontPage.getSearchBar().getCreateNewButton().click();
		Assertions.assertTrue(storefrontPage.getDialog().get().isOpen());

		storefrontPage.getOrderEditor().cancel();
		Assertions.assertFalse(storefrontPage.getDialog().get().isOpen());

		OrderCardElement order = storefrontPage.getOrderCard(0);
		Assertions.assertNotNull(order);
		order.click();

		Assertions.assertTrue(storefrontPage.getOrderDetails().isDisplayed());

		storefrontPage.getOrderDetails().getCancelButton().click();
		Assertions.assertFalse(storefrontPage.getDialog().get().isOpen());
	}

	@Test
	@DisplayName("The order editor rejects a customer name, details or comment longer than 255 characters, and an empty name")
	public void testTextFieldValidation(UnifiedPage page) {
		StorefrontViewElement storefrontPage = openView(page);

		int orderIndex = new Random().nextInt(10);

		OrderCardElement order = storefrontPage.getOrderCard(orderIndex);
		Assertions.assertNotNull(order);
		order.click();

		ButtonElement editBtn = storefrontPage.getOrderDetails().getEditButton();
		editBtn.click();

		OrderEditorElement orderEditor = storefrontPage.getOrderEditor();
		TextFieldElement customerNameField = orderEditor.getCustomerNameField();
		testFieldOverflow(customerNameField);
		testClearRequiredField(customerNameField);
		testFieldOverflow(orderEditor.getCustomerDetailsField());

		OrderItemEditorElement firstOrderItemEditor = orderEditor.getOrderItemEditor(0);
		testFieldOverflow(firstOrderItemEditor.getCommentField());
	}

	private void testFieldOverflow(TextFieldElement textFieldElement) {
		textFieldElement.setValue(IntStream.range(0, 256).mapToObj(i -> "A").collect(Collectors.joining()));
		String msg = getErrorMessage(textFieldElement);
		Assertions.assertTrue(msg.matches("(maximum length is 255 characters|size must be between 0 and 255)"));
	}

	private void testClearRequiredField(TextFieldElement textFieldElement) {
		textFieldElement.setValue("");
		Assertions.assertEquals("must not be blank", getErrorMessage(textFieldElement));
	}

	private String getErrorMessage(TextFieldElement textFieldElement) {
		return textFieldElement.getPropertyString("errorMessage");
	}
}

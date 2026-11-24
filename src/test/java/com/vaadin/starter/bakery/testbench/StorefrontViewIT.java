package com.vaadin.starter.bakery.testbench;

import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.testbench.ButtonElement;
import com.vaadin.flow.component.notification.testbench.NotificationElement;
import com.vaadin.flow.component.textfield.testbench.TextFieldElement;
import com.vaadin.starter.bakery.testbench.elements.components.OrderCardElement;
import com.vaadin.starter.bakery.testbench.elements.ui.OrderItemEditorElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement.OrderEditorElement;
import com.vaadin.starter.bakery.testbench.elements.ui.UsersViewElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

public class StorefrontViewIT extends AbstractIT<StorefrontViewElement> {

	@Override
	protected StorefrontViewElement openView(UnifiedPage page) {
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		return openLoginView(page).login("admin@vaadin.com", "admin");
	}

	@Test
	@Disabled
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void editOrder(UnifiedPage page) throws Exception {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		setup(page);
		StorefrontViewElement storefrontPage = openView(page);

		int orderIndex = 1;

		OrderCardElement order = storefrontPage.getOrderCard(orderIndex);
		Assertions.assertNotNull(order);
		int initialCount = Integer.parseInt(order.getGoodsCount(0));

		order.click();
		ButtonElement editBtn = storefrontPage.getOrderDetails().getEditButton();
		editBtn.click();
		// TODO unified: TestBench Escape Hatch: waitUntil(driver -> driver.getCurrentUrl().matches("^(.+)\\/(storefront)\\/[0-9]+\\/(edit)$"))
		testBench.waitUntil(driver -> driver.getCurrentUrl().matches("^(.+)\\/(storefront)\\/[0-9]+\\/(edit)$"));

		OrderEditorElement orderEditor = storefrontPage.getOrderEditor();
		orderEditor.getOrderItemEditor(0).clickAmountFieldPlus();

		orderEditor.review();
		storefrontPage.getOrderDetails().getSaveButton().click();

		// TODO unified: TestBench Escape Hatch: $(NotificationElement.class)
		NotificationElement notification = testBench.$(NotificationElement.class).last();
		Assertions.assertTrue(notification.getText().contains("was updated"));

		order = storefrontPage.getOrderCard(orderIndex);
		Assertions.assertNotNull(order);
		int currentCount = Integer.parseInt(order.getGoodsCount(0));
		Assertions.assertEquals(initialCount + 1, currentCount);

	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void testDialogs(UnifiedPage page) throws Exception {
		setup(page);
		StorefrontViewElement storefrontPage = openView(page);
		openAllDialogs(storefrontPage);

		UsersViewElement usersPage = storefrontPage.getMenu().navigateToUsers();
		storefrontPage = usersPage.getMenu().navigateToStorefront();

		openAllDialogs(storefrontPage);
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
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void testTextFieldValidation(UnifiedPage page) throws Exception {
		setup(page);
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

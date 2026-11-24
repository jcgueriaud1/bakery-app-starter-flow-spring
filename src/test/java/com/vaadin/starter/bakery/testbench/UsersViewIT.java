package com.vaadin.starter.bakery.testbench;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.tabs.testbench.TabElement;
import com.vaadin.flow.component.textfield.testbench.EmailFieldElement;
import com.vaadin.flow.component.textfield.testbench.PasswordFieldElement;
import com.vaadin.starter.bakery.testbench.elements.ui.StorefrontViewElement;
import com.vaadin.starter.bakery.testbench.elements.ui.UsersViewElement;
import com.vaadin.testbench.TestBenchElement;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

import org.junit.jupiter.api.parallel.Isolated;

@Isolated
public class UsersViewIT extends AbstractIT<UsersViewElement> {

	private static Random r = new Random();

	@Override
	protected UsersViewElement openView(UnifiedPage page) {
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		StorefrontViewElement storefront = openLoginView(page).login("admin@vaadin.com", "admin");
		return storefront.getMenu().navigateToUsers();
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void updatePassword(UnifiedPage page) throws Exception {
		setup(page);
		UsersViewElement usersView = openView(page);

		Assertions.assertFalse(usersView.getCrud().isEditorOpen());

		String uniqueEmail = "e" + r.nextInt() + "@vaadin.com";

		createUser(usersView, uniqueEmail, "Paul", "Irwin", "Vaadin10", "baker");

		int rowNum = usersView.getCrud().getGrid().getCell(uniqueEmail).getRow();
		usersView.openRowForEditing(rowNum);

		Assertions.assertTrue(usersView.getCrud().isEditorOpen());

		Assertions.assertTrue(usersView.getCrud().isEditorOpen());

		// When opening form the password value must be always empty
		PasswordFieldElement password = usersView.getPasswordField();
		Assertions.assertEquals("", password.getValue());

		// Saving any field without changing password should save and close
		EmailFieldElement emailField = usersView.getEmailField();
		String newEmail = "foo" + r.nextInt() + "@bar.com";
		emailField.setValue(newEmail);

		usersView.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(usersView.getCrud().isEditorOpen());

		// Invalid password prevents closing form
		rowNum = usersView.getCrud().getGrid().getCell(newEmail).getRow();
		usersView.openRowForEditing(rowNum);

		emailField = usersView.getEmailField(); // Requery email field.
		password = usersView.getPasswordField(); // Requery password field.

		emailField.setValue(uniqueEmail);
		password.setValue("123");

		usersView.getCrud().getEditorSaveButton().click();

		Assertions.assertTrue(usersView.getCrud().isEditorOpen());

		password = usersView.getPasswordField(); // Requery password field.

		// Good password
		password.setValue("Abc123");
		usersView.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(usersView.getCrud().isEditorOpen());

		// When reopening the form password field must be empty.
		rowNum = usersView.getCrud().getGrid().getCell(uniqueEmail).getRow();
		usersView.openRowForEditing(rowNum);

		password = usersView.getPasswordField(); // Requery password field.
		Assertions.assertEquals("", password.getDomProperty("value"));
	}

	private void createUser(UsersViewElement usersView, String email, String firstName, String lastName,
			String password, String role) {
		usersView.getSearchBar().getCreateNewButton().click();
		Assertions.assertTrue(usersView.getCrud().isEditorOpen());

		usersView.getEmailField().setValue(email);
		usersView.getFirstName().setValue(firstName);
		usersView.getLastName().setValue(lastName);
		usersView.getPasswordField().setValue(password);

		usersView.getRole().selectByText(role);

		usersView.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(usersView.getCrud().isEditorOpen());
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void tryToUpdateLockedEntity(UnifiedPage unifiedPage) throws Exception {
		setup(unifiedPage);
		UsersViewElement page = openView(unifiedPage);

		int rowNum = page.getCrud().getGrid().getCell("barista@vaadin.com").getRow();
		page.openRowForEditing(rowNum);

		PasswordFieldElement field = page.getPasswordField();
		field.setValue("Abc123");
		page.getEmailField().setValue("barista123@vaadin.com");
		page.getCrud().getEditorSaveButton().click();

		Assertions.assertEquals(rowNum, page.getCrud().getGrid().getCell("barista@vaadin.com").getRow());
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void tryToDeleteLockedEntity(UnifiedPage unifiedPage) throws Exception {
		setup(unifiedPage);
		UsersViewElement page = openView(unifiedPage);

		int rowNum = page.getCrud().getGrid().getCell("barista@vaadin.com").getRow();
		page.openRowForEditing(rowNum);

		Assertions.assertTrue(page.getCrud().isEditorOpen());

		page.getCrud().getEditorDeleteButton().click();
		page.getDeleteConfirmDialog().getConfirmButton().click();

		Assertions.assertEquals(rowNum, page.getCrud().getGrid().getCell("barista@vaadin.com").getRow());
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void testCancelConfirmationMessage(UnifiedPage unifiedPage) throws Exception {
		setup(unifiedPage);
		UsersViewElement page = openView(unifiedPage);
		page.getSearchBar().getCreateNewButton().click();
		page.getFirstName().setValue("Some name");
		page.getCrud().getEditorCancelButton().click();

		Assertions.assertEquals(page.getDiscardConfirmDialog().getHeaderText(), "Discard changes");
	}

	@Test
	@RequiresTestingTool(TestingTool.SELENIUM)
	public void accessDenied(UnifiedPage page) throws Exception {
		TestBenchEscapeHatch testBench = TestBenchEscapeHatch.of(page);
		setup(page);
		// TODO unified: signs in through the login form: unless this test checks the login itself, consider @WithMockUser; to keep the form, page.find(UnifiedLogin.class).login(username, password)
		StorefrontViewElement storefront = openLoginView(page).login("barista@vaadin.com", "barista");
		Assertions.assertEquals(3, storefront.getMenu().$(TabElement.class).all().size());

		page.navigate("users");
		// TODO unified: TestBench Escape Hatch: $("vaadin-app-layout > div > a")
		TestBenchElement link404 = testBench.$("vaadin-app-layout > div > a").waitForFirst();

		Assertions.assertEquals("Go to the front page.", link404.getText());
	}
}

package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.security.test.context.support.WithMockUser;

import com.vaadin.flow.component.textfield.testbench.EmailFieldElement;
import com.vaadin.flow.component.textfield.testbench.PasswordFieldElement;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.starter.bakery.testbench.elements.ui.UsersViewElement;
import com.vaadin.starter.bakery.ui.views.admin.users.UsersView;
import com.vaadin.testing.unified.RequiresTestingTool;
import com.vaadin.testing.unified.TestingTool;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextField;
import com.vaadin.testing.unified.selenium.TestBenchEscapeHatch;

@Isolated
public class UsersViewIT extends AbstractIT {

	private static Random r = new Random();

	private static UsersViewElement openView(UnifiedPage page) {
		page.navigate(UsersView.class);
		return TestBenchEscapeHatch.of(page).$(UsersViewElement.class).onPage().waitForFirst();
	}

	private static UnifiedGrid users(UnifiedPage page) {
		return page.find(UnifiedGrid.class);
	}

	private static int rowOf(UnifiedPage page, String email) {
		UnifiedGrid.UnifiedGridRow row = users(page).row().withCellText("Email", email);
		assertThat(row).exists();
		return row.getIndex();
	}

	@Test
	@DisplayName("A user's password is never shown, a weak one is refused and a strong one is saved")
	// Escape Hatch kept: "New user" is @Id-mapped in the SearchBar LitTemplate; EmailField and PasswordField
	// have no Wrapper; the Crud's editor state, edit column and buttons have no Wrapper
	@RequiresTestingTool(TestingTool.SELENIUM)
	@WithMockUser(username = "admin@vaadin.com", roles = "admin")
	public void updatePassword(UnifiedPage page) {
		UsersViewElement usersView = openView(page);

		Assertions.assertFalse(usersView.getCrud().isEditorOpen());

		String uniqueEmail = "e" + r.nextInt() + "@vaadin.com";

		createUser(page, usersView, uniqueEmail, "Paul", "Irwin", "Vaadin10", "baker");

		usersView.openRowForEditing(rowOf(page, uniqueEmail));

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
		usersView.openRowForEditing(rowOf(page, newEmail));

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
		usersView.openRowForEditing(rowOf(page, uniqueEmail));

		password = usersView.getPasswordField(); // Requery password field.
		Assertions.assertEquals("", password.getDomProperty("value"));
	}

	private void createUser(UnifiedPage page, UsersViewElement usersView, String email, String firstName,
			String lastName, String password, String role) {
		usersView.getSearchBar().getCreateNewButton().click();
		Assertions.assertTrue(usersView.getCrud().isEditorOpen());

		usersView.getEmailField().setValue(email);
		page.find(UnifiedTextField.class).withLabel("First name").setValue(firstName);
		page.find(UnifiedTextField.class).withLabel("Last name").setValue(lastName);
		usersView.getPasswordField().setValue(password);

		page.find(UnifiedComboBox.class).withLabel("Role").selectItem(role);

		usersView.getCrud().getEditorSaveButton().click();
		Assertions.assertFalse(usersView.getCrud().isEditorOpen());
	}

	@Test
	@DisplayName("Changing the email of a user who is signed in elsewhere is refused")
	// Escape Hatch kept: EmailField and PasswordField have no Wrapper; the Crud's edit column and buttons have none
	@RequiresTestingTool(TestingTool.SELENIUM)
	@WithMockUser(username = "admin@vaadin.com", roles = "admin")
	public void tryToUpdateLockedEntity(UnifiedPage page) {
		UsersViewElement usersView = openView(page);

		int rowNum = rowOf(page, "barista@vaadin.com");
		usersView.openRowForEditing(rowNum);

		PasswordFieldElement field = usersView.getPasswordField();
		field.setValue("Abc123");
		usersView.getEmailField().setValue("barista123@vaadin.com");
		usersView.getCrud().getEditorSaveButton().click();

		assertThat(users(page).row(rowNum)).hasCellText("Email", "barista@vaadin.com");
	}

	@Test
	@DisplayName("Deleting a user who is signed in elsewhere is refused")
	// Escape Hatch kept: the Crud's edit column, its Delete button and its confirm dialog have no Wrapper
	@RequiresTestingTool(TestingTool.SELENIUM)
	@WithMockUser(username = "admin@vaadin.com", roles = "admin")
	public void tryToDeleteLockedEntity(UnifiedPage page) {
		UsersViewElement usersView = openView(page);

		int rowNum = rowOf(page, "barista@vaadin.com");
		usersView.openRowForEditing(rowNum);

		Assertions.assertTrue(usersView.getCrud().isEditorOpen());

		usersView.getCrud().getEditorDeleteButton().click();
		usersView.getDeleteConfirmDialog().getConfirmButton().click();

		assertThat(users(page).row(rowNum)).hasCellText("Email", "barista@vaadin.com");
	}

	@Test
	@DisplayName("Cancelling a new user with changes asks to discard them")
	// Escape Hatch kept: "New user" is @Id-mapped in the SearchBar LitTemplate; the Crud's buttons and confirm
	// dialogs have no Wrapper
	@RequiresTestingTool(TestingTool.SELENIUM)
	@WithMockUser(username = "admin@vaadin.com", roles = "admin")
	public void testCancelConfirmationMessage(UnifiedPage page) {
		UsersViewElement usersView = openView(page);
		usersView.getSearchBar().getCreateNewButton().click();
		page.find(UnifiedTextField.class).withLabel("First name").setValue("Some name");
		usersView.getCrud().getEditorCancelButton().click();

		Assertions.assertEquals(usersView.getDiscardConfirmDialog().getHeaderText(), "Discard changes");
	}

	@Test
	@DisplayName("A barista sees no admin views in the menu and gets a not-found page at the users view")
	@WithMockUser(username = "barista@vaadin.com", roles = "barista")
	public void accessDenied(UnifiedPage page) {
		page.navigate("storefront");
		assertThat(page.find(UnifiedTabs.class)).hasTabs("Storefront", "Dashboard", "Logout");

		page.navigate("users");

		assertThat(page.findComponent(RouterLink.class).withAccessibleName("Go to the front page.")).isVisible();
	}
}

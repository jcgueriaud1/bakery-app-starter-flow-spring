package com.vaadin.starter.bakery.testbench;

import static com.vaadin.testing.unified.assertion.UnifiedAssertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.security.test.context.support.WithUserDetails;

import com.vaadin.flow.router.RouterLink;
import com.vaadin.starter.bakery.ui.views.admin.users.UsersView;
import com.vaadin.testing.unified.RequiresBrowser;
import com.vaadin.testing.unified.UnifiedPage;
import com.vaadin.testing.unified.component.UnifiedComboBox;
import com.vaadin.testing.unified.component.UnifiedConfirmDialog;
import com.vaadin.testing.unified.component.UnifiedEmailField;
import com.vaadin.testing.unified.component.UnifiedGrid;
import com.vaadin.testing.unified.component.UnifiedGrid.UnifiedGridRow;
import com.vaadin.testing.unified.component.UnifiedPasswordField;
import com.vaadin.testing.unified.component.UnifiedTabs;
import com.vaadin.testing.unified.component.UnifiedTextField;

@Isolated
@WithUserDetails("admin@vaadin.com")
public class UsersViewIT extends AbstractIT {

	private static final Random r = new Random();

	@Test
	@RequiresBrowser // Flow's Crud needs a browser (see CrudTasks)
	@DisplayName("An admin edits a new user: the password field starts empty, a weak password is refused, a good one is saved")
	public void updatePassword(UnifiedPage page) {
		// given
		page.navigate(UsersView.class);
		assertThat(firstName(page).visible()).doesNotExist();
		String uniqueEmail = "e" + r.nextInt() + "@vaadin.com";
		createUser(page, uniqueEmail, "Paul", "Irwin", "Vaadin10", "baker");

		// when
		openUser(page, uniqueEmail);

		// then: the password is never shown
		assertThat(password(page)).isEmpty();

		// when: saving another field without touching the password
		String newEmail = "foo" + r.nextInt() + "@bar.com";
		email(page).setValue(newEmail);
		CrudTasks.saveButton(page).click();

		// then
		assertThat(firstName(page).visible()).doesNotExist();

		// when: an invalid password
		openUser(page, newEmail);
		email(page).setValue(uniqueEmail);
		password(page).setValue("123");
		CrudTasks.saveButton(page).click();

		// then: the editor stays open
		assertThat(password(page)).isVisible().isInvalid();

		// when: a good password
		password(page).setValue("Abc123");
		CrudTasks.saveButton(page).click();

		// then
		assertThat(firstName(page).visible()).doesNotExist();
		openUser(page, uniqueEmail);
		assertThat(password(page)).isEmpty();
	}

	@Test
	@RequiresBrowser
	@DisplayName("A locked user can't be changed: the barista keeps their email")
	public void tryToUpdateLockedEntity(UnifiedPage page) {
		page.navigate(UsersView.class);
		UnifiedGrid users = page.find(UnifiedGrid.class);
		int rowNum = userRow(page, "barista@vaadin.com").getIndex();

		openUser(page, "barista@vaadin.com");
		password(page).setValue("Abc123");
		email(page).setValue("barista123@vaadin.com");
		CrudTasks.saveButton(page).click();

		assertThat(users.row(rowNum)).hasCellText("Email", "barista@vaadin.com");
	}

	@Test
	@RequiresBrowser
	@DisplayName("A locked user can't be deleted: the barista stays in the grid")
	public void tryToDeleteLockedEntity(UnifiedPage page) {
		page.navigate(UsersView.class);
		UnifiedGrid users = page.find(UnifiedGrid.class);
		int rowNum = userRow(page, "barista@vaadin.com").getIndex();

		openUser(page, "barista@vaadin.com");
		CrudTasks.deleteButton(page).click();
		page.find(UnifiedConfirmDialog.class).visible().confirm();

		assertThat(users.row(rowNum)).hasCellText("Email", "barista@vaadin.com");
	}

	@Test
	@RequiresBrowser
	@DisplayName("Cancelling a new user with a name typed in asks to discard the changes")
	public void testCancelConfirmationMessage(UnifiedPage page) {
		page.navigate(UsersView.class);

		CrudTasks.newItemButton(page).click();
		firstName(page).setValue("Some name");
		CrudTasks.cancelButton(page).click();

		assertThat(page.find(UnifiedConfirmDialog.class).withHeaderTitle("Discard changes")).isOpen();
	}

	@Test
	@WithUserDetails("barista@vaadin.com")
	@DisplayName("A barista sees no admin menu entries and gets a not-found page at the users URL")
	public void accessDenied(UnifiedPage page) {
		page.navigate("");
		assertThat(page.find(UnifiedTabs.class)).hasTabs("Storefront", "Dashboard", "Logout");

		page.navigate("users");

		assertThat(page).hasTitle("Page was not found");
		// page.find(UnifiedLink.class) throws on Browserless when the page holds a Flow Text component (see findings)
		assertThat(page.findComponent(RouterLink.class).withText("Go to the front page.")).exists();
	}

	private static UnifiedEmailField email(UnifiedPage page) {
		return page.find(UnifiedEmailField.class).withLabel("Email (login)");
	}

	private static UnifiedTextField firstName(UnifiedPage page) {
		return page.find(UnifiedTextField.class).withLabel("First name");
	}

	private static UnifiedTextField lastName(UnifiedPage page) {
		return page.find(UnifiedTextField.class).withLabel("Last name");
	}

	private static UnifiedPasswordField password(UnifiedPage page) {
		return page.find(UnifiedPasswordField.class).withLabel("Password");
	}

	private static UnifiedGridRow userRow(UnifiedPage page, String email) {
		return page.find(UnifiedGrid.class).row().withCellText("Email", email);
	}

	private static void openUser(UnifiedPage page, String email) {
		page.step("Open user " + email, () -> {
			CrudTasks.openForEditing(page, userRow(page, email));
			// the editor shows the user once the server has loaded it; typing earlier gets overwritten
			assertThat(email(page)).isVisible().hasValue(email);
		});
	}

	private static void createUser(UnifiedPage page, String email, String first, String last, String pwd,
			String role) {
		page.step("Create user " + email, () -> {
			CrudTasks.newItemButton(page).click();
			email(page).setValue(email);
			firstName(page).setValue(first);
			lastName(page).setValue(last);
			password(page).setValue(page.secret(pwd));
			page.find(UnifiedComboBox.class).withLabel("Role").selectItem(role);
			CrudTasks.saveButton(page).click();
			assertThat(firstName(page).visible()).doesNotExist();
			assertThat(userRow(page, email)).exists();
		});
	}
}

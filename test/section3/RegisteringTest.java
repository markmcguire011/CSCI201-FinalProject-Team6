package section3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import edu.usc.marketplace.model.AccountType;
import edu.usc.marketplace.model.RegisterForm;
import edu.usc.marketplace.service.Validator;

import org.junit.jupiter.api.Test;

/**
 * Testing Plan 3b: Registering. Each test starts from a valid form and changes one field.
 */
class RegisteringTest {

    private static RegisterForm validForm() {
        return new RegisterForm("Tommy Trojan", "ttrojan@usc.edu", "fighton123", "fighton123", AccountType.STUDENT);
    }

    /** 3b-U1 */
    @Test
    void validFormPasses() {
        assertTrue(Validator.validateRegistration(validForm()).isEmpty());
    }

    /** 3b-U2 */
    @Test
    void fiftyCharacterNameIsAllowed() {
        RegisterForm form = validForm();
        form.fullName = "a".repeat(50);
        assertFalse(Validator.validateRegistration(form).containsKey("fullName"));
    }

    /** 3b-U3 */
    @Test
    void fiftyOneCharacterNameIsRejected() {
        RegisterForm form = validForm();
        form.fullName = "a".repeat(51);
        assertEquals("Enter your full name (up to 50 characters)",
                Validator.validateRegistration(form).get("fullName"));
    }

    /** 3b-U4 */
    @Test
    void blankNameIsRejected() {
        RegisterForm form = validForm();
        form.fullName = "   ";
        assertTrue(Validator.validateRegistration(form).containsKey("fullName"));
    }

    /** 3b-U5 */
    @Test
    void nonUscEmailIsRejected() {
        RegisterForm form = validForm();
        form.email = "tommy@gmail.com";
        assertEquals("Use your USC email address", Validator.validateRegistration(form).get("email"));
    }

    /** 3b-U6 */
    @Test
    void uscEmailInCapitalLettersIsAccepted() {
        RegisterForm form = validForm();
        form.email = "TTrojan@USC.EDU";
        assertFalse(Validator.validateRegistration(form).containsKey("email"));
    }

    /** 3b-U7 */
    @Test
    void emailWithNothingBeforeAtIsRejected() {
        RegisterForm form = validForm();
        form.email = "@usc.edu";
        assertTrue(Validator.validateRegistration(form).containsKey("email"));
    }

    /** 3b-U8 */
    @Test
    void passwordLengthBoundary() {
        RegisterForm form = validForm();
        form.password = "abc1234";
        form.confirmPassword = "abc1234";
        assertEquals("Password must be at least 8 characters",
                Validator.validateRegistration(form).get("password"));
        form.password = "abc12345";
        form.confirmPassword = "abc12345";
        assertFalse(Validator.validateRegistration(form).containsKey("password"));
    }

    /** 3b-U9 */
    @Test
    void passwordsThatDoNotMatchAreRejected() {
        RegisterForm form = validForm();
        form.confirmPassword = "fighton124";
        assertEquals("Passwords do not match", Validator.validateRegistration(form).get("confirmPassword"));
    }

    /** 3b-U10 */
    @Test
    void missingAccountTypeIsRejected() {
        RegisterForm form = validForm();
        form.accountType = null;
        assertTrue(Validator.validateRegistration(form).containsKey("accountType"));
    }

    /** 3b-U11 */
    @Test
    void everyBadFieldGetsItsOwnError() {
        RegisterForm form = new RegisterForm("", "x@gmail.com", "short", "different", null);
        assertEquals(5, Validator.validateRegistration(form).size());
    }
}

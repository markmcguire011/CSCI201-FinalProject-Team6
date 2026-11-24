package edu.usc.marketplace.model;

/** What the Register page sends. Checked by Validator.validateRegistration(). */
public class RegisterForm {
    public String fullName;
    public String email;
    public String password;
    public String confirmPassword;
    public AccountType accountType;

    public RegisterForm() {
    }

    public RegisterForm(String fullName, String email, String password, String confirmPassword, AccountType accountType) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.accountType = accountType;
    }
}

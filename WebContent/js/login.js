
/**
 * Login page for USC Marketplace.
 * Sends credentials to LoginServlet through api.js.
 */
(() => {
    const form = document.getElementById("login-form");
    const button = document.getElementById("login-button");
    const errorMessage = document.getElementById("login-error");

    const LOGIN_URL = "api/login";
    const DEFAULT_PAGE = "index.html";

    function showError(message) {
        errorMessage.textContent = message;
        errorMessage.hidden = false;
    }

    function clearError() {
        errorMessage.textContent = "";
        errorMessage.hidden = true;
    }

    /**
     * Return to the requested page after login.
     * Only allow HTML pages within this application.
     */
    function getDestination() {
        const params = new URLSearchParams(window.location.search);
        const next = params.get("next");

        if (!next || next.includes("\\")) {
            return DEFAULT_PAGE;
        }

        try {
            const destination = new URL(next, window.location.href);
            const appPath = new URL(".", window.location.href).pathname;

            if (destination.origin !== window.location.origin ||
                !destination.pathname.startsWith(appPath) ||
                !destination.pathname.endsWith(".html") ||
                destination.pathname === appPath + "login.html") {
                return DEFAULT_PAGE;
            }

            return destination.href;
        } catch (error) {
            return DEFAULT_PAGE;
        }
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        clearError();

        const email = form.elements.namedItem("email").value.trim();
        const password = form.elements.namedItem("password").value;

        if (!email || !password) {
            showError("Enter your email and password.");
            return;
        }

        button.disabled = true;
        button.textContent = "Logging in...";

        try {
            await Api.post(LOGIN_URL, {
                email: email,
                password: password
            }, { silent: true });

            Toast.flash("Logged in.");
            window.location.href = getDestination();

        } catch (error) {
            showError(error.message);
        } finally {
            button.disabled = false;
            button.textContent = "Log In";
        }
    });
})();

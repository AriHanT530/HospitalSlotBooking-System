package com.hospital.ui;

import com.hospital.model.User;
import com.hospital.service.ServiceRegistry;
import com.hospital.util.ConsoleInput;
import com.hospital.util.TableFormatter;

/**
 * Abstract menu (Unit 2: abstract class + template method pattern).
 * The loop, the header and the error handling are written once here; each
 * role subclass only declares its options and how to handle a choice.
 */
public abstract class RoleMenu {

    protected final ServiceRegistry registry;

    protected RoleMenu(ServiceRegistry registry) { this.registry = registry; }

    protected abstract String title();
    protected abstract String[] options();
    /** @return false to exit the menu loop (logout). */
    protected abstract boolean handle(String choice);

    protected User currentUser() { return registry.getAuthService().getCurrentUser(); }

    /** Template method - fixed algorithm, variable steps. */
    public final void show() {
        boolean active = true;
        while (active) {
            TableFormatter.header(title() + " - " + currentUser().getFullName());
            String[] opts = options();
            for (int i = 0; i < opts.length; i++) {
                System.out.printf("  %d. %s%n", i + 1, opts[i]);
            }
            System.out.println("  0. Logout");
            String choice = ConsoleInput.ask("\nSelect an option: ");
            if ("0".equals(choice)) {
                active = false;
            } else {
                try {
                    active = handle(choice);
                } catch (RuntimeException unexpected) {
                    // last line of defence so a bug never kills the session
                    TableFormatter.error("Unexpected error: " + unexpected.getMessage());
                    com.hospital.util.AppLogger.getInstance().error("Menu failure", unexpected);
                }
                if (active) ConsoleInput.pause();
            }
        }
    }
}

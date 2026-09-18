package com.hospital.web;

import com.hospital.exception.HospitalException;
import com.hospital.model.Appointment;
import com.hospital.model.AppointmentStatus;
import com.hospital.model.Doctor;
import com.hospital.model.Patient;
import com.hospital.model.Prescription;
import com.hospital.model.Role;
import com.hospital.model.Slot;
import com.hospital.model.Specialization;
import com.hospital.model.User;
import com.hospital.service.ServiceRegistry;
import com.hospital.util.AppLogger;
import com.hospital.util.Validator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Browser front end for the hospital system, built entirely on
 * {@code com.sun.net.httpserver} - part of the standard JDK, so the project
 * still needs nothing beyond {@code javac}/{@code java} to run.
 *
 * This class is a second presentation layer next to {@code com.hospital.ui}.
 * Every request is routed to the same {@link ServiceRegistry} the console
 * app uses, so booking rules, validation, locking and persistence are
 * completely unchanged - only how a person interacts with them differs.
 */
public class WebServer {

    private final ServiceRegistry registry;
    private final SessionManager sessions = new SessionManager();
    private final AppLogger log = AppLogger.getInstance();

    public WebServer(ServiceRegistry registry) { this.registry = registry; }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::route);
        server.start();
        log.info("Web server listening on http://localhost:" + port);
        System.out.println("Web server running:  http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    /* ---------------------------------------------------------------- routing ---- */

    private void route(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            switch (path) {
                case "/"                      -> home(ex);
                case "/login"                 -> { if (method.equals("POST")) doLogin(ex); else loginPage(ex, null, null); }
                case "/logout"                -> doLogout(ex);
                case "/register"              -> { if (method.equals("POST")) doRegister(ex); else registerPage(ex, Map.of()); }
                case "/account/password"      -> { if (method.equals("POST")) doChangePassword(ex); else passwordPage(ex); }

                case "/patient"                -> patientHome(ex);
                case "/patient/slots"          -> patientSlots(ex);
                case "/patient/book"           -> { if (method.equals("POST")) doBook(ex); else bookForm(ex); }
                case "/patient/appointments"   -> patientAppointments(ex);
                case "/patient/cancel"         -> { if (method.equals("POST")) doCancel(ex, "/patient/appointments"); }
                case "/patient/reschedule"     -> { if (method.equals("POST")) doReschedule(ex); else rescheduleForm(ex); }
                case "/patient/prescriptions"  -> patientPrescriptions(ex);

                case "/doctor"                 -> doctorSchedule(ex);
                case "/doctor/complete"        -> { if (method.equals("POST")) doComplete(ex); }
                case "/doctor/cancel"          -> { if (method.equals("POST")) doCancel(ex, "/doctor"); }
                case "/doctor/prescribe"       -> { if (method.equals("POST")) doPrescribe(ex); else prescribeForm(ex); }
                case "/doctor/earnings"        -> doctorEarnings(ex);

                case "/admin"                  -> adminHome(ex);
                case "/admin/doctors"          -> { if (method.equals("POST")) doAddDoctor(ex); else adminDoctors(ex); }
                case "/admin/patients"         -> adminPatients(ex);
                case "/admin/toggle"           -> { if (method.equals("POST")) doToggle(ex); }
                case "/admin/appointments"     -> adminAppointments(ex);
                case "/admin/analytics"        -> adminAnalytics(ex);
                case "/admin/export"           -> doExport(ex);
                case "/admin/logs"             -> adminLogs(ex);

                default -> WebUtil.sendHtml(ex, 404, Html.page("Not found", currentUser(ex), null, null,
                        "<div class='card'><h2>404</h2><p>No such page.</p><a href='/'>Go home</a></div>"));
            }
        } catch (HospitalException e) {
            safeRedirectBack(ex, e.getMessage());
        } catch (Exception e) {
            log.error("Unhandled web error on " + path, e);
            try {
                WebUtil.sendHtml(ex, 500, Html.page("Error", currentUser(ex), e.getMessage(), "error",
                        "<div class='card'><h2>Something went wrong</h2><p>" + Html.escape(e.getMessage())
                        + "</p><a href='/'>Go home</a></div>"));
            } catch (IOException ignored) { }
        }
    }

    /** On a business-rule failure, bounce back to the referring page with the message as a flash. */
    private void safeRedirectBack(HttpExchange ex, String message) {
        try {
            String referer = ex.getRequestHeaders().getFirst("Referer");
            String fallback = currentUser(ex) == null ? "/login" : "/" + currentUser(ex).getRole().name().toLowerCase();
            WebUtil.redirect(ex, referer != null ? stripQuery(referer) : fallback, message, "error");
        } catch (IOException ignored) { }
    }

    private String stripQuery(String url) {
        int i = url.indexOf('?');
        String path = i >= 0 ? url.substring(0, i) : url;
        int hostIdx = path.indexOf("://");
        if (hostIdx >= 0) {
            int slash = path.indexOf('/', hostIdx + 3);
            path = slash >= 0 ? path.substring(slash) : "/";
        }
        return path;
    }

    /* --------------------------------------------------------------- session ---- */

    private User currentUser(HttpExchange ex) {
        return sessions.get(WebUtil.cookie(ex, "SID"));
    }

    private User requireLogin(HttpExchange ex, Role... allowed) throws IOException {
        User user = currentUser(ex);
        if (user == null) {
            WebUtil.redirect(ex, "/login", "Please log in first.", "error");
            return null;
        }
        if (allowed.length > 0) {
            boolean ok = false;
            for (Role r : allowed) if (user.getRole() == r) ok = true;
            if (!ok) {
                WebUtil.redirect(ex, "/", "You are not allowed to view that page.", "error");
                return null;
            }
        }
        return user;
    }

    private Map<String, String> flash(HttpExchange ex) {
        return WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
    }

    /* ------------------------------------------------------------ public pages -- */

    private void home(HttpExchange ex) throws IOException {
        User user = currentUser(ex);
        if (user != null) {
            WebUtil.redirect(ex, "/" + user.getRole().name().toLowerCase(), null, null);
            return;
        }
        String body = "<div class='center card'>"
                + "<h1>Hospital Slot Booking</h1>"
                + "<p class='muted'>Book, manage and track outpatient appointments.</p>"
                + "<p><a href='/login'><button>Login</button></a> "
                + "<a href='/register'><button class='secondary'>Register as patient</button></a></p>"
                + "</div>";
        WebUtil.sendHtml(ex, 200, Html.page("Welcome", null, null, null, body));
    }

    private void loginPage(HttpExchange ex, String prefillUser, String error) throws IOException {
        Map<String, String> f = flash(ex);
        String body = "<div class='center card'><h1>Login</h1>"
                + "<form method='post' action='/login'>"
                + Html.field("Username", "username", "text", prefillUser == null ? "" : prefillUser, "e.g. arjun")
                + Html.field("Password", "password", "password", "", "")
                + Html.button("Login")
                + "</form>"
                + "<p class='muted'>New patient? <a href='/register'>Register here</a>.</p>"
                + "<p class='muted'>Demo: admin/admin123 &middot; dr.mehta/doctor123 &middot; arjun/patient123</p>"
                + "</div>";
        WebUtil.sendHtml(ex, 200, Html.page("Login", null, f.get("msg"), f.get("mt"), body));
    }

    private void doLogin(HttpExchange ex) throws IOException {
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            User user = registry.getAuthService().login(form.get("username"), form.get("password"));
            String token = sessions.createSession(user);
            WebUtil.setCookie(ex, "SID", token);
            WebUtil.redirect(ex, "/" + user.getRole().name().toLowerCase(),
                    "Welcome, " + user.getFullName() + "!", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/login", e.getMessage(), "error");
        }
    }

    private void doLogout(HttpExchange ex) throws IOException {
        sessions.invalidate(WebUtil.cookie(ex, "SID"));
        WebUtil.clearCookie(ex, "SID");
        WebUtil.redirect(ex, "/", "Logged out.", "ok");
    }

    private void registerPage(HttpExchange ex, Map<String, String> old) throws IOException {
        Map<String, String> f = flash(ex);
        String body = "<div class='center card'><h1>Patient Registration</h1>"
                + "<form method='post' action='/register'>"
                + Html.field("Username", "username", "text", Html.val(old, "username"), "3-20 chars")
                + Html.field("Password", "password", "password", "", "min 6 chars")
                + Html.field("Full name", "fullName", "text", Html.val(old, "fullName"), "")
                + Html.field("Phone", "phone", "text", Html.val(old, "phone"), "10 digits")
                + Html.field("Age", "age", "number", Html.val(old, "age"), "")
                + Html.select("Gender", "gender", List.of(
                        new String[]{"M", "Male"}, new String[]{"F", "Female"}, new String[]{"O", "Other"}))
                + Html.field("Blood group", "bloodGroup", "text", Html.val(old, "bloodGroup"), "e.g. O+")
                + Html.button("Register")
                + "</form></div>";
        WebUtil.sendHtml(ex, 200, Html.page("Register", null, f.get("msg"), f.get("mt"), body));
    }

    private void doRegister(HttpExchange ex) throws IOException {
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            int age = Validator.requireAge(form.get("age"));
            Patient p = registry.getAuthService().registerPatient(form.get("username"), form.get("password"),
                    form.get("fullName"), form.get("phone"), age, form.get("gender"), form.get("bloodGroup"));
            WebUtil.redirect(ex, "/login", "Registered as " + p.getId() + ". You can log in now.", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/register", e.getMessage(), "error");
        }
    }

    private void passwordPage(HttpExchange ex) throws IOException {
        User user = requireLogin(ex);
        if (user == null) return;
        Map<String, String> f = flash(ex);
        String body = Html.card("Change password",
                "<form method='post' action='/account/password'>"
                + Html.field("Current password", "oldPassword", "password", "", "")
                + Html.field("New password", "newPassword", "password", "", "min 6 chars")
                + Html.button("Update password")
                + "</form>");
        WebUtil.sendHtml(ex, 200, Html.page("Password", user, f.get("msg"), f.get("mt"), body));
    }

    private void doChangePassword(HttpExchange ex) throws IOException {
        User user = requireLogin(ex);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            registry.getAuthService().changePassword(form.get("oldPassword"), form.get("newPassword"));
            WebUtil.redirect(ex, "/account/password", "Password updated.", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/account/password", e.getMessage(), "error");
        }
    }

    /* ------------------------------------------------------------ patient pages - */

    private void patientHome(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        List<Doctor> doctors = registry.getUserRepository().findDoctors();

        StringBuilder rows = new StringBuilder();
        for (Doctor d : doctors) {
            if (!d.isActive()) continue;
            rows.append("<tr><td>").append(Html.escape(d.getId())).append("</td><td>Dr. ")
                .append(Html.escape(d.getFullName())).append("</td><td>")
                .append(Html.escape(d.getSpecialization().getDisplayName())).append("</td><td>")
                .append(Html.escape(d.getRoomNo())).append("</td><td>")
                .append(d.getWorkStart()).append(" - ").append(d.getWorkEnd()).append("</td><td>INR ")
                .append(String.format("%.2f", d.getConsultationFee())).append("</td><td>")
                .append("<a href='/patient/slots?doctorId=").append(d.getId())
                .append("'><button>View slots</button></a></td></tr>");
        }
        String table = doctors.isEmpty() ? Html.emptyState("No doctors registered yet.")
                : Html.table(new String[]{"Id", "Name", "Specialization", "Room", "Hours", "Fee", ""}, rows);

        Map<String, String> f = flash(ex);
        String body = "<h1>Find a doctor</h1>" + Html.card("Doctors", table);
        WebUtil.sendHtml(ex, 200, Html.page("Patient", user, f.get("msg"), f.get("mt"), body));
    }

    private void patientSlots(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        String doctorId = q.get("doctorId");
        Doctor doctor = registry.getUserRepository().findDoctorById(doctorId).orElse(null);
        if (doctor == null) { WebUtil.redirect(ex, "/patient", "Doctor not found.", "error"); return; }

        LocalDate date = parseDateOrDefault(q.get("date"), LocalDate.now().plusDays(1));
        List<Slot> slots = registry.getSlotService().getSlots(doctor, date);

        StringBuilder grid = new StringBuilder();
        for (Slot s : slots) {
            String kind = s.statusLabel().toLowerCase();
            grid.append("<tr><td>").append(s.getStart()).append(" - ").append(s.getEnd()).append("</td><td>")
                .append(Html.badge(s.statusLabel(), kind)).append("</td><td>");
            if (s.isBookable()) {
                grid.append("<a href='/patient/book?doctorId=").append(doctor.getId())
                    .append("&date=").append(date).append("&time=").append(s.getStart())
                    .append("'><button>Book</button></a>");
            }
            grid.append("</td></tr>");
        }
        String table = slots.isEmpty() ? Html.emptyState("This doctor has no slots configured.")
                : Html.table(new String[]{"Time", "Status", ""}, grid);

        String dateForm = "<form method='get' action='/patient/slots' class='inline'>"
                + Html.hidden("doctorId", doctorId)
                + Html.field("Date", "date", "date", date.toString(), "")
                + Html.button("View")
                + "</form>";

        String body = "<div class='linkbar'><a href='/patient'>&larr; Back to doctors</a></div>"
                + "<h1>Dr. " + Html.escape(doctor.getFullName()) + " - " + Html.escape(date.toString()) + "</h1>"
                + Html.card("Choose a date", dateForm)
                + Html.card("Slots", table);
        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Slots", user, f.get("msg"), f.get("mt"), body));
    }

    private void bookForm(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        Doctor doctor = registry.getUserRepository().findDoctorById(q.get("doctorId")).orElse(null);
        if (doctor == null) { WebUtil.redirect(ex, "/patient", "Doctor not found.", "error"); return; }

        String body = "<div class='linkbar'><a href='/patient/slots?doctorId=" + doctor.getId()
                + "'>&larr; Back to slots</a></div>"
                + Html.card("Confirm booking",
                    "<p>Dr. " + Html.escape(doctor.getFullName()) + " &middot; " + Html.escape(q.get("date"))
                    + " at " + Html.escape(q.get("time")) + " &middot; Fee INR "
                    + String.format("%.2f", doctor.getConsultationFee()) + "</p>"
                    + "<form method='post' action='/patient/book'>"
                    + Html.hidden("doctorId", doctor.getId())
                    + Html.hidden("date", q.get("date"))
                    + Html.hidden("time", q.get("time"))
                    + Html.field("Reason for visit", "reason", "text", "", "General consultation")
                    + Html.button("Confirm booking")
                    + "</form>");
        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Book", user, f.get("msg"), f.get("mt"), body));
    }

    private void doBook(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            LocalDate date = LocalDate.parse(form.get("date"));
            LocalTime time = LocalTime.parse(form.get("time"));
            Appointment a = registry.getBookingService().book(user.getId(), form.get("doctorId"),
                    date, time, form.get("reason"));
            WebUtil.redirect(ex, "/patient/appointments", "Booked! Reference " + a.getId() + ".", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/patient/slots?doctorId=" + form.get("doctorId") + "&date=" + form.get("date"),
                    e.getMessage(), "error");
        }
    }

    private void patientAppointments(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        List<Appointment> list = registry.getAppointmentRepository().findByPatient(user.getId());

        StringBuilder rows = new StringBuilder();
        for (Appointment a : list) {
            rows.append("<tr><td>").append(Html.escape(a.getId())).append("</td><td>")
                .append(a.getDate()).append(" ").append(a.getStartTime()).append("</td><td>")
                .append(Html.escape(doctorName(a.getDoctorId()))).append("</td><td>")
                .append(Html.badge(a.getStatus().name(), a.getStatus().name().toLowerCase())).append("</td><td>")
                .append("INR ").append(String.format("%.2f", a.calculateBill())).append("</td><td>");
            if (a.getStatus() == AppointmentStatus.BOOKED) {
                rows.append("<div class='row-actions'>")
                    .append("<a href='/patient/reschedule?id=").append(a.getId()).append("'><button class='secondary'>Reschedule</button></a>")
                    .append(cancelForm("/patient/cancel", a.getId()))
                    .append("</div>");
            }
            rows.append("</td></tr>");
        }
        String table = list.isEmpty() ? Html.emptyState("No appointments yet.")
                : Html.table(new String[]{"Id", "When", "Doctor", "Status", "Payable", ""}, rows);

        Map<String, String> f = flash(ex);
        String body = "<h1>My appointments</h1>" + Html.card("History", table);
        WebUtil.sendHtml(ex, 200, Html.page("Appointments", user, f.get("msg"), f.get("mt"), body));
    }

    private void rescheduleForm(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        String id = WebUtil.parseQuery(ex.getRequestURI().getRawQuery()).get("id");
        Appointment a = registry.getAppointmentRepository().findById(id).orElse(null);
        if (a == null) { WebUtil.redirect(ex, "/patient/appointments", "Appointment not found.", "error"); return; }

        String body = Html.card("Reschedule " + Html.escape(id),
                "<p>Currently " + a.getDate() + " " + a.getStartTime() + " with "
                + Html.escape(doctorName(a.getDoctorId())) + "</p>"
                + "<form method='post' action='/patient/reschedule'>"
                + Html.hidden("id", id)
                + Html.field("New date", "date", "date", "", "")
                + Html.field("New time", "time", "time", "", "")
                + Html.button("Reschedule")
                + "</form>");
        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Reschedule", user, f.get("msg"), f.get("mt"), body));
    }

    private void doReschedule(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            LocalDate date = Validator.requireFutureDate(form.get("date"));
            LocalTime time = Validator.requireTime(form.get("time"));
            Appointment moved = registry.getBookingService().reschedule(form.get("id"), date, time, user.getId());
            WebUtil.redirect(ex, "/patient/appointments", "Moved to new reference " + moved.getId() + ".", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/patient/reschedule?id=" + form.get("id"), e.getMessage(), "error");
        }
    }

    private void patientPrescriptions(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.PATIENT);
        if (user == null) return;
        List<Prescription> history = registry.getPrescriptionService().historyOfPatient(user.getId());

        StringBuilder rows = new StringBuilder();
        for (Prescription p : history) {
            rows.append("<tr><td>").append(Html.escape(p.getId())).append("</td><td>")
                .append(p.getIssuedAt().toLocalDate()).append("</td><td>")
                .append(Html.escape(p.getDiagnosis())).append("</td><td>")
                .append(Html.escape(String.join(", ", p.getMedicines()))).append("</td><td>")
                .append(Html.escape(p.getAdvice())).append("</td></tr>");
        }
        String table = history.isEmpty() ? Html.emptyState("No prescriptions yet.")
                : Html.table(new String[]{"Id", "Date", "Diagnosis", "Medicines", "Advice"}, rows);

        Map<String, String> f = flash(ex);
        String body = "<h1>My prescriptions</h1>" + Html.card("History", table);
        WebUtil.sendHtml(ex, 200, Html.page("Prescriptions", user, f.get("msg"), f.get("mt"), body));
    }

    /* ------------------------------------------------------------- doctor pages - */

    private void doctorSchedule(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.DOCTOR);
        if (user == null) return;
        Doctor doc = (Doctor) user;
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        LocalDate date = parseDateOrDefault(q.get("date"), LocalDate.now());

        List<Slot> slots = registry.getSlotService().getSlots(doc, date);
        List<Appointment> appts = registry.getAppointmentRepository().findByDoctorAndDate(doc.getId(), date);

        StringBuilder rows = new StringBuilder();
        for (Slot s : slots) {
            rows.append("<tr><td>").append(s.getStart()).append(" - ").append(s.getEnd()).append("</td><td>")
                .append(Html.badge(s.statusLabel(), s.statusLabel().toLowerCase())).append("</td>");
            Appointment match = null;
            if (s.isBooked()) {
                for (Appointment a : appts) if (a.getId().equals(s.getAppointmentId())) match = a;
            }
            if (match != null) {
                rows.append("<td>").append(Html.escape(patientName(match.getPatientId()))).append("</td><td>")
                    .append(Html.escape(match.getReason())).append("</td><td>")
                    .append(Html.badge(match.getStatus().name(), match.getStatus().name().toLowerCase())).append("</td><td>");
                if (match.getStatus() == AppointmentStatus.BOOKED) {
                    rows.append("<div class='row-actions'>")
                        .append(postButton("/doctor/complete", "id", match.getId(), "Complete", null))
                        .append(cancelForm("/doctor/cancel", match.getId()))
                        .append("</div>");
                } else if (match.getStatus() == AppointmentStatus.COMPLETED) {
                    rows.append("<a href='/doctor/prescribe?apptId=").append(match.getId())
                        .append("'><button class='secondary'>Prescribe</button></a>");
                }
                rows.append("</td>");
            } else {
                rows.append("<td colspan='4' class='muted'>&mdash;</td>");
            }
            rows.append("</tr>");
        }
        String table = slots.isEmpty() ? Html.emptyState("No slots configured.")
                : Html.table(new String[]{"Time", "Status", "Patient", "Reason", "Appt. status", ""}, rows);

        String dateForm = "<form method='get' action='/doctor'>"
                + Html.field("Date", "date", "date", date.toString(), "") + Html.button("View") + "</form>";

        double occ = registry.getSlotService().occupancyRate(doc, date);
        String body = "<h1>My schedule</h1>"
                + Html.card("Choose a date", dateForm)
                + Html.card("Schedule for " + date, table
                    + "<p class='muted' style='margin-top:12px'>Occupancy: " + String.format("%.1f%%", occ) + "</p>")
                + "<p><a href='/doctor/earnings'>View earnings summary &rarr;</a></p>";

        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Doctor", user, f.get("msg"), f.get("mt"), body));
    }

    private void doComplete(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.DOCTOR);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            registry.getBookingService().complete(form.get("id"), user.getId());
            WebUtil.redirect(ex, "/doctor", form.get("id") + " marked completed.", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/doctor", e.getMessage(), "error");
        }
    }

    private void prescribeForm(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.DOCTOR);
        if (user == null) return;
        String apptId = WebUtil.parseQuery(ex.getRequestURI().getRawQuery()).get("apptId");
        Appointment a = registry.getAppointmentRepository().findById(apptId).orElse(null);
        if (a == null) { WebUtil.redirect(ex, "/doctor", "Appointment not found.", "error"); return; }

        String body = Html.card("Prescription for " + Html.escape(apptId),
                "<p>Patient: " + Html.escape(patientName(a.getPatientId())) + "</p>"
                + "<form method='post' action='/doctor/prescribe'>"
                + Html.hidden("apptId", apptId)
                + Html.field("Diagnosis", "diagnosis", "text", "", "")
                + Html.textarea("Medicines (one per line)", "medicines", "", "Paracetamol 500mg\nSteam inhalation")
                + Html.field("Advice", "advice", "text", "", "Review if symptoms persist")
                + Html.button("Issue prescription")
                + "</form>");
        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Prescribe", user, f.get("msg"), f.get("mt"), body));
    }

    private void doPrescribe(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.DOCTOR);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        List<String> meds = new ArrayList<>();
        for (String line : form.getOrDefault("medicines", "").split("\\r?\\n")) {
            if (!line.isBlank()) meds.add(line.trim());
        }
        try {
            Prescription rx = registry.getPrescriptionService().issue(form.get("apptId"), user.getId(),
                    form.get("diagnosis"), meds, form.get("advice"));
            WebUtil.redirect(ex, "/doctor", "Prescription " + rx.getId() + " issued.", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/doctor/prescribe?apptId=" + form.get("apptId"), e.getMessage(), "error");
        }
    }

    private void doctorEarnings(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.DOCTOR);
        if (user == null) return;
        List<Appointment> all = registry.getAppointmentRepository().findByDoctor(user.getId());
        double gross = 0, realised = 0;
        int completed = 0, cancelled = 0;
        for (Appointment a : all) {
            gross += a.calculateBill();
            switch (a.getStatus()) {
                case COMPLETED -> { realised += a.calculateBill(); completed++; }
                case CANCELLED, NO_SHOW -> cancelled++;
                default -> { }
            }
        }
        String body = "<h1>Earnings summary</h1>" + Html.card("Overview",
                stat(all.size(), "Total appointments") + stat(completed, "Completed") + stat(cancelled, "Cancelled / no-show")
                + "<hr><p>Realised from completed: <strong>INR " + String.format("%.2f", realised) + "</strong></p>"
                + "<p>Gross billable: <strong>INR " + String.format("%.2f", gross) + "</strong></p>"
                + "<p>Estimated tax component: INR " + String.format("%.2f", gross * 0.05) + "</p>");
        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Earnings", user, f.get("msg"), f.get("mt"), body));
    }

    /* -------------------------------------------------------------- admin pages - */

    private void adminHome(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        String body = "<h1>Administration</h1><div class='grid'>"
                + navCard("/admin/doctors", "Doctors", "Add and list doctors")
                + navCard("/admin/patients", "Patients", "View registered patients")
                + navCard("/admin/appointments", "Appointments", "All bookings for a date")
                + navCard("/admin/analytics", "Analytics", "Revenue, load and demand dashboard")
                + navCard("/admin/export", "Export", "Download the appointment book as CSV")
                + navCard("/admin/logs", "System log", "Tail the audit log")
                + "</div>";
        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Admin", user, f.get("msg"), f.get("mt"), body));
    }

    private void adminDoctors(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        List<Doctor> doctors = registry.getUserRepository().findDoctors();

        StringBuilder rows = new StringBuilder();
        for (Doctor d : doctors) {
            rows.append("<tr><td>").append(Html.escape(d.getId())).append("</td><td>Dr. ")
                .append(Html.escape(d.getFullName())).append("</td><td>")
                .append(Html.escape(d.getSpecialization().getDisplayName())).append("</td><td>INR ")
                .append(String.format("%.2f", d.getConsultationFee())).append("</td><td>")
                .append(d.isActive() ? Html.badge("ACTIVE", "free") : Html.badge("DISABLED", "booked")).append("</td><td>")
                .append(postButton("/admin/toggle", "id", d.getId(), d.isActive() ? "Disable" : "Enable",
                        d.isActive() ? "danger" : null))
                .append("</td></tr>");
        }
        String table = doctors.isEmpty() ? Html.emptyState("No doctors yet.")
                : Html.table(new String[]{"Id", "Name", "Specialization", "Fee", "Status", ""}, rows);

        List<String[]> specOptions = new ArrayList<>();
        for (Specialization s : Specialization.values()) specOptions.add(new String[]{s.name(), s.getDisplayName()});

        String addForm = "<form method='post' action='/admin/doctors'>"
                + Html.field("Username", "username", "text", "", "login id")
                + Html.field("Temp password", "password", "text", "", "min 6 chars")
                + Html.field("Full name", "fullName", "text", "", "")
                + Html.field("Phone", "phone", "text", "", "10 digits")
                + Html.select("Specialization", "specialization", specOptions)
                + Html.field("Fee (INR)", "fee", "number", "500", "")
                + Html.field("Room", "roomNo", "text", "", "")
                + Html.field("Work start", "start", "time", "09:00", "")
                + Html.field("Work end", "end", "time", "17:00", "")
                + Html.field("Slot length (min)", "slotMinutes", "number", "30", "")
                + Html.button("Add doctor")
                + "</form>";

        Map<String, String> f = flash(ex);
        String body = "<div class='linkbar'><a href='/admin'>&larr; Admin home</a></div>"
                + "<h1>Doctors</h1>"
                + Html.card("Existing doctors", table)
                + Html.card("Onboard a new doctor", addForm);
        WebUtil.sendHtml(ex, 200, Html.page("Doctors", user, f.get("msg"), f.get("mt"), body));
    }

    private void doAddDoctor(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            double fee = Validator.requireFee(form.get("fee"));
            LocalTime start = Validator.requireTime(form.get("start"));
            LocalTime end = Validator.requireTime(form.get("end"));
            int slotMinutes = Validator.requireRange(form.get("slotMinutes"), 5, 120, "Slot length");
            Doctor doc = registry.getAuthService().registerDoctor(form.get("username"), form.get("password"),
                    form.get("fullName"), form.get("phone"), Specialization.fromString(form.get("specialization")),
                    fee, form.get("roomNo"), start, end, slotMinutes);
            WebUtil.redirect(ex, "/admin/doctors", "Created " + doc.getId() + ".", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/admin/doctors", e.getMessage(), "error");
        }
    }

    private void adminPatients(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        List<Patient> patients = registry.getUserRepository().findPatients();

        StringBuilder rows = new StringBuilder();
        for (Patient p : patients) {
            rows.append("<tr><td>").append(Html.escape(p.getId())).append("</td><td>")
                .append(Html.escape(p.getFullName())).append("</td><td>").append(p.getAge()).append("/")
                .append(Html.escape(p.getGender())).append("</td><td>").append(Html.escape(p.getBloodGroup()))
                .append("</td><td>").append(Html.escape(p.getPhone())).append("</td><td>")
                .append(p.isActive() ? Html.badge("ACTIVE", "free") : Html.badge("DISABLED", "booked")).append("</td><td>")
                .append(postButton("/admin/toggle", "id", p.getId(), p.isActive() ? "Disable" : "Enable",
                        p.isActive() ? "danger" : null))
                .append("</td></tr>");
        }
        String table = patients.isEmpty() ? Html.emptyState("No patients yet.")
                : Html.table(new String[]{"Id", "Name", "Age/Gender", "Blood", "Phone", "Status", ""}, rows);

        Map<String, String> f = flash(ex);
        String body = "<div class='linkbar'><a href='/admin'>&larr; Admin home</a></div>"
                + "<h1>Patients</h1>" + Html.card("Registered patients", table);
        WebUtil.sendHtml(ex, 200, Html.page("Patients", user, f.get("msg"), f.get("mt"), body));
    }

    private void doToggle(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            String id = form.get("id");
            boolean currentlyActive = registry.getUserRepository().findById(id)
                    .map(u -> u.isActive()).orElse(false);
            registry.getAuthService().setActive(id, !currentlyActive);
            String referer = ex.getRequestHeaders().getFirst("Referer");
            WebUtil.redirect(ex, referer != null ? stripQuery(referer) : "/admin",
                    id + " is now " + (!currentlyActive ? "ACTIVE" : "DISABLED") + ".", "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, "/admin", e.getMessage(), "error");
        }
    }

    private void adminAppointments(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        Map<String, String> q = WebUtil.parseQuery(ex.getRequestURI().getRawQuery());
        LocalDate date = parseDateOrDefault(q.get("date"), LocalDate.now());
        List<Appointment> list = registry.getAppointmentRepository().findByDate(date);

        StringBuilder rows = new StringBuilder();
        for (Appointment a : list) {
            rows.append("<tr><td>").append(Html.escape(a.getId())).append("</td><td>")
                .append(a.getStartTime()).append("</td><td>").append(Html.escape(doctorName(a.getDoctorId())))
                .append("</td><td>").append(Html.escape(patientName(a.getPatientId()))).append("</td><td>")
                .append(Html.badge(a.getStatus().name(), a.getStatus().name().toLowerCase())).append("</td><td>INR ")
                .append(String.format("%.2f", a.calculateBill())).append("</td></tr>");
        }
        String table = list.isEmpty() ? Html.emptyState("Nothing scheduled.")
                : Html.table(new String[]{"Id", "Time", "Doctor", "Patient", "Status", "Payable"}, rows);

        String dateForm = "<form method='get' action='/admin/appointments'>"
                + Html.field("Date", "date", "date", date.toString(), "") + Html.button("View") + "</form>";

        Map<String, String> f = flash(ex);
        String body = "<div class='linkbar'><a href='/admin'>&larr; Admin home</a></div>"
                + "<h1>Appointments</h1>" + Html.card("Choose a date", dateForm)
                + Html.card("On " + date, table);
        WebUtil.sendHtml(ex, 200, Html.page("Appointments", user, f.get("msg"), f.get("mt"), body));
    }

    private void adminAnalytics(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        var reports = registry.getReportService();

        StringBuilder statusRows = new StringBuilder();
        int maxStatus = reports.statusBreakdown().values().stream().max(Integer::compareTo).orElse(1);
        reports.statusBreakdown().forEach((status, count) -> statusRows.append("<tr><td>")
                .append(status).append("</td><td>").append(count).append("</td><td>")
                .append(bar(count, Math.max(1, maxStatus))).append("</td></tr>"));

        StringBuilder revRows = new StringBuilder();
        reports.revenueByDoctor().forEach((docId, amount) -> revRows.append("<tr><td>")
                .append(Html.escape(doctorName(docId))).append("</td><td>INR ")
                .append(String.format("%.2f", amount)).append("</td></tr>"));

        StringBuilder demandRows = new StringBuilder();
        reports.demandBySpecialization().forEach((spec, count) -> demandRows.append("<tr><td>")
                .append(Html.escape(spec.getDisplayName())).append("</td><td>").append(count).append("</td></tr>"));

        StringBuilder loadRows = new StringBuilder();
        int maxLoad = reports.dailyLoad().values().stream().max(Integer::compareTo).orElse(1);
        reports.dailyLoad().forEach((date, count) -> loadRows.append("<tr><td>").append(date).append("</td><td>")
                .append(count).append("</td><td>").append(bar(count, Math.max(1, maxLoad))).append("</td></tr>"));

        StringBuilder topRows = new StringBuilder();
        for (String line : reports.topDoctors(5)) {
            topRows.append("<tr><td colspan='2'>").append(Html.escape(line)).append("</td></tr>");
        }

        String body = "<div class='linkbar'><a href='/admin'>&larr; Admin home</a></div>"
                + "<h1>Analytics</h1>"
                + Html.card("Totals", stat(String.format("INR %.2f", reports.totalRevenue()), "Gross billable")
                        + stat(String.format("%.1f%%", reports.cancellationRate()), "Cancellation + no-show rate"))
                + Html.card("Status breakdown", Html.table(new String[]{"Status", "Count", ""}, statusRows))
                + Html.card("Revenue by doctor", revRows.length() == 0 ? Html.emptyState("No data yet.")
                        : Html.table(new String[]{"Doctor", "Revenue"}, revRows))
                + Html.card("Demand by specialization", demandRows.length() == 0 ? Html.emptyState("No data yet.")
                        : Html.table(new String[]{"Specialization", "Bookings"}, demandRows))
                + Html.card("Daily load", Html.table(new String[]{"Date", "Count", ""}, loadRows))
                + Html.card("Top doctors (by completed)", topRows.length() == 0
                        ? Html.emptyState("No completed consultations yet.")
                        : Html.table(new String[]{"Doctor", ""}, topRows));

        Map<String, String> f = flash(ex);
        WebUtil.sendHtml(ex, 200, Html.page("Analytics", user, f.get("msg"), f.get("mt"), body));
    }

    private void doExport(HttpExchange ex) throws IOException, HospitalException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        var path = registry.getReportService().exportAppointmentsCsv();
        byte[] bytes = Files.readAllBytes(path);
        WebUtil.sendDownload(ex, bytes, path.getFileName().toString(), "text/csv");
    }

    private void adminLogs(HttpExchange ex) throws IOException {
        User user = requireLogin(ex, Role.ADMIN);
        if (user == null) return;
        StringBuilder pre = new StringBuilder("<pre style='white-space:pre-wrap;font-size:12px;line-height:1.5;'>");
        for (String line : AppLogger.getInstance().tail(40)) pre.append(Html.escape(line)).append("\n");
        pre.append("</pre>");

        Map<String, String> f = flash(ex);
        String body = "<div class='linkbar'><a href='/admin'>&larr; Admin home</a></div>"
                + "<h1>System log</h1>" + Html.card("Last 40 lines", pre.toString());
        WebUtil.sendHtml(ex, 200, Html.page("Logs", user, f.get("msg"), f.get("mt"), body));
    }

    /* ------------------------------------------------------------------ shared -- */

    private void doCancel(HttpExchange ex, String backTo) throws IOException {
        User user = requireLogin(ex);
        if (user == null) return;
        Map<String, String> form = WebUtil.readForm(ex);
        try {
            Appointment cancelled = registry.getBookingService().cancel(form.get("id"), user.getId());
            String note = cancelled.getStatus() == AppointmentStatus.NO_SHOW
                    ? "Cancelled inside 2h window - 50% charge applies." : "Cancelled.";
            WebUtil.redirect(ex, backTo, note, "ok");
        } catch (HospitalException e) {
            WebUtil.redirect(ex, backTo, e.getMessage(), "error");
        }
    }

    private String cancelForm(String action, String id) {
        return "<form class='inline' method='post' action='" + action + "' "
                + "onsubmit=\"return confirm('Cancel appointment " + id + "?');\">"
                + Html.hidden("id", id) + "<button class='danger' type='submit'>Cancel</button></form>";
    }

    private String postButton(String action, String field, String value, String label, String cssClass) {
        String cls = cssClass == null ? "" : " class='" + cssClass + "'";
        return "<form class='inline' method='post' action='" + action + "'>"
                + Html.hidden(field, value) + "<button" + cls + " type='submit'>" + Html.escape(label)
                + "</button></form>";
    }

    private String navCard(String href, String title, String desc) {
        return "<a href='" + href + "' style='text-decoration:none;color:inherit;'>"
                + Html.card(title, "<p class='muted'>" + Html.escape(desc) + "</p>") + "</a>";
    }

    private String stat(Object number, String label) {
        return "<span class='stat'><span class='n'>" + Html.escape(number) + "</span><span class='l'>"
                + Html.escape(label) + "</span></span>";
    }

    private String bar(int value, int max) {
        int pct = Math.min(100, (int) Math.round(value * 100.0 / max));
        return "<div class='bar'><span style='width:" + pct + "%'></span></div>";
    }

    private LocalDate parseDateOrDefault(String raw, LocalDate fallback) {
        try {
            return raw == null || raw.isBlank() ? fallback : LocalDate.parse(raw);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String doctorName(String doctorId) {
        try {
            return registry.getUserRepository().findDoctorById(doctorId)
                    .map(d -> "Dr. " + d.getFullName()).orElse(doctorId);
        } catch (HospitalException e) {
            return doctorId;
        }
    }

    private String patientName(String patientId) {
        try {
            return registry.getUserRepository().findPatientById(patientId)
                    .map(p -> p.getFullName()).orElse(patientId);
        } catch (HospitalException e) {
            return patientId;
        }
    }
}

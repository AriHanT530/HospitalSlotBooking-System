package com.hospital.web;

import com.hospital.model.Role;
import com.hospital.model.User;

import java.util.List;
import java.util.Map;

/**
 * Server-side HTML rendering. No template engine and no external libraries -
 * every page is built from small String helpers so the whole web layer stays
 * inside the "pure JDK" constraint the rest of the project follows.
 *
 * Every value that came from user input is escaped before being inlined, so
 * a diagnosis or reason containing "<script>" cannot execute in the browser.
 */
public final class Html {

    private Html() { }

    public static String escape(Object value) {
        if (value == null) return "";
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Wraps a page body with the shared nav bar, styling and flash banner. */
    public static String page(String title, User user, String flash, String flashType, String body) {
        StringBuilder nav = new StringBuilder();
        nav.append("<nav><div class='brand'>Hospital Slot Booking</div><div class='navlinks'>");
        if (user != null) {
            String home = switch (user.getRole()) {
                case ADMIN -> "/admin";
                case DOCTOR -> "/doctor";
                case PATIENT -> "/patient";
            };
            nav.append("<a href='").append(home).append("'>Dashboard</a>");
            nav.append("<a href='/account/password'>Password</a>");
            nav.append("<span class='who'>").append(escape(user.getFullName()))
               .append(" &middot; ").append(user.getRole().getLabel()).append("</span>");
            nav.append("<a class='logout' href='/logout'>Logout</a>");
        } else {
            nav.append("<a href='/login'>Login</a><a href='/register'>Register</a>");
        }
        nav.append("</div></nav>");

        String banner = "";
        if (flash != null && !flash.isBlank()) {
            String cls = "error".equals(flashType) ? "flash flash-error" : "flash flash-ok";
            banner = "<div class='" + cls + "'>" + escape(flash) + "</div>";
        }

        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                + "<meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<title>" + escape(title) + " - Hospital Slot Booking</title>"
                + "<style>" + CSS + "</style></head><body>"
                + nav + "<main>" + banner + body + "</main>"
                + "</body></html>";
    }

    /* ------------------------- small building blocks ------------------------- */

    public static String card(String title, String innerHtml) {
        return "<section class='card'><h2>" + escape(title) + "</h2>" + innerHtml + "</section>";
    }

    public static String hidden(String name, String value) {
        return "<input type='hidden' name='" + escape(name) + "' value='" + escape(value) + "'>";
    }

    public static String field(String label, String name, String type, String value, String placeholder) {
        return "<label>" + escape(label)
                + "<input type='" + type + "' name='" + escape(name) + "' value='" + escape(value)
                + "' placeholder='" + escape(placeholder) + "'></label>";
    }

    public static String textarea(String label, String name, String value, String placeholder) {
        return "<label>" + escape(label) + "<textarea name='" + escape(name)
                + "' placeholder='" + escape(placeholder) + "'>" + escape(value) + "</textarea></label>";
    }

    public static String select(String label, String name, List<String[]> options) {
        StringBuilder sb = new StringBuilder("<label>").append(escape(label)).append("<select name='")
                .append(escape(name)).append("'>");
        for (String[] opt : options) {
            sb.append("<option value='").append(escape(opt[0])).append("'>")
              .append(escape(opt[1])).append("</option>");
        }
        return sb.append("</select></label>").toString();
    }

    public static String badge(String text, String kind) {
        return "<span class='badge badge-" + escape(kind) + "'>" + escape(text) + "</span>";
    }

    public static String button(String text) {
        return "<button type='submit'>" + escape(text) + "</button>";
    }

    /** Small helper for building a <table> from a header row and body rows already built by the caller. */
    public static String table(String[] headers, StringBuilder rows) {
        StringBuilder sb = new StringBuilder("<div class='tablewrap'><table><thead><tr>");
        for (String h : headers) sb.append("<th>").append(escape(h)).append("</th>");
        sb.append("</tr></thead><tbody>").append(rows).append("</tbody></table></div>");
        return sb.toString();
    }

    public static String emptyState(String message) {
        return "<p class='empty'>" + escape(message) + "</p>";
    }

    /** Query string / form values map to a single value, defaulting to "". */
    public static String val(Map<String, String> form, String key) {
        return form.getOrDefault(key, "");
    }

    private static final String CSS = """
        :root{--bg:#f4f6f9;--card:#ffffff;--ink:#1f2937;--muted:#6b7280;--line:#e5e7eb;
              --brand:#2563eb;--brand-dark:#1d4ed8;--ok:#059669;--err:#dc2626;--warn:#d97706;}
        *{box-sizing:border-box;}
        body{margin:0;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;
             background:var(--bg);color:var(--ink);}
        nav{display:flex;justify-content:space-between;align-items:center;background:#111827;
            color:#fff;padding:14px 28px;}
        nav .brand{font-weight:700;letter-spacing:.3px;}
        nav .navlinks{display:flex;align-items:center;gap:18px;}
        nav a{color:#e5e7eb;text-decoration:none;font-size:14px;}
        nav a:hover{color:#fff;text-decoration:underline;}
        nav a.logout{color:#fca5a5;}
        nav .who{color:#93c5fd;font-size:13px;}
        main{max-width:1000px;margin:28px auto;padding:0 20px 60px;}
        h1{font-size:22px;margin:0 0 18px;}
        h2{font-size:17px;margin:0 0 14px;color:#111827;}
        .card{background:var(--card);border:1px solid var(--line);border-radius:10px;
              padding:20px 22px;margin-bottom:20px;box-shadow:0 1px 2px rgba(0,0,0,.03);}
        .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:16px;}
        form.inline{display:inline;}
        label{display:block;font-size:13px;color:var(--muted);margin-bottom:12px;}
        input,select,textarea{width:100%;margin-top:5px;padding:9px 10px;border:1px solid var(--line);
             border-radius:6px;font-size:14px;font-family:inherit;background:#fff;color:var(--ink);}
        textarea{min-height:70px;resize:vertical;}
        button{background:var(--brand);color:#fff;border:none;padding:10px 18px;border-radius:6px;
               font-size:14px;cursor:pointer;font-weight:600;}
        button:hover{background:var(--brand-dark);}
        button.secondary{background:#6b7280;}
        button.danger{background:var(--err);}
        .row-actions{display:flex;gap:8px;flex-wrap:wrap;}
        .tablewrap{overflow-x:auto;}
        table{width:100%;border-collapse:collapse;font-size:14px;}
        th,td{text-align:left;padding:9px 10px;border-bottom:1px solid var(--line);}
        th{color:var(--muted);font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.4px;}
        tr:hover td{background:#fafbff;}
        .empty{color:var(--muted);font-style:italic;padding:8px 0;}
        .flash{padding:12px 16px;border-radius:8px;margin-bottom:18px;font-size:14px;}
        .flash-ok{background:#ecfdf5;color:var(--ok);border:1px solid #a7f3d0;}
        .flash-error{background:#fef2f2;color:var(--err);border:1px solid #fecaca;}
        .badge{display:inline-block;padding:2px 9px;border-radius:99px;font-size:12px;font-weight:600;}
        .badge-free{background:#ecfdf5;color:var(--ok);}
        .badge-booked{background:#fef2f2;color:var(--err);}
        .badge-elapsed{background:#f3f4f6;color:var(--muted);}
        .badge-completed{background:#eff6ff;color:var(--brand);}
        .badge-cancelled{background:#f3f4f6;color:var(--muted);}
        .badge-no_show{background:#fffbeb;color:var(--warn);}
        .stat{display:inline-block;min-width:150px;margin:6px 18px 6px 0;}
        .stat .n{font-size:24px;font-weight:700;}
        .stat .l{font-size:12px;color:var(--muted);text-transform:uppercase;letter-spacing:.4px;}
        .muted{color:var(--muted);font-size:13px;}
        .center{max-width:420px;margin:60px auto;}
        .linkbar{margin-bottom:16px;}
        .linkbar a{margin-right:14px;font-size:14px;color:var(--brand);text-decoration:none;}
        .linkbar a:hover{text-decoration:underline;}
        .bar{background:#eef2ff;border-radius:4px;height:8px;overflow:hidden;margin-top:4px;}
        .bar>span{display:block;height:100%;background:var(--brand);}
        """;
}

package com.campusshare.db;

import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;

public class SeedData {

    public static void seed(Connection conn) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM users");
        rs.next();
        if (rs.getInt(1) > 0) { rs.close(); seedChannelsIfEmpty(conn); return; }
        rs.close();

        System.out.println("Seeding demo data...");

        // Users
        PreparedStatement ps = conn.prepareStatement(
            "INSERT INTO users(full_name,email,password_hash,role,department,semester) VALUES(?,?,?,?,?,?)");
        Object[][] users = {
            {"Admin User",    "admin@campus.edu",   BCrypt.hashpw("admin123",   BCrypt.gensalt()), "ADMIN",   "Administration", 0},
            {"Dr. Smith",     "smith@campus.edu",   BCrypt.hashpw("faculty123", BCrypt.gensalt()), "FACULTY", "CSE",            0},
            {"Alice Johnson", "alice@campus.edu",   BCrypt.hashpw("student123", BCrypt.gensalt()), "STUDENT", "CSE",            3},
            {"Bob Wilson",    "bob@campus.edu",     BCrypt.hashpw("student123", BCrypt.gensalt()), "STUDENT", "EEE",            2},
            {"raian",         "raian@campus.edu",   BCrypt.hashpw("student123", BCrypt.gensalt()), "STUDENT", "CSE",            1},
        };
        for (Object[] u : users) {
            ps.setString(1,(String)u[0]); ps.setString(2,(String)u[1]); ps.setString(3,(String)u[2]);
            ps.setString(4,(String)u[3]); ps.setString(5,(String)u[4]); ps.setInt(6,(int)u[5]);
            ps.executeUpdate();
        }
        ps.close();

        // Subjects — now with semester
        ps = conn.prepareStatement(
            "INSERT INTO subjects(name,code,department,semester,credit_hours) VALUES(?,?,?,?,?)");
        Object[][] subjects = {
            {"Data Structures",          "CSE201", "CSE", 3, 3},
            {"Algorithms",               "CSE301", "CSE", 5, 3},
            {"Operating Systems",        "CSE401", "CSE", 7, 3},
            {"Intro to Programming",     "CSE101", "CSE", 1, 3},
            {"Digital Logic Design",     "CSE102", "CSE", 2, 3},
            {"Circuit Analysis",         "EEE201", "EEE", 3, 3},
            {"Digital Electronics",      "EEE301", "EEE", 5, 3},
            {"Power Systems",            "EEE401", "EEE", 7, 3},
            {"Basic Electrical",         "EEE101", "EEE", 1, 3},
        };
        for (Object[] s : subjects) {
            ps.setString(1,(String)s[0]); ps.setString(2,(String)s[1]);
            ps.setString(3,(String)s[2]); ps.setInt(4,(int)s[3]); ps.setInt(5,(int)s[4]);
            ps.executeUpdate();
        }
        ps.close();

        // Resources (notes)
        ps = conn.prepareStatement(
            "INSERT INTO resources(subject_id,uploaded_by,file_name,file_type,file_size,approved) VALUES(?,?,?,?,?,?)");
        Object[][] notes = {
            {1, 3, "Linked Lists Complete Notes.pdf",   "PDF",  "2.4 MB", 1},
            {2, 3, "Sorting Algorithms Cheatsheet.pdf", "PDF",  "1.1 MB", 1},
            {1, 2, "Memory Management Slides.pptx",     "PPTX", "3.2 MB", 0},
            {6, 4, "Circuit Basics.pdf",                "PDF",  "1.8 MB", 1},
        };
        for (Object[] n : notes) {
            ps.setInt(1,(int)n[0]); ps.setInt(2,(int)n[1]); ps.setString(3,(String)n[2]);
            ps.setString(4,(String)n[3]); ps.setString(5,(String)n[4]); ps.setInt(6,(int)n[5]);
            ps.executeUpdate();
        }
        ps.close();

        // Events
        ps = conn.prepareStatement(
            "INSERT INTO events(title,event_date,event_time,category,location,created_by) VALUES(?,?,?,?,?,2)");
        String[][] events = {
            {"CSE Tech Symposium",      "2026-04-15", "10:00", "SEMINAR",  "Auditorium A"},
            {"AI & ML Workshop",        "2026-04-22", "14:00", "WORKSHOP", "Lab 3"},
            {"Cultural Night 2026",     "2026-04-28", "18:00", "CULTURAL", "Main Stage"},
            {"Career Fair Spring 2026", "2026-05-05", "09:00", "SEMINAR",  "Sports Complex"},
        };
        for (String[] e : events) {
            ps.setString(1,e[0]); ps.setString(2,e[1]); ps.setString(3,e[2]);
            ps.setString(4,e[3]); ps.setString(5,e[4]); ps.executeUpdate();
        }
        ps.close();

        // Announcements
        ps = conn.prepareStatement(
            "INSERT INTO announcements(title,body,posted_by,tag) VALUES(?,?,2,?)");
        String[][] anns = {
            {"Mid-Term Schedule Released",
             "Mid-term examinations will be held April 14–20. Check the portal for your seat plan.", "ACADEMIC"},
            {"Library Extended Hours",
             "The library will be open until midnight during finals season. Book study rooms at the front desk.", "FACILITY"},
            {"Campus Wi-Fi Upgrade",
             "IT will upgrade campus wireless infrastructure this weekend. Expect brief outages on Saturday.", "GENERAL"},
        };
        for (String[] a : anns) {
            ps.setString(1,a[0]); ps.setString(2,a[1]); ps.setString(3,a[2]); ps.executeUpdate();
        }
        ps.close();

        seedChannelsIfEmpty(conn);

        // Chat messages
        ps = conn.prepareStatement(
            "INSERT INTO messages(type,channel,sender_id,content) VALUES('channel',?,?,?)");
        Object[][] msgs = {
            {"general", 3, "Hey everyone! Anyone have the DS assignment solutions?"},
            {"general", 4, "Check the Notes section, Alice uploaded something yesterday"},
            {"general", 2, "Office hours tomorrow 3–5 PM in Room 401"},
            {"cse",     3, "Anyone struggling with Red-Black Trees?"},
            {"cse",     2, "I have notes from last semester, uploading now"},
            {"eee",     4, "Circuit simulation lab is moved to Room 302"},
        };
        for (Object[] m : msgs) {
            ps.setString(1,(String)m[0]); ps.setInt(2,(int)m[1]); ps.setString(3,(String)m[2]);
            ps.executeUpdate();
        }
        ps.close();

        System.out.println("Seed complete.");
    }

    /** Ensure channels table has the default channels. Called on every startup. */
    public static void seedChannelsIfEmpty(Connection conn) throws SQLException {
        ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM channels");
        rs.next(); int count = rs.getInt(1); rs.close();
        if (count > 0) return;

        PreparedStatement ps = conn.prepareStatement(
            "INSERT OR IGNORE INTO channels(name,department,is_general) VALUES(?,?,?)");
        Object[][] channels = {
            {"general",       "ALL", 1},
            {"cse",           "CSE", 0},
            {"eee",           "EEE", 0},
            {"announcements", "ALL", 0},
        };
        for (Object[] c : channels) {
            ps.setString(1,(String)c[0]); ps.setString(2,(String)c[1]); ps.setInt(3,(int)c[2]);
            ps.executeUpdate();
        }
        ps.close();
    }
}

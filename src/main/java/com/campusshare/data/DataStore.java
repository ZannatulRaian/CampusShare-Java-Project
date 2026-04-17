package com.campusshare.data;

import com.campusshare.db.DAO;

import java.util.*;

/**
 * CampusShare data store.
 * Model classes + mutable lists populated from DAO (SQLite or Supabase).
 */
public class DataStore {

    private static User currentUser;
    public static User getCurrentUser() { return currentUser; }
    public static void setCurrentUser(User u) { currentUser = u; }

    /** Supabase integer user_id — may differ from currentUser.id (SQLite id).
     *  Set after cloud login. Used to correctly mark own chat messages. */
    public static volatile int cloudUserId = -1;

    // Mutable lists reloaded from DB
    public static List<Subject>      SUBJECTS      = new ArrayList<>();
    public static List<Note>         NOTES         = new ArrayList<>();
    public static List<Event>        EVENTS        = new ArrayList<>();
    public static List<Announcement> ANNOUNCEMENTS = new ArrayList<>();
    public static List<User>         USERS         = new ArrayList<>();

    /** Load / reload all lists from DAO. */
    public static synchronized void loadAll() {
        try {
            SUBJECTS      = DAO.getAllSubjects();
            NOTES         = DAO.getAllNotes();
            EVENTS        = DAO.getAllEvents();
            ANNOUNCEMENTS = DAO.getAllAnnouncements();
            USERS         = DAO.getAllUsers();
        } catch (Exception e) {
            System.err.println("DataStore.loadAll() error: " + e.getMessage());
        }
    }

    public static void reloadNotes()         { NOTES         = DAO.getAllNotes(); }
    public static void reloadEvents()        { EVENTS        = DAO.getAllEvents(); }
    public static void reloadAnnouncements() { ANNOUNCEMENTS = DAO.getAllAnnouncements(); }

    /** BCrypt-backed login via DAO. */
    public static User login(String email, String password) {
        return DAO.login(email, password);
    }

    // ── Model classes ─────────────────────────────────────────────────────

    public static class User {
        public int id, semester;
        public String fullName, email, role, department, avatarPath, lastSeen;

        public User(int id, String fullName, String email, String role, String department, int semester) {
            this.id = id; this.fullName = fullName; this.email = email;
            this.role = role; this.department = department; this.semester = semester;
        }

        /** Returns true if this user was last seen within 3 minutes. */
        public boolean isOnline() {
            if (lastSeen == null || lastSeen.isEmpty()) return false;
            try {
                java.time.LocalDateTime ls = java.time.LocalDateTime.parse(
                    lastSeen.replace(" ", "T"));
                return java.time.LocalDateTime.now().minusMinutes(3).isBefore(ls);
            } catch (Exception e) { return false; }
        }

        public boolean isAdmin()   { return "ADMIN".equalsIgnoreCase(role); }
        public boolean isFaculty() { return "FACULTY".equalsIgnoreCase(role) || isAdmin(); }

        public String initials() {
            String[] p = fullName.trim().split(" ");
            return p.length == 1
                ? p[0].substring(0, 1).toUpperCase()
                : (p[0].substring(0, 1) + p[p.length - 1].substring(0, 1)).toUpperCase();
        }

        public String firstName() {
            String[] p = fullName.trim().split(" ");
            return p.length > 0 ? p[0] : fullName;
        }

        @Override public String toString() { return fullName; }
    }

    public static class Subject {
        public int id;
        public String name, code, department;

        public Subject(int id, String name, String code, String department) {
            this.id = id; this.name = name; this.code = code; this.department = department;
        }

        public Subject(int id, String name, String code, String department, int creditHours) {
            this(id, name, code, department);
        }

        @Override public String toString() { return name + " (" + code + ")"; }
    }

    public static class Note {
        public int id, subjectId;
        public String fileName, fileType, fileSize, uploadedBy, filePath;
        public boolean approved;

        public Note(int id, int subjectId, String fileName, String fileType,
                    String fileSize, String uploadedBy, String uploadedAt,
                    boolean approved, String filePath) {
            this.id = id; this.subjectId = subjectId; this.uploadedBy = uploadedBy;
            this.fileName = fileName; this.fileType = fileType; this.fileSize = fileSize;
            this.filePath = filePath; this.approved = approved;
        }

        public String subjectName() {
            return SUBJECTS.stream()
                .filter(s -> s.id == subjectId)
                .map(s -> s.name)
                .findFirst().orElse("Unknown");
        }
    }

    public static class Event {
        public int id;
        public String title, date, time, category, location;

        public Event(int id, String title, String date, String time, String category, String location) {
            this.id = id; this.title = title; this.date = date;
            this.time = time; this.category = category; this.location = location;
        }

        public Event(int id, String title, String date, String time,
                     String category, String location, int createdBy) {
            this(id, title, date, time, category, location);
        }
    }

    public static class Announcement {
        public int id;
        public String title, body, category, postedBy, date, tag;

        public Announcement(int id, String title, String body,
                            String category, String postedBy, String date) {
            this.id = id; this.title = title; this.body = body;
            this.category = category; this.postedBy = postedBy; this.date = date;
            this.tag = category;
        }

        public String tag() { return category; }
    }

    public static class Message {
        public int id, senderId;
        public String channel, senderName, content, sentAt;
        public boolean isFaculty;

        public Message(int id, String channel, String senderName, String content, String sentAt) {
            this.id = id; this.channel = channel; this.senderName = senderName;
            this.content = content; this.sentAt = sentAt;
        }

        public Message(int id, int senderId, String senderName, String content,
                       String sentAt, boolean isFaculty) {
            this.id = id; this.senderId = senderId; this.senderName = senderName;
            this.content = content; this.sentAt = sentAt; this.isFaculty = isFaculty;
        }
    }
}

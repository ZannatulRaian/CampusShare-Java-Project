package com.campusshare.data;

import com.campusshare.db.DAO;
import java.util.*;

public class DataStore {

    private static User currentUser;
    public static User getCurrentUser() { return currentUser; }
    public static void setCurrentUser(User u) { currentUser = u; }
    public static volatile int cloudUserId = -1;

    public static List<Subject>      SUBJECTS      = new ArrayList<>();
    public static List<Note>         NOTES         = new ArrayList<>();
    public static List<Event>        EVENTS        = new ArrayList<>();
    public static List<Announcement> ANNOUNCEMENTS = new ArrayList<>();
    public static List<User>         USERS         = new ArrayList<>();
    public static List<Channel>      CHANNELS      = new ArrayList<>();

    public static synchronized void loadAll() {
        try {
            SUBJECTS      = DAO.getAllSubjectsFull();   // always includes semester column
            NOTES         = DAO.getAllNotes();
            EVENTS        = DAO.getAllEvents();
            ANNOUNCEMENTS = DAO.getAllAnnouncements();
            USERS         = DAO.getAllUsers();
            CHANNELS      = DAO.getAllChannels();
        } catch (Exception e) { System.err.println("DataStore.loadAll() error: " + e.getMessage()); }
    }

    public static void reloadNotes()         { NOTES         = DAO.getAllNotes(); }
    public static void reloadEvents()        { EVENTS        = DAO.getAllEvents(); }
    public static void reloadAnnouncements() { ANNOUNCEMENTS = DAO.getAllAnnouncements(); }
    public static void reloadChannels()      { CHANNELS      = DAO.getAllChannels(); }

    public static User login(String email, String password) { return DAO.login(email, password); }

    // ── Models ──────────────────────────────────────────────────────────────

    public static class User {
        public int id, semester;
        public String fullName, email, role, department, studentId, avatarPath, lastSeen;

        public User(int id, String fullName, String email, String role, String department, int semester) {
            this.id = id; this.fullName = fullName; this.email = email;
            this.role = role; this.department = department; this.semester = semester;
            this.studentId = "";
        }

        public boolean isOnline() {
            if (lastSeen == null || lastSeen.isEmpty()) return false;
            try {
                java.time.LocalDateTime ls = java.time.LocalDateTime.parse(lastSeen.replace(" ", "T"));
                return java.time.LocalDateTime.now().minusMinutes(3).isBefore(ls);
            } catch (Exception e) { return false; }
        }

        public boolean isAdmin()   { return "ADMIN".equalsIgnoreCase(role); }
        public boolean isFaculty() { return "FACULTY".equalsIgnoreCase(role) || isAdmin(); }

        public String initials() {
            if (fullName == null || fullName.trim().isEmpty()) return "?";
            String[] p = fullName.trim().split("\\s+");
            String first = p[0].length() > 0 ? p[0].substring(0,1).toUpperCase() : "?";
            if (p.length == 1) return first;
            String last = p[p.length-1].length() > 0 ? p[p.length-1].substring(0,1).toUpperCase() : "";
            return first + last;
        }

        public String firstName() {
            if (fullName == null || fullName.trim().isEmpty()) return "User";
            String[] p = fullName.trim().split("\\s+");
            return p.length > 0 && !p[0].isEmpty() ? p[0] : fullName;
        }

        /** Can this user access the given channel? */
        public boolean canAccessChannel(Channel ch) {
            if (isFaculty()) return true;                            // faculty/admin sees all
            if ("ALL".equalsIgnoreCase(ch.department)) return true;  // general channels
            return ch.department.equalsIgnoreCase(department);       // dept match
        }

        @Override public String toString() { return fullName; }
    }

    public static class Subject {
        public int id, semester;
        public String name, code, department;

        public Subject(int id, String name, String code, String department) {
            this.id = id; this.name = name; this.code = code; this.department = department; this.semester = 1;
        }
        public Subject(int id, String name, String code, String department, int semester, int creditHours) {
            this(id, name, code, department); this.semester = semester;
        }
        // legacy compat
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
            return SUBJECTS.stream().filter(s -> s.id == subjectId)
                .map(s -> s.name).findFirst().orElse("Unknown");
        }
        public int subjectSemester() {
            return SUBJECTS.stream().filter(s -> s.id == subjectId)
                .map(s -> s.semester).findFirst().orElse(0);
        }
        public String subjectDepartment() {
            return SUBJECTS.stream().filter(s -> s.id == subjectId)
                .map(s -> s.department).findFirst().orElse("");
        }
    }

    public static class Event {
        public int id;
        public String title, date, time, category, location, department;
        public Event(int id, String title, String date, String time, String category, String location) {
            this(id, title, date, time, category, location, "ALL");
        }
        public Event(int id, String title, String date, String time, String category, String location, int createdBy) {
            this(id, title, date, time, category, location, "ALL");
        }
        public Event(int id, String title, String date, String time, String category, String location, String department) {
            this.id = id; this.title = title; this.date = date;
            this.time = time; this.category = category; this.location = location;
            this.department = department != null ? department : "ALL";
        }
        public boolean visibleTo(User u) {
            if (u.isFaculty()) return true;
            return "ALL".equalsIgnoreCase(department) || department.equalsIgnoreCase(u.department);
        }
    }

    public static class Announcement {
        public int id;
        public String title, body, category, postedBy, date, tag, department;
        public Announcement(int id, String title, String body, String category, String postedBy, String date) {
            this(id, title, body, category, postedBy, date, "ALL");
        }
        public Announcement(int id, String title, String body, String category, String postedBy, String date, String department) {
            this.id = id; this.title = title; this.body = body;
            this.category = category; this.postedBy = postedBy; this.date = date; this.tag = category;
            this.department = department != null ? department : "ALL";
        }
        public String tag() { return category; }
        public boolean visibleTo(User u) {
            if (u.isFaculty()) return true;
            return "ALL".equalsIgnoreCase(department) || department.equalsIgnoreCase(u.department);
        }
    }

    public static class Channel {
        public int id;
        public String name, department;
        public boolean isGeneral;
        public Channel(int id, String name, String department, boolean isGeneral) {
            this.id = id; this.name = name; this.department = department; this.isGeneral = isGeneral;
        }
    }

    public static class Message {
        public int id, senderId;
        public String channel, senderName, content, sentAt;
        public boolean isFaculty;

        public Message(int id, String channel, String senderName, String content, String sentAt) {
            this.id = id; this.channel = channel; this.senderName = senderName;
            this.content = content; this.sentAt = sentAt;
        }
        public Message(int id, int senderId, String senderName, String content, String sentAt, boolean isFaculty) {
            this.id = id; this.senderId = senderId; this.senderName = senderName;
            this.content = content; this.sentAt = sentAt; this.isFaculty = isFaculty;
        }
    }

    public static class Notification {
        public int id, userId, refId;
        public String type, title, body, createdAt;
        public boolean isRead;
        public Notification(int id, int userId, String type, String title, String body, int refId, boolean isRead, String createdAt) {
            this.id = id; this.userId = userId; this.type = type; this.title = title;
            this.body = body; this.refId = refId; this.isRead = isRead; this.createdAt = createdAt;
        }
    }
}

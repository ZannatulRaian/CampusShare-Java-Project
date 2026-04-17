# 🎓 CampusShare

CampusShare is a Java Swing desktop application designed for university campuses.  
It provides a unified platform for students, faculty, and administrators to share academic resources, post announcements, manage events, and communicate — with full offline support and automatic cloud synchronization via Supabase.

Built with Java 17 + SQLite + Supabase (PostgreSQL).

---

## 🚀 Key Features

### 🏠 Dashboard
- Bento-grid layout
- Clickable stat blocks
- Recent notes, events, announcements
- Detail popups for every item

### 📚 Notes & Resources
- Upload any file type (PDF, Word, PPT, Excel, images, etc.)
- Local storage: ~/CampusShare/files/
- Optional cloud storage via Supabase Storage
- Faculty approval system

### 📅 Events
- Faculty can create events
- Title, date, time, category, location
- Clickable detail view

### 📢 Announcements
Tags:
- GENERAL
- URGENT
- ACADEMIC
- FACILITY
- EXAM
- EVENT

Featured top announcement with full-text popup.

### 💬 Chat System
- Channel chats (#general, #cse, #eee, #announcements)
- Direct messages
- Messenger-style UI
- Right/Left bubble layout
- Delete message option
- Online status indicator
- Auto-refresh every 5 seconds

### 👤 Profile
- Edit name, department, semester
- Upload circular avatar
- Change password
- Admin-only cloud connection settings

---

## 🏗 Architecture

Dual-Database System (SQLite-first, Supabase-second)

WRITE:
1. Save to local SQLite
2. If online → write to Supabase
3. Translate local ID → cloud ID
4. Upload files to Supabase Storage

READ:
- Online → Supabase
- Offline → SQLite
- Cached in memory via DataStore

---

## 🗄 Database Schema (v13)

Tables:
- users
- subjects
- resources
- events
- announcements
- messages (channel + dm unified)

---

## 🛠 Tech Stack

- Java 17
- Java Swing + FlatLaf
- SQLite (JDBC)
- Supabase (PostgreSQL)
- Maven
- OkHttp
- BCrypt

---

## 📦 Build & Run

Requirements:
- Java JDK 17+
- Maven 3.6+

Check:

    java -version
    mvn -version

Clean old local data (important):

Windows:
    C:\Users\YourName\CampusShare\

Mac/Linux:
    ~/CampusShare/

Build:

    mvn clean package

Run:

    java -jar target/CampusShare.jar

---

## 🔐 Demo Accounts

Admin:
    admin@campus.edu / admin123

Faculty:
    smith@campus.edu / faculty123

Students:
    alice@campus.edu / student123
    bob@campus.edu / student123

---

## ☁ Supabase Setup (Admin)

1. Create new project at supabase.com
2. Run supabase_schema.sql in SQL Editor
3. Disable Email Confirmation:
   Authentication → Providers → Email → Confirm email OFF
4. Create storage buckets:
   - notes (Public)
   - avatars (Public)
5. Copy:
   - Project URL
   - anon public key
6. Login as Admin → Profile → Cloud Connection → Save & Connect

---

## ⚠ Known Limitations

- Chat uses polling (5s), not realtime websockets
- Offline uploads sync when online
- Local and Supabase passwords can diverge
- Demo users auto-register on first online login

---

## 🧪 Troubleshooting

Always Offline?
- Ensure credentials entered
- URL must start with https://
- anon key begins with eyJhbGci

SQL errors?
Delete:
    ~/CampusShare/
and relaunch.

Unable to access jarfile?
Run:
    java -jar target/CampusShare.jar

---

EOF

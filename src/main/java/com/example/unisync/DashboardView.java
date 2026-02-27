package com.example.unisync;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public class DashboardView {
    private BorderPane mainLayout;

    public BorderPane getView() {
        mainLayout = new BorderPane();

        HBox header = createHeader();
        mainLayout.setTop(header);

        VBox sidebar = createSidebar();
        mainLayout.setLeft(sidebar);

        showHomeView();

        return mainLayout;
    }

    private HBox createHeader() {
        HBox header = new HBox();
        header.setPadding(new Insets(15, 30, 15, 30));
        header.setStyle("-fx-background-color: #ffffff; -fx-border-color: #ecf0f1; -fx-border-width: 0 0 1 0;");
        header.setAlignment(Pos.CENTER_LEFT);

        Label logo = new Label("UniSync Dashboard");
        logo.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        logo.setStyle("-fx-text-fill: #2C3E50;");

        header.getChildren().add(logo);
        return header;
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(10);
        sidebar.setPadding(new Insets(20));
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-background-color: #2C3E50;");

        Button btnHome = createNavButton("🏠 Dashboard");
        Button btnEvents = createNavButton("📅 Events");
        Button btnResources = createNavButton("📚 Resources");
        Button btnChat = createNavButton("💬 Chat");
        Button btnProfile = createNavButton("👤 Profile");
        Button btnLogout = createNavButton("🚪 Logout");

        btnHome.setOnAction(e -> showHomeView());
        btnEvents.setOnAction(e -> showEventsView());
        btnResources.setOnAction(e -> showResourcesView());
        btnChat.setOnAction(e -> showChatView());
        btnProfile.setOnAction(e -> showProfileView());
        btnLogout.setOnAction(e -> HelloApplication.showLoginScene());

        sidebar.getChildren().addAll(btnHome, btnEvents, btnResources, btnChat, btnProfile, new Region(), btnLogout);
        VBox.setVgrow(sidebar.getChildren().get(4), Priority.ALWAYS);

        return sidebar;
    }

    private Button createNavButton(String text) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ecf0f1; -fx-font-size: 14px; -fx-padding: 10; -fx-cursor: hand;");

        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #34495E; -fx-text-fill: white; -fx-padding: 10;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #ecf0f1; -fx-padding: 10;"));

        return btn;
    }

    private void showHomeView() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(30));
        content.setStyle("-fx-background-color: #f8f9fa;");

        Label welcome = new Label("Welcome Back, Student!");
        welcome.setFont(Font.font("Arial", FontWeight.BOLD, 24));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(20);

        grid.add(createStatCard("Upcoming Events", "5 Active", "#3498DB"), 0, 0);
        grid.add(createStatCard("New Resources", "12 Files", "#2ECC71"), 1, 0);
        grid.add(createStatCard("Notifications", "3 New", "#E67E22"), 2, 0);

        content.getChildren().addAll(welcome, new Label("Quick Overview:"), grid);
        mainLayout.setCenter(new ScrollPane(content));
    }

    private void showResourcesView() {
        VBox content = new VBox(25);
        content.setPadding(new Insets(30));
        content.setStyle("-fx-background-color: #f4f7f6;");

        Label title = new Label("Academic Resources");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 26));

        TextField searchBar = new TextField();
        searchBar.setPromptText("Search subjects (e.g. Java, DBMS)...");
        searchBar.setStyle("-fx-padding: 10; -fx-background-radius: 20; -fx-border-color: #ddd; -fx-border-radius: 20;");
        searchBar.setMaxWidth(400);

        FlowPane subjectGrid = new FlowPane(20, 20);
        subjectGrid.setAlignment(Pos.TOP_LEFT);

        subjectGrid.getChildren().addAll(
                createSubjectCard("Object Oriented Programming", "CSE-211", "#3498DB"),
                createSubjectCard("Database Management", "CSE-212", "#E67E22"),
                createSubjectCard("Data Structures", "CSE-213", "#2ECC71"),
                createSubjectCard("Digital Logic Design", "EEE-214", "#9B59B6"),
                createSubjectCard("Discrete Mathematics", "CSE-215", "#E74C3C")
        );

        content.getChildren().addAll(title, searchBar, new Label("Available Subjects:"), subjectGrid);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        mainLayout.setCenter(scrollPane);
    }

    private VBox createSubjectCard(String name, String code, String color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setPrefSize(180, 150);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 15; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5); " +
                "-fx-cursor: hand;");

        Label lblCode = new Label(code);
        lblCode.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");

        Label lblName = new Label(name);
        lblName.setWrapText(true);
        lblName.setTextAlignment(TextAlignment.CENTER);
        lblName.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        card.getChildren().addAll(lblCode, lblName);

        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #f1f1f1; -fx-background-radius: 15; -fx-scale-x: 1.05; -fx-scale-y: 1.05;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);"));

        card.setOnMouseClicked(e -> showFileList(name));
        return card;
    }

    private void showEventsView() {
        VBox content = new VBox(25);
        content.setPadding(new Insets(30));
        content.setStyle("-fx-background-color: #f4f7f6;");

        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Campus Events");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 26));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAddEvent = new Button("+ Add New Event");
        btnAddEvent.setStyle("-fx-background-color: #E67E22; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 20; -fx-cursor: hand;");

        btnAddEvent.setOnAction(e -> showAddEventForm());

        header.getChildren().addAll(title, spacer, btnAddEvent);

        HBox filters = new HBox(15);
        filters.getChildren().addAll(
                new Button("All Events"),
                new Button("Academic"),
                new Button("Club Activity"),
                new Button("Holidays")
        );

        VBox eventList = new VBox(15);

        eventList.getChildren().addAll(
                createEventCard("Tech Fest 2026", "Feb 28, 2026", "09:00 AM", "Main Auditorium", "#3498DB"),
                createEventCard("Mid-Term Examination", "March 05, 2026", "10:30 AM", "Exam Hall", "#E74C3C"),
                createEventCard("Programming Contest", "March 12, 2026", "02:00 PM", "Lab 402", "#2ECC71"),
                createEventCard("Cultural Night", "March 20, 2026", "06:00 PM", "University Field", "#9B59B6")
        );

        content.getChildren().addAll(header, filters, new Separator(), eventList);
        mainLayout.setCenter(new ScrollPane(content));
    }
    private void showAddEventForm() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(40));
        content.setMaxWidth(500);
        content.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Create New Event");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));

        TextField eventTitle = new TextField();
        eventTitle.setPromptText("Event Title");

        DatePicker datePicker = new DatePicker();
        datePicker.setPromptText("Select Date");

        TextField location = new TextField();
        location.setPromptText("Location");

        TextArea description = new TextArea();
        description.setPromptText("Event Description...");
        description.setPrefHeight(100);

        Button btnSubmit = new Button("Post Event");
        btnSubmit.setStyle("-fx-background-color: #2ECC71; -fx-text-fill: white; -fx-padding: 10 30; -fx-font-weight: bold;");
        btnSubmit.setOnAction(e -> showEventsView()); // সাবমিট করলে আবার লিস্টে ফিরে যাবে

        content.getChildren().addAll(title, new Label("Title"), eventTitle, new Label("Date"), datePicker,
                new Label("Location"), location, new Label("Description"), description, btnSubmit);

        mainLayout.setCenter(new ScrollPane(content));
    }

    private HBox createEventCard(String title, String date, String time, String location, String color) {
        HBox card = new HBox(20);
        card.setPadding(new Insets(20));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 10; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.05), 5, 0, 0, 3);");

        VBox colorBar = new VBox();
        colorBar.setPrefWidth(5);
        colorBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 5;");

        VBox details = new VBox(5);
        Label lblTitle = new Label(title);
        lblTitle.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        Label lblInfo = new Label("📅 " + date + "  |  ⏰ " + time + "  |  📍 " + location);
        lblInfo.setStyle("-fx-text-fill: #7F8C8D;");

        details.getChildren().addAll(lblTitle, lblInfo);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnJoin = new Button("View Details");
        btnJoin.setStyle("-fx-background-color: transparent; -fx-border-color: #3498DB; -fx-text-fill: #3498DB; -fx-cursor: hand;");

        card.getChildren().addAll(colorBar, details, spacer, btnJoin);

        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #fcfcfc; -fx-background-radius: 10; -fx-scale-x: 1.01;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: white; -fx-background-radius: 10;"));

        return card;
    }

    private VBox createStatCard(String title, String value, String color) {
        VBox card = new VBox(10);
        card.setPadding(new Insets(20));
        card.setPrefSize(200, 100);
        card.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 10;");

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        Label lblValue = new Label(value);
        lblValue.setStyle("-fx-text-fill: white; -fx-font-size: 20px;");

        card.getChildren().addAll(lblTitle, lblValue);
        return card;
    }

    private void showFileList(String subjectName) {
        VBox content = new VBox(20);
        content.setPadding(new Insets(30));
        content.setStyle("-fx-background-color: #ffffff;");

        Button btnBack = new Button("← Back to Subjects");
        btnBack.setStyle("-fx-background-color: transparent; -fx-text-fill: #3498DB; -fx-cursor: hand;");
        btnBack.setOnAction(e -> showResourcesView());

        Label title = new Label("Resources for: " + subjectName);
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));

        VBox fileList = new VBox(10);

        fileList.getChildren().addAll(
                createFileRow("Lecture_01_Introduction.pdf", "1.2 MB", "PDF"),
                createFileRow("OOP_Principles_Slides.pptx", "4.5 MB", "Slide"),
                createFileRow("Assignment_Brief.docx", "800 KB", "Doc"),
                createFileRow("Lab_Manual_Final.pdf", "2.1 MB", "PDF")
        );

        content.getChildren().addAll(btnBack, title, new Separator(), fileList);
        mainLayout.setCenter(new ScrollPane(content));
    }

    private HBox createFileRow(String fileName, String size, String type) {
        HBox row = new HBox(20);
        row.setPadding(new Insets(15));
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #f9f9f9; -fx-background-radius: 5; -fx-border-color: #eee; -fx-border-width: 0 0 1 0;");

        Label icon = new Label("📄");
        Label name = new Label(fileName);
        name.setPrefWidth(300);
        Label fileSize = new Label(size);
        fileSize.setStyle("-fx-text-fill: gray;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnDownload = new Button("Download");
        btnDownload.setStyle("-fx-background-color: #2ECC71; -fx-text-fill: white; -fx-cursor: hand;");

        row.getChildren().addAll(icon, name, fileSize, spacer, btnDownload);

        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color: #f1f1f1; -fx-background-radius: 5;"));
        row.setOnMouseExited(e -> row.setStyle("-fx-background-color: #f9f9f9; -fx-background-radius: 5;"));

        return row;
    }

    private void showChatView() {
        VBox content = new VBox(20);
        content.setPadding(new Insets(25));
        content.setStyle("-fx-background-color: #f4f7f6;");

        Label title = new Label("Campus Discussions");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 26));

        VBox messageArea = new VBox(15);
        messageArea.setPadding(new Insets(10));
        messageArea.getChildren().addAll(
                createChatMessage("Student Support", "Hello! Does anyone have the Java lab report?", "10:15 AM", false),
                createChatMessage("Rahat (Faculty)", "The lab report deadline is extended to Sunday.", "10:30 AM", true),
                createChatMessage("You", "Thank you, sir! Noted.", "10:35 AM", false)
        );

        ScrollPane chatScroll = new ScrollPane(messageArea);
        chatScroll.setFitToWidth(true);
        chatScroll.setPrefHeight(400);
        chatScroll.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: #ddd;");
        HBox inputArea = new HBox(10);
        TextField messageBox = new TextField();
        messageBox.setPromptText("Type your message here...");
        HBox.setHgrow(messageBox, Priority.ALWAYS);
        messageBox.setStyle("-fx-padding: 10; -fx-background-radius: 20;");

        Button btnSend = new Button("Send ➤");
        btnSend.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-background-radius: 20; -fx-padding: 10 20; -fx-cursor: hand;");

        inputArea.getChildren().addAll(messageBox, btnSend);

        content.getChildren().addAll(title, chatScroll, inputArea);
        mainLayout.setCenter(content);
    }
    private VBox createChatMessage(String sender, String msg, String time, boolean isFaculty) {
        VBox bubble = new VBox(5);
        bubble.setMaxWidth(400);
        bubble.setPadding(new Insets(10));
        String bgColor = isFaculty ? "#FAD7A0" : "#E8F6F3";
        bubble.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 15;");

        Label lblSender = new Label(sender);
        lblSender.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        lblSender.setStyle("-fx-text-fill: #2C3E50;");

        Label lblMsg = new Label(msg);
        lblMsg.setWrapText(true);

        Label lblTime = new Label(time);
        lblTime.setFont(Font.font("Arial", 10));
        lblTime.setStyle("-fx-text-fill: gray;");

        bubble.getChildren().addAll(lblSender, lblMsg, lblTime);

        if(sender.equals("You")) {
            VBox rightAlign = new VBox(bubble);
            rightAlign.setAlignment(Pos.CENTER_RIGHT);
            return rightAlign;
        }

        return bubble;
    }

    private void showProfileView() {
        VBox content = new VBox(25);
        content.setPadding(new Insets(40));
        content.setAlignment(Pos.TOP_CENTER);
        content.setStyle("-fx-background-color: #f8f9fa;");

        VBox profileCard = new VBox(20);
        profileCard.setPadding(new Insets(30));
        profileCard.setMaxWidth(450);
        profileCard.setAlignment(Pos.CENTER);
        profileCard.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");

        StackPane avatar = new StackPane();
        avatar.setPrefSize(100, 100);
        avatar.setMaxSize(100, 100);
        avatar.setStyle("-fx-background-color: #3498DB; -fx-background-radius: 50;");
        Label initial = new Label("S");
        initial.setFont(Font.font("Arial", FontWeight.BOLD, 40));
        initial.setStyle("-fx-text-fill: white;");
        avatar.getChildren().add(initial);

        Label name = new Label("Iron Man");
        name.setFont(Font.font("Arial", FontWeight.BOLD, 22));

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(15);
        infoGrid.setVgap(15);
        infoGrid.setAlignment(Pos.CENTER);

        addProfileRow(infoGrid, "Student ID:", "23", 0);
        addProfileRow(infoGrid, "Department:", "Computer Science", 1);
        addProfileRow(infoGrid, "Email:", "ironman@university.edu", 2);
        addProfileRow(infoGrid, "Semester:", "6th", 3);

        Button btnEdit = new Button("Edit Profile Details");
        btnEdit.setStyle("-fx-background-color: #2C3E50; -fx-text-fill: white; -fx-padding: 10 25; -fx-background-radius: 5; -fx-cursor: hand;");

        profileCard.getChildren().addAll(avatar, name, new Separator(), infoGrid, btnEdit);

        content.getChildren().add(profileCard);
        mainLayout.setCenter(new ScrollPane(content));
    }

    private void addProfileRow(GridPane grid, String label, String value, int row) {
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7F8C8D;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #2C3E50;");
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }
}
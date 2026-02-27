package com.example.unisync;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

public class OnboardingView {
    private StackPane root;
    private VBox layout;

    public OnboardingView() {
        root = new StackPane();
        root.setPrefSize(900, 650);

        layout = new VBox(20);
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: white;");
        layout.setPrefSize(900, 650);

        root.getChildren().add(layout);
        showStep1();
    }

    public Pane getView() {
        return root;
    }

    private void showStep1() {
        layout.getChildren().clear();
        layout.setSpacing(30);
        layout.setPadding(new Insets(80, 200, 80, 200));

        StackPane logoHolder = new StackPane();
        logoHolder.setPrefSize(110, 110);
        logoHolder.setMaxSize(110, 110);
        logoHolder.setStyle("-fx-background-color: linear-gradient(to bottom right, #3498DB, #2980B9); " +
                "-fx-background-radius: 28; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(52,152,219,0.4), 18, 0, 0, 10);");

        Label logoText = new Label("U");
        logoText.setFont(Font.font("Arial", FontWeight.BOLD, 52));
        logoText.setStyle("-fx-text-fill: white;");
        logoHolder.getChildren().add(logoText);

        VBox textSection = new VBox(12);
        textSection.setAlignment(Pos.CENTER);

        Label title = new Label("Welcome to UniSync");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        title.setStyle("-fx-text-fill: #2C3E50;");

        Label desc = new Label("Your all-in-one campus companion.\nManage resources, events, and chats effortlessly.");
        desc.setFont(Font.font("Arial", 16));
        desc.setStyle("-fx-text-fill: #7F8C8D;");
        desc.setTextAlignment(TextAlignment.CENTER);
        desc.setWrapText(true);

        textSection.getChildren().addAll(title, desc);

        Button nextBtn = new Button("Get Started →");
        stylePrimaryButton(nextBtn, "#3498DB", "#2980B9");
        nextBtn.setOnAction(e -> showStep2());

        layout.getChildren().addAll(logoHolder, textSection, nextBtn);
    }

    private void showStep2() {
        layout.getChildren().clear();
        layout.setSpacing(20);
        layout.setPadding(new Insets(60, 150, 60, 150));

        Label title = new Label("Key Features");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        title.setStyle("-fx-text-fill: #2C3E50;");

        VBox featuresBox = new VBox(14);
        featuresBox.setAlignment(Pos.CENTER);
        featuresBox.setMaxWidth(550);

        featuresBox.getChildren().addAll(
                createFeatureItem("📅", "Smart Event Calendar", "Never miss an exam or fest."),
                createFeatureItem("📚", "Resource Hub", "Access all PDFs, notes & slides."),
                createFeatureItem("📢", "Real-time Notices", "Instant campus announcements."),
                createFeatureItem("🚌", "Bus & Campus Map", "Track bus and find rooms.")
        );

        Button nextBtn = new Button("Continue →");
        stylePrimaryButton(nextBtn, "#3498DB", "#2980B9");
        nextBtn.setOnAction(e -> showStep3());

        layout.getChildren().addAll(title, featuresBox, nextBtn);
    }

    private void showStep3() {
        layout.getChildren().clear();
        layout.setSpacing(25);
        layout.setPadding(new Insets(80, 200, 80, 200));

        Label title = new Label("Choose Your Role");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        title.setStyle("-fx-text-fill: #2C3E50;");

        Label sub = new Label("Select your role to personalize your experience");
        sub.setStyle("-fx-text-fill: #7F8C8D; -fx-font-size: 15px;");

        Button studentBtn = new Button("🎓  I am a Student");
        Button facultyBtn = new Button("👨‍🏫  I am a Faculty");

        stylePrimaryButton(studentBtn, "#3498DB", "#2980B9");
        stylePrimaryButton(facultyBtn, "#2ECC71", "#27AE60");

        studentBtn.setOnAction(e -> HelloApplication.showLoginScene());
        facultyBtn.setOnAction(e -> HelloApplication.showLoginScene());

        layout.getChildren().addAll(title, sub, studentBtn, facultyBtn);
    }

    private HBox createFeatureItem(String icon, String title, String subtitle) {
        HBox item = new HBox(18);
        item.setPadding(new Insets(14));
        item.setAlignment(Pos.CENTER_LEFT);
        item.setStyle("-fx-background-color: #F8F9FA; -fx-background-radius: 12; -fx-border-color: #EDF2F4; -fx-border-radius: 12;");

        Label lblIcon = new Label(icon);
        lblIcon.setFont(Font.font(26));

        VBox texts = new VBox(3);
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #2C3E50;");
        Label lblSub = new Label(subtitle);
        lblSub.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");
        texts.getChildren().addAll(lblTitle, lblSub);

        item.getChildren().addAll(lblIcon, texts);
        item.setOnMouseEntered(e -> item.setStyle("-fx-background-color: #EBF5FB; -fx-background-radius: 12; -fx-border-color: #3498DB; -fx-border-radius: 12;"));
        item.setOnMouseExited(e -> item.setStyle("-fx-background-color: #F8F9FA; -fx-background-radius: 12; -fx-border-color: #EDF2F4; -fx-border-radius: 12;"));

        return item;
    }

    private void stylePrimaryButton(Button btn, String color, String hoverColor) {
        btn.setPrefWidth(280);
        btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 13; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 15px;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: " + hoverColor + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 13; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 15px;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 13; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 15px;"));
    }
}

package com.example.unisync;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class SignupView {
    public Pane getView() {
        // Left decorative panel
        VBox leftPanel = new VBox(20);
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setPrefWidth(450);
        leftPanel.setStyle("-fx-background-color: linear-gradient(to bottom right, #27AE60, #2ECC71);");

        Label appName = new Label("UniSync");
        appName.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        appName.setStyle("-fx-text-fill: white;");

        Label tagline = new Label("Join Your Campus Community");
        tagline.setFont(Font.font("Arial", 16));
        tagline.setStyle("-fx-text-fill: rgba(255,255,255,0.85);");

        Label icon = new Label("🎓");
        icon.setFont(Font.font(80));

        leftPanel.getChildren().addAll(icon, appName, tagline);

        // Right form panel
        VBox rightPanel = new VBox(14);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPrefWidth(450);
        rightPanel.setPadding(new Insets(50, 70, 50, 70));
        rightPanel.setStyle("-fx-background-color: white;");

        Label title = new Label("Create Account");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        title.setStyle("-fx-text-fill: #2C3E50;");

        String fieldStyle = "-fx-padding: 11; -fx-background-radius: 8; -fx-border-color: #ddd; -fx-border-radius: 8; -fx-font-size: 14px;";

        TextField nameField = new TextField();
        nameField.setPromptText("Full Name");
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameField.setStyle(fieldStyle);

        TextField emailField = new TextField();
        emailField.setPromptText("University Email");
        emailField.setMaxWidth(Double.MAX_VALUE);
        emailField.setStyle(fieldStyle);

        ComboBox<String> deptBox = new ComboBox<>();
        deptBox.getItems().addAll("CSE", "EEE", "BBA", "English", "Islamic Studies", "Law", "Bangla");
        deptBox.setPromptText("Select Department");
        deptBox.setMaxWidth(Double.MAX_VALUE);
        deptBox.setStyle("-fx-padding: 5; -fx-font-size: 14px;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Create Password");
        passField.setMaxWidth(Double.MAX_VALUE);
        passField.setStyle(fieldStyle);

        Button signupBtn = new Button("Sign Up");
        signupBtn.setMaxWidth(Double.MAX_VALUE);
        signupBtn.setStyle("-fx-background-color: #2ECC71; -fx-text-fill: white; -fx-padding: 13; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 15px;");
        signupBtn.setOnMouseEntered(e -> signupBtn.setStyle("-fx-background-color: #27AE60; -fx-text-fill: white; -fx-padding: 13; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 15px;"));
        signupBtn.setOnMouseExited(e -> signupBtn.setStyle("-fx-background-color: #2ECC71; -fx-text-fill: white; -fx-padding: 13; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 15px;"));

        signupBtn.setOnAction(e -> HelloApplication.showLoginScene());

        Hyperlink loginLink = new Hyperlink("Already have an account? Login");
        loginLink.setStyle("-fx-text-fill: #2ECC71; -fx-font-size: 13px;");
        loginLink.setOnAction(e -> HelloApplication.showLoginScene());

        rightPanel.getChildren().addAll(title, nameField, emailField, deptBox, passField, signupBtn, loginLink);

        HBox root = new HBox(leftPanel, rightPanel);
        root.setPrefSize(900, 650);
        return root;
    }
}

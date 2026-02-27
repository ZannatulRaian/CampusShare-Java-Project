package com.example.unisync;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class LoginView {
    public Pane getView() {
        // Left decorative panel
        VBox leftPanel = new VBox(20);
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setPrefWidth(450);
        leftPanel.setStyle("-fx-background-color: linear-gradient(to bottom right, #2C3E50, #3498DB);");

        Label appName = new Label("UniSync");
        appName.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        appName.setStyle("-fx-text-fill: white;");

        Label tagline = new Label("Connecting Campus");
        tagline.setFont(Font.font("Arial", 18));
        tagline.setStyle("-fx-text-fill: rgba(255,255,255,0.8);");

        Label icon = new Label("🎓");
        icon.setFont(Font.font(80));

        leftPanel.getChildren().addAll(icon, appName, tagline);

        // Right form panel
        VBox rightPanel = new VBox(18);
        rightPanel.setAlignment(Pos.CENTER);
        rightPanel.setPrefWidth(450);
        rightPanel.setPadding(new Insets(60, 70, 60, 70));
        rightPanel.setStyle("-fx-background-color: white;");

        Label title = new Label("Welcome Back");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 30));
        title.setStyle("-fx-text-fill: #2C3E50;");

        Label subtitle = new Label("Login to your account");
        subtitle.setStyle("-fx-text-fill: #7F8C8D; -fx-font-size: 14px;");

        TextField emailField = new TextField();
        emailField.setPromptText("University Email");
        emailField.setMaxWidth(Double.MAX_VALUE);
        emailField.setStyle("-fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #ddd; -fx-border-radius: 8; -fx-font-size: 14px;");

        PasswordField passField = new PasswordField();
        passField.setPromptText("Password");
        passField.setMaxWidth(Double.MAX_VALUE);
        passField.setStyle("-fx-padding: 12; -fx-background-radius: 8; -fx-border-color: #ddd; -fx-border-radius: 8; -fx-font-size: 14px;");

        Button loginBtn = new Button("Login");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-padding: 13; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 15px;");
        loginBtn.setOnMouseEntered(e -> loginBtn.setStyle("-fx-background-color: #2980B9; -fx-text-fill: white; -fx-padding: 13; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 15px;"));
        loginBtn.setOnMouseExited(e -> loginBtn.setStyle("-fx-background-color: #3498DB; -fx-text-fill: white; -fx-padding: 13; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8; -fx-font-size: 15px;"));

        loginBtn.setOnAction(e -> {
            DashboardView dashboard = new DashboardView();
            HelloApplication.setMainContent(dashboard.getView());
        });

        Hyperlink signupLink = new Hyperlink("Don't have an account? Sign Up");
        signupLink.setStyle("-fx-text-fill: #3498DB; -fx-font-size: 13px;");
        signupLink.setOnAction(e -> HelloApplication.showSignupScene());

        rightPanel.getChildren().addAll(title, subtitle, emailField, passField, loginBtn, signupLink);

        HBox root = new HBox(leftPanel, rightPanel);
        root.setPrefSize(900, 650);
        return root;
    }
}

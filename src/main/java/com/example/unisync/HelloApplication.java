package com.example.unisync;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    private static Stage mainStage;
    private static final double W = 900;
    private static final double H = 650;

    @Override
    public void start(Stage stage) {
        mainStage = stage;
        stage.setTitle("UniSync - Connecting Campus");
        stage.setResizable(false);
        stage.setMinWidth(W);
        stage.setMinHeight(H);
        stage.setMaxWidth(W);
        stage.setMaxHeight(H);
        showOnboardingScene();
        stage.centerOnScreen();
        stage.show();
    }

    public static void showOnboardingScene() {
        OnboardingView view = new OnboardingView();
        mainStage.setScene(new Scene(view.getView(), W, H));
    }

    public static void showLoginScene() {
        LoginView view = new LoginView();
        mainStage.setScene(new Scene(view.getView(), W, H));
    }

    public static void showSignupScene() {
        SignupView view = new SignupView();
        mainStage.setScene(new Scene(view.getView(), W, H));
    }

    public static void setMainContent(Pane layout) {
        if (mainStage != null) {
            mainStage.setScene(new Scene(layout, W, H));
        }
    }

    public static void main(String[] args) {
        launch();
    }
}

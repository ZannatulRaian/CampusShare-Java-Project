module com.example.unisync {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.example.unisync to javafx.fxml;
    exports com.example.unisync;
}
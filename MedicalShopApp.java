package com.medicalshop;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

import javafx.stage.Stage;

import javafx.util.Duration;

import java.io.File;
import java.io.FileOutputStream;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

public class MedicalShopApp extends Application {

    // Database configuration
    private static final String DB_SERVER_URL = "jdbc:mysql://localhost:3306/?serverTimezone=UTC";
    private static final String DB_URL = "jdbc:mysql://localhost:3306/medical_shop?serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Jeetu@123";

    // UI Constants
    private static final String APP_FONT = "Segoe UI";
    private static final String HEADER_FONT = "Segoe UI Semibold";
    
    // Modern Gradient Colors
    private final Color COLOR_1 = Color.web("#800080"); // Purple
    private final Color COLOR_2 = Color.web("#008080"); // Teal
    private final Color COLOR_3 = Color.web("#b0e0e6"); // Powder Blue
    
    // UI State
    private Stage primaryStage;
    private TabPane mainTabs;
    private ObservableList<Medicine> medicines = FXCollections.observableArrayList();
    private ObservableList<SaleItem> currentSaleItems = FXCollections.observableArrayList();
    private ObservableList<PreviousInvoice> previousInvoices = FXCollections.observableArrayList();
    
    // Current sale info
    private String currentInvoiceNumber;
    private String currentPaymentMode = "Cash";
    
    // Animation
    private Timeline currentAnimation;
    
    // Reference to medicine combo box in purchase tab
    private ComboBox<String> purchaseMedicineCombo;
    
    // References to Sales Tab controls
    private TextField salesPatientField;
    private TextField salesDoctorField;
    private ComboBox<String> paymentCombo;
    private DatePicker saleDatePicker;
    private TextField salesInvoiceField;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        initializeDatabase();
        currentInvoiceNumber = generateInvoiceNumber();
        
        // Load icon
        try {
            Image icon = new Image("medicine-icon.png");
            primaryStage.getIcons().add(icon);
        } catch (Exception e) {
            System.err.println("Icon not found: " + e.getMessage());
        }
        
        // Remove full screen properties
        primaryStage.setFullScreenExitHint("");
        primaryStage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        
        // Set stage to maximized instead of full screen
        primaryStage.setMaximized(true);
        primaryStage.setTitle("PHARMA RETAIL - Login");
        
        // Set minimum size
        primaryStage.setMinWidth(1024);
        primaryStage.setMinHeight(768);
        
        showLoginScreen();
    }

    // =============== DATABASE OPERATIONS ===============

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_SERVER_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS medical_shop");
        } catch (SQLException e) {
            showAlert("Database Error", "Failed to create database: " + e.getMessage());
            return;
        }

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement()) {

            // Users table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "username VARCHAR(50) UNIQUE NOT NULL, " +
                    "password VARCHAR(100) NOT NULL, " +
                    "full_name VARCHAR(100) NOT NULL, " +
                    "email VARCHAR(100), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

            // Medicines table (removed purchase_rate)
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS medicines (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(255) NOT NULL, " +
                    "batch_number VARCHAR(255), " +
                    "mrp DECIMAL(10,2) NOT NULL, " +
                    "rate DECIMAL(10,2) NOT NULL, " +
                    "expiry_date DATE, " +
                    "hsn_code VARCHAR(20), " +
                    "pack VARCHAR(50), " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "INDEX idx_name (name))");

            // Purchases table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS purchases (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "medicine_id INT NOT NULL, " +
                    "supplier VARCHAR(100), " +
                    "batch_number VARCHAR(255), " +
                    "quantity INT NOT NULL, " +
                    "cost_price DECIMAL(10,2) NOT NULL, " +
                    "purchase_date DATE, " +
                    "invoice_no VARCHAR(50), " +
                    "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE, " +
                    "INDEX idx_purchase_date (purchase_date))");

            // Sales table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS sales (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "invoice_no VARCHAR(20) UNIQUE, " +
                    "medicine_id INT NOT NULL, " +
                    "customer VARCHAR(100), " +
                    "patient_name VARCHAR(100), " +
                    "prescribed_by VARCHAR(100), " +
                    "payment_mode VARCHAR(20) DEFAULT 'Cash', " +
                    "quantity INT NOT NULL, " +
                    "mrp DECIMAL(10,2), " +
                    "rate DECIMAL(10,2) NOT NULL, " +
                    "discount_percent DECIMAL(5,2) DEFAULT 0, " +
                    "net_rate DECIMAL(10,2), " +
                    "amount DECIMAL(10,2), " +
                    "sale_date DATE, " +
                    "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE, " +
                    "INDEX idx_invoice (invoice_no), " +
                    "INDEX idx_sale_date (sale_date))");

            // Medicine stock table
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS medicine_stock (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "medicine_id INT NOT NULL, " +
                    "stock_quantity INT NOT NULL DEFAULT 0, " +
                    "last_updated DATE, " +
                    "FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE, " +
                    "UNIQUE KEY unique_medicine (medicine_id))");

        } catch (SQLException e) {
            showAlert("Database Error", "Failed to initialize tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String generateInvoiceNumber() {
        String sql = "SELECT MAX(CAST(SUBSTRING(invoice_no, 3) AS UNSIGNED)) as max_num FROM sales";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            int nextNumber = 1; // Default starting number
            
            if (rs.next()) {
                String maxNumStr = rs.getString("max_num");
                if (maxNumStr != null) {
                    nextNumber = Integer.parseInt(maxNumStr) + 1;
                }
            }
            
            // Generate invoice number with leading zeros
            String invoiceNo = "A-" + String.format("%06d", nextNumber);
            return invoiceNo;
            
        } catch (SQLException e) {
            System.err.println("Failed to generate invoice number: " + e.getMessage());
            // Fallback: Use current date time
            String timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return "A-" + timestamp + "-001";
        } catch (NumberFormatException e) {
            System.err.println("Error parsing invoice number: " + e.getMessage());
            return "A-000001";
        }
    }

    // =============== ANIMATION EFFECTS ===============

    private void startGradientAnimation(Region region) {
        if (currentAnimation != null) {
            currentAnimation.stop();
        }

        SimpleDoubleProperty hueShift = new SimpleDoubleProperty(0);

        // Faster animation - 3 seconds instead of 8
        currentAnimation = new Timeline(
            new KeyFrame(Duration.ZERO, new KeyValue(hueShift, 0.0)),
            new KeyFrame(Duration.seconds(3), new KeyValue(hueShift, 1.0))
        );
        currentAnimation.setAutoReverse(true);
        currentAnimation.setCycleCount(Timeline.INDEFINITE);

        hueShift.addListener((obs, oldVal, newVal) -> {
            double t = newVal.doubleValue();
            
            // Interpolate colors
            Color c1 = (Color) Interpolator.LINEAR.interpolate(COLOR_1, COLOR_3, t);
            Color c2 = (Color) Interpolator.LINEAR.interpolate(COLOR_2, COLOR_1, t);
            
            // Faster movement of gradient focus
            double endX = 1.0 + (t * 0.4); // Increased from 0.2
            double endY = 1.0 + (t * 0.2); // Increased from 0.1

            LinearGradient lg = new LinearGradient(
                0, 0, endX, endY, true, CycleMethod.NO_CYCLE,
                new Stop(0, c1), new Stop(1, c2)
            );
            
            region.setBackground(new Background(new BackgroundFill(lg, CornerRadii.EMPTY, Insets.EMPTY)));
        });

        currentAnimation.play();
    }
    
    private void applyCardStyle(Region region) {
        region.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); -fx-background-radius: 15;");
        region.setEffect(new DropShadow(20, Color.rgb(0,0,0,0.2)));
    }

    // =============== LOGIN SCREEN ===============

    private void showLoginScreen() {
        primaryStage.setTitle("PHARMA RETAIL - Login");
        
        StackPane root = new StackPane();
        startGradientAnimation(root);

        VBox loginBox = new VBox(25);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setPadding(new Insets(40));
        loginBox.setMaxWidth(450);
        loginBox.setMaxHeight(500);
        applyCardStyle(loginBox);

        // Pharmacy icon
        ImageView pharmacyIcon = new ImageView();
        pharmacyIcon.setFitWidth(80);
        pharmacyIcon.setFitHeight(80);
        pharmacyIcon.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 2, 2);");
        
        try {
            Image icon = new Image("medicine-icon.png");
            pharmacyIcon.setImage(icon);
        } catch (Exception e) {}

        Label title = new Label("PHARMA RETAIL");
        title.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 32));
        title.setTextFill(COLOR_1);
        
        Label subTitle = new Label("Management System");
        subTitle.setFont(Font.font(APP_FONT, 16));
        subTitle.setTextFill(Color.GRAY);
        
        Separator sep = new Separator();
        sep.setPadding(new Insets(10, 0, 10, 0));

        TextField userField = createStyledTextField("Username", "admin");
        PasswordField passField = createStyledPasswordField("Password", "admin123");

        Button loginBtn = createPrimaryButton("LOGIN", 300);
        Hyperlink registerLink = new Hyperlink("Create New Account");
        registerLink.setFont(Font.font(APP_FONT, 12));

        // Event handlers
        loginBtn.setOnAction(e -> handleLogin(userField.getText(), passField.getText()));
        registerLink.setOnAction(e -> showRegisterScreen());
        
        // Keyboard navigation
        userField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) passField.requestFocus();
        });
        
        passField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) loginBtn.fire();
        });

        loginBox.getChildren().addAll(pharmacyIcon, title, subTitle, sep, 
            new Label("Username"), userField, new Label("Password"), passField, 
            new Label(""), loginBtn, registerLink);
        root.getChildren().add(loginBox);

        Scene scene = new Scene(root, 1200, 800);
        setupGlobalShortcuts(scene);
        primaryStage.setScene(scene);
        
        // Center on screen and show maximized
        primaryStage.centerOnScreen();
        primaryStage.show();
        
        // Auto-focus
        Platform.runLater(userField::requestFocus);
    }

    // =============== REGISTRATION SCREEN ===============

    private void showRegisterScreen() {
        StackPane root = new StackPane();
        startGradientAnimation(root);
        
        VBox regBox = new VBox(20);
        regBox.setAlignment(Pos.CENTER);
        regBox.setPadding(new Insets(40));
        regBox.setMaxWidth(500);
        applyCardStyle(regBox);
        
        Label title = new Label("CREATE ACCOUNT");
        title.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 26));
        title.setTextFill(COLOR_2);
        
        TextField fullName = createStyledTextField("Full Name", "");
        TextField email = createStyledTextField("Email", "");
        TextField username = createStyledTextField("Username", "");
        PasswordField pass = createStyledPasswordField("Password", "");
        PasswordField confirm = createStyledPasswordField("Confirm Password", "");
        
        Button regBtn = createPrimaryButton("Register", 250);
        Button backBtn = new Button("Back to Login");
        backBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #555; -fx-underline: true; -fx-cursor: hand;");
        backBtn.setOnAction(e -> showLoginScreen());
        
        regBtn.setOnAction(e -> handleRegistration(
            fullName.getText(), 
            email.getText(), 
            username.getText(), 
            pass.getText(), 
            confirm.getText()
        ));
        
        // Keyboard navigation
        Control[] fields = {fullName, email, username, pass, confirm, regBtn, backBtn};
        setupTabNavigation(fields);
        
        regBox.getChildren().addAll(title, new Separator(), 
            fullName, email, username, pass, confirm, regBtn, backBtn);
        root.getChildren().add(regBox);
        
        primaryStage.getScene().setRoot(root);
        Platform.runLater(fullName::requestFocus);
    }

    // =============== HOMEPAGE ===============

    private void showHomePage() {
        StackPane rootStack = new StackPane();
        startGradientAnimation(rootStack);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: transparent;");
        
        // Header
        HBox header = new HBox(20);
        header.setPadding(new Insets(15, 30, 15, 30));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); " +
                       "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Label appTitle = new Label("PHARMA RETAIL PRO");
        appTitle.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 24));
        appTitle.setTextFill(COLOR_1);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label userLabel = new Label("Welcome, Admin");
        userLabel.setFont(Font.font(APP_FONT, FontWeight.BOLD, 14));
        userLabel.setTextFill(Color.DARKSLATEGRAY);
        
        Button logoutBtn = createLogoutButton();
        
        header.getChildren().addAll(appTitle, spacer, userLabel, logoutBtn);
        root.setTop(header);
        
        // Main Content - Home Page Cards
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(30);
        grid.setVgap(30);
        grid.setPadding(new Insets(50));
        
        // Create feature cards
        VBox medicineEntryCard = createFeatureCard("Medicine Entry", "Add new medicines to inventory", "F1 / Alt+E", COLOR_1);
        medicineEntryCard.setOnMouseClicked(e -> showMainDashboard(0));
        
        VBox stockCard = createFeatureCard("Stock Management", "View and manage medicine stock", "F2 / Alt+T", COLOR_2);
        stockCard.setOnMouseClicked(e -> showMainDashboard(1));
        
        VBox purchaseCard = createFeatureCard("Purchase", "Record medicine purchases", "F3 / Alt+P", Color.web("#F18F01"));
        purchaseCard.setOnMouseClicked(e -> showMainDashboard(2));
        
        VBox salesCard = createFeatureCard("Sales & Billing", "Create sales bills and invoices", "F4 / Alt+S", Color.web("#2E86AB"));
        salesCard.setOnMouseClicked(e -> showMainDashboard(3));
        
        VBox billsCard = createFeatureCard("Previous Bills", "View and modify previous invoices", "F5 / Alt+B", Color.web("#A23B72"));
        billsCard.setOnMouseClicked(e -> showMainDashboard(4));
        
        VBox reportsCard = createFeatureCard("Reports", "Generate various reports", "F6 / Alt+R", Color.web("#00bcd4"));
        reportsCard.setOnMouseClicked(e -> showMainDashboard(5));
        
        // Add cards to grid
        grid.add(medicineEntryCard, 0, 0);
        grid.add(stockCard, 1, 0);
        grid.add(purchaseCard, 2, 0);
        grid.add(salesCard, 0, 1);
        grid.add(billsCard, 1, 1);
        grid.add(reportsCard, 2, 1);
        
        root.setCenter(grid);
        rootStack.getChildren().add(root);

        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(rootStack, 1400, 900);
            primaryStage.setScene(scene);
        } else {
            primaryStage.getScene().setRoot(rootStack);
        }
        
        primaryStage.setTitle("PHARMA RETAIL - Dashboard");
        setupGlobalShortcuts(scene);
        
        // Keep maximized
        primaryStage.setMaximized(true);
        
        loadAllMedicinesToMemory();
    }
    
    private VBox createFeatureCard(String title, String description, String shortcut, Color color) {
        VBox card = new VBox(20);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30));
        card.setMinWidth(250);
        card.setMinHeight(200);
        card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); " +
                     "-fx-background-radius: 15; " +
                     "-fx-border-color: " + toHex(color) + "; " +
                     "-fx-border-width: 2; " +
                     "-fx-border-radius: 15;");
        card.setEffect(new DropShadow(20, Color.rgb(0,0,0,0.2)));
        card.setCursor(javafx.scene.Cursor.HAND);
        
        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 20));
        titleLabel.setTextFill(color);
        
        Label descLabel = new Label(description);
        descLabel.setFont(Font.font(APP_FONT, 14));
        descLabel.setTextFill(Color.DARKGRAY);
        descLabel.setWrapText(true);
        descLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        Label shortcutLabel = new Label("Shortcut: " + shortcut);
        shortcutLabel.setFont(Font.font(APP_FONT, FontWeight.BOLD, 12));
        shortcutLabel.setTextFill(Color.GRAY);
        
        card.getChildren().addAll(titleLabel, descLabel, shortcutLabel);
        
        // Hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle("-fx-background-color: " + toHex(color.brighter().brighter()) + "; " +
                         "-fx-background-radius: 15; " +
                         "-fx-border-color: " + toHex(color) + "; " +
                         "-fx-border-width: 2; " +
                         "-fx-border-radius: 15;");
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); " +
                         "-fx-background-radius: 15; " +
                         "-fx-border-color: " + toHex(color) + "; " +
                         "-fx-border-width: 2; " +
                         "-fx-border-radius: 15;");
        });
        
        return card;
    }

    // =============== MAIN DASHBOARD WITH TABS ===============

    private void showMainDashboard(int tabIndex) {
        StackPane rootStack = new StackPane();
        startGradientAnimation(rootStack);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: transparent;");
        
        // Header
        HBox header = new HBox(20);
        header.setPadding(new Insets(15, 30, 15, 30));
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); " +
                       "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");
        
        Button homeBtn = new Button("← Home");
        homeBtn.setStyle("-fx-background-color: " + toHex(COLOR_1) + "; -fx-text-fill: white; " +
                        "-fx-font-weight: bold; -fx-background-radius: 20; " +
                        "-fx-cursor: hand; -fx-padding: 8 20;");
        homeBtn.setOnAction(e -> showHomePage());
        
        Label appTitle = new Label("PHARMA RETAIL PRO");
        appTitle.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 24));
        appTitle.setTextFill(COLOR_1);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Label userLabel = new Label("Welcome, Admin");
        userLabel.setFont(Font.font(APP_FONT, FontWeight.BOLD, 14));
        userLabel.setTextFill(Color.DARKSLATEGRAY);
        
        Button logoutBtn = createLogoutButton();
        
        header.getChildren().addAll(homeBtn, appTitle, spacer, userLabel, logoutBtn);
        root.setTop(header);
        
        // Tabs
        mainTabs = new TabPane();
        mainTabs.setStyle("-fx-tab-min-width: 120; -fx-tab-min-height: 40; " +
                         "-fx-font-family: '" + APP_FONT + "'; -fx-font-size: 14;");
        
        mainTabs.getTabs().addAll(
            createMedicineEntryTab(), 
            createStockTab(), 
            createPurchaseTab(), 
            createSalesTab(),
            createPreviousBillsTab(),
            createReportsTab()
        );
        
        // Select the requested tab
        if (tabIndex >= 0 && tabIndex < mainTabs.getTabs().size()) {
            mainTabs.getSelectionModel().select(tabIndex);
        }
        
        StackPane contentPane = new StackPane(mainTabs);
        contentPane.setPadding(new Insets(20));
        root.setCenter(contentPane);
        
        rootStack.getChildren().add(root);

        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(rootStack, 1400, 900);
            primaryStage.setScene(scene);
        } else {
            primaryStage.getScene().setRoot(rootStack);
        }
        
        primaryStage.setTitle("PHARMA RETAIL - " + mainTabs.getSelectionModel().getSelectedItem().getText());
        setupGlobalShortcuts(scene);
        
        // Keep maximized
        primaryStage.setMaximized(true);
        
        loadAllMedicinesToMemory();
    }

    // =============== MEDICINE ENTRY TAB ===============

    private Tab createMedicineEntryTab() {
        Tab tab = new Tab("Medicine Entry");
        tab.setClosable(false);

        StackPane centerContainer = new StackPane();
        centerContainer.setPadding(new Insets(30));
        centerContainer.setStyle("-fx-background-color: transparent;");

        VBox card = new VBox(20);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(600);
        card.setMaxHeight(650);
        card.setPadding(new Insets(40));
        applyCardStyle(card);

        Label title = new Label("Add New Medicine");
        title.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 24));
        title.setTextFill(Color.BLACK);
        
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        // Fields
        TextField nameField = createStyledTextField("Medicine Name *", "");
        TextField batchField = createStyledTextField("Batch Number", "");
        TextField mrpField = createStyledTextField("MRP *", "");
        TextField rateField = createStyledTextField("Rate *", "");
        TextField expiryField = createStyledTextField("Expiry (DD/MM/YYYY)", "");
        TextField hsnField = createStyledTextField("HSN Code", "");
        TextField packField = createStyledTextField("Pack (e.g., 100ML)", "");

        // Adding to Grid
        grid.add(createStyledLabel("Medicine Name:"), 0, 0); 
        grid.add(nameField, 1, 0);
        grid.add(createStyledLabel("Batch Number:"), 0, 1); 
        grid.add(batchField, 1, 1);
        grid.add(createStyledLabel("MRP:"), 0, 2); 
        grid.add(mrpField, 1, 2);
        grid.add(createStyledLabel("Rate:"), 0, 3); 
        grid.add(rateField, 1, 3);
        grid.add(createStyledLabel("Expiry Date:"), 0, 4); 
        grid.add(expiryField, 1, 4);
        grid.add(createStyledLabel("HSN Code:"), 0, 5); 
        grid.add(hsnField, 1, 5);
        grid.add(createStyledLabel("Pack:"), 0, 6); 
        grid.add(packField, 1, 6);

        Button addBtn = createPrimaryButton("Add Medicine", 200);
        Button clearBtn = createSecondaryButton("Clear", 120);
        
        HBox actions = new HBox(15, addBtn, clearBtn);
        actions.setAlignment(Pos.CENTER);

        // Keyboard navigation
        Control[] fields = {nameField, batchField, mrpField, rateField, 
                           expiryField, hsnField, packField, addBtn, clearBtn};
        setupTabNavigation(fields);

        // Event handlers
        addBtn.setOnAction(e -> {
            try {
                String name = nameField.getText().trim();
                String batch = batchField.getText().trim();
                String mrpText = mrpField.getText().trim();
                String rateText = rateField.getText().trim();
                String expiry = expiryField.getText().trim();
                String hsn = hsnField.getText().trim();
                String pack = packField.getText().trim();

                // Validation
                if (name.isEmpty() || mrpText.isEmpty() || rateText.isEmpty()) {
                    showAlert("Validation Error", "Please fill required fields: Name, MRP, Rate.");
                    return;
                }

                double mrp = Double.parseDouble(mrpText);
                double rate = Double.parseDouble(rateText);
                
                if (rate > mrp) {
                    showAlert("Validation Error", "Rate cannot be higher than MRP.");
                    return;
                }
                
                java.sql.Date sqlDate = null;
                if (!expiry.isEmpty()) {
                    String[] parts = expiry.split("/");
                    if (parts.length == 3) {
                        int day = Integer.parseInt(parts[0]);
                        int month = Integer.parseInt(parts[1]);
                        int year = Integer.parseInt(parts[2]);
                        if (year < 100) year += 2000;
                        sqlDate = java.sql.Date.valueOf(
                            LocalDate.of(year, month, day)
                        );
                    } else {
                        throw new IllegalArgumentException("Invalid date format");
                    }
                }
                
                addMedicineToDB(name, batch, mrp, rate, sqlDate, hsn, pack);
                showAlert("Success", "Medicine added successfully!");
                
                // Clear fields
                nameField.clear(); 
                batchField.clear(); 
                mrpField.clear(); 
                rateField.clear();
                expiryField.clear(); 
                hsnField.clear(); 
                packField.clear();
                
                loadAllMedicinesToMemory();
                refreshPurchaseMedicineCombo();
                nameField.requestFocus();

            } catch (NumberFormatException nfe) {
                showAlert("Validation Error", "Invalid number format for MRP or Rate.");
            } catch (IllegalArgumentException iae) {
                showAlert("Validation Error", "Invalid expiry date format. Use DD/MM/YYYY.");
            } catch (SQLException sqle) {
                showAlert("DB Error", "Failed to add medicine: " + sqle.getMessage());
            }
        });
        
        clearBtn.setOnAction(e -> {
            nameField.clear(); 
            batchField.clear(); 
            mrpField.clear(); 
            rateField.clear();
            expiryField.clear(); 
            hsnField.clear(); 
            packField.clear();
            nameField.requestFocus();
        });

        card.getChildren().addAll(title, new Separator(), grid, new Separator(), actions);
        centerContainer.getChildren().add(card);
        tab.setContent(centerContainer);
        return tab;
    }

    //=============== STOCK MANAGEMENT TAB ===============

    private Tab createStockTab() {
        Tab tab = new Tab("Stock Management");
        tab.setClosable(false);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 10;");
        
        HBox tools = new HBox(15);
        Button refresh = createSecondaryButton("Refresh", 120);
        Button export = createAccentButton("Export CSV", 120);
        tools.getChildren().addAll(refresh, export);
        
        TableView<Medicine> table = new TableView<>(medicines);
        setupStockColumns(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);
        
        refresh.setOnAction(e -> { 
            loadAllMedicinesToMemory(); 
            table.refresh(); 
        });
        
        export.setOnAction(e -> exportStockToCSV());
        
        root.getChildren().addAll(
            createStyledLabel("Current Inventory", 18),
            tools, table
        );
        
        tab.setContent(root);
        return tab;
    }

    //=============== PURCHASE MANAGEMENT TAB ===============

    private Tab createPurchaseTab() {
        Tab tab = new Tab("Purchase");
        tab.setClosable(false);
        
        StackPane centerContainer = new StackPane();
        centerContainer.setPadding(new Insets(30));
        centerContainer.setStyle("-fx-background-color: transparent;");

        VBox card = new VBox(20);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(700);
        card.setPadding(new Insets(40));
        applyCardStyle(card);

        Label title = new Label("Record Purchase");
        title.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 24));
        title.setTextFill(Color.BLACK);
        
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        // Fields - centered like Medicine Entry
        purchaseMedicineCombo = new ComboBox<>(loadMedicineNames());
        purchaseMedicineCombo.setPromptText("Select Medicine"); 
        purchaseMedicineCombo.setPrefWidth(250);
        purchaseMedicineCombo.setStyle("-fx-font-size: 14;");
        
        TextField invoiceField = createStyledTextField("Invoice No", "");
        TextField supp = createStyledTextField("Supplier Name", "");
        TextField batch = createStyledTextField("Batch Number", "");
        TextField qty = createStyledTextField("Quantity", "");
        TextField mrpField = createStyledTextField("MRP", "");
        TextField rateField = createStyledTextField("Rate", "");
        DatePicker date = new DatePicker(LocalDate.now());
        date.setPrefWidth(150);
        
        //Add to Grid - Centered layout
        grid.add(createStyledLabel("Medicine:"), 0, 0); 
        grid.add(purchaseMedicineCombo, 1, 0);
        grid.add(createStyledLabel("Invoice No:"), 0, 1); 
        grid.add(invoiceField, 1, 1);
        grid.add(createStyledLabel("Supplier:"), 0, 2); 
        grid.add(supp, 1, 2);
        grid.add(createStyledLabel("Batch:"), 0, 3); 
        grid.add(batch, 1, 3);
        grid.add(createStyledLabel("Quantity:"), 0, 4); 
        grid.add(qty, 1, 4);
        grid.add(createStyledLabel("MRP:"), 0, 5); 
        grid.add(mrpField, 1, 5);
        grid.add(createStyledLabel("Rate:"), 0, 6); 
        grid.add(rateField, 1, 6);
        grid.add(createStyledLabel("Date:"), 0, 7); 
        grid.add(date, 1, 7);
        
        HBox buttons = new HBox(15);
        Button saveBtn = createPrimaryButton("Record Purchase", 180);
        Button clearBtn = createSecondaryButton("Clear", 120);
        buttons.setAlignment(Pos.CENTER);
        buttons.getChildren().addAll(saveBtn, clearBtn);
        
        // Keyboard navigation
        Control[] fields = {purchaseMedicineCombo, invoiceField, supp, batch, qty, mrpField, rateField, date, saveBtn, clearBtn};
        setupTabNavigation(fields);
        
        saveBtn.setOnAction(e -> {
            try {
                String medicine = purchaseMedicineCombo.getValue();
                String invoiceNo = invoiceField.getText().trim();
                String supplier = supp.getText().trim();
                String batchNo = batch.getText().trim();
                String qtyText = qty.getText().trim();
                String mrpText = mrpField.getText().trim();
                String rateText = rateField.getText().trim();
                
                if (medicine == null || qtyText.isEmpty() || rateText.isEmpty()) {
                    showAlert("Validation Error", "Please select medicine and enter quantity & rate.");
                    return;
                }
                
                int quantity = Integer.parseInt(qtyText);
                double rate = Double.parseDouble(rateText);
                double mrp = mrpText.isEmpty() ? rate : Double.parseDouble(mrpText);
                
                if (quantity <= 0 || rate <= 0) {
                    showAlert("Validation Error", "Quantity and rate must be greater than 0.");
                    return;
                }
                
                if (mrp < rate) {
                    showAlert("Validation Error", "MRP cannot be less than rate.");
                    return;
                }
                
                recordPurchase(medicine, invoiceNo, supplier, batchNo, quantity, mrp, rate, date.getValue());
                showAlert("Success", "Purchase recorded and stock updated!");
                
                supp.clear(); 
                batch.clear(); 
                qty.clear(); 
                mrpField.clear();
                rateField.clear();
                invoiceField.clear();
                loadAllMedicinesToMemory();
                refreshPurchaseMedicineCombo();
                purchaseMedicineCombo.requestFocus();
                
            } catch (NumberFormatException nfe) {
                showAlert("Validation Error", "Please enter valid numbers for quantity and rate.");
            } catch (SQLException sqle) {
                showAlert("DB Error", "Failed to record purchase: " + sqle.getMessage());
            }
        });
        
        clearBtn.setOnAction(e -> {
            purchaseMedicineCombo.setValue(null);
            invoiceField.clear();
            supp.clear();
            batch.clear();
            qty.clear();
            mrpField.clear();
            rateField.clear();
            date.setValue(LocalDate.now());
            purchaseMedicineCombo.requestFocus();
        });
        
        card.getChildren().addAll(title, new Separator(), grid, new Separator(), buttons);
        centerContainer.getChildren().add(card);
        tab.setContent(centerContainer);
        return tab;
    }

    //=============== SALES & BILLING TAB ===============

    @SuppressWarnings("unchecked")
	private Tab createSalesTab() {
        Tab tab = new Tab("Sales & Billing");
        tab.setClosable(false);
        
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 10;");
        
        // Header Info
        GridPane top = new GridPane();
        top.setHgap(15); 
        top.setVgap(10);
        
        // Store references to these controls
        salesInvoiceField = createStyledTextField("", currentInvoiceNumber);
        salesInvoiceField.setEditable(false);
        salesInvoiceField.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ddd; " +
                                  "-fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8;");
        
        saleDatePicker = new DatePicker(LocalDate.now());
        saleDatePicker.setPrefWidth(150);
        
        salesPatientField = createStyledTextField("Patient Name", "");
        salesDoctorField = createStyledTextField("Doctor Name", "");
        
        paymentCombo = new ComboBox<>();
        paymentCombo.getItems().addAll("Cash", "Card", "UPI", "Online");
        paymentCombo.setValue("Cash");
        paymentCombo.setPrefWidth(120);
        
        paymentCombo.setOnAction(e -> currentPaymentMode = paymentCombo.getValue());
        
        // Add keyboard navigation for these fields
        salesPatientField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                salesDoctorField.requestFocus();
            } else if (e.getCode() == KeyCode.TAB) {
                salesDoctorField.requestFocus();
                e.consume();
            }
        });
        
        salesDoctorField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                paymentCombo.requestFocus();
                e.consume();
            }
        });
        
        paymentCombo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
       
                BorderPane content = root;
                VBox center = (VBox) content.getCenter();
                HBox itemRow = (HBox) center.getChildren().get(0);
                ComboBox<Medicine> medCombo = (ComboBox<Medicine>) itemRow.getChildren().get(1);
                medCombo.requestFocus();
            }
        });
        
        top.add(createStyledLabel("Invoice:"), 0, 0); 
        top.add(salesInvoiceField, 1, 0);
        top.add(createStyledLabel("Date:"), 2, 0); 
        top.add(saleDatePicker, 3, 0);
        top.add(createStyledLabel("Patient:"), 0, 1); 
        top.add(salesPatientField, 1, 1);
        top.add(createStyledLabel("Doctor:"), 2, 1); 
        top.add(salesDoctorField, 3, 1);
        top.add(createStyledLabel("Payment:"), 4, 0); 
        top.add(paymentCombo, 5, 0);
        
        root.setTop(top);
        
        // Items Entry
        VBox center = new VBox(10);
        center.setPadding(new Insets(15, 0, 15, 0));
        
        HBox itemRow = new HBox(10);
        itemRow.setAlignment(Pos.CENTER_LEFT);
        
        ComboBox<Medicine> medCombo = new ComboBox<>(medicines); 
        medCombo.setPromptText("Select Medicine"); 
        medCombo.setPrefWidth(250);
        
        TextField qty = createStyledTextField("Qty", ""); 
        qty.setPrefWidth(70);
        
        TextField rate = createStyledTextField("Rate", ""); 
        rate.setPrefWidth(90);
        
        TextField disc = createStyledTextField("Disc %", "0"); 
        disc.setPrefWidth(70);
        
        Button add = createAccentButton("Add Item", 100);
        
        medCombo.setOnAction(e -> {
            if (medCombo.getValue() != null) {
                rate.setText(String.format("%.2f", medCombo.getValue().getRate()));
            }
        });
        
        // Enhanced keyboard navigation for item entry
        medCombo.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                qty.requestFocus();
            } else if (e.getCode() == KeyCode.TAB) {
                qty.requestFocus();
                e.consume();
            }
        });
        
        qty.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                if (medCombo.getValue() != null) {
                    rate.setText(String.valueOf(medCombo.getValue().getRate()));
                    disc.requestFocus();
                } else {
                    rate.requestFocus();
                }
                e.consume();
            }
        });
        
        rate.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                disc.requestFocus();
                e.consume();
            }
        });
        
        disc.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                add.fire();
            } else if (e.getCode() == KeyCode.TAB) {
                add.requestFocus();
                e.consume();
            }
        });
        
        add.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                add.fire();
            }
        });
        
        add.setOnAction(e -> addItemToSale(medCombo, qty, rate, disc));
        
        itemRow.getChildren().addAll(
            new Label("Medicine:"), medCombo, 
            new Label("Qty:"), qty,
            new Label("Rate:"), rate,
            new Label("Disc%:"), disc, 
            add
        );
        
        TableView<SaleItem> table = new TableView<>(currentSaleItems);
        setupSaleColumns(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);
        
        center.getChildren().addAll(itemRow, table);
        root.setCenter(center);
        
        // Bottom (Total & Actions)
        HBox bottom = new HBox(20);
        bottom.setAlignment(Pos.CENTER_RIGHT);
        
        // Total display
        VBox totalBox = new VBox(5);
        Label totalLabel = new Label("Total Amount:");
        totalLabel.setFont(Font.font(APP_FONT, FontWeight.BOLD, 14));
        Label totalValue = new Label("₹0.00");
        totalValue.setFont(Font.font(HEADER_FONT, FontWeight.BOLD, 18));
        totalValue.setTextFill(COLOR_2);
        totalBox.getChildren().addAll(totalLabel, totalValue);
        
        // Update total when items change
        currentSaleItems.addListener((javafx.collections.ListChangeListener.Change<? extends SaleItem> c) -> {
            double total = currentSaleItems.stream()
                .mapToDouble(SaleItem::getTotal)
                .sum();
            totalValue.setText(String.format("₹%.2f", total));
        });
        
        // Action buttons with proper text wrapping
        Button save = createPrimaryButton("Save Sale", 120);
        Button print = createAccentButton("Print Bill", 100);
        Button pdf = createSecondaryButton("PDF Bill", 100);
        Button clear = new Button("New Sale");
        clear.setStyle("-fx-background-color: #757575; -fx-text-fill: white; " +
                      "-fx-background-radius: 5; -fx-padding: 8 15;");
        
        save.setOnAction(e -> {
            saveCurrentSale(salesPatientField.getText(), salesDoctorField.getText(), 
                          currentPaymentMode, saleDatePicker.getValue());
        });
        
        print.setOnAction(e -> {
            if (currentSaleItems.isEmpty()) {
                showAlert("Empty Sale", "Add items before printing.");
                return;
            }
            printGSTBill(currentSaleItems, salesPatientField.getText(), salesDoctorField.getText(), 
                        salesInvoiceField.getText(), saleDatePicker.getValue(), currentPaymentMode);
        });
        
        pdf.setOnAction(e -> {
            if (currentSaleItems.isEmpty()) {
                showAlert("Empty Sale", "Add items before generating PDF.");
                return;
            }
            generatePDFBill(primaryStage, currentSaleItems, salesPatientField.getText(), 
                          salesDoctorField.getText(), salesInvoiceField.getText(), 
                          saleDatePicker.getValue(), currentPaymentMode);
        });
        
        clear.setOnAction(e -> {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle("New Sale");
            alert.setHeaderText("Start new sale?");
            alert.setContentText("Current sale will be cleared.");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                clearCurrentSale();
                salesPatientField.clear();
                salesDoctorField.clear();
                paymentCombo.setValue("Cash");
                saleDatePicker.setValue(LocalDate.now());
                medCombo.requestFocus();
            }
        });
        
        bottom.getChildren().addAll(totalBox, save, print, pdf, clear);
        root.setBottom(bottom);
        
        tab.setContent(root);
        return tab;
    }

    // =============== PREVIOUS BILLS TAB ===============

    private Tab createPreviousBillsTab() {
        Tab tab = new Tab("Previous Bills");
        tab.setClosable(false);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 10;");
        
        Label title = createStyledLabel("Previous Bills & Modifications", 18);
        
        HBox searchBox = new HBox(10);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        
        TextField searchField = createStyledTextField("Search by Invoice No or Patient", "");
        searchField.setPrefWidth(300);
        
        DatePicker fromDate = new DatePicker(LocalDate.now().minusDays(30));
        DatePicker toDate = new DatePicker(LocalDate.now());
        
        Button searchBtn = createPrimaryButton("Search", 100);
        Button refreshBtn = createSecondaryButton("Refresh", 100);
        
        searchBox.getChildren().addAll(
            new Label("Search:"), searchField,
            new Label("From:"), fromDate,
            new Label("To:"), toDate,
            searchBtn, refreshBtn
        );
        
        TableView<PreviousInvoice> table = new TableView<>(previousInvoices);
        setupPreviousBillsColumns(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);
        
        // Action buttons
        HBox actionBox = new HBox(10);
        Button viewBtn = createAccentButton("View Details", 120);
        Button modifyBtn = createPrimaryButton("Modify Bill", 120);
        Button reprintBtn = createSecondaryButton("Reprint", 100);
        Button deleteBtn = new Button("Delete");
        deleteBtn.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; " +
                          "-fx-background-radius: 5; -fx-padding: 8 15;");
        
        actionBox.getChildren().addAll(viewBtn, modifyBtn, reprintBtn, deleteBtn);
        
        // Event handlers
        searchBtn.setOnAction(e -> loadPreviousInvoices(searchField.getText(), 
            fromDate.getValue(), toDate.getValue()));
        
        refreshBtn.setOnAction(e -> {
            loadPreviousInvoices("", fromDate.getValue(), toDate.getValue());
            searchField.clear();
        });
        
        viewBtn.setOnAction(e -> {
            PreviousInvoice selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                viewInvoiceDetails(selected.getInvoiceNo());
            }
        });
        
        modifyBtn.setOnAction(e -> {
            PreviousInvoice selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                modifyInvoice(selected.getInvoiceNo());
            }
        });
        
        reprintBtn.setOnAction(e -> {
            PreviousInvoice selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                reprintInvoice(selected.getInvoiceNo());
            }
        });
        
        deleteBtn.setOnAction(e -> {
            PreviousInvoice selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteInvoice(selected.getInvoiceNo());
            }
        });
        
        // Load initial data
        loadPreviousInvoices("", fromDate.getValue(), toDate.getValue());
        
        root.getChildren().addAll(title, searchBox, table, actionBox);
        tab.setContent(root);
        return tab;
    }

    // =============== REPORTS TAB ===============

    private Tab createReportsTab() {
        Tab tab = new Tab("Reports");
        tab.setClosable(false);
        
        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(255,255,255,0.95); -fx-background-radius: 10;");
        
        Label title = createStyledLabel("Reports & Analytics", 18);
        
        HBox btns = new HBox(15);
        Button daily = createPrimaryButton("Daily Sales", 120);
        Button monthly = createSecondaryButton("Monthly Sales", 140);
        Button stock = createAccentButton("Stock Report", 120);
        Button expiry = createPrimaryButton("Expiry Alert", 120);
        
        btns.getChildren().addAll(daily, monthly, stock, expiry);
        
        TextArea area = new TextArea();
        area.setFont(Font.font("Consolas", 12));
        area.setEditable(false);
        area.setStyle("-fx-control-inner-background: #f8f9fa;");
        VBox.setVgrow(area, Priority.ALWAYS);
        
        daily.setOnAction(e -> generateDailySalesReport(area));
        monthly.setOnAction(e -> generateMonthlySalesReport(area));
        stock.setOnAction(e -> generateStockReport(area));
        expiry.setOnAction(e -> generateExpiryReport(area));
        
        root.getChildren().addAll(title, btns, area);
        tab.setContent(root);
        return tab;
    }

    // =============== DATABASE HELPER METHODS ===============

    private void addMedicineToDB(String name, String batch, double mrp, double rate, 
                                Date expiry, String hsn, String pack) throws SQLException {
        String sql = "INSERT INTO medicines (name, batch_number, mrp, rate, expiry_date, hsn_code, pack) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, batch);
            ps.setDouble(3, mrp);
            ps.setDouble(4, rate);
            ps.setDate(5, expiry);
            ps.setString(6, hsn);
            ps.setString(7, pack);
            ps.executeUpdate();
            
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int medId = generatedKeys.getInt(1);
                    initializeStock(medId);
                }
            }
        }
    }

    private void initializeStock(int medicineId) throws SQLException {
        String sql = "INSERT INTO medicine_stock (medicine_id, stock_quantity, last_updated) " +
                    "VALUES (?, 0, CURDATE())";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            ps.executeUpdate();
        } catch (SQLException e) {
            // If already exists, update
            String updateSql = "UPDATE medicine_stock SET last_updated = CURDATE() WHERE medicine_id = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                 PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setInt(1, medicineId);
                ps.executeUpdate();
            }
        }
    }
    
    private void recordPurchase(String medicineName, String invoiceNo, String supplier, String batchNumber, 
                               int quantity, double mrp, double costPrice, LocalDate purchaseDate) throws SQLException {
        Medicine m = getMedicineByName(medicineName);
        if (m == null) throw new SQLException("Medicine not found: " + medicineName);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);
            try {
                // First, update medicine mrp and rate if they exist
                String updateMed = "UPDATE medicines SET mrp = ?, rate = ? WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateMed)) {
                    ps.setDouble(1, mrp);
                    ps.setDouble(2, costPrice);
                    ps.setInt(3, m.getId());
                    ps.executeUpdate();
                }

                // Record purchase
                String insert = "INSERT INTO purchases (medicine_id, invoice_no, supplier, batch_number, quantity, cost_price, purchase_date) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setInt(1, m.getId());
                    ps.setString(2, invoiceNo);
                    ps.setString(3, supplier);
                    ps.setString(4, batchNumber);
                    ps.setInt(5, quantity);
                    ps.setDouble(6, costPrice);
                    ps.setDate(7, java.sql.Date.valueOf(purchaseDate));
                    ps.executeUpdate();
                }

                // Update stock
                String updateStock = "UPDATE medicine_stock SET stock_quantity = stock_quantity + ?, last_updated = CURDATE() " +
                                   "WHERE medicine_id = ?";
                try (PreparedStatement ps2 = conn.prepareStatement(updateStock)) {
                    ps2.setInt(1, quantity);
                    ps2.setInt(2, m.getId());
                    ps2.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
    
    private void addItemToSale(ComboBox<Medicine> combo, TextField qty, TextField rate, TextField disc) {
        if (combo.getValue() == null) {
            showAlert("Validation Error", "Please select a medicine.");
            return;
        }
        
        if (qty.getText().trim().isEmpty()) {
            showAlert("Validation Error", "Please enter quantity.");
            return;
        }
        
        try {
            int quantity = Integer.parseInt(qty.getText().trim());
            double price = rate.getText().trim().isEmpty() ? 
                         combo.getValue().getRate() : 
                         Double.parseDouble(rate.getText().trim());
            double discount = disc.getText().trim().isEmpty() ? 
                           0 : Double.parseDouble(disc.getText().trim());
            
            if (quantity <= 0) {
                showAlert("Validation Error", "Quantity must be greater than 0.");
                return;
            }
            
            if (price <= 0) {
                showAlert("Validation Error", "Rate must be greater than 0.");
                return;
            }
            
            if (discount < 0 || discount > 100) {
                showAlert("Validation Error", "Discount must be between 0 and 100.");
                return;
            }
            
            Medicine medicine = combo.getValue();
            if (quantity > medicine.getStock()) {
                showAlert("Stock Error", "Insufficient stock. Available: " + medicine.getStock());
                return;
            }
            
            SaleItem item = new SaleItem(medicine.getName(), quantity, price);
            item.setDiscountPercent(discount);
            item.setBatchNumber(medicine.getBatchNumber());
            item.setExpiryDate(medicine.getExpiryDate());
            item.setHsnCode(medicine.getHsnCode());
            item.setPack(medicine.getPack());
            item.setMrp(medicine.getMrp());
            
            currentSaleItems.add(item);
            
            // Clear input fields
            qty.clear();
            rate.clear();
            disc.setText("0");
            combo.requestFocus();
            
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Please enter valid numbers.");
        }
    }
    
    private void saveCurrentSale(String patient, String doctor, String paymentMode, LocalDate saleDate) {
        if (currentSaleItems.isEmpty()) {
            showAlert("Empty Sale", "Please add items before saving.");
            salesPatientField.requestFocus();
            return;
        }

        if (patient == null || patient.trim().isEmpty()) {
            showAlert("Validation Error", "Please enter Patient Name.");
            salesPatientField.requestFocus();
            return;
        }

        String invoiceNo = currentInvoiceNumber;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            conn.setAutoCommit(false);
            
            try {
                for (SaleItem item : currentSaleItems) {
                    Medicine medicine = getMedicineByName(item.getMedicineName());
                    if (medicine == null) {
                        throw new SQLException("Medicine not found: " + item.getMedicineName());
                    }
                    
                    // Check stock
                    int currentStock = getStockForMedicine(medicine.getId());
                    if (item.getQuantity() > currentStock) {
                        throw new SQLException("Insufficient stock for " + item.getMedicineName() + 
                                              ". Available: " + currentStock);
                    }
                    
                    String sql = "INSERT INTO sales (invoice_no, medicine_id, patient_name, prescribed_by, " +
                                "payment_mode, quantity, mrp, rate, discount_percent, net_rate, amount, sale_date) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setString(1, invoiceNo);
                        ps.setInt(2, medicine.getId());
                        ps.setString(3, patient);
                        ps.setString(4, doctor);
                        ps.setString(5, paymentMode);
                        ps.setInt(6, item.getQuantity());
                        ps.setDouble(7, item.getMrp());
                        ps.setDouble(8, item.getPrice());
                        ps.setDouble(9, item.getDiscountPercent());
                        ps.setDouble(10, item.getNetRate());
                        ps.setDouble(11, item.getTotal());
                        ps.setDate(12, java.sql.Date.valueOf(saleDate));
                        ps.executeUpdate();
                    }
                    
                    // Update stock
                    String updateStock = "UPDATE medicine_stock SET stock_quantity = stock_quantity - ?, last_updated = CURDATE() " +
                                        "WHERE medicine_id = ?";
                    try (PreparedStatement ps = conn.prepareStatement(updateStock)) {
                        ps.setInt(1, item.getQuantity());
                        ps.setInt(2, medicine.getId());
                        ps.executeUpdate();
                    }
                }
                
                conn.commit();
                
                // Generate PDF automatically after save
                generatePDFAfterSave(invoiceNo, patient, doctor, saleDate, paymentMode);
                
                // Show success message
                Alert successAlert = new Alert(AlertType.INFORMATION);
                successAlert.setTitle("Success");
                successAlert.setHeaderText("Sale Saved Successfully!");
                successAlert.setContentText("Invoice No: " + invoiceNo + "\nPDF has been generated automatically.");
                successAlert.showAndWait();
                
                // Clear current sale and setup new one
                clearCurrentSale();
                loadAllMedicinesToMemory();
                refreshPurchaseMedicineCombo();
                
                // Update invoice field with new invoice number
                if (salesInvoiceField != null) {
                    salesInvoiceField.setText(currentInvoiceNumber);
                }
                
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            showAlert("DB Error", "Failed to record sale: " + ex.getMessage());
        }
    }
    
    private void generatePDFAfterSave(String invoiceNo, String patient, String doctor, 
                                     LocalDate saleDate, String paymentMode) {
        try {
            // Create PDF directory if not exists
            File pdfDir = new File("MedicalBills");
            if (!pdfDir.exists()) {
                pdfDir.mkdir();
            }
            
            String fileName = "Medical_Bill_" + invoiceNo + "_" + 
                            saleDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".pdf";
            File file = new File(pdfDir, fileName);
            
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();
            
            com.itextpdf.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            com.itextpdf.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            com.itextpdf.text.Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            
            Paragraph title = new Paragraph("GST INVOICE\nPHARMA RETAIL STORE\n\n", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            
            Paragraph address = new Paragraph(
                "SHNO C-19, Opp. Mascoat School, Near Natraj Colony\n" +
                "Foysagar Road, Ajmer(Raj), Ph: 8426831176\n" +
                "GSTIN: 08ACNPA3859Q1ZW\n\n", normalFont);
            address.setAlignment(Element.ALIGN_CENTER);
            document.add(address);
            
            Paragraph patientInfo = new Paragraph();
            patientInfo.add(new Phrase("Patient: ", headerFont));
            patientInfo.add(new Phrase(patient.isEmpty() ? "N/A" : patient + "\n"));
            patientInfo.add(new Phrase("Doctor: ", headerFont));
            patientInfo.add(new Phrase(doctor.isEmpty() ? "N/A" : doctor + "\n"));
            patientInfo.add(new Phrase("Invoice No.: ", headerFont));
            patientInfo.add(new Phrase(invoiceNo + "\n"));
            patientInfo.add(new Phrase("Date: ", headerFont));
            patientInfo.add(new Phrase(saleDate.format(DateTimeFormatter.ofPattern("dd/MM/yy")) + "\n"));
            patientInfo.add(new Phrase("Payment Mode: ", headerFont));
            patientInfo.add(new Phrase(paymentMode + "\n\n"));
            document.add(patientInfo);
            
            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            
            table.addCell(new PdfPCell(new Phrase("Qty", headerFont)));
            table.addCell(new PdfPCell(new Phrase("Product Name", headerFont)));
            table.addCell(new PdfPCell(new Phrase("Rate", headerFont)));
            table.addCell(new PdfPCell(new Phrase("Disc%", headerFont)));
            table.addCell(new PdfPCell(new Phrase("Amount", headerFont)));
            
            double totalAmount = 0;
            double totalDiscount = 0;
            
            for (SaleItem item : currentSaleItems) {
                table.addCell(new PdfPCell(new Phrase(String.valueOf(item.getQuantity()), normalFont)));
                table.addCell(new PdfPCell(new Phrase(item.getMedicineName(), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.format("₹%.2f", item.getPrice()), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.format("%.1f%%", item.getDiscountPercent()), normalFont)));
                table.addCell(new PdfPCell(new Phrase(String.format("₹%.2f", item.getTotal()), normalFont)));
                
                totalAmount += item.getPrice() * item.getQuantity();
                totalDiscount += item.getPrice() * item.getQuantity() * (item.getDiscountPercent() / 100);
            }
            
            document.add(table);
            document.add(new Paragraph(" "));
            
            double taxableAmount = totalAmount - totalDiscount;
            double tax = taxableAmount * 0.05;
            double netAmount = taxableAmount + tax;
            
            Paragraph summary = new Paragraph();
            summary.add(new Phrase(String.format("Items: %d\n", currentSaleItems.size())));
            summary.add(new Phrase(String.format("Total: ₹%.2f\n", totalAmount)));
            summary.add(new Phrase(String.format("Discount: ₹%.2f\n", totalDiscount)));
            summary.add(new Phrase(String.format("Taxable Amount: ₹%.2f\n", taxableAmount)));
            summary.add(new Phrase(String.format("SGST+CGST (5%%): ₹%.2f\n", tax)));
            summary.add(new Phrase("----------------------------------------------------------------------------------------------------------------------------------\n"));
            summary.add(new Phrase(String.format("%130s ₹%.2f\n", "Net Amount:", netAmount)));
            summary.add(new Phrase(String.format("%130s %s\n\n", "Payment Mode:", paymentMode)));
            summary.add(new Phrase("Thank You! Visit Again\n"));
            summary.add(new Phrase("E & O E\n"));
            summary.add(new Phrase("Subject to AJMER Jurisdiction.\n"));
            summary.add(new Phrase("=========================================================================="));
            document.add(summary);
            
            document.close();
            
        } catch (Exception ex) {
            System.err.println("Failed to generate PDF after save: " + ex.getMessage());
        }
    }
    
    private void clearCurrentSale() {
        currentSaleItems.clear();
        currentInvoiceNumber = generateInvoiceNumber();
    }
    
    private void loadAllMedicinesToMemory() {
        medicines.clear();
        String sql = "SELECT m.id, m.name, m.batch_number, m.mrp, m.rate, m.expiry_date, " +
                     "m.hsn_code, m.pack, COALESCE(ms.stock_quantity, 0) as stock " +
                     "FROM medicines m LEFT JOIN medicine_stock ms ON m.id = ms.medicine_id " +
                     "ORDER BY m.name";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String batchNumber = rs.getString("batch_number");
                double mrp = rs.getDouble("mrp");
                double rate = rs.getDouble("rate");
                int stock = rs.getInt("stock");
                Date expiry = rs.getDate("expiry_date");
                String hsnCode = rs.getString("hsn_code");
                String pack = rs.getString("pack");
                String expiryStr = expiry != null ? expiry.toString() : "";
                
                medicines.add(new Medicine(id, name, batchNumber, mrp, rate, stock, expiryStr, hsnCode, pack));
            }
        } catch (SQLException e) {
            System.err.println("Error loading medicines: " + e.getMessage());
        }
    }
    
    private void loadPreviousInvoices(String search, LocalDate fromDate, LocalDate toDate) {
        previousInvoices.clear();
        StringBuilder sql = new StringBuilder(
            "SELECT DISTINCT invoice_no, patient_name, prescribed_by, payment_mode, sale_date, " +
            "SUM(amount) as total_amount " +
            "FROM sales WHERE 1=1 "
        );
        
        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (invoice_no LIKE ? OR patient_name LIKE ?) ");
        }
        
        if (fromDate != null) {
            sql.append(" AND sale_date >= ? ");
        }
        
        if (toDate != null) {
            sql.append(" AND sale_date <= ? ");
        }
        
        sql.append(" GROUP BY invoice_no, patient_name, prescribed_by, payment_mode, sale_date ");
        sql.append(" ORDER BY sale_date DESC, invoice_no DESC ");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim() + "%";
                ps.setString(paramIndex++, searchPattern);
                ps.setString(paramIndex++, searchPattern);
            }
            
            if (fromDate != null) {
                ps.setDate(paramIndex++, java.sql.Date.valueOf(fromDate));
            }
            
            if (toDate != null) {
                ps.setDate(paramIndex++, java.sql.Date.valueOf(toDate));
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    previousInvoices.add(new PreviousInvoice(
                        rs.getString("invoice_no"),
                        rs.getString("patient_name"),
                        rs.getString("prescribed_by"),
                        rs.getString("payment_mode"),
                        rs.getDate("sale_date").toLocalDate(),
                        rs.getDouble("total_amount")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading previous invoices: " + e.getMessage());
            showAlert("Error", "Failed to load previous invoices: " + e.getMessage());
        }
    }
    
    private void viewInvoiceDetails(String invoiceNo) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Invoice Details");
        dialog.setHeaderText("Invoice No: " + invoiceNo);
        
        VBox content = new VBox(10);
        content.setPadding(new Insets(20));
        content.setPrefSize(600, 400);
        
        TextArea detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(Font.font("Monospaced", 12));
        VBox.setVgrow(detailsArea, Priority.ALWAYS);
        
        StringBuilder details = new StringBuilder();
        details.append("INVOICE DETAILS\n");
        details.append("================\n\n");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Get invoice header
            String headerSql = "SELECT DISTINCT patient_name, prescribed_by, payment_mode, sale_date " +
                             "FROM sales WHERE invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(headerSql)) {
                ps.setString(1, invoiceNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        details.append("Patient: ").append(rs.getString("patient_name")).append("\n");
                        details.append("Doctor: ").append(rs.getString("prescribed_by")).append("\n");
                        details.append("Payment Mode: ").append(rs.getString("payment_mode")).append("\n");
                        details.append("Date: ").append(rs.getDate("sale_date")).append("\n\n");
                    }
                }
            }
            
            // Get invoice items
            String itemsSql = "SELECT m.name, s.quantity, s.rate, s.discount_percent, s.amount " +
                            "FROM sales s JOIN medicines m ON s.medicine_id = m.id " +
                            "WHERE s.invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setString(1, invoiceNo);
                try (ResultSet rs = ps.executeQuery()) {
                    details.append("ITEMS:\n");
                    details.append("-------------------------------------------------\n");
                    details.append(String.format("%-25s %6s %8s %8s %10s\n", 
                        "Medicine", "Qty", "Rate", "Disc%", "Amount"));
                    details.append("-------------------------------------------------\n");
                    
                    double total = 0;
                    while (rs.next()) {
                        details.append(String.format("%-25s %6d %8.2f %7.1f%% %10.2f\n",
                            rs.getString("name"),
                            rs.getInt("quantity"),
                            rs.getDouble("rate"),
                            rs.getDouble("discount_percent"),
                            rs.getDouble("amount")));
                        total += rs.getDouble("amount");
                    }
                    
                    details.append("-------------------------------------------------\n");
                    details.append(String.format("%52s %10.2f\n", "TOTAL:", total));
                }
            }
        } catch (SQLException e) {
            details.append("\nError loading details: ").append(e.getMessage());
        }
        
        detailsArea.setText(details.toString());
        content.getChildren().add(detailsArea);
        
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }
    
    private void modifyInvoice(String invoiceNo) {
        // Ask for confirmation
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Modify Invoice");
        confirm.setHeaderText("Modify Invoice: " + invoiceNo);
        confirm.setContentText("This will load the invoice into the sales tab for modification.\n" +
                              "The original invoice will be deleted and a new one created.\n" +
                              "Continue?");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Load invoice data
                loadInvoiceForModification(invoiceNo);
                
                // Switch to Sales tab
                mainTabs.getSelectionModel().select(3);
                
                showAlert("Invoice Loaded", "Invoice " + invoiceNo + " loaded for modification.");
                
            } catch (SQLException e) {
                showAlert("Error", "Failed to load invoice: " + e.getMessage());
            }
        }
    }
    
    private void loadInvoiceForModification(String invoiceNo) throws SQLException {
        // Clear current sale
        currentSaleItems.clear();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Get invoice header
            String headerSql = "SELECT DISTINCT patient_name, prescribed_by, payment_mode, sale_date " +
                             "FROM sales WHERE invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(headerSql)) {
                ps.setString(1, invoiceNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // Set fields in sales tab
                        if (salesPatientField != null) salesPatientField.setText(rs.getString("patient_name"));
                        if (salesDoctorField != null) salesDoctorField.setText(rs.getString("prescribed_by"));
                        if (paymentCombo != null) paymentCombo.setValue(rs.getString("payment_mode"));
                        if (saleDatePicker != null) saleDatePicker.setValue(rs.getDate("sale_date").toLocalDate());
                    }
                }
            }
            
            // Get invoice items
            String itemsSql = "SELECT m.name, s.quantity, s.rate, s.discount_percent, s.mrp, " +
                            "m.batch_number, m.expiry_date, m.hsn_code, m.pack " +
                            "FROM sales s JOIN medicines m ON s.medicine_id = m.id " +
                            "WHERE s.invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setString(1, invoiceNo);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String medicineName = rs.getString("name");
                        int quantity = rs.getInt("quantity");
                        double rate = rs.getDouble("rate");
                        double discount = rs.getDouble("discount_percent");
                        double mrp = rs.getDouble("mrp");
                        String batch = rs.getString("batch_number");
                        String expiry = rs.getString("expiry_date");
                        String hsn = rs.getString("hsn_code");
                        String pack = rs.getString("pack");
                        
                        SaleItem item = new SaleItem(medicineName, quantity, rate);
                        item.setDiscountPercent(discount);
                        item.setMrp(mrp);
                        item.setBatchNumber(batch);
                        item.setExpiryDate(expiry);
                        item.setHsnCode(hsn);
                        item.setPack(pack);
                        
                        currentSaleItems.add(item);
                    }
                }
            }
            
            // Delete the original invoice (we'll create a new one)
            String deleteSql = "DELETE FROM sales WHERE invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                ps.setString(1, invoiceNo);
                ps.executeUpdate();
            }
            
            // Restore stock
            String restoreStockSql = "UPDATE medicine_stock ms " +
                                   "JOIN sales s ON ms.medicine_id = s.medicine_id " +
                                   "SET ms.stock_quantity = ms.stock_quantity + s.quantity " +
                                   "WHERE s.invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(restoreStockSql)) {
                ps.setString(1, invoiceNo);
                ps.executeUpdate();
            }
            
        }
    }
    
    private void reprintInvoice(String invoiceNo) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // Get invoice data
            List<SaleItem> items = FXCollections.observableArrayList();
            String patient = "";
            String doctor = "";
            LocalDate date = LocalDate.now();
            String paymentMode = "Cash";
            
            // Get header
            String headerSql = "SELECT DISTINCT patient_name, prescribed_by, payment_mode, sale_date " +
                             "FROM sales WHERE invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(headerSql)) {
                ps.setString(1, invoiceNo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        patient = rs.getString("patient_name");
                        doctor = rs.getString("prescribed_by");
                        paymentMode = rs.getString("payment_mode");
                        date = rs.getDate("sale_date").toLocalDate();
                    }
                }
            }
            
            // Get items
            String itemsSql = "SELECT m.name, s.quantity, s.rate, s.discount_percent, s.amount, s.mrp " +
                            "FROM sales s JOIN medicines m ON s.medicine_id = m.id " +
                            "WHERE s.invoice_no = ?";
            try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
                ps.setString(1, invoiceNo);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        SaleItem item = new SaleItem(
                            rs.getString("name"),
                            rs.getInt("quantity"),
                            rs.getDouble("rate")
                        );
                        item.setDiscountPercent(rs.getDouble("discount_percent"));
                        item.setMrp(rs.getDouble("mrp"));
                        items.add(item);
                    }
                }
            }
            
            // Ask for print or PDF
            Alert choice = new Alert(AlertType.CONFIRMATION);
            choice.setTitle("Reprint Invoice");
            choice.setHeaderText("Reprint Invoice: " + invoiceNo);
            choice.setContentText("Choose output format:");
            
            ButtonType printBtn = new ButtonType("Print");
            ButtonType pdfBtn = new ButtonType("PDF");
            ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            
            choice.getButtonTypes().setAll(printBtn, pdfBtn, cancelBtn);
            
            Optional<ButtonType> result = choice.showAndWait();
            if (result.isPresent()) {
                if (result.get() == printBtn) {
                    printGSTBill(items, patient, doctor, invoiceNo, date, paymentMode);
                } else if (result.get() == pdfBtn) {
                    generatePDFBill(primaryStage, items, patient, doctor, invoiceNo, date, paymentMode);
                }
            }
            
        } catch (SQLException e) {
            showAlert("Error", "Failed to reprint invoice: " + e.getMessage());
        }
    }
    
    private void deleteInvoice(String invoiceNo) {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Delete Invoice");
        confirm.setHeaderText("Delete Invoice: " + invoiceNo);
        confirm.setContentText("This will permanently delete the invoice and restore stock.\n" +
                              "Are you sure?");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                conn.setAutoCommit(false);
                
                try {
                    // Restore stock first
                    String restoreSql = "UPDATE medicine_stock ms " +
                                      "JOIN sales s ON ms.medicine_id = s.medicine_id " +
                                      "SET ms.stock_quantity = ms.stock_quantity + s.quantity " +
                                      "WHERE s.invoice_no = ?";
                    try (PreparedStatement ps = conn.prepareStatement(restoreSql)) {
                        ps.setString(1, invoiceNo);
                        ps.executeUpdate();
                    }
                    
                    // Delete invoice
                    String deleteSql = "DELETE FROM sales WHERE invoice_no = ?";
                    try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                        ps.setString(1, invoiceNo);
                        ps.executeUpdate();
                    }
                    
                    conn.commit();
                    
                    // Refresh the list
                    loadPreviousInvoices("", LocalDate.now().minusDays(30), LocalDate.now());
                    loadAllMedicinesToMemory();
                    
                    showAlert("Success", "Invoice deleted successfully.");
                    
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                showAlert("Error", "Failed to delete invoice: " + e.getMessage());
            }
        }
    }
    
    private Medicine getMedicineByName(String name) {
        for (Medicine m : medicines) {
            if (m.getName().equalsIgnoreCase(name)) return m;
        }
        return null;
    }
    
    private ObservableList<String> loadMedicineNames() {
        ObservableList<String> list = FXCollections.observableArrayList();
        String sql = "SELECT name FROM medicines ORDER BY name";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("Error loading medicine names: " + e.getMessage());
        }
        return list;
    }
    
    private int getStockForMedicine(int medicineId) throws SQLException {
        String sql = "SELECT stock_quantity FROM medicine_stock WHERE medicine_id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, medicineId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("stock_quantity");
                }
                return 0;
            }
        }
    }
    
    private void refreshPurchaseMedicineCombo() {
        if (purchaseMedicineCombo != null) {
            purchaseMedicineCombo.setItems(loadMedicineNames());
        }
    }

    // =============== REPORT GENERATION ===============

    private void generateDailySalesReport(TextArea reportArea) {
        StringBuilder report = new StringBuilder();
        report.append("DAILY SALES REPORT\n");
        report.append("===================\n");
        report.append("Date: ").append(LocalDate.now()).append("\n\n");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT invoice_no, SUM(amount) as total FROM sales " +
                        "WHERE DATE(sale_date) = CURDATE() GROUP BY invoice_no";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                double dailyTotal = 0;
                int count = 0;
                
                while (rs.next()) {
                    report.append(String.format("Invoice: %-12s Total: ₹%-10.2f\n",
                        rs.getString("invoice_no"), rs.getDouble("total")));
                    dailyTotal += rs.getDouble("total");
                    count++;
                }
                
                report.append("\n").append("=".repeat(40)).append("\n");
                report.append(String.format("Total Invoices: %d\n", count));
                report.append(String.format("Daily Total: ₹%.2f\n", dailyTotal));
            }
        } catch (SQLException e) {
            report.append("\nError: ").append(e.getMessage());
        }
        
        reportArea.setText(report.toString());
    }
    
    private void generateMonthlySalesReport(TextArea reportArea) {
        StringBuilder report = new StringBuilder();
        report.append("MONTHLY SALES REPORT\n");
        report.append("====================\n\n");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT DATE_FORMAT(sale_date, '%Y-%m') as month, " +
                        "COUNT(DISTINCT invoice_no) as invoices, SUM(amount) as total " +
                        "FROM sales GROUP BY DATE_FORMAT(sale_date, '%Y-%m') " +
                        "ORDER BY month DESC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                double grandTotal = 0;
                
                while (rs.next()) {
                    report.append(String.format("Month: %-10s Invoices: %-5d Total: ₹%-10.2f\n",
                        rs.getString("month"), rs.getInt("invoices"), rs.getDouble("total")));
                    grandTotal += rs.getDouble("total");
                }
                
                report.append("\n").append("=".repeat(50)).append("\n");
                report.append(String.format("Grand Total: ₹%.2f\n", grandTotal));
            }
        } catch (SQLException e) {
            report.append("\nError: ").append(e.getMessage());
        }
        
        reportArea.setText(report.toString());
    }
    
    private void generateStockReport(TextArea reportArea) {
        StringBuilder report = new StringBuilder();
        report.append("STOCK REPORT\n");
        report.append("============\n\n");
        
        int lowStock = 0;
        int outOfStock = 0;
        double totalValue = 0;
        
        for (Medicine med : medicines) {
            String status = "";
            if (med.getStock() == 0) {
                status = "[OUT OF STOCK]";
                outOfStock++;
            } else if (med.getStock() < 10) {
                status = "[LOW STOCK]";
                lowStock++;
            }
            
            report.append(String.format("%-30s Stock: %-5d MRP: ₹%-8.2f %s\n",
                med.getName(), med.getStock(), med.getMrp(), status));
            
            totalValue += med.getStock() * med.getRate();
        }
        
        report.append("\n").append("=".repeat(60)).append("\n");
        report.append(String.format("Total Items: %d\n", medicines.size()));
        report.append(String.format("Low Stock (<10): %d\n", lowStock));
        report.append(String.format("Out of Stock: %d\n", outOfStock));
        report.append(String.format("Total Stock Value: ₹%.2f\n", totalValue));
        
        reportArea.setText(report.toString());
    }
    
    private void generateExpiryReport(TextArea reportArea) {
        StringBuilder report = new StringBuilder();
        report.append("EXPIRY ALERT REPORT\n");
        report.append("===================\n\n");
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            String sql = "SELECT name, batch_number, expiry_date, stock_quantity " +
                        "FROM medicines m JOIN medicine_stock ms ON m.id = ms.medicine_id " +
                        "WHERE expiry_date IS NOT NULL AND expiry_date <= DATE_ADD(CURDATE(), INTERVAL 90 DAY) " +
                        "ORDER BY expiry_date";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                int critical = 0;
                int warning = 0;
                
                while (rs.next()) {
                    Date expiry = rs.getDate("expiry_date");
                    long days = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(), expiry.toLocalDate());
                    
                    String alert = "";
                    if (days <= 30) {
                        alert = "[CRITICAL - " + days + " days]";
                        critical++;
                    } else if (days <= 60) {
                        alert = "[WARNING - " + days + " days]";
                        warning++;
                    } else {
                        alert = "[ALERT - " + days + " days]";
                    }
                    
                    report.append(String.format("%-25s Exp: %-12s Stock: %-5d %s\n",
                        rs.getString("name"), expiry.toString(), 
                        rs.getInt("stock_quantity"), alert));
                }
                
                report.append("\n").append("=".repeat(70)).append("\n");
                report.append(String.format("Critical (<30 days): %d\n", critical));
                report.append(String.format("Warning (30-60 days): %d\n", warning));
                report.append(String.format("Total Expiring Soon: %d\n", critical + warning));
            }
        } catch (SQLException e) {
            report.append("\nError: ").append(e.getMessage());
        }
        
        reportArea.setText(report.toString());
    }
    
    private void exportStockToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Stock to CSV");
        fileChooser.setInitialFileName("stock_report_" + LocalDate.now() + ".csv");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(file)) {
                writer.println("Medicine Name,Batch Number,MRP,Rate,Stock,Expiry Date,HSN Code,Pack");
                for (Medicine med : medicines) {
                    writer.println(String.format("\"%s\",\"%s\",%.2f,%.2f,%d,\"%s\",\"%s\",\"%s\"",
                        med.getName().replace("\"", "\"\""),
                        med.getBatchNumber() != null ? med.getBatchNumber().replace("\"", "\"\"") : "",
                        med.getMrp(),
                        med.getRate(),
                        med.getStock(),
                        med.getExpiryDate() != null ? med.getExpiryDate() : "",
                        med.getHsnCode() != null ? med.getHsnCode().replace("\"", "\"\"") : "",
                        med.getPack() != null ? med.getPack().replace("\"", "\"\"") : ""));
                }
                showAlert("Export Successful", "Stock data exported to: " + file.getAbsolutePath());
            } catch (Exception e) {
                showAlert("Export Error", "Failed to export CSV: " + e.getMessage());
            }
        }
    }

    // =============== PRINTING & PDF ===============

    private void printGSTBill(List<SaleItem> items, String patientName, String prescribedBy, 
                             String invoiceNo, LocalDate date, String paymentMode) {
        Printer printer = Printer.getDefaultPrinter();
        if (printer == null) {
            showAlert("Printer Error", "No printer found!");
            return;
        }

        StringBuilder bill = new StringBuilder();
        bill.append("==============================================\n");
        bill.append("           GST INVOICE\n");
        bill.append("         PHARMA RETAIL STORE\n\n");
        bill.append("SHNO C-19, Opp. Mascoat School, Near Natraj Colony\n");
        bill.append("Foysagar Road, Ajmer(Raj), Ph: 8426831176\n");
        bill.append("          GSTIN: 08ACNPA3859Q1ZW\n\n");
        bill.append("Patient: ").append(patientName.isEmpty() ? "N/A" : patientName).append("\n");
        bill.append("Doctor: ").append(prescribedBy.isEmpty() ? "N/A" : prescribedBy).append("\n");
        bill.append("Invoice No.: ").append(invoiceNo).append("\n");
        bill.append("Date: ").append(date.format(DateTimeFormatter.ofPattern("dd/MM/yy"))).append("\n");
        bill.append("Payment Mode: ").append(paymentMode).append("\n\n");
        
        bill.append("==============================================\n");
        bill.append(String.format("%-3s %-20s %-6s %-8s %-8s\n", 
            "Qty", "Product Name", "Rate", "Disc%", "Amount"));
        bill.append("----------------------------------------------\n");
        
        double totalAmount = 0;
        double totalDiscount = 0;
        
        for (SaleItem si : items) {
            bill.append(String.format("%-3d %-20s ₹%-7.2f %-7.1f ₹%-7.2f\n",
                si.getQuantity(), 
                trimTo(si.getMedicineName(), 20),
                si.getPrice(),
                si.getDiscountPercent(),
                si.getTotal()));
                
            totalAmount += si.getPrice() * si.getQuantity();
            totalDiscount += si.getPrice() * si.getQuantity() * (si.getDiscountPercent() / 100);
        }
        
        bill.append("----------------------------------------------\n");
        
        double taxableAmount = totalAmount - totalDiscount;
        double tax = taxableAmount * 0.05; // 5% GST
        double netAmount = taxableAmount + tax;
        
        bill.append(String.format("Items: %d\n", items.size()));
        bill.append(String.format("Total: ₹%.2f\n", totalAmount));
        bill.append(String.format("Discount: ₹%.2f\n", totalDiscount));
        bill.append(String.format("Taxable: ₹%.2f\n", taxableAmount));
        bill.append(String.format("SGST+CGST (5%%): ₹%.2f\n", tax));
        bill.append("----------------------------------------------\n");
        bill.append(String.format("%35s ₹%.2f\n", "Net Amount:", netAmount));
        bill.append(String.format("%35s %s\n", "Payment Mode:", paymentMode));
        bill.append("\n");
        bill.append("Thank You! Visit Again\n");
        bill.append("E & O E\n");
        bill.append("Subject to AJMER Jurisdiction.\n");
        bill.append("==============================================\n");

        Text text = new Text(bill.toString());
        text.setFont(Font.font("Monospaced", 10));
        PrinterJob job = PrinterJob.createPrinterJob();

        if (job != null && job.showPrintDialog(null)) {
            boolean success = job.printPage(text);
            if (success) {
                job.endJob();
                showAlert("Success", "Bill sent to printer successfully!");
            } else {
                showAlert("Print Error", "Failed to print the bill.");
            }
        }
    }
    
    private void generatePDFBill(Stage stage, List<SaleItem> items, String patientName, 
                               String prescribedBy, String invoiceNo, LocalDate date, String paymentMode) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Medical Bill As");
        fileChooser.setInitialFileName("Medical_Bill_" + invoiceNo + ".pdf");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
            
        File file = fileChooser.showSaveDialog(stage);
        
        if (file != null) {
            try {
                Document document = new Document();
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();
                
                com.itextpdf.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
                com.itextpdf.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
                com.itextpdf.text.Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
                
                Paragraph title = new Paragraph("GST INVOICE\nPHARMA RETAIL STORE\n\n", titleFont);
                title.setAlignment(Element.ALIGN_CENTER);
                document.add(title);
                
                Paragraph address = new Paragraph(
                    "SHNO C-19, Opp. Mascoat School, Near Natraj Colony\n" +
                    "Foysagar Road, Ajmer(Raj), Ph: 8426831176\n" +
                    "GSTIN: 08ACNPA3859Q1ZW\n\n", normalFont);
                address.setAlignment(Element.ALIGN_CENTER);
                document.add(address);
                
                Paragraph patientInfo = new Paragraph();
                patientInfo.add(new Phrase("Patient: ", headerFont));
                patientInfo.add(new Phrase(patientName.isEmpty() ? "N/A" : patientName + "\n"));
                patientInfo.add(new Phrase("Doctor: ", headerFont));
                patientInfo.add(new Phrase(prescribedBy.isEmpty() ? "N/A" : prescribedBy + "\n"));
                patientInfo.add(new Phrase("Invoice No.: ", headerFont));
                patientInfo.add(new Phrase(invoiceNo + "\n"));
                patientInfo.add(new Phrase("Date: ", headerFont));
                patientInfo.add(new Phrase(date.format(DateTimeFormatter.ofPattern("dd/MM/yy")) + "\n"));
                patientInfo.add(new Phrase("Payment Mode: ", headerFont));
                patientInfo.add(new Phrase(paymentMode + "\n\n"));
                document.add(patientInfo);
                
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                
                table.addCell(new PdfPCell(new Phrase("Qty", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Product Name", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Rate", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Disc%", headerFont)));
                table.addCell(new PdfPCell(new Phrase("Amount", headerFont)));
                
                double totalAmount = 0;
                double totalDiscount = 0;
                
                for (SaleItem item : items) {
                    table.addCell(new PdfPCell(new Phrase(String.valueOf(item.getQuantity()), normalFont)));
                    table.addCell(new PdfPCell(new Phrase(item.getMedicineName(), normalFont)));
                    table.addCell(new PdfPCell(new Phrase(String.format("₹%.2f", item.getPrice()), normalFont)));
                    table.addCell(new PdfPCell(new Phrase(String.format("%.1f%%", item.getDiscountPercent()), normalFont)));
                    table.addCell(new PdfPCell(new Phrase(String.format("₹%.2f", item.getTotal()), normalFont)));
                    
                    totalAmount += item.getPrice() * item.getQuantity();
                    totalDiscount += item.getPrice() * item.getQuantity() * (item.getDiscountPercent() / 100);
                }
                
                document.add(table);
                document.add(new Paragraph(" "));
                
                double taxableAmount = totalAmount - totalDiscount;
                double tax = taxableAmount * 0.05;
                double netAmount = taxableAmount + tax;
                
                Paragraph summary = new Paragraph();
                summary.add(new Phrase(String.format("Items: %d\n", items.size())));
                summary.add(new Phrase(String.format("Total: ₹%.2f\n", totalAmount)));
                summary.add(new Phrase(String.format("Discount: ₹%.2f\n", totalDiscount)));
                summary.add(new Phrase(String.format("Taxable Amount: ₹%.2f\n", taxableAmount)));
                summary.add(new Phrase(String.format("SGST+CGST (5%%): ₹%.2f\n", tax)));
                summary.add(new Phrase("----------------------------------------------------------------------------------------------------------------------------------\n"));
                summary.add(new Phrase(String.format("%130s ₹%.2f\n", "Net Amount:", netAmount)));
                summary.add(new Phrase(String.format("%130s %s\n\n", "Payment Mode:", paymentMode)));
                summary.add(new Phrase("Thank You! Visit Again\n"));
                summary.add(new Phrase("E & O E\n"));
                summary.add(new Phrase("Subject to AJMER Jurisdiction.\n"));
                summary.add(new Phrase("=========================================================================="));
                document.add(summary);
                
                document.close();
                
                showAlert("PDF Generated", 
                    "Bill saved successfully!\nLocation: " + file.getAbsolutePath());
                
            } catch (Exception ex) {
                showAlert("PDF Error", "Failed to generate PDF: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    // =============== AUTHENTICATION ===============

    private void handleLogin(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            showAlert("Validation Error", "Please enter username.");
            return;
        }
        
        if (password == null || password.trim().isEmpty()) {
            showAlert("Validation Error", "Please enter password.");
            return;
        }

        // Hardcoded admin for demo
        if ("admin".equals(username.trim()) && "admin123".equals(password)) {
            showHomePage();
            return;
        }

        // Database authentication
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    showHomePage();
                } else {
                    showAlert("Login Failed", "Invalid username or password.");
                }
            }
        } catch (SQLException e) {
            showAlert("DB Error", "Could not check credentials: " + e.getMessage());
        }
    }
    
    private void handleRegistration(String fullName, String email, String username, 
                                   String password, String confirmPassword) {
        if (fullName == null || fullName.trim().isEmpty() || 
            username == null || username.trim().isEmpty() || 
            password == null || password.trim().isEmpty()) {
            showAlert("Validation Error", "Full name, username and password are required.");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            showAlert("Validation Error", "Passwords do not match.");
            return;
        }
        
        String sql = "INSERT INTO users (username, password, full_name, email) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, password);
            ps.setString(3, fullName.trim());
            ps.setString(4, email.trim().isEmpty() ? null : email.trim());
            ps.executeUpdate();
            showAlert("Success", "Account created successfully. Please log in.");
            showLoginScreen();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // Duplicate entry
                showAlert("Registration Failed", "Username already exists.");
            } else {
                showAlert("DB Error", "Failed to create account: " + e.getMessage());
            }
        }
    }

    // =============== UTILITY METHODS ===============

    private TextField createStyledTextField(String prompt, String defaultValue) {
        TextField tf = new TextField(defaultValue);
        tf.setPromptText(prompt);
        tf.setFont(Font.font(APP_FONT, 14));
        tf.setStyle("-fx-background-color: white; -fx-border-color: #ddd; " +
                   "-fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8;");
        tf.setMaxWidth(300);
        return tf;
    }
    
    private PasswordField createStyledPasswordField(String prompt, String defaultValue) {
        PasswordField pf = new PasswordField();
        pf.setText(defaultValue);
        pf.setPromptText(prompt);
        pf.setFont(Font.font(APP_FONT, 14));
        pf.setStyle("-fx-background-color: white; -fx-border-color: #ddd; " +
                   "-fx-border-radius: 5; -fx-background-radius: 5; -fx-padding: 8;");
        pf.setMaxWidth(300);
        return pf;
    }
    
    private Label createStyledLabel(String text) {
        return createStyledLabel(text, 14);
    }
    
    private Label createStyledLabel(String text, double size) {
        Label label = new Label(text);
        label.setFont(Font.font(APP_FONT, FontWeight.BOLD, size));
        label.setTextFill(Color.web("#333"));
        return label;
    }
    
    private Button createPrimaryButton(String text, double width) {
        Button button = new Button(text);
        button.setPrefWidth(width);
        button.setFont(Font.font(APP_FONT, FontWeight.BOLD, 14));
        button.setTextFill(Color.WHITE);
        button.setStyle("-fx-background-color: " + toHex(COLOR_1) + "; " +
                       "-fx-background-radius: 5; -fx-cursor: hand; " +
                       "-fx-padding: 10 15; -fx-wrap-text: true;");
        button.setOnMouseEntered(e -> button.setStyle(
            "-fx-background-color: " + toHex(COLOR_1.darker()) + "; " +
            "-fx-background-radius: 5; -fx-cursor: hand; " +
            "-fx-padding: 10 15; -fx-wrap-text: true; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 5, 0, 0, 2);"));
        button.setOnMouseExited(e -> button.setStyle(
            "-fx-background-color: " + toHex(COLOR_1) + "; " +
            "-fx-background-radius: 5; -fx-cursor: hand; " +
            "-fx-padding: 10 15; -fx-wrap-text: true;"));
        return button;
    }
    
    private Button createSecondaryButton(String text, double width) {
        Button button = new Button(text);
        button.setPrefWidth(width);
        button.setFont(Font.font(APP_FONT, FontWeight.BOLD, 14));
        button.setTextFill(Color.WHITE);
        button.setStyle("-fx-background-color: " + toHex(COLOR_2) + "; " +
                       "-fx-background-radius: 5; -fx-cursor: hand; " +
                       "-fx-padding: 10 15; -fx-wrap-text: true;");
        return button;
    }

    private Button createAccentButton(String text, double width) {
        Button button = new Button(text);
        button.setPrefWidth(width);
        button.setFont(Font.font(APP_FONT, FontWeight.BOLD, 14));
        button.setTextFill(Color.WHITE);
        button.setStyle("-fx-background-color: #F18F01; " +
                       "-fx-background-radius: 5; -fx-cursor: hand; " +
                       "-fx-padding: 10 15; -fx-wrap-text: true;");
        return button;
    }
    
    private Button createLogoutButton() {
        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; " +
                          "-fx-font-weight: bold; -fx-background-radius: 20; " +
                          "-fx-cursor: hand; -fx-padding: 8 20;");
        logoutBtn.setOnAction(e -> logoutAction());
        return logoutBtn;
    }
    
    private void logoutAction() {
        Alert confirm = new Alert(AlertType.CONFIRMATION);
        confirm.setTitle("Logout");
        confirm.setHeaderText("Logout Confirmation");
        confirm.setContentText("Are you sure you want to logout?");
        
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            currentSaleItems.clear();
            medicines.clear();
            previousInvoices.clear();
            purchaseMedicineCombo = null;
            salesPatientField = null;
            salesDoctorField = null;
            paymentCombo = null;
            saleDatePicker = null;
            salesInvoiceField = null;
            showLoginScreen();
        }
    }
    
    @SuppressWarnings("unchecked")
	private void setupStockColumns(TableView<Medicine> table) {
        TableColumn<Medicine, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setPrefWidth(200);
        
        TableColumn<Medicine, String> batchCol = new TableColumn<>("Batch");
        batchCol.setCellValueFactory(new PropertyValueFactory<>("batchNumber"));
        batchCol.setPrefWidth(100);
        
        TableColumn<Medicine, Double> mrpCol = new TableColumn<>("MRP");
        mrpCol.setCellValueFactory(new PropertyValueFactory<>("mrp"));
        mrpCol.setPrefWidth(80);
        mrpCol.setCellFactory(col -> new TableCell<Medicine, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("₹%.2f", item));
                }
            }
        });
        
        TableColumn<Medicine, Double> rateCol = new TableColumn<>("Rate");
        rateCol.setCellValueFactory(new PropertyValueFactory<>("rate"));
        rateCol.setPrefWidth(80);
        rateCol.setCellFactory(col -> new TableCell<Medicine, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("₹%.2f", item));
                }
            }
        });
        
        TableColumn<Medicine, Integer> stockCol = new TableColumn<>("Stock");
        stockCol.setCellValueFactory(new PropertyValueFactory<>("stock"));
        stockCol.setPrefWidth(80);
        stockCol.setCellFactory(col -> new TableCell<Medicine, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                    if (item == 0) {
                        setTextFill(Color.RED);
                        setStyle("-fx-font-weight: bold;");
                    } else if (item < 10) {
                        setTextFill(Color.ORANGE);
                    } else {
                        setTextFill(Color.GREEN);
                    }
                }
            }
        });
        
        table.getColumns().addAll(nameCol, batchCol, mrpCol, rateCol, stockCol);
    }
    
    @SuppressWarnings("unchecked")
	private void setupSaleColumns(TableView<SaleItem> table) {
        TableColumn<SaleItem, String> nameCol = new TableColumn<>("Product");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("medicineName"));
        nameCol.setPrefWidth(200);
        
        TableColumn<SaleItem, Integer> qtyCol = new TableColumn<>("Qty");
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        qtyCol.setPrefWidth(60);
        
        TableColumn<SaleItem, Double> rateCol = new TableColumn<>("Rate");
        rateCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        rateCol.setPrefWidth(80);
        rateCol.setCellFactory(col -> new TableCell<SaleItem, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("₹%.2f", item));
                }
            }
        });
        
        TableColumn<SaleItem, Double> discCol = new TableColumn<>("Disc%");
        discCol.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
        discCol.setPrefWidth(70);
        discCol.setCellFactory(col -> new TableCell<SaleItem, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f%%", item));
                }
            }
        });
        
        TableColumn<SaleItem, Double> totalCol = new TableColumn<>("Amount");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("total"));
        totalCol.setPrefWidth(90);
        totalCol.setCellFactory(col -> new TableCell<SaleItem, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("₹%.2f", item));
                }
            }
        });
        
        TableColumn<SaleItem, Void> actionCol = new TableColumn<>("Action");
        actionCol.setPrefWidth(80);
        actionCol.setCellFactory(param -> new TableCell<SaleItem, Void>() {
            private final Button deleteBtn = new Button("Delete");
            {
                deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                                 "-fx-font-size: 10px; -fx-padding: 3 8; -fx-wrap-text: true;");
                deleteBtn.setOnAction(event -> {
                    SaleItem item = getTableView().getItems().get(getIndex());
                    currentSaleItems.remove(item);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(deleteBtn);
                }
            }
        });
        
        table.getColumns().addAll(nameCol, qtyCol, rateCol, discCol, totalCol, actionCol);
    }
    
    @SuppressWarnings("unchecked")
	private void setupPreviousBillsColumns(TableView<PreviousInvoice> table) {
        TableColumn<PreviousInvoice, String> invCol = new TableColumn<>("Invoice No");
        invCol.setCellValueFactory(new PropertyValueFactory<>("invoiceNo"));
        invCol.setPrefWidth(120);
        
        TableColumn<PreviousInvoice, String> patientCol = new TableColumn<>("Patient");
        patientCol.setCellValueFactory(new PropertyValueFactory<>("patientName"));
        patientCol.setPrefWidth(150);
        
        TableColumn<PreviousInvoice, String> doctorCol = new TableColumn<>("Doctor");
        doctorCol.setCellValueFactory(new PropertyValueFactory<>("prescribedBy"));
        doctorCol.setPrefWidth(120);
        
        TableColumn<PreviousInvoice, String> paymentCol = new TableColumn<>("Payment");
        paymentCol.setCellValueFactory(new PropertyValueFactory<>("paymentMode"));
        paymentCol.setPrefWidth(80);
        
        TableColumn<PreviousInvoice, LocalDate> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(new PropertyValueFactory<>("saleDate"));
        dateCol.setPrefWidth(100);
        dateCol.setCellFactory(col -> new TableCell<PreviousInvoice, LocalDate>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(DateTimeFormatter.ofPattern("dd/MM/yy")));
                }
            }
        });
        
        TableColumn<PreviousInvoice, Double> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        totalCol.setPrefWidth(100);
        totalCol.setCellFactory(col -> new TableCell<PreviousInvoice, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("₹%.2f", item));
                }
            }
        });
        
        table.getColumns().addAll(invCol, patientCol, doctorCol, paymentCol, dateCol, totalCol);
    }
    
    private void setupTabNavigation(Control[] controls) {
        for (int i = 0; i < controls.length; i++) {
            final int index = i;
            Control control = controls[i];
            
            if (control instanceof TextInputControl) {
                control.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.ENTER) {
                        if (index < controls.length - 1) {
                            controls[index + 1].requestFocus();
                        } else {
                            // Find and fire the primary button
                            for (Control c : controls) {
                                if (c instanceof Button) {
                                    ((Button) c).fire();
                                    break;
                                }
                            }
                        }
                    }
                });
            }
        }
    }
    
    private void setupGlobalShortcuts(Scene scene) {
        // Home page shortcuts
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.E, KeyCombination.ALT_DOWN), () -> {
            showMainDashboard(0); // Medicine Entry
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.T, KeyCombination.ALT_DOWN), () -> {
            showMainDashboard(1); // Stock Management
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.P, KeyCombination.ALT_DOWN), () -> {
            showMainDashboard(2); // Purchase
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN), () -> {
            showMainDashboard(3); // Sales & Billing
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.B, KeyCombination.ALT_DOWN), () -> {
            showMainDashboard(4); // Previous Bills
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.R, KeyCombination.ALT_DOWN), () -> {
            showMainDashboard(5); // Reports
        });
        
        // Function keys (for both home page and tabbed interface)
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F1), () -> {
            if (mainTabs != null) {
                mainTabs.getSelectionModel().select(0);
            } else {
                showMainDashboard(0);
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F2), () -> {
            if (mainTabs != null) {
                mainTabs.getSelectionModel().select(1);
            } else {
                showMainDashboard(1);
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F3), () -> {
            if (mainTabs != null) {
                mainTabs.getSelectionModel().select(2);
            } else {
                showMainDashboard(2);
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F4), () -> {
            if (mainTabs != null) {
                mainTabs.getSelectionModel().select(3);
            } else {
                showMainDashboard(3);
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F5), () -> {
            if (mainTabs != null) {
                mainTabs.getSelectionModel().select(4);
            } else {
                showMainDashboard(4);
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F6), () -> {
            if (mainTabs != null) {
                mainTabs.getSelectionModel().select(5);
            } else {
                showMainDashboard(5);
            }
        });
        
        // Navigation shortcuts
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN), () -> {
            showHomePage();
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN), () -> {
            if (mainTabs != null && mainTabs.getSelectionModel().getSelectedIndex() == 3) {
                if (!currentSaleItems.isEmpty()) {
                    Alert alert = new Alert(AlertType.CONFIRMATION);
                    alert.setTitle("New Sale");
                    alert.setHeaderText("Start new sale?");
                    alert.setContentText("Current sale will be cleared.");
                    
                    Optional<ButtonType> result = alert.showAndWait();
                    if (result.isPresent() && result.get() == ButtonType.OK) {
                        clearCurrentSale();
                        if (salesPatientField != null) salesPatientField.clear();
                        if (salesDoctorField != null) salesDoctorField.clear();
                        if (paymentCombo != null) paymentCombo.setValue("Cash");
                        if (saleDatePicker != null) saleDatePicker.setValue(LocalDate.now());
                        if (salesInvoiceField != null) salesInvoiceField.setText(currentInvoiceNumber);
                    }
                }
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN), () -> {
            // Save current sale
            if (mainTabs != null && mainTabs.getSelectionModel().getSelectedIndex() == 3) {
                saveCurrentSale(
                    salesPatientField != null ? salesPatientField.getText() : "",
                    salesDoctorField != null ? salesDoctorField.getText() : "",
                    currentPaymentMode,
                    saleDatePicker != null ? saleDatePicker.getValue() : LocalDate.now()
                );
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN), () -> {
            // Print current bill
            if (mainTabs != null && mainTabs.getSelectionModel().getSelectedIndex() == 3) {
                if (currentSaleItems.isEmpty()) {
                    showAlert("Empty Sale", "Add items before printing.");
                    return;
                }
                printGSTBill(currentSaleItems, 
                    salesPatientField != null ? salesPatientField.getText() : "",
                    salesDoctorField != null ? salesDoctorField.getText() : "",
                    salesInvoiceField != null ? salesInvoiceField.getText() : currentInvoiceNumber,
                    saleDatePicker != null ? saleDatePicker.getValue() : LocalDate.now(),
                    currentPaymentMode);
            }
        });
        
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN), () -> {
            // Generate PDF
            if (mainTabs != null && mainTabs.getSelectionModel().getSelectedIndex() == 3) {
                if (currentSaleItems.isEmpty()) {
                    showAlert("Empty Sale", "Add items before generating PDF.");
                    return;
                }
                generatePDFBill(primaryStage, currentSaleItems,
                    salesPatientField != null ? salesPatientField.getText() : "",
                    salesDoctorField != null ? salesDoctorField.getText() : "",
                    salesInvoiceField != null ? salesInvoiceField.getText() : currentInvoiceNumber,
                    saleDatePicker != null ? saleDatePicker.getValue() : LocalDate.now(),
                    currentPaymentMode);
            }
        });
        
        // MODIFIED: ESCAPE key now only shows exit confirmation on homepage/login screen
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.ESCAPE), () -> {
            // Only show exit confirmation if we're on homepage or login
            if (mainTabs == null) {
                showExitConfirmation();
            }
            // If we're in tabs/dashboard, ESCAPE does nothing
        });
        
        // IMPORTANT: REMOVED Enter key shortcut that was causing logout
        // No global Enter key shortcut to prevent accidental logout
    }
    
    private void showExitConfirmation() {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Exit Application");
        alert.setHeaderText("Close PHARMA RETAIL?");
        alert.setContentText("Are you sure you want to exit?");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            Platform.exit();
        }
    }
    
    private String toHex(Color color) {
        return String.format("#%02X%02X%02X",
            (int) (color.getRed() * 255),
            (int) (color.getGreen() * 255),
            (int) (color.getBlue() * 255));
    }
    
    private void showAlert(String title, String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private String trimTo(String s, int length) {
        if (s == null || s.length() <= length) return s;
        return s.substring(0, length - 3) + "...";
    }

    // =============== DATA CLASSES ===============

    public static class Medicine {
        private SimpleIntegerProperty id;
        private SimpleStringProperty name;
        private SimpleStringProperty batchNumber;
        private SimpleDoubleProperty mrp;
        private SimpleDoubleProperty rate;
        private SimpleIntegerProperty stock;
        private SimpleStringProperty expiryDate;
        private SimpleStringProperty hsnCode;
        private SimpleStringProperty pack;

        public Medicine(int id, String name, String batchNumber, double mrp, double rate, 
                       int stock, String expiryDate, String hsnCode, String pack) {
            this.id = new SimpleIntegerProperty(id);
            this.name = new SimpleStringProperty(name);
            this.batchNumber = new SimpleStringProperty(batchNumber);
            this.mrp = new SimpleDoubleProperty(mrp);
            this.rate = new SimpleDoubleProperty(rate);
            this.stock = new SimpleIntegerProperty(stock);
            this.expiryDate = new SimpleStringProperty(expiryDate);
            this.hsnCode = new SimpleStringProperty(hsnCode);
            this.pack = new SimpleStringProperty(pack);
        }

        public int getId() { return id.get(); }
        public String getName() { return name.get(); }
        public String getBatchNumber() { return batchNumber.get(); }
        public double getMrp() { return mrp.get(); }
        public double getRate() { return rate.get(); }
        public int getStock() { return stock.get(); }
        public String getExpiryDate() { return expiryDate.get(); }
        public String getHsnCode() { return hsnCode.get(); }
        public String getPack() { return pack.get(); }
        
        public SimpleIntegerProperty idProperty() { return id; }
        public SimpleStringProperty nameProperty() { return name; }
        public SimpleStringProperty batchNumberProperty() { return batchNumber; }
        public SimpleDoubleProperty mrpProperty() { return mrp; }
        public SimpleDoubleProperty rateProperty() { return rate; }
        public SimpleIntegerProperty stockProperty() { return stock; }
        public SimpleStringProperty expiryDateProperty() { return expiryDate; }
        public SimpleStringProperty hsnCodeProperty() { return hsnCode; }
        public SimpleStringProperty packProperty() { return pack; }
        
        @Override
        public String toString() {
            return name.get() + " - " + batchNumber.get() + " (Stock: " + stock.get() + ")";
        }
    }

    public static class SaleItem {
        private SimpleStringProperty medicineName;
        private SimpleStringProperty batchNumber;
        private SimpleStringProperty expiryDate;
        private SimpleStringProperty hsnCode;
        private SimpleStringProperty pack;
        private SimpleDoubleProperty mrp;
        private SimpleIntegerProperty quantity;
        private SimpleDoubleProperty price;
        private SimpleDoubleProperty discountPercent;
        private SimpleDoubleProperty netRate;
        private SimpleDoubleProperty amount;

        public SaleItem(String medicineName, int quantity, double price) {
            this.medicineName = new SimpleStringProperty(medicineName);
            this.quantity = new SimpleIntegerProperty(quantity);
            this.price = new SimpleDoubleProperty(price);
            this.discountPercent = new SimpleDoubleProperty(0);
            this.netRate = new SimpleDoubleProperty(price);
            this.amount = new SimpleDoubleProperty(quantity * price);
            this.batchNumber = new SimpleStringProperty("");
            this.expiryDate = new SimpleStringProperty("");
            this.hsnCode = new SimpleStringProperty("");
            this.pack = new SimpleStringProperty("");
            this.mrp = new SimpleDoubleProperty(0);
        }

        public String getMedicineName() { return medicineName.get(); }
        public int getQuantity() { return quantity.get(); }
        public double getPrice() { return price.get(); }
        public double getDiscountPercent() { return discountPercent.get(); }
        public double getNetRate() { return netRate.get(); }
        public double getTotal() { return amount.get(); }
        public String getBatchNumber() { return batchNumber.get(); }
        public String getExpiryDate() { return expiryDate.get(); }
        public String getHsnCode() { return hsnCode.get(); }
        public String getPack() { return pack.get(); }
        public double getMrp() { return mrp.get(); }

        public void setDiscountPercent(double discountPercent) { 
            this.discountPercent.set(discountPercent); 
            this.netRate.set(price.get() * (1 - discountPercent / 100));
            this.amount.set(netRate.get() * quantity.get());
        }
        
        public void setNetRate(double netRate) { this.netRate.set(netRate); }
        public void setBatchNumber(String batchNumber) { this.batchNumber.set(batchNumber); }
        public void setExpiryDate(String expiryDate) { this.expiryDate.set(expiryDate); }
        public void setHsnCode(String hsnCode) { this.hsnCode.set(hsnCode); }
        public void setPack(String pack) { this.pack.set(pack); }
        public void setMrp(double mrp) { this.mrp.set(mrp); }

        public SimpleStringProperty medicineNameProperty() { return medicineName; }
        public SimpleIntegerProperty quantityProperty() { return quantity; }
        public SimpleDoubleProperty priceProperty() { return price; }
        public SimpleDoubleProperty discountPercentProperty() { return discountPercent; }
        public SimpleDoubleProperty netRateProperty() { return netRate; }
        public SimpleDoubleProperty amountProperty() { return amount; }
        public SimpleStringProperty batchNumberProperty() { return batchNumber; }
        public SimpleStringProperty expiryDateProperty() { return expiryDate; }
        public SimpleStringProperty hsnCodeProperty() { return hsnCode; }
        public SimpleStringProperty packProperty() { return pack; }
        public SimpleDoubleProperty mrpProperty() { return mrp; }
    }
    
    public static class PreviousInvoice {
        private SimpleStringProperty invoiceNo;
        private SimpleStringProperty patientName;
        private SimpleStringProperty prescribedBy;
        private SimpleStringProperty paymentMode;
        private SimpleObjectProperty<LocalDate> saleDate;
        private SimpleDoubleProperty totalAmount;

        public PreviousInvoice(String invoiceNo, String patientName, String prescribedBy, 
                              String paymentMode, LocalDate saleDate, double totalAmount) {
            this.invoiceNo = new SimpleStringProperty(invoiceNo);
            this.patientName = new SimpleStringProperty(patientName);
            this.prescribedBy = new SimpleStringProperty(prescribedBy);
            this.paymentMode = new SimpleStringProperty(paymentMode);
            this.saleDate = new SimpleObjectProperty<>(saleDate);
            this.totalAmount = new SimpleDoubleProperty(totalAmount);
        }

        public String getInvoiceNo() { return invoiceNo.get(); }
        public String getPatientName() { return patientName.get(); }
        public String getPrescribedBy() { return prescribedBy.get(); }
        public String getPaymentMode() { return paymentMode.get(); }
        public LocalDate getSaleDate() { return saleDate.get(); }
        public double getTotalAmount() { return totalAmount.get(); }
        
        public SimpleStringProperty invoiceNoProperty() { return invoiceNo; }
        public SimpleStringProperty patientNameProperty() { return patientName; }
        public SimpleStringProperty prescribedByProperty() { return prescribedBy; }
        public SimpleStringProperty paymentModeProperty() { return paymentMode; }
        public SimpleObjectProperty<LocalDate> saleDateProperty() { return saleDate; }
        public SimpleDoubleProperty totalAmountProperty() { return totalAmount; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
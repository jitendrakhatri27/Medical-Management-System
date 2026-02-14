@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-24
set PATH=%JAVA_HOME%\bin;%PATH%
set JAVAFX_HOME=lib\javafx-sdk-15.0.1\lib

javac --module-path %JAVAFX_HOME% --add-modules javafx.controls,javafx.fxml -cp "lib\mysql-connector-j-9.3.0.jar;lib\itextpdf-5.5.13.4.jar" src/MedicalShopApp.java -d bin

java --module-path %JAVAFX_HOME% --add-modules javafx.controls,javafx.fxml -cp "bin;lib\mysql-connector-j-9.3.0.jar;lib\itextpdf-5.5.13.4.jar" MedicalShopApp
pause
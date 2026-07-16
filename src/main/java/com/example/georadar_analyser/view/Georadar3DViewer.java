package com.example.georadar_analyser.view;

import com.example.georadar_analyser.entity.LayerPoint;
import com.example.georadar_analyser.entity.RadarProfile;
import com.example.georadar_analyser.entity.TrackSection;
import com.example.georadar_analyser.repository.TrackSectionRepository;
import com.example.georadar_analyser.service.GeoradarImportService;
import javafx.application.Application;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;
import java.util.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.geometry.Insets;
import javafx.scene.SubScene;

import javafx.stage.FileChooser;
import java.io.File;
import java.io.FileInputStream;





@Component
public class Georadar3DViewer extends Application {

    private static TrackSectionRepository sectionRepository;
    private static GeoradarImportService importService;
    private static Long targetSectionId;

    private Group modelGroup;
    private PerspectiveCamera camera; //

    private double mousePosX;
    private double mousePosY;
    private double mouseOldX;
    private double mouseOldY;

    private List<File> selectedFiles = new ArrayList<>(); // <-- СТАЛО: список для пакетной загрузки


    // Конструктор по умолчанию необходим для JavaFX движка
    public Georadar3DViewer() {}

    /**
     * ГЛАВНАЯ ТОЧКА СВЯЗИ SPRING И JAVAFX
     * Принимает зависимости напрямую из контекста Spring Boot перед запуском окна
     */
    public void viewSection(Long sectionId, TrackSectionRepository repository, GeoradarImportService service) {
        Georadar3DViewer.sectionRepository = repository;
        Georadar3DViewer.importService = service;
        Georadar3DViewer.targetSectionId = sectionId;

        // Запускаем графическое окно JavaFX
        Application.launch(Georadar3DViewer.class);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane mainLayout = new BorderPane();
        Group root3D = new Group();

        Group globalGroup = new Group();
        modelGroup = new Group();
        globalGroup.getChildren().add(modelGroup);
        root3D.getChildren().add(globalGroup);

        // --- ЛЕВАЯ ПАНЕЛЬ УПРАВЛЕНИЯ (САЙДБАР) ---
        VBox sidePanel = new VBox(15);
        sidePanel.setPadding(new Insets(20));
        sidePanel.setStyle("-fx-background-color: #EAEAEA; -fx-border-color: #CCCCCC; -fx-border-width: 0 1 0 0;");
        sidePanel.setPrefWidth(250);

        Label titleLabel = new Label("Панель инженера");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Button uploadBtn = new Button("Обзор файла...");
        uploadBtn.setPrefWidth(210);

        Label fileLabel = new Label("Файл не выбран");
        fileLabel.setStyle("-fx-text-fill: #666666; -fx-font-style: italic;");

        Label correctionLabel = new Label("Поправка оси Z (м):");
        TextField correctionInput = new TextField("0.40");

        Button processBtn = new Button("Визуализировать 3D");
        processBtn.setPrefWidth(210);
        processBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");

        sidePanel.getChildren().addAll(titleLabel, uploadBtn, fileLabel, correctionLabel, correctionInput, processBtn);

        // --- НАСТРОЙКА СВЕТА И КАМЕРЫ ---
        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateY(-100);
        pointLight.setTranslateZ(-200);
        root3D.getChildren().add(pointLight);

        AmbientLight ambientLight = new AmbientLight(Color.rgb(80, 80, 80));
        root3D.getChildren().add(ambientLight);

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(1000.0);
        camera.setTranslateZ(-400);

        Group cameraXform = new Group();
        cameraXform.getChildren().add(camera);
        cameraXform.setTranslateX(20);
        cameraXform.setTranslateY(-10);
        root3D.getChildren().add(cameraXform);

        // --- КОНТЕЙНЕР ДЛЯ 3D ГРАФИКИ (АДАПТИВНАЯ СУБСЦЕНА) ---
        SubScene subScene3D = new SubScene(root3D, 774, 768, true, SceneAntialiasing.BALANCED);
        subScene3D.setCamera(camera);
        subScene3D.widthProperty().bind(mainLayout.widthProperty().subtract(250));
        subScene3D.heightProperty().bind(mainLayout.heightProperty());

        mainLayout.setLeft(sidePanel);
        mainLayout.setCenter(subScene3D);

        Scene scene = new Scene(mainLayout, 1024, 768);

        // --- ЛОГИКА КНОПКИ ОБЗОР ФАЙЛА ---
        // --- ЛОГИКА КНОПКИ ОБЗОР ФАЙЛОВ (ПАКЕТНЫЙ ВЫБОР) ---
        uploadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите ВСЕ профили участка (выделите через Ctrl или Shift)");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Таблицы Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"),
                    new FileChooser.ExtensionFilter("Все файлы (*.*)", "*.*")
            );

            // СТАЛО: Открываем проводник в режиме множественного выбора файлов!
            List<File> files = fileChooser.showOpenMultipleDialog(primaryStage);

            if (files != null && !files.isEmpty()) {
                this.selectedFiles = files; // Сохраняем всю пачку файлов
                fileLabel.setText("Выбрано профилей: " + files.size());
                fileLabel.setStyle("-fx-text-fill: #0066CC; -fx-font-weight: bold;");
                System.out.println("Инженер выбрал пакет из " + files.size() + " файлов.");
            }
        });


        // --- ЛОГИКА КНОПКИ ВИЗУАЛИЗАЦИЯ 3D ---
        // --- ЛОГИКА КНОПКИ ВИЗУАЛИЗАЦИЯ (ПАКЕТНАЯ ОБРАБОТКА) ---
        processBtn.setOnAction(event -> {
            if (this.selectedFiles == null || this.selectedFiles.isEmpty()) {
                fileLabel.setText("ОШИБКА: Выберите файлы профилей!");
                fileLabel.setStyle("-fx-text-fill: #FF0000; -fx-font-weight: bold;");
                return;
            }

            try {
                String inputTxt = correctionInput.getText().trim().replace(",", ".");
                float userAxisCorrection = Float.parseFloat(inputTxt);

                Long freshSectionId = null;
                boolean isFirstFile = true;

                System.out.println("=== Запуск пакетного импорта профилей ===");

                // Перебираем все выбранные инженером файлы Excel в цикле!
                for (File file : this.selectedFiles) {
                    try (FileInputStream fis = new FileInputStream(file)) {

                        // Если это первый файл — создаем новый участок ж/д пути.
                        // Если последующие — передаем тот же самый ID, чтобы привязать профили к одной сцене!
                        // Для этого мы на Шаге 3 слегка обновим аргументы GeoradarImportService.
                        TrackSection freshSection = Georadar3DViewer.importService.importExcelData(
                                fis, "Участок Geoscan32", 1, userAxisCorrection
                        );

                        if (isFirstFile) {
                            freshSectionId = freshSection.getId();
                            isFirstFile = false;
                        }
                    }
                }

                if (freshSectionId != null) {
                    Georadar3DViewer.targetSectionId = freshSectionId;
                    // Перестраиваем адаптивную 3D сетку на экране
                    rebuild3DModel(Georadar3DViewer.targetSectionId);

                    fileLabel.setText("Загружено профилей: " + this.selectedFiles.size());
                    fileLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                }

                subScene3D.requestFocus(); // Возвращаем фокус мыши в 3D

            } catch (NumberFormatException nfe) {
                correctionLabel.setText("Ошибка: введите число (например 0.40)!");
                correctionLabel.setStyle("-fx-text-fill: #FF0000;");
            } catch (Exception e) {
                e.printStackTrace();
                fileLabel.setText("Ошибка пакетного чтения Excel!");
                fileLabel.setStyle("-fx-text-fill: #FF0000;");
            }
        });


        // --- НАВИГАЦИЯ И ВРАЩЕНИЕ МЫШИ ---
        javafx.scene.transform.Rotate rotateY = new javafx.scene.transform.Rotate(-30, javafx.geometry.Point3D.ZERO.add(0, 1, 0));
        globalGroup.getTransforms().add(rotateY);

        javafx.scene.transform.Rotate rotateX = new javafx.scene.transform.Rotate(45, javafx.geometry.Point3D.ZERO.add(1, 0, 0));
        modelGroup.getTransforms().add(rotateX);

        subScene3D.setOnMousePressed(me -> {
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
        });

        subScene3D.setOnMouseDragged(me -> {
            mouseOldX = mousePosX;
            mouseOldY = mousePosY;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            double mouseDeltaX = (mousePosX - mouseOldX);
            double mouseDeltaY = (mousePosY - mouseOldY);

            if (me.isPrimaryButtonDown()) {
                rotateY.setAngle(rotateY.getAngle() + mouseDeltaX * 0.4);
                rotateX.setAngle(rotateX.getAngle() - mouseDeltaY * 0.4);
            } else if (me.isSecondaryButtonDown()) {
                cameraXform.setTranslateX(cameraXform.getTranslateX() - mouseDeltaX * 0.5);
                cameraXform.setTranslateY(cameraXform.getTranslateY() - mouseDeltaY * 0.5);
            }
        });

        subScene3D.setOnScroll(se -> {
            double z = camera.getTranslateZ();
            double newZ = z + se.getDeltaY() * 0.8;
            if (newZ >= -600 && newZ <= -80) {
                camera.setTranslateZ(newZ);
            }
        });

        rebuild3DModel(targetSectionId);

        primaryStage.setTitle("Инженерная CAD-панель георадарного анализа ж/д пути");
        primaryStage.setScene(scene);
        primaryStage.show();
        subScene3D.requestFocus();
    }

    /**
     * МЕТОД ДИНАМИЧЕСКОЙ СБОРКИ И ПЕРЕРИСОВКИ 3D МОДЕЛИ
     */
    /**
     * МЕТОД АДАПТИВНОЙ ТРИАНГУЛЯЦИИ СЛОЕВ (С учетом выклинивания на осях)
     */
    private void rebuild3DModel(Long sectionId) {
        modelGroup.getChildren().clear(); // Стираем старую графику

        Optional<TrackSection> sectionOpt = Georadar3DViewer.sectionRepository.findByIdWithWithAllData(sectionId);
        if (sectionOpt.isEmpty()) return;
        TrackSection section = sectionOpt.get();

        Map<String, Color> layerColors = Map.of(
                "балласт", Color.GRAY,
                "песок", Color.GOLD,
                "земляное полотно", Color.SADDLEBROWN
        );

        // Собираем все уникальные имена слоев, которые есть хоть где-то на участке
        Set<String> layerNames = new HashSet<>();
        for (RadarProfile p : section.getProfiles()) {
            for (LayerPoint pt : p.getPoints()) {
                layerNames.add(pt.getLayerName().toLowerCase().trim());
            }
        }

        // Строим каждый слой независимо и адаптивно!
        for (String layer : layerNames) {

            // Фильтруем ТОЛЬКО те профили, на которых этот слой РЕАЛЬНО существует
            List<RadarProfile> profilesWithThisLayer = new ArrayList<>();
            for (RadarProfile p : section.getProfiles()) {
                boolean hasLayer = p.getPoints().stream()
                        .anyMatch(pt -> pt.getLayerName().toLowerCase().trim().equals(layer));
                if (hasLayer) {
                    profilesWithThisLayer.add(p);
                }
            }

            // Сортируем профили слева направо по оси Y (0м -> 1.5м -> 3.5м -> ...)
            profilesWithThisLayer.sort(Comparator.comparing(RadarProfile::getLateralOffset));

            // Если слой есть меньше чем на 2 профилях — натянуть поверхность невозможно
            if (profilesWithThisLayer.size() < 2) continue;

            TriangleMesh mesh = new TriangleMesh();
            List<Float> pointsList = new ArrayList<>();
            int pointsPerProfile = 0;
            boolean firstProfileCounted = false;

            for (RadarProfile profile : profilesWithThisLayer) {
                float y = profile.getLateralOffset();

                List<LayerPoint> layerPoints = new ArrayList<>();
                for (LayerPoint pt : profile.getPoints()) {
                    if (pt.getLayerName().toLowerCase().trim().equals(layer)) {
                        layerPoints.add(pt);
                    }
                }
                // Сортируем точки по метражу вдоль пути (X)
                layerPoints.sort((p1, p2) -> Float.compare(p1.getDistanceX(), p2.getDistanceX()));

                if (!firstProfileCounted) {
                    pointsPerProfile = layerPoints.size();
                    firstProfileCounted = true;
                }

                // Добавляем координаты в 3D сетку
                for (LayerPoint pt : layerPoints) {
                    pointsList.add(pt.getDistanceX() * 40f);
                    pointsList.add(y * 40f);
                    pointsList.add(-pt.getDepthZ() * 80f);
                }
            }

            if (pointsPerProfile < 2) continue;

            // Загружаем точки в mesh
            float[] pointsArray = new float[pointsList.size()];
            for (int i = 0; i < pointsList.size(); i++) pointsArray[i] = pointsList.get(i);
            mesh.getPoints().setAll(pointsArray);
            mesh.getTexCoords().addAll(0, 0);

            // СВЯЗЫВАЕМ ТРЕУГОЛЬНИКИ АДАПТИВНО:
            //numProfiles теперь динамический — равен числу профилей, где этот слой РЕАЛЬНО есть!
            int numProfiles = profilesWithThisLayer.size();
            for (int p = 0; p < numProfiles - 1; p++) {
                for (int t = 0; t < pointsPerProfile - 1; t++) {
                    int p0 = p * pointsPerProfile + t;
                    int p1 = p0 + 1;
                    int p2 = (p + 1) * pointsPerProfile + t;
                    int p3 = p2 + 1;

                    // Натягиваем сетку полигонов между соседними доступными профилями
                    mesh.getFaces().addAll(p0, 0, p2, 0, p1, 0);
                    mesh.getFaces().addAll(p1, 0, p2, 0, p3, 0);
                }
            }

            MeshView meshView = new MeshView(mesh);
            meshView.setCullFace(javafx.scene.shape.CullFace.NONE);

            PhongMaterial material = new PhongMaterial();
            material.setDiffuseColor(layerColors.getOrDefault(layer, Color.CYAN).deriveColor(0, 1, 1, 0.75));
            meshView.setMaterial(material);

            modelGroup.getChildren().add(meshView);
        }
    }


}

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

    private static Group modelGroup;
    private PerspectiveCamera camera;

    private double mousePosX;
    private double mousePosY;
    private double mouseOldX;
    private double mouseOldY;

    private List<File> selectedFiles = new ArrayList<>();

    public Georadar3DViewer() {}

    /**
     * ГЛАВНАЯ ТОЧКА СВЯЗИ SPRING И JAVAFX
     */
    public void viewSection(Long sectionId, TrackSectionRepository repository, GeoradarImportService service) {
        Georadar3DViewer.sectionRepository = repository;
        Georadar3DViewer.importService = service;
        Georadar3DViewer.targetSectionId = sectionId;
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

        // --- НАСТРОЙКА ОБЪЕМНОГО СВЕТА ДЛЯ МАСШТАБА 1:1 ---
        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(10);
        pointLight.setTranslateY(-20);
        pointLight.setTranslateZ(-30);
        root3D.getChildren().add(pointLight);

        AmbientLight ambientLight = new AmbientLight(Color.rgb(80, 80, 80));
        root3D.getChildren().add(ambientLight);

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000.0);

        camera.setTranslateX(300);
        camera.setTranslateY(-80);
        camera.setTranslateZ(-800);

        Group cameraXform = new Group();
        cameraXform.getChildren().add(camera);
        cameraXform.setTranslateX(20);
        cameraXform.setTranslateY(-10);
        root3D.getChildren().add(cameraXform);

        SubScene subScene3D = new SubScene(root3D, 774, 768, true, SceneAntialiasing.BALANCED);
        subScene3D.setCamera(camera);
        subScene3D.widthProperty().bind(mainLayout.widthProperty().subtract(250));
        subScene3D.heightProperty().bind(mainLayout.heightProperty());

        mainLayout.setLeft(sidePanel);
        mainLayout.setCenter(subScene3D);

        Scene scene = new Scene(mainLayout, 1024, 768);

        // --- ЛОГИКА КНОПКИ ОБЗОР ФАЙЛОВ (ПАКЕТНЫЙ ВЫБОР) ---
        uploadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите ВСЕ профили участка (выделите через Ctrl или Shift)");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Таблицы Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls"),
                    new FileChooser.ExtensionFilter("Все файлы (*.*)", "*.*")
            );
            List<File> files = fileChooser.showOpenMultipleDialog(primaryStage);
            if (files != null && !files.isEmpty()) {
                this.selectedFiles = files;
                fileLabel.setText("Выбрано профилей: " + files.size());
                fileLabel.setStyle("-fx-text-fill: #0066CC; -fx-font-weight: bold;");
            }
        });

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

                if (Georadar3DViewer.targetSectionId != null) {
                    Georadar3DViewer.sectionRepository.deleteById(Georadar3DViewer.targetSectionId);
                }

                Long freshSectionId = null;
                for (File file : this.selectedFiles) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        TrackSection freshSection = Georadar3DViewer.importService.importExcelData(
                                fis, "Участок Geoscan32", 1, userAxisCorrection, freshSectionId
                        );
                        if (freshSectionId == null) {
                            freshSectionId = freshSection.getId();
                        }
                    }
                }

                if (freshSectionId != null) {
                    Georadar3DViewer.targetSectionId = freshSectionId;
                    rebuild3DModel(Georadar3DViewer.targetSectionId);
                    fileLabel.setText("Загружено профилей: " + this.selectedFiles.size());
                    fileLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                }
                subScene3D.requestFocus();

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
            double newZ = z + se.getDeltaY() * 1.5;
            if (newZ >= -2500 && newZ <= -50) {
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
     * ПРОМЫШЛЕННЫЙ CAD-МЕТОД ТРИАНГУЛЯЦИИ ПО РЕГУЛЯРНОЙ СЕТКЕ ИНТЕРПОЛЯЦИИ
     */
    private void rebuild3DModel(Long sectionId) {
        Georadar3DViewer.modelGroup.getChildren().clear();

        Optional<TrackSection> sectionOpt = Georadar3DViewer.sectionRepository.findByIdWithWithAllData(sectionId);
        if (sectionOpt.isEmpty()) return;
        TrackSection section = sectionOpt.get();

        Map<String, Color> layerColors = Map.of(
                "слой 1", Color.GRAY,
                "слой 2", Color.GOLD,
                "слой 3", Color.SADDLEBROWN
        );

        Set<String> layerNames = new HashSet<>();
        for (RadarProfile p : section.getProfiles()) {
            for (LayerPoint pt : p.getPoints()) {
                layerNames.add(pt.getLayerName().toLowerCase().trim());
            }
        }

        float minMaxX = Float.MAX_VALUE;
        for (RadarProfile p : section.getProfiles()) {
            if (p.getPoints() != null && !p.getPoints().isEmpty()) {
                List<LayerPoint> sortedPts = new ArrayList<>(p.getPoints());
                sortedPts.sort(Comparator.comparing(LayerPoint::getDistanceX));
                float profileLastX = sortedPts.get(sortedPts.size() - 1).getDistanceX();
                if (profileLastX < minMaxX) {
                    minMaxX = profileLastX;
                }
            }
        }

        float gridStepX = 4.0f;

        // Задаем безопасный предел интерполяции (минус 5 метров на крайние погрешности файла)
        float maxLimitX = minMaxX - 5.0f;
        System.out.println("--- ГАРАНТИРОВАННАЯ ДЛИНА ИНТЕРПОЛЯЦИИ УЧАСТКА: " + maxLimitX + " метров ---");

        // --- СТРОИМ ПРОЕКТНУЮ ПОВЕРХНОСТЬ БАЛЛАСТНОЙ ПРИЗМЫ С ОТКОСАМИ ---
        TriangleMesh projectMesh = new TriangleMesh();
        List<Float> projectPoints = new ArrayList<>();

        // Задаем наши 11 идеальных конструктивных точек по ширине Y
        // Обновите координаты сетки Y под наш новый шаблон:
        // Обновите координаты сетки Y под отцентрированный гостовский шаблон:
        // Обновите координаты сетки Y под идеально симметричный шаблон:
        float[] projectYGrid = {-1.0f, -0.6f, 0.0f, 1.5f, 3.0f, 3.5f, 4.0f, 5.5f, 7.0f, 7.6f, 8.2f};



        int gridNodesCountX = (int) (maxLimitX / gridStepX) + 1;

        // Генерируем 3D-узлы для проектной поверхности
        for (float y : projectYGrid) {
            for (int i = 0; i < gridNodesCountX; i++) {
                float targetX = i * gridStepX;
                float projectZ = getProjectHeight(y); // Считаем плавную высоту по шаблону!

                projectPoints.add(targetX * 1.5f);
                projectPoints.add(y * 50f);
                projectPoints.add(-projectZ * 50f);
            }
        }

        float[] projPointsArray = new float[projectPoints.size()];
        for (int i = 0; i < projectPoints.size(); i++) projPointsArray[i] = projectPoints.get(i);
        projectMesh.getPoints().setAll(projPointsArray);
        projectMesh.getTexCoords().addAll(0, 0);

        // Связываем треугольники проектной сетки
        int numProjectProfiles = projectYGrid.length;
        for (int p = 0; p < numProjectProfiles - 1; p++) {
            for (int t = 0; t < gridNodesCountX - 1; t++) {
                int p0 = p * gridNodesCountX + t;
                int p1 = p0 + 1;
                int p2 = (p + 1) * gridNodesCountX + t;
                int p3 = p2 + 1;

                projectMesh.getFaces().addAll(p0, 0, p2, 0, p1, 0);
                projectMesh.getFaces().addAll(p1, 0, p2, 0, p3, 0);
            }
        }

        MeshView projectView = new MeshView(projectMesh);
        projectView.setCullFace(javafx.scene.shape.CullFace.NONE);
        projectView.setDrawMode(javafx.scene.shape.DrawMode.FILL);

        PhongMaterial projectMaterial = new PhongMaterial();
        // Красим верхнюю проектную призму в красивый полупрозрачный серый цвет (эффект стекла)
        projectMaterial.setDiffuseColor(Color.rgb(180, 180, 180, 0.4));
        projectView.setMaterial(projectMaterial);
        Georadar3DViewer.modelGroup.getChildren().add(projectView);


        for (String layer : layerNames) {

            // ЗАЩИТА: Отключаем отрисовку старой ломаной дневной поверхности
            if (layer.equalsIgnoreCase("слой 1")) {
                continue;
            }

            List<RadarProfile> profilesWithThisLayer = new ArrayList<>();
            for (RadarProfile p : section.getProfiles()) {
                boolean hasLayer = p.getPoints().stream()
                        .anyMatch(pt -> pt.getLayerName().toLowerCase().trim().equals(layer));
                if (hasLayer) profilesWithThisLayer.add(p);
            }
            profilesWithThisLayer.sort(Comparator.comparing(RadarProfile::getLateralOffset));
            if (profilesWithThisLayer.size() < 2) continue;

            TriangleMesh mesh = new TriangleMesh();
            List<Float> pointsList = new ArrayList<>();

            for (RadarProfile profile : profilesWithThisLayer) {
                float y = profile.getLateralOffset();
                List<LayerPoint> layerPoints = new ArrayList<>();

                for (LayerPoint pt : profile.getPoints()) {
                    if (pt.getLayerName().equalsIgnoreCase(layer)) {
                        layerPoints.add(pt);
                    }
                }
                layerPoints.sort(Comparator.comparing(LayerPoint::getDistanceX));

                for (int i = 0; i < gridNodesCountX; i++) {
                    float targetX = i * gridStepX;
                    float interpolatedZ = getInterpolatedZ(layerPoints, targetX);

                    pointsList.add(targetX * 1.5f);
                    pointsList.add(y * 50f);
                    pointsList.add(-interpolatedZ * 50f);
                }
            }

            float[] pointsArray = new float[pointsList.size()];
            for (int i = 0; i < pointsList.size(); i++) pointsArray[i] = pointsList.get(i);
            mesh.getPoints().setAll(pointsArray);
            mesh.getTexCoords().addAll(0, 0);

            int numProfiles = profilesWithThisLayer.size();
            for (int p = 0; p < numProfiles - 1; p++) {
                for (int t = 0; t < gridNodesCountX - 1; t++) {
                    int p0 = p * gridNodesCountX + t;
                    int p1 = p0 + 1;
                    int p2 = (p + 1) * gridNodesCountX + t;
                    int p3 = p2 + 1;

                    mesh.getFaces().addAll(p0, 0, p2, 0, p1, 0);
                    mesh.getFaces().addAll(p1, 0, p2, 0, p3, 0);
                }
            }

            MeshView meshView = new MeshView(mesh);
            meshView.setCullFace(javafx.scene.shape.CullFace.NONE);
            meshView.setDrawMode(javafx.scene.shape.DrawMode.FILL);

            PhongMaterial material = new PhongMaterial();
            material.setDiffuseColor(layerColors.getOrDefault(layer, Color.CYAN));
            material.setSpecularColor(Color.WHITE);
            meshView.setMaterial(material);

            Georadar3DViewer.modelGroup.getChildren().add(meshView);
        }
    }

    /**
     * ВСПОМОГАТЕЛЬНЫЙ МЕТОД ЛИНЕЙНОЙ ИНТЕРПОЛЯЦИИ ГЕОФИЗИЧЕСКИХ ОТМЕТОК
     */
    private float getInterpolatedZ(List<LayerPoint> points, float targetX) {
        if (points.isEmpty()) return 0.0f;
        if (targetX <= points.get(0).getDistanceX()) return points.get(0).getDepthZ();
        if (targetX >= points.get(points.size() - 1).getDistanceX()) return points.get(points.size() - 1).getDepthZ();

        for (int i = 0; i < points.size() - 1; i++) {
            LayerPoint p1 = points.get(i);
            LayerPoint p2 = points.get(i + 1);
            if (targetX >= p1.getDistanceX() && targetX <= p2.getDistanceX()) {
                if (Math.abs(p2.getDistanceX() - p1.getDistanceX()) < 0.001f) {
                    return p1.getDepthZ();
                }
                float ratio = (targetX - p1.getDistanceX()) / (p2.getDistanceX() - p1.getDistanceX());
                return p1.getDepthZ() + ratio * (p2.getDepthZ() - p1.getDepthZ());
            }
        }
        return points.get(0).getDepthZ();
    }

    /**
     * ГЕОМЕТРИЧЕСКИЙ ШАБЛОН ДЛЯ ПОСТРОЕНИЯ 3D ПОВЕРХНОСТИ призмы
     */
    /**
     * ГЕОМЕТРИЧЕСКИЙ ШАБЛОН ДЛЯ ПОСТРОЕНИЯ 3D ПОВЕРХНОСТИ С ОТКОСАМИ И КОЛЫБЕЛЯМИ
     * Менять размеры и высоты можно прямо здесь!
     */
    /**
     * КОРРЕКТНЫЙ ГЕОМЕТРИЧЕСКИЙ ШАБЛОН (Левое плечо Y=0.0м — на верху призмы)
     */
    private float getProjectHeight(float y) {
        // --- ВЫСОТНЫЕ ОТМЕТКИ (Z) ---
        float hMax = 0.40f; // Высота рельсовой платформы путей (+40 см)
        float hMid = 0.20f; // Высота междупутья (+20 см)
        float hMin = 0.0f;  // Базовый уровень земляного полотна (обочины)

        // --- ТОЧКИ ПЕРЕЛОМА ПРОФИЛЯ ПО ОСИ Y ---
        // --- ТОЧКИ ПЕРЕЛОМА ПРОФИЛЯ ПО ОСИ Y (Симметрично вокруг осей 1.5м и 5.5м) ---
        // --- ТОЧКИ ПЕРЕЛОМА ПРОФИЛЯ ПО ОСИ Y (АБСОЛЮТНАЯ СИММЕТРИЯ ДЛЯ ОБЕИХ ОСЕЙ) ---
        float leftSubgradeStart  = -0.6f; // Начало подъема левого внешнего откоса
        float leftShoulder       = 0.0f;  // Левая бровка балластной призмы (СТАРТ РАДАРА, высота +0.40м)
        float leftPlatformEnd    = 3.0f;  // Конец платформы 1-го пути (Ось 1.5м СТРОГО ПО ЦЕНТРУ: 0.0 + 1.5 = 1.5)
        float interTrackStart    = 3.5f;  // Начало площадки междупутья (спуск окончен)
        float interTrackEnd      = 3.5f;  // Схлопываем междупутье в V-образный лоток (или задайте ширину, например 3.4 - 3.6)
        float rightSlopeStart    = 4.0f;  // Начало платформы 2-го пути (подъем окончен)
        float rightPlatformEnd   = 7.0f;  // Правая бровка балластной призмы (Ось 5.5м СТРОГО ПО ЦЕНТРУ: 4.0 + 1.5 = 5.5)
        float rightSubgradeEnd   = 7.6f;  // Конец правого внешнего откоса балласта на земле



        // --- МАТЕМАТИКА КУСОЧНО-ЛИНЕЙНОЙ ФУНКЦИИ ---
        if (y < leftSubgradeStart) {
            return hMin; // Левая обочина земполотна
        }
        else if (y >= leftSubgradeStart && y < leftShoulder) {
            // ПЛАВНЫЙ ПОДЪЕМ: Внешний левый откос балластной призмы уходит вверх к плечу
            float ratio = (y - leftSubgradeStart) / (leftShoulder - leftSubgradeStart);
            return hMin + ratio * (hMax - hMin);
        }
        else if (y >= leftShoulder && y < leftPlatformEnd) {
            return hMax; // ГОРИЗОНТАЛЬ: Платформа 1-го пути (Включает плечо 0.0м и ось 1.5м)
        }
        else if (y >= leftPlatformEnd && y < interTrackStart) {
            // Внутренний спуск от 1-го пути к междупутью
            float ratio = (y - leftPlatformEnd) / (interTrackStart - leftPlatformEnd);
            return hMax + ratio * (hMid - hMax);
        }
        else if (y >= interTrackStart && y < interTrackEnd) {
            return hMid; // ГОРИЗОНТАЛЬ: Пониженное междупутье (включает точку 3.5м)
        }
        else if (y >= interTrackEnd && y < rightSlopeStart) {
            // Внутренний подъем от междупутья к 2-му пути
            float ratio = (y - interTrackEnd) / (rightSlopeStart - interTrackEnd);
            return hMid + ratio * (hMax - hMid);
        }
        else if (y >= rightSlopeStart && y < rightPlatformEnd) {
            return hMax; // ГОРИЗОНТАЛЬ: Платформа 2-го пути (Включает ось 5.5м и правое плечо 7.0м)
        }
        else if (y >= rightPlatformEnd && y < rightSubgradeEnd) {
            // Внешний правый откос балластной призмы, уходящий вниз к земле
            float ratio = (y - rightPlatformEnd) / (rightSubgradeEnd - rightPlatformEnd);
            return hMax + ratio * (hMin - hMax);
        }
        else {
            return hMin; // Правая обочина земполотна
        }
    }

}

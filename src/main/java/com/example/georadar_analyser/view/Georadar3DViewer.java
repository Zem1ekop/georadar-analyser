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
import javafx.scene.control.TextField;
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
import javafx.scene.control.Slider;
import javafx.geometry.Insets;
import javafx.scene.SubScene;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.FileInputStream;

import javafx.scene.text.Text;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;


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
    private float currentScaleX = 1.5f;
    private float currentScaleYZ = 100.0f;
    private float currentOpacityEmbankment = 0.4f;

    // Начальная путевая отметка (абсолютный пикетаж в метрах)
    private float startLocationX = 0.0f;

    public Georadar3DViewer() {}

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

        VBox sidePanel = new VBox(15);
        sidePanel.setPadding(new Insets(20));
        sidePanel.setStyle("-fx-background-color: #EAEAEA; -fx-border-color: #CCCCCC; -fx-border-width: 0 1 0 0;");
        sidePanel.setPrefWidth(250);

        Label titleLabel = new Label("Панель инженера");
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        Button uploadBtn = new Button("Обзор файлов...");
        uploadBtn.setPrefWidth(210);

        Label fileLabel = new Label("Файлы не выбраны");
        fileLabel.setStyle("-fx-text-fill: #666666; -fx-font-style: italic;");

        // 1. Слайдер длины X
        Label scaleXLabel = new Label("Масштаб вдоль пути (X): 1.5x");
        Slider scaleXSlider = new Slider(0.1, 10.0, 1.5);
        scaleXSlider.setShowTickMarks(true);
        scaleXSlider.setShowTickLabels(true);
        scaleXSlider.setMajorTickUnit(2.0);
        scaleXSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            this.currentScaleX = newVal.floatValue();
            scaleXLabel.setText(String.format("Масштаб вдоль пути (X): %.1fx", this.currentScaleX));
            if (Georadar3DViewer.targetSectionId != null) rebuild3DModel(Georadar3DViewer.targetSectionId);
        });

        // 2. Слайдер профиля Y, Z
        Label scaleYZLabel = new Label("Масштаб профиля (Y, Z): 100%");
        Slider scaleYZSlider = new Slider(10.0, 300.0, 100.0);
        scaleYZSlider.setShowTickMarks(true);
        scaleYZSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            this.currentScaleYZ = newVal.floatValue();
            scaleYZLabel.setText(String.format("Масштаб профиля (Y, Z): %.0f%%", this.currentScaleYZ));
            if (Georadar3DViewer.targetSectionId != null) rebuild3DModel(Georadar3DViewer.targetSectionId);
        });

        // 3. Слайдер прозрачности насыпи
        Label opacityLabel = new Label("Прозрачность насыпи: 40%");
        Slider opacitySlider = new Slider(0.0, 1.0, 0.4);
        opacitySlider.setShowTickMarks(true);
        opacitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            this.currentOpacityEmbankment = newVal.floatValue();
            opacityLabel.setText(String.format("Прозрачность насыпи: %.0f%%", this.currentOpacityEmbankment * 100));
            if (Georadar3DViewer.targetSectionId != null) rebuild3DModel(Georadar3DViewer.targetSectionId);
        });

        // 4. Текстовое поле для ввода начальной отметки километража по ПУ-9
        Label startLocationLabel = new Label("Начальная отметка X (м):");
        TextField startLocationInput = new TextField("0.0");
        startLocationInput.setPrefWidth(210);

        // СЛУШАТЕЛЬ: при изменении текста мгновенно пересчитываем шкалу пикетажа
        startLocationInput.textProperty().addListener((obs, oldText, newText) -> {
            try {
                // Если поле пустое, временно считаем за 0
                this.startLocationX = newText.trim().isEmpty() ? 0.0f : Float.parseFloat(newText.trim());
                if (Georadar3DViewer.targetSectionId != null) {
                    rebuild3DModel(Georadar3DViewer.targetSectionId);
                }
            } catch (NumberFormatException e) {
                // Защита от случайного ввода букв инженером
            }
        });


        Button processBtn = new Button("Визуализировать 3D");
        processBtn.setPrefWidth(210);
        processBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");

        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(10);
        pointLight.setTranslateY(-20);
        pointLight.setTranslateZ(-30);
        root3D.getChildren().add(pointLight);

        AmbientLight ambientLight = new AmbientLight(Color.rgb(100, 100, 100));
        root3D.getChildren().add(ambientLight);

        // Настройка честной Ортографической CAD-камеры
        camera = new PerspectiveCamera(false);
        camera.setNearClip(0.1);
        camera.setFarClip(15000.0);
        camera.setTranslateX(250);
        camera.setTranslateY(-120);
        camera.setTranslateZ(-150);

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

        uploadBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Выберите ВСЕ профили участка");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Таблицы Excel (*.xlsx, *.xls)", "*.xlsx", "*.xls")
            );
            List<File> files = fileChooser.showOpenMultipleDialog(primaryStage);
            if (files != null && !files.isEmpty()) {
                this.selectedFiles = files;
                fileLabel.setText("Выбрано профилей: " + files.size());
                fileLabel.setStyle("-fx-text-fill: #0066CC; -fx-font-weight: bold;");
            }
        });

        processBtn.setOnAction(event -> {
            if (this.selectedFiles == null || this.selectedFiles.isEmpty()) {
                fileLabel.setText("ОШИБКА: Выберите файлы профилей!");
                fileLabel.setStyle("-fx-text-fill: #FF0000; -fx-font-weight: bold;");
                return;
            }
            try {
                if (Georadar3DViewer.targetSectionId != null) {
                    Georadar3DViewer.sectionRepository.deleteById(Georadar3DViewer.targetSectionId);
                }
                Long freshSectionId = null;
                for (File file : this.selectedFiles) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        // ЧИСТЫЙ ВЫЗОВ: аргумент поправки удален!
                        TrackSection freshSection = Georadar3DViewer.importService.importExcelData(
                                fis, "Участок Geoscan32", 1, freshSectionId
                        );
                        if (freshSectionId == null) freshSectionId = freshSection.getId();
                    }
                }
                if (freshSectionId != null) {
                    Georadar3DViewer.targetSectionId = freshSectionId;
                    rebuild3DModel(Georadar3DViewer.targetSectionId);
                    fileLabel.setText("Загружено профилей: " + this.selectedFiles.size());
                    fileLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                }
                subScene3D.requestFocus();
            } catch (Exception e) {
                e.printStackTrace();
                fileLabel.setText("Ошибка пакетного чтения Excel!");
                fileLabel.setStyle("-fx-text-fill: #FF0000;");
            }
        });

        javafx.scene.transform.Rotate rotateY = new javafx.scene.transform.Rotate(-30, javafx.geometry.Point3D.ZERO.add(0, 1, 0));
        globalGroup.getTransforms().add(rotateY);
        javafx.scene.transform.Rotate rotateX = new javafx.scene.transform.Rotate(35, javafx.geometry.Point3D.ZERO.add(1, 0, 0));
        modelGroup.getTransforms().add(rotateX);

        subScene3D.setOnMousePressed(me -> {
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
        });
        subScene3D.setOnMouseDragged(me -> {
            mouseOldX = mousePosX; mouseOldY = mousePosY;
            mousePosX = me.getSceneX(); mousePosY = me.getSceneY();
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
            double newZ = z + se.getDeltaY() * 2.0;
            if (newZ >= -5000 && newZ <= -10) camera.setTranslateZ(newZ);
        });

        sidePanel.getChildren().addAll(
                titleLabel, uploadBtn, fileLabel,
                scaleXLabel, scaleXSlider,
                scaleYZLabel, scaleYZSlider,
                opacityLabel, opacitySlider,
                startLocationLabel, startLocationInput,
                processBtn
        );
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
                if (profileLastX < minMaxX) minMaxX = profileLastX;
            }
        }

        float gridStepX = 4.0f;
        float maxLimitX = minMaxX - 5.0f;
        int gridNodesCountX = (int) (maxLimitX / gridStepX) + 1;

        // --- 1. СТРОИМ ПРОЕКТНУЮ ПОВЕРХНОСТЬ НАСЫПИ (ПРИВЯЗКА К СЛАЙДЕРАМ) ---
        TriangleMesh projectMesh = new TriangleMesh();
        List<Float> projectPoints = new ArrayList<>();
        float[] projectYGrid = {-8.0f, -7.0f, -1.0f, -0.6f, 0.0f, 1.775f, 3.55f, 4.15f, 4.7f, 5.275f, 5.875f, 7.65f, 9.425f, 10.025f, 10.425f, 16.425f, 17.5f};

        for (float y : projectYGrid) {
            for (int i = 0; i < gridNodesCountX; i++) {
                float targetX = i * gridStepX;
                float projectZ = getProjectHeight(y);

                projectPoints.add(targetX * this.currentScaleX);
                projectPoints.add(y * this.currentScaleYZ);          // СТАЛО: масштаб Y с ползунка!
                projectPoints.add(-projectZ * this.currentScaleYZ);    // СТАЛО: масштаб Z с ползунка!
            }
        }

        float[] projPointsArray = new float[projectPoints.size()];
        for (int i = 0; i < projectPoints.size(); i++) projPointsArray[i] = projectPoints.get(i);
        projectMesh.getPoints().setAll(projPointsArray);
        projectMesh.getTexCoords().addAll(0, 0);

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

        MeshView meshViewProj = new MeshView(projectMesh);
        meshViewProj.setCullFace(javafx.scene.shape.CullFace.NONE);
        meshViewProj.setDrawMode(javafx.scene.shape.DrawMode.FILL);
        PhongMaterial projectMaterial = new PhongMaterial();

        projectMaterial.setDiffuseColor(Color.rgb(180, 180, 180, this.currentOpacityEmbankment));
        meshViewProj.setMaterial(projectMaterial);
        Georadar3DViewer.modelGroup.getChildren().add(meshViewProj);

        // --- 2. СТРОИМ ГЕОРАДАРНЫЕ СЛОИ ИЗ БАЗЫ ДАННЫХ (ПРИВЯЗКА К СЛАЙДЕРАМ) ---
        for (String layer : layerNames) {
            if (layer.equalsIgnoreCase("слой 1")) continue;

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
                    if (pt.getLayerName().equalsIgnoreCase(layer)) layerPoints.add(pt);
                }
                layerPoints.sort(Comparator.comparing(LayerPoint::getDistanceX));

                for (int i = 0; i < gridNodesCountX; i++) {
                    float targetX = i * gridStepX;
                    float interpolatedZ = getInterpolatedZ(layerPoints, targetX);

                    pointsList.add(targetX * this.currentScaleX);
                    pointsList.add(y * this.currentScaleYZ);          // СТАЛО: масштаб Y привязан!
                    pointsList.add(-interpolatedZ * this.currentScaleYZ);  // СТАЛО: масштаб Z привязан!
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

            MeshView meshViewLayers = new MeshView(mesh);
            meshViewLayers.setCullFace(javafx.scene.shape.CullFace.NONE);
            meshViewLayers.setDrawMode(javafx.scene.shape.DrawMode.FILL);
            PhongMaterial material = new PhongMaterial(layerColors.getOrDefault(layer, Color.CYAN));
            material.setSpecularColor(Color.WHITE);
            meshViewLayers.setMaterial(material);
            Georadar3DViewer.modelGroup.getChildren().add(meshViewLayers);
        }

        // --- 3. СТРОИМ ШПАЛЫ И РЕЛЬСЫ (ОТСТУПЫ ПО 40 СМ ОТ БРОВОК ПРИЗМ) ---
        float hBallastTop = 0.40f;
        float hSleeperTop = 0.60f; // Высота верха шпалы (+20 см)

        // ТОЧНЫЕ ЧЕРТЕЖНЫЕ КООРДИНАТЫ: отступили по 40 см от бровок призм 0.0м и 5.875м
        float[][] trackSleeperBounds = {
                {0.400f, 3.150f}, // Шпальный брус 1-го пути (длина 2.75м)
                {6.275f, 9.025f}  // Шпальный брус 2-го пути (длина 2.75м)
        };

        for (float[] bounds : trackSleeperBounds) {
            TriangleMesh sleeperMesh = new TriangleMesh();
            float yLeft = bounds[0];
            float yRight = bounds[1];

            sleeperMesh.getPoints().addAll(
                    0.0f * this.currentScaleX,       yLeft * this.currentScaleYZ,  -hBallastTop * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yLeft * this.currentScaleYZ,  -hBallastTop * this.currentScaleYZ,
                    0.0f * this.currentScaleX,       yRight * this.currentScaleYZ, -hBallastTop * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yRight * this.currentScaleYZ, -hBallastTop * this.currentScaleYZ,
                    0.0f * this.currentScaleX,       yLeft * this.currentScaleYZ,  -hSleeperTop * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yLeft * this.currentScaleYZ,  -hSleeperTop * this.currentScaleYZ,
                    0.0f * this.currentScaleX,       yRight * this.currentScaleYZ, -hSleeperTop * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yRight * this.currentScaleYZ, -hSleeperTop * this.currentScaleYZ
            );
            sleeperMesh.getTexCoords().addAll(0,0);
            // Задаем грани параллелепипеда шпалы (ВКЛЮЧАЯ ТОРЦЫ!)
            sleeperMesh.getFaces().addAll(
                    0,0, 2,0, 1,0,   1,0, 2,0, 3,0, // Низ
                    4,0, 5,0, 6,0,   5,0, 7,0, 6,0, // Верх
                    0,0, 1,0, 4,0,   1,0, 5,0, 4,0, // Левый бок
                    2,0, 6,0, 3,0,   3,0, 6,0, 7,0, // Правый бок
                    0,0, 4,0, 2,0,   2,0, 4,0, 6,0, // Передний торец (X = 0)
                    1,0, 3,0, 5,0,   3,0, 7,0, 5,0  // Задний торец (X = maxLimitX)
            );


            MeshView sleeperView = new MeshView(sleeperMesh);
            sleeperView.setCullFace(javafx.scene.shape.CullFace.NONE);
            PhongMaterial sleeperMat = new PhongMaterial(Color.rgb(105, 75, 45)); // Матовый темно-деревянный цвет
            sleeperMat.setSpecularColor(Color.WHITE);
            sleeperView.setMaterial(sleeperMat);
            Georadar3DViewer.modelGroup.getChildren().add(sleeperView);
        }

        // Строим 4 рельсовые нити поверх шпал (колея 1520мм от истинных осей 1.775м и 7.650м)
        float[] railYCoordinates = {
                1.775f - 0.76f, 1.775f + 0.76f, // Рельсы 1-го пути
                7.650f - 0.76f, 7.650f + 0.76f  // Рельсы 2-го пути
        };

        float hRailBottom = hSleeperTop; // Нижняя грань рельса ложится на шпалу (+0.60м)
        float hRailTop    = hSleeperTop + 0.18f; // Верхняя грань (высота рельса 18 см)
        float railWidth   = 0.15f; // Ширина подошвы рельса (15 см)

        for (float railY : railYCoordinates) {
            TriangleMesh railMesh = new TriangleMesh();

            // Центрируем 15-сантиметровый рельс вокруг его теоретической оси колеи
            float yLeft  = railY - (railWidth / 2f);
            float yRight = railY + (railWidth / 2f);

            // Генерируем 8 вершин полноценного 3D-бруса рельса вдоль всего перегона
            railMesh.getPoints().addAll(
                    0.0f * this.currentScaleX,       yLeft * this.currentScaleYZ,  -hRailBottom * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yLeft * this.currentScaleYZ,  -hRailBottom * this.currentScaleYZ,
                    0.0f * this.currentScaleX,       yRight * this.currentScaleYZ, -hRailBottom * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yRight * this.currentScaleYZ, -hRailBottom * this.currentScaleYZ,
                    0.0f * this.currentScaleX,       yLeft * this.currentScaleYZ,  -hRailTop * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yLeft * this.currentScaleYZ,  -hRailTop * this.currentScaleYZ,
                    0.0f * this.currentScaleX,       yRight * this.currentScaleYZ, -hRailTop * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  yRight * this.currentScaleYZ, -hRailTop * this.currentScaleYZ
            );
            railMesh.getTexCoords().addAll(0,0);

            // Связываем грани параллелепипеда рельса (ВКЛЮЧАЯ ТОРЦЫ!)
            railMesh.getFaces().addAll(
                    0,0, 2,0, 1,0,   1,0, 2,0, 3,0, // Низ
                    4,0, 5,0, 6,0,   5,0, 7,0, 6,0, // Верх
                    0,0, 1,0, 4,0,   1,0, 5,0, 4,0, // Левый бок
                    2,0, 6,0, 3,0,   3,0, 6,0, 7,0, // Правый бок
                    0,0, 4,0, 2,0,   2,0, 4,0, 6,0, // Передний торец (X = 0)
                    1,0, 3,0, 5,0,   3,0, 7,0, 5,0  // Задний торец (X = maxLimitX)
            );


            MeshView railView = new MeshView(railMesh);
            railView.setCullFace(javafx.scene.shape.CullFace.NONE);
            PhongMaterial railMat = new PhongMaterial(Color.DARKSLATEGRAY); // Плотный стальной цвет
            railMat.setSpecularColor(Color.WHITE); // Блик металла под лучами PointLight
            railView.setMaterial(railMat);
            Georadar3DViewer.modelGroup.getChildren().add(railView);
        }

        // --- ПРОГРАММНОЕ ДОБАВЛЕНИЕ КОНТРАСТНЫХ ЛИНИЙ БРОВОК И ПОДОШВ (12 CAD ЛИНИЙ) ---
        // Массив строго упорядочен слева направо по оси Y от подошвы до подошвы насыпи
        float[][] structuralEdges = {
                {-7.000f, -4.000f},  // 1. НОВАЯ: Подошва левого откоса насыпи
                {-1.000f, 0.000f},   // 2. Левая бровка земполотна (край обочины)
                {-0.600f, 0.000f},   // 3. Подошва левого внешнего откоса балласта
                {0.000f,  0.400f},   // 4. Левая бровка призмы 1 пути (Левое плечо)
                {3.550f,  0.400f},   // 5. Правое плечо призмы 1 пути
                {4.150f,  0.000f},   // 6. Подошва правого откоса 1 пути (Начало междупутья)
                {5.275f,  0.000f},   // 7. Подошва левого откоса 2 пути (Конец междупутья)
                {5.875f,  0.400f},   // 8. Левое плечо призмы 2 пути
                {9.425f,  0.400f},   // 9. Правое плечо призмы 2 пути
                {10.025f, 0.000f},   // 10. Подошва правого внешнего откоса балласта
                {10.425f, 0.000f},   // 11. Правая бровка земполотна (край обочины)
                {16.425f, -4.000f}   // 12. Подошва правого откоса насыпи

        };

        PhongMaterial edgeMat = new PhongMaterial(Color.rgb(100, 100, 100)); // Мягкий графитовый цвет

        for (float[] edge : structuralEdges) {
            float edgeY = edge[0];
            float edgeZ = edge[1];

            TriangleMesh edgeMesh = new TriangleMesh();
            // Приподнимаем линию на 0.015м (1.5 см) над стеклом призмы, чтобы полностью исключить Z-fighting!
            float raisedZ = edgeZ + 0.015f;
            float lineWidth = 0.03f; // Четкая толщина линии 3 см

            edgeMesh.getPoints().setAll(
                    0.0f * this.currentScaleX,       edgeY * this.currentScaleYZ,             -raisedZ * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  edgeY * this.currentScaleYZ,             -raisedZ * this.currentScaleYZ,
                    0.0f * this.currentScaleX,       (edgeY + lineWidth) * this.currentScaleYZ, -raisedZ * this.currentScaleYZ,
                    maxLimitX * this.currentScaleX,  (edgeY + lineWidth) * this.currentScaleYZ, -raisedZ * this.currentScaleYZ
            );

            edgeMesh.getTexCoords().addAll(0,0);
            edgeMesh.getFaces().addAll(0,0, 2,0, 1,0,   1,0, 2,0, 3,0);

            MeshView edgeView = new MeshView(edgeMesh);
            edgeView.setCullFace(javafx.scene.shape.CullFace.NONE);
            edgeView.setMaterial(edgeMat);
            Georadar3DViewer.modelGroup.getChildren().add(edgeView);
        }

        // --- ПРОГРАММНОЕ ДОБАВЛЕНИЕ ПЛОСКОСТИ ОСНОВАНИЯ НАСЫПИ ---
        TriangleMesh groundMesh = new TriangleMesh();
        float groundZ = -4.000f; // Глубина основания (-4 метра реальности)
        float groundYLeft = -7.000f;  // Левая подошва насыпи
        float groundYRight = 16.425f; // Правая подошва насыпи

        // Задаем 4 вершины прямоугольника земли вдоль всего перегона
        groundMesh.getPoints().addAll(
                0.0f * this.currentScaleX,      groundYLeft * this.currentScaleYZ,  -groundZ * this.currentScaleYZ,
                maxLimitX * this.currentScaleX, groundYLeft * this.currentScaleYZ,  -groundZ * this.currentScaleYZ,
                0.0f * this.currentScaleX,      groundYRight * this.currentScaleYZ, -groundZ * this.currentScaleYZ,
                maxLimitX * this.currentScaleX, groundYRight * this.currentScaleYZ, -groundZ * this.currentScaleYZ
        );
        groundMesh.getTexCoords().addAll(0,0);
        // Затягиваем треугольниками (верхнюю и нижнюю стороны плоскости)
        groundMesh.getFaces().addAll(
                0,0, 2,0, 1,0,   1,0, 2,0, 3,0
        );

        MeshView groundView = new MeshView(groundMesh);
        groundView.setCullFace(javafx.scene.shape.CullFace.NONE);
        groundView.setDrawMode(javafx.scene.shape.DrawMode.FILL);

        // --- СТАНДАРТНЫЙ МАТОВЫЙ МАТЕРИАЛ ЗЕМЛИ (БЕЗ ГЛЯНЦА И ОШИБОК API) ---
        PhongMaterial groundMaterial = new PhongMaterial();
        Color baseColor = Color.rgb(180, 180, 180, this.currentOpacityEmbankment);

        // Задаем основной цвет
        groundMaterial.setDiffuseColor(baseColor);

        // Размываем зеркальный блик до минимума, делая поверхность шероховатой
        groundMaterial.setSpecularColor(Color.rgb(30, 30, 30));
        groundMaterial.setSpecularPower(1.0); // Максимальное рассеивание лучей света

        groundView.setMaterial(groundMaterial);
        Georadar3DViewer.modelGroup.getChildren().add(groundView);


        // --- ПРОГРАММНОЕ ДОБАВЛЕНИЕ НЕПРЕРЫВНОГО ТОРЦЕВОГО СЕЧЕНИЯ (ВЕРТИКАЛЬНАЯ КАЙМА) ---
        float[][] crossSectionPoints = {
                {-7.000f,  -4.000f}, // 1. Левый край основания земли
                {-1.000f,   0.000f}, // 2. Левая бровка земляного полотна (обочина)
                {-0.600f,   0.000f}, // 3. Начало левого откоса балластной призмы 1 пути
                {0.000f,    0.400f}, // 4. Профиль 1: Левое плечо призмы 1 пути
                {3.550f,    0.400f}, // 5. Конец призмы 1 пути (Правое плечо 1 пути)
                {4.150f,    0.000f}, // 6. Спуск 1 пути в междупутье
                {5.275f,    0.000f}, // 7. Ровное междупутье (Начало подъема призмы 2 пути)
                {5.875f,    0.400f}, // 8. Левое плечо призмы 2 пути
                {9.425f,    0.400f}, // 9. Профиль 5: Правое плечо призмы 2 пути
                {10.025f,   0.000f}, // 10. Внешний спуск 2 пути на обочину
                {10.425f,   0.000f}, // 11. Правая бровка земляного полотна (обочина)
                {16.425f,  -4.000f}, // 12. Подошва правого откоса насыпи
                {-7.000f,  -4.000f}  // 13. Замыкаем контур обратно в левую точку земли!
        };

        float endX = maxLimitX * this.currentScaleX;
        // Делаем линию чуть толще (4 см), пуская её вертикально вниз, как рамку чертежа
        float lineWidthEdge = 0.04f;

        for (int i = 0; i < crossSectionPoints.length - 1; i++) {
            float y1 = crossSectionPoints[i][0];
            float z1 = crossSectionPoints[i][1];
            float y2 = crossSectionPoints[i + 1][0];
            float z2 = crossSectionPoints[i + 1][1];

            // Выносим рамку на 1.5 см ВПЕРЕД перед торцом по оси X (X - 0.015),
            // чтобы полностью исключить наложение плоскостей (Z-fighting)!
            float frontX1 = 0.000f - 0.015f;
            float frontX2 = 0.000f - 0.015f;
            float backX1  = endX + 0.015f;
            float backX2  = endX + 0.015f;

            // 1. КОНТУР НА ПЕРЕДНЕМ ТОРЦЕ (Толщина уходит вниз по оси Z!)
            TriangleMesh frontEdgeMesh = new TriangleMesh();
            frontEdgeMesh.getPoints().setAll(
                    frontX1, y1 * this.currentScaleYZ, -z1 * this.currentScaleYZ,
                    frontX2, y1 * this.currentScaleYZ, -(z1 - lineWidthEdge) * this.currentScaleYZ,
                    frontX1, y2 * this.currentScaleYZ, -z2 * this.currentScaleYZ,
                    frontX2, y2 * this.currentScaleYZ, -(z2 - lineWidthEdge) * this.currentScaleYZ
            );
            frontEdgeMesh.getTexCoords().addAll(0,0);
            frontEdgeMesh.getFaces().addAll(0,0, 2,0, 1,0,   1,0, 2,0, 3,0);

            MeshView frontEdgeView = new MeshView(frontEdgeMesh);
            frontEdgeView.setCullFace(javafx.scene.shape.CullFace.NONE);
            frontEdgeView.setMaterial(edgeMat); // Наш мягкий серый графитовый цвет
            Georadar3DViewer.modelGroup.getChildren().add(frontEdgeView);

            // 2. КОНТУР НА ЗАДНЕМ ТОРЦЕ (Толщина уходит вниз по оси Z!)
            TriangleMesh backEdgeMesh = new TriangleMesh();
            backEdgeMesh.getPoints().setAll(
                    backX1, y1 * this.currentScaleYZ, -z1 * this.currentScaleYZ,
                    backX2, y1 * this.currentScaleYZ, -(z1 - lineWidthEdge) * this.currentScaleYZ,
                    backX1, y2 * this.currentScaleYZ, -z2 * this.currentScaleYZ,
                    backX2, y2 * this.currentScaleYZ, -(z2 - lineWidthEdge) * this.currentScaleYZ
            );
            backEdgeMesh.getTexCoords().addAll(0,0);
            backEdgeMesh.getFaces().addAll(0,0, 2,0, 1,0,   1,0, 2,0, 3,0);

            MeshView backEdgeView = new MeshView(backEdgeMesh);
            backEdgeView.setCullFace(javafx.scene.shape.CullFace.NONE);
            backEdgeView.setMaterial(edgeMat);
            Georadar3DViewer.modelGroup.getChildren().add(backEdgeView);
        }

        // --- ПРОГРАММНОЕ ДОБАВЛЕНИЕ ДВУХСТОРОННЕГО ПИКЕТАЖА (ЛЕВАЯ И ПРАВАЯ ОБОЧИНЫ) ---
        float piquetZ = 0.000f;  // Уровень земляного полотна
        int stepMeters = 20;     // Шаг меток - каждые 20 метров

        // Координаты Y для засечек и подписей на левой и правой обочинах
        float leftBermY   = -1.000f;
        float rightBermY  = 10.425f;

        for (int meter = 0; meter <= maxLimitX; meter += stepMeters) {
            float screenX = meter * this.currentScaleX;

            // --- 1. ОРИЕНТАЦИЯ ЛЕВОЙ СТОРОНЫ ---
            // Физическая засечка слева (Покрашена в мягкий edgeMat!)
            TriangleMesh leftTick = new TriangleMesh();
            leftTick.getPoints().addAll(
                    screenX, leftBermY * this.currentScaleYZ, -piquetZ * this.currentScaleYZ,
                    screenX, (leftBermY - 0.3f) * this.currentScaleYZ, -piquetZ * this.currentScaleYZ,
                    screenX + 1.0f, leftBermY * this.currentScaleYZ, -piquetZ * this.currentScaleYZ,
                    screenX + 1.0f, (leftBermY - 0.3f) * this.currentScaleYZ, -piquetZ * this.currentScaleYZ
            );
            leftTick.getTexCoords().addAll(0, 0);
            leftTick.getFaces().addAll(0, 0, 2, 0, 1, 0, 1, 0, 2, 0, 3, 0);
            MeshView leftTickView = new MeshView(leftTick);
            leftTickView.setCullFace(javafx.scene.shape.CullFace.NONE);
            leftTickView.setMaterial(edgeMat); // СТАЛО: мягкий серый вместо черного вырвиглазного
            Georadar3DViewer.modelGroup.getChildren().add(leftTickView);

            // Вертикальные маркеры-флажки слева
            //Text leftText = new Text(String.valueOf(meter));

            int absoluteMeter = Math.round(meter + this.startLocationX);
            Text leftText = new Text(String.valueOf(absoluteMeter));
            leftText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            leftText.setFill(Color.DARKSLATEBLUE);
            leftText.setTranslateX(screenX - 8f);
            leftText.setTranslateY((leftBermY - 0.5f) * this.currentScaleYZ);
            leftText.setTranslateZ(-piquetZ * this.currentScaleYZ - 15f);

            javafx.scene.transform.Rotate standVerticalLeft = new javafx.scene.transform.Rotate(90, javafx.scene.transform.Rotate.Z_AXIS);
            leftText.getTransforms().addAll(standVerticalLeft);
            Georadar3DViewer.modelGroup.getChildren().add(leftText);


            // --- 2. ОРИЕНТАЦИЯ ПРАВОЙ СТОРОНЫ ---
            // Физическая засечка справа (Покрашена в мягкий edgeMat!)
            TriangleMesh rightTick = new TriangleMesh();
            rightTick.getPoints().addAll(
                    screenX, rightBermY * this.currentScaleYZ, -piquetZ * this.currentScaleYZ,
                    screenX, (rightBermY + 0.3f) * this.currentScaleYZ, -piquetZ * this.currentScaleYZ,
                    screenX + 1.0f, rightBermY * this.currentScaleYZ, -piquetZ * this.currentScaleYZ,
                    screenX + 1.0f, (rightBermY + 0.3f) * this.currentScaleYZ, -piquetZ * this.currentScaleYZ
            );
            rightTick.getTexCoords().addAll(0, 0);
            rightTick.getFaces().addAll(0, 0, 2, 0, 1, 0, 1, 0, 2, 0, 3, 0);
            MeshView rightTickView = new MeshView(rightTick);
            rightTickView.setCullFace(javafx.scene.shape.CullFace.NONE);
            rightTickView.setMaterial(edgeMat); // СТАЛО: мягкий серый вместо черного
            Georadar3DViewer.modelGroup.getChildren().add(rightTickView);

            // Вертикальные маркеры-флажки справа
            //Text rightText = new Text(String.valueOf(meter));
            Text rightText = new Text(String.valueOf(absoluteMeter));
            rightText.setFont(Font.font("Arial", FontWeight.BOLD, 14));
            rightText.setFill(Color.DARKSLATEBLUE);
            rightText.setTranslateX(screenX - 8f);
            rightText.setTranslateY((rightBermY + 0.4f) * this.currentScaleYZ);
            rightText.setTranslateZ(-piquetZ * this.currentScaleYZ - 15f);

            javafx.scene.transform.Rotate standVerticalRight = new javafx.scene.transform.Rotate(90, javafx.scene.transform.Rotate.Z_AXIS);
            rightText.getTransforms().addAll(standVerticalRight);
            Georadar3DViewer.modelGroup.getChildren().add(rightText);
        }
    }

    private float getProjectHeight(float y) {
        float hBallastTop   = 0.40f;
        float hSubgradeZero = 0.0f;
        float hGroundLevel  = -4.0f;

        float leftEmbankmentBottom  = -7.000f;
        float leftBermStart         = -1.000f;
        float leftBallastSlopeStart = -0.600f;

        float leftShoulderTrack1    = 0.000f;
        float rightShoulderTrack1   = 3.550f;
        float leftBallastSlopeEnd   = 4.150f;

        float rightBallastSlopeStart= 5.275f;
        float leftShoulderTrack2    = 5.875f;
        float rightShoulderTrack2   = 9.425f;

        float rightBallastSlopeEnd  = 10.025f;
        float rightBermEnd          = 10.425f;
        float rightEmbankmentBottom = 16.425f;

        if (y < leftEmbankmentBottom) return hGroundLevel;
        else if (y >= leftEmbankmentBottom && y < leftBermStart) {
            float ratio = (y - leftEmbankmentBottom) / (leftBermStart - leftEmbankmentBottom);
            return hGroundLevel + ratio * (hSubgradeZero - hGroundLevel);
        }
        else if (y >= leftBermStart && y < leftBallastSlopeStart) return hSubgradeZero;
        else if (y >= leftBallastSlopeStart && y < leftShoulderTrack1) {
            float ratio = (y - leftBallastSlopeStart) / (leftShoulderTrack1 - leftBallastSlopeStart);
            return hSubgradeZero + ratio * (hBallastTop - hSubgradeZero);
        }
        else if (y >= leftShoulderTrack1 && y < rightShoulderTrack1) return hBallastTop;
        else if (y >= rightShoulderTrack1 && y < leftBallastSlopeEnd) {
            float ratio = (y - rightShoulderTrack1) / (leftBallastSlopeEnd - rightShoulderTrack1);
            return hBallastTop + ratio * (hSubgradeZero - hBallastTop);
        }
        else if (y >= leftBallastSlopeEnd && y < rightBallastSlopeStart) return hSubgradeZero;
        else if (y >= rightBallastSlopeStart && y < leftShoulderTrack2) {
            float ratio = (y - rightBallastSlopeStart) / (leftShoulderTrack2 - rightBallastSlopeStart);
            return hSubgradeZero + ratio * (hBallastTop - hSubgradeZero);
        }
        else if (y >= leftShoulderTrack2 && y < rightShoulderTrack2) return hBallastTop;
        else if (y >= rightShoulderTrack2 && y < rightBallastSlopeEnd) {
            float ratio = (y - rightShoulderTrack2) / (rightBallastSlopeEnd - rightShoulderTrack2);
            return hBallastTop + ratio * (hSubgradeZero - hBallastTop);
        }
        else if (y >= rightBallastSlopeEnd && y < rightBermEnd) return hSubgradeZero;
        else if (y >= rightBermEnd && y < rightEmbankmentBottom) {
            float ratio = (y - rightBermEnd) / (rightEmbankmentBottom - rightBermEnd);
            return hSubgradeZero + ratio * (hGroundLevel - hSubgradeZero);
        }
        else return hGroundLevel;
    }

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
}


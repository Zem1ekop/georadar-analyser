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

        // --- НАСТРОЙКА ОБЪЕМНОГО СВЕТА ДЛЯ МАСШТАБА 1:1 ---
        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(10);  // Светит в центр начального участка пути
        pointLight.setTranslateY(-20); // Подвешиваем «лампочку» на 20 метров над рельсами
        pointLight.setTranslateZ(-30); // Сдвигаем свет чуть ближе к наблюдателю
        root3D.getChildren().add(pointLight);

        AmbientLight ambientLight = new AmbientLight(Color.rgb(80, 80, 80));
        root3D.getChildren().add(ambientLight);

        camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(5000.0);

        // Позиционируем камеру для красивого панорамного ракурса
        camera.setTranslateX(300);   // Смотрим в центр модели
        camera.setTranslateY(-80);  // Поднимаем камеру чуть выше над рельсами
        camera.setTranslateZ(-800);  // Отодвигаем на комфортное расстояние


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


                System.out.println("=== Запуск пакетного импорта профилей ===");
                System.out.println("=== Очистка базы данных от старой синтетики ===");
                // Стираем старый дефолтный участок, чтобы он не подмешивал свои точки к реальному файлу!
                if (Georadar3DViewer.targetSectionId != null) {
                    Georadar3DViewer.sectionRepository.deleteById(Georadar3DViewer.targetSectionId);
                }

                Long freshSectionId = null;

                for (File file : this.selectedFiles) {
                    try (FileInputStream fis = new FileInputStream(file)) {

                        // Передаем freshSectionId аргументом. На первом шаге он null (создастся новый участок),
                        // на последующих шагах новые профили будут вливаться в этот же ID!
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
            double newZ = z + se.getDeltaY() * 1.5; // Сделали зум еще чуть более отзывчивым

            // СТАЛО: разрешаем камере отлетать до -2500 пикселей в космос!
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
        System.out.println("=== СИСТЕМА ДИНАМИЧЕСКИХ СЛОЕВ ОБНАРУЖИЛА: " + layerNames + " ===");

        // Шаг координатной сетки вдоль пути — каждые 4 метра для высокой точности
        float gridStepX = 4.0f;

        // АВТОМАТИЧЕСКИ НАХОДИМ МИНИМАЛЬНУЮ ОБЩУЮ ДЛИНУ УЧАСТКА ПО ВСЕМ ПРОФИЛЯМ
        float minMaxX = Float.MAX_VALUE;
        for (RadarProfile p : section.getProfiles()) {
            if (p.getPoints() != null && !p.getPoints().isEmpty()) {
                // Создаем чистый, изменяемый список-копию, чтобы обойти ограничения базы данных
                List<LayerPoint> sortedPts = new ArrayList<>(p.getPoints());
                sortedPts.sort(Comparator.comparing(LayerPoint::getDistanceX));

                // Теперь метод get() сработает идеально и без подчеркиваний!
                float profileLastX = sortedPts.get(sortedPts.size() - 1).getDistanceX();
                if (profileLastX < minMaxX) {
                    minMaxX = profileLastX;
                }
            }
        }
        // Задаем безопасный предел интерполяции (минус 5 метров на крайние погрешности файла)
        float maxLimitX = minMaxX - 5.0f;
        System.out.println("--- ГАРАНТИРОВАННАЯ ДЛИНА ИНТЕРПОЛЯЦИИ УЧАСТКА: " + maxLimitX + " метров ---");

        for (String layer : layerNames) {
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

            // Считаем, сколько узлов сетки поместится в длину
            int gridNodesCountX = (int) (maxLimitX / gridStepX) + 1;

            for (RadarProfile profile : profilesWithThisLayer) {
                float y = profile.getLateralOffset();

                List<LayerPoint> layerPoints = new ArrayList<>();

                // СТАЛО: Берем точки СТРОГО из текущего профиля,
                // полностью исключая перекрестное подмешивание из других файлов пакета!
                for (LayerPoint pt : profile.getPoints()) {
                    if (pt.getLayerName().equalsIgnoreCase(layer)) {
                        layerPoints.add(pt);
                    }
                }

                // Сортируем строго изолированные точки одного прохода
                layerPoints.sort(Comparator.comparing(LayerPoint::getDistanceX));

                // Математическая интерполяция по ровной сетке X
                for (int i = 0; i < gridNodesCountX; i++) {
                    float targetX = i * gridStepX;
                    float interpolatedZ = getInterpolatedZ(layerPoints, targetX);

                    // Переводим в 3D координаты (масштабы оставляем как есть)
                    pointsList.add(targetX * 1.5f);
                    pointsList.add(y * 40f);
                    pointsList.add(-interpolatedZ * 200f);
                }
            }

            float[] pointsArray = new float[pointsList.size()];
            for (int i = 0; i < pointsList.size(); i++) pointsArray[i] = pointsList.get(i);
            mesh.getPoints().setAll(pointsArray);
            mesh.getTexCoords().addAll(0, 0);

            // Связываем треугольники по идеальной регулярной сетке узлов
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
            PhongMaterial material = new PhongMaterial();
            material.setDiffuseColor(layerColors.getOrDefault(layer, Color.CYAN));
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

        // Ищем два ближайших соседа слева и справа от targetX
        for (int i = 0; i < points.size() - 1; i++) {
            LayerPoint p1 = points.get(i);
            LayerPoint p2 = points.get(i + 1);
            if (targetX >= p1.getDistanceX() && targetX <= p2.getDistanceX()) {

                // ЗАЩИТА ОТ ДЕЛЕНИЯ НА НОЛЬ ( Field Дубляж точек в БД )
                if (Math.abs(p2.getDistanceX() - p1.getDistanceX()) < 0.001f) {
                    return p1.getDepthZ(); // Если точки в одной координате, просто отдаем Z без деления!
                }

                float ratio = (targetX - p1.getDistanceX()) / (p2.getDistanceX() - p1.getDistanceX());
                return p1.getDepthZ() + ratio * (p2.getDepthZ() - p1.getDepthZ());
            }
        }
        return points.get(0).getDepthZ();
    }
}

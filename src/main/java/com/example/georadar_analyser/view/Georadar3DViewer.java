package com.example.georadar_analyser.view;

import com.example.georadar_analyser.entity.LayerPoint;
import com.example.georadar_analyser.entity.RadarProfile;
import com.example.georadar_analyser.entity.TrackSection;
import com.example.georadar_analyser.repository.TrackSectionRepository;
import javafx.application.Application;
import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class Georadar3DViewer extends Application {

    // Сделаем репозиторий статическим, чтобы JavaFX-поток имел к нему доступ при старте окна
    private static TrackSectionRepository sectionRepository;
    private static Long targetSectionId;

    // Конструктор по умолчанию необходим для запуска JavaFX
    public Georadar3DViewer() {}

    public static void initDependencies(TrackSectionRepository repository) {
        sectionRepository = repository;
    }

    /**
     * Метод для вызова 3D-окна из любой части Spring Boot приложения
     */
    public void viewSection(Long sectionId) {
        targetSectionId = sectionId;
        // Запускаем поток JavaFX сцены
        Application.launch(Georadar3DViewer.class);
    }

    @Override
    public void start(Stage primaryStage) {
        // 1. Извлекаем данные из БД
        Optional<TrackSection> sectionOpt = sectionRepository.findById(targetSectionId);
        if (sectionOpt.isEmpty()) {
            System.err.println("Ошибка: Участок ж/д пути с ID " + targetSectionId + " не найден в БД!");
            return;
        }
        TrackSection section = sectionOpt.get();

        // Создаем корневую 3D группу
        Group root3D = new Group();

        // Палитра цветов для слоев георадара (Балласт - серый, Песок - желтый, Глина/Земля - коричневый)
        Map<String, Color> layerColors = Map.of(
                "балласт", Color.GRAY,
                "песок", Color.GOLD,
                "земляное полотно", Color.SADDLEBROWN
        );

        // 2. Группируем профили ж/д пути
        List<RadarProfile> profiles = section.getProfiles();
        if (profiles.size() < 2) {
            System.err.println("Ошибка: Для построения 3D поверхности нужно как минимум 2 параллельных профиля!");
            return;
        }

        // Вытаскиваем уникальные имена слоев на этом участке
        Set<String> layerNames = new HashSet<>();
        for (RadarProfile p : profiles) {
            for (LayerPoint pt : p.getPoints()) {
                layerNames.add(pt.getLayerName().toLowerCase().trim());
            }
        }

        // 3. Строим 3D-сетку (Mesh) отдельно для каждого конструктивного слоя
        for (String layer : layerNames) {
            TriangleMesh mesh = new TriangleMesh();

            // Списки для индексов точек
            List<Float> pointsList = new ArrayList<>();

            // На ж/д путях шаг оцифровки одинаковый, считаем количество точек на один профиль
            int pointsPerProfile = profiles.get(0).getPoints().size();

            // Заполняем массив вершин (Vertices)
            for (RadarProfile profile : profiles) {
                float y = profile.getLateralOffset(); // Расстояние от оси пути (ширина)

                for (LayerPoint pt : profile.getPoints()) {
                    if (pt.getLayerName().toLowerCase().trim().equals(layer)) {
                        pointsList.add(pt.getDistanceX()); // Координата X (длина пути)
                        pointsList.add(y);                 // Координата Y (ширина пути)
                        // Инвертируем Z в минус, чтобы просадки уходили физически "вниз"
                        pointsList.add(-pt.getDepthZ() * 10f); // Умножаем на 10 для наглядности рельефа
                    }
                }
            }

            // Переносим координаты точек в Mesh
            float[] pointsArray = new float[pointsList.size()];
            for (int i = 0; i < pointsList.size(); i++) pointsArray[i] = pointsList.get(i);
            mesh.getPoints().setAll(pointsArray);

            // Добавляем фиктивную текстурную координату (обязательно для JavaFX)
            mesh.getTexCoords().addAll(0, 0);

            // Натягиваем сетку полигонов (Faces) — связываем соседние точки в треугольники
            int numProfiles = profiles.size();
            for (int p = 0; p < numProfiles - 1; p++) {
                for (int t = 0; t < pointsPerProfile - 1; t++) {
                    int p0 = p * pointsPerProfile + t;
                    int p1 = p0 + 1;
                    int p2 = (p + 1) * pointsPerProfile + t;
                    int p3 = p2 + 1;

                    // Задаем треугольники (точки + индекс текстуры 0)
                    mesh.getFaces().addAll(p0, 0, p2, 0, p1, 0);
                    mesh.getFaces().addAll(p1, 0, p2, 0, p3, 0);
                }
            }

            // Создаем визуальный 3D объект на сцене
            MeshView meshView = new MeshView(mesh);
            PhongMaterial material = new PhongMaterial();
            material.setDiffuseColor(layerColors.getOrDefault(layer, Color.CYAN).deriveColor(0, 1, 1, 0.7)); // 0.7 - легкая прозрачность
            meshView.setMaterial(material);

            // Добавляем слой в общую 3D-группу
            root3D.getChildren().add(meshView);
        }

        // 4. Настраиваем камеру обзора
        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(1000.0);
        camera.setTranslateZ(-150); // Отодвигаем камеру назад, чтобы увидеть весь масштаб
        camera.setTranslateX(50);
        camera.setTranslateY(0);

        // Собираем сцену
        Scene scene = new Scene(root3D, 1024, 768, true, SceneAntialiasing.BALANCED);
        scene.setCamera(camera);
        scene.setFill(Color.LIGHTBLUE); // Цвет неба/фона

        primaryStage.setTitle("3D Визуализация конструктивных слоев ж/д пути и просадок грунта");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}


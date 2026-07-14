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

    private static TrackSectionRepository sectionRepository;
    private static Long targetSectionId;

    private double mousePosX;
    private double mousePosY;
    private double mouseOldX;
    private double mouseOldY;

    public Georadar3DViewer() {}

    public static void initDependencies(TrackSectionRepository repository) {
        sectionRepository = repository;
    }

    public void viewSection(Long sectionId) {
        targetSectionId = sectionId;
        Application.launch(Georadar3DViewer.class);
    }

    @Override
    public void start(Stage primaryStage) {
        Optional<TrackSection> sectionOpt = sectionRepository.findByIdWithWithAllData(targetSectionId);
        if (sectionOpt.isEmpty()) {
            System.err.println("Ошибка: Участок ж/д пути с ID " + targetSectionId + " не найден в БД!");
            return;
        }
        TrackSection section = sectionOpt.get();

        Group root3D = new Group();

        // Разделяем контейнеры для правильной CAD-математики вращения:
        Group globalGroup = new Group(); // Этот узел будет крутиться вокруг строгой вертикали экрана
        Group modelGroup = new Group();  // Этот узел хранит сами 3D-сетки слоев

        globalGroup.getChildren().add(modelGroup);
        root3D.getChildren().add(globalGroup);

        Map<String, Color> layerColors = Map.of(
                "балласт", Color.GRAY,
                "песок", Color.GOLD,
                "земляное полотно", Color.SADDLEBROWN
        );

        List<RadarProfile> sortedProfiles = new ArrayList<>(section.getProfiles());
        sortedProfiles.sort(Comparator.comparing(RadarProfile::getLateralOffset));

        if (sortedProfiles.size() < 2) {
            System.err.println("Ошибка: Для построения 3D поверхности нужно как минимум 2 параллельных профиля!");
            return;
        }

        Set<String> layerNames = new HashSet<>();
        for (RadarProfile p : sortedProfiles) {
            for (LayerPoint pt : p.getPoints()) {
                layerNames.add(pt.getLayerName().toLowerCase().trim());
            }
        }

        for (String layer : layerNames) {
            TriangleMesh mesh = new TriangleMesh();
            List<Float> pointsList = new ArrayList<>();

            int pointsPerProfile = 0;
            boolean firstProfileCounted = false;

            for (RadarProfile profile : sortedProfiles) {
                float y = profile.getLateralOffset();

                List<LayerPoint> layerPoints = new ArrayList<>();
                for (LayerPoint pt : profile.getPoints()) {
                    if (pt.getLayerName().toLowerCase().trim().equals(layer)) {
                        layerPoints.add(pt);
                    }
                }
                layerPoints.sort((p1, p2) -> Float.compare(p1.getDistanceX(), p2.getDistanceX()));

                if (!firstProfileCounted) {
                    pointsPerProfile = layerPoints.size();
                    firstProfileCounted = true;
                }

                for (LayerPoint pt : layerPoints) {
                    pointsList.add((pt.getDistanceX() - 11.0f) * 40f);
                    pointsList.add(y * 40f);
                    pointsList.add(-pt.getDepthZ() * 60f);
                }
            }

            if (pointsPerProfile < 2) continue;

            float[] pointsArray = new float[pointsList.size()];
            for (int i = 0; i < pointsList.size(); i++) pointsArray[i] = pointsList.get(i);
            mesh.getPoints().setAll(pointsArray);
            mesh.getTexCoords().addAll(0, 0);

            int numProfiles = sortedProfiles.size();
            for (int p = 0; p < numProfiles - 1; p++) {
                for (int t = 0; t < pointsPerProfile - 1; t++) {
                    int p0 = p * pointsPerProfile + t;
                    int p1 = p0 + 1;
                    int p2 = (p + 1) * pointsPerProfile + t;
                    int p3 = p2 + 1;

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

        PointLight pointLight = new PointLight(Color.WHITE);
        pointLight.setTranslateX(0);
        pointLight.setTranslateY(-100);
        pointLight.setTranslateZ(-200);
        root3D.getChildren().add(pointLight);

        AmbientLight ambientLight = new AmbientLight(Color.rgb(80, 80, 80));
        root3D.getChildren().add(ambientLight);

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(1000.0);
        camera.setTranslateZ(-350);

        Scene scene = new Scene(root3D, 1024, 768, true, SceneAntialiasing.BALANCED);
        scene.setCamera(camera);
        scene.setFill(Color.LIGHTBLUE);

        // --- НАСТРОЙКА ПРАВИЛЬНЫХ НЕЗАВИСИМЫХ ОСЕЙ ВРАЩЕНИЯ ---
        // rotateY вешаем на globalGroup — это даст чистое вращение вокруг ВЕРТИКАЛЬНОЙ оси экрана
        javafx.scene.transform.Rotate rotateY = new javafx.scene.transform.Rotate(-30, javafx.geometry.Point3D.ZERO.add(0, 1, 0));
        globalGroup.getTransforms().add(rotateY);

        // rotateX вешаем на внутреннюю modelGroup — это даст наклон путей к нам/от нас
        javafx.scene.transform.Rotate rotateX = new javafx.scene.transform.Rotate(45, javafx.geometry.Point3D.ZERO.add(1, 0, 0));
        modelGroup.getTransforms().add(rotateX);

        scene.setOnMousePressed(me -> {
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
        });

        scene.setOnMouseDragged(me -> {
            mouseOldX = mousePosX;
            mouseOldY = mousePosY;
            mousePosX = me.getSceneX();
            mousePosY = me.getSceneY();
            double mouseDeltaX = (mousePosX - mouseOldX);
            double mouseDeltaY = (mousePosY - mouseOldY);

            // ЛЕВАЯ КНОПКА МЫШИ — Чистое, ортогональное вращение
            if (me.isPrimaryButtonDown()) {
                // Движение мыши влево-вправо вращает ВСЮ сцену вокруг вертикали экрана
                rotateY.setAngle(rotateY.getAngle() + mouseDeltaX * 0.4);
                // Движение мыши вверх-вниз наклоняет рельеф путей к наблюдателю
                rotateX.setAngle(rotateX.getAngle() - mouseDeltaY * 0.4);
            }
            // ПРАВАЯ КНОПКА МЫШИ — Сдвиг (панорамирование) всей сцены
            else if (me.isSecondaryButtonDown()) {
                camera.setTranslateX(camera.getTranslateX() - mouseDeltaX * 0.2);
                camera.setTranslateY(camera.getTranslateY() - mouseDeltaY * 0.2);
            }
        });

        scene.setOnScroll(se -> {
            double z = camera.getTranslateZ();
            camera.setTranslateZ(z + se.getDeltaY() * 0.8);
        });

        primaryStage.setTitle("3D Визуализация конструктивных слоев ж/д пути и просадок грунта");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}




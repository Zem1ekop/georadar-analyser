package com.example.georadar_analyser.service;

import com.example.georadar_analyser.entity.LayerPoint;
import com.example.georadar_analyser.entity.RadarProfile;
import com.example.georadar_analyser.entity.TrackSection;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.*;

@Service
public class DxfExportService {

    private final GeometryService geometryService;

    public DxfExportService(GeometryService geometryService) {
        this.geometryService = geometryService;
    }

    /**
     * Экспортирует поперечный разрез на заданном метре пути в формат AutoCAD DXF.
     * Использует кодировку Windows-1251 для корректного отображения кириллицы в CAD-системах.
     */
    public void exportCrossSectionToDxf(TrackSection section, float meterX, float startLocationX, File targetFile) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(targetFile, Charset.forName("Windows-1251")))) {
            // 1. Заголовок и таблица слоев AutoCAD
            writeHeaderAndLayers(writer);

            // 2. Секция геометрических примитивов (ENTITIES)
            writer.println("0\nSECTION\n2\nENTITIES");

            // 2.0. Координатная сетка и шкала высотных отметок
            writeCoordinateGridAndScales(writer);


            // 2.1. Заголовок чертежа и пунктирные оси путей (белый цвет ACI = 7)
            int absoluteMeter = Math.round(meterX + startLocationX);
            writeText(writer, "TEXT_ANNOTATIONS", -7.0f, 1.5f, 0.35f,
                    String.format(Locale.US, "Поперечный профиль: ПК %d м (X = %.2f м)", absoluteMeter, meterX));

            writeLine(writer, "AXIS_LINES", 1.775f, -4.2f, 1.775f, 1.0f);
            writeText(writer, "TEXT_ANNOTATIONS", 1.45f, -4.5f, 0.2f, "Ось 1");

            writeLine(writer, "AXIS_LINES", 7.650f, -4.2f, 7.650f, 1.0f);
            writeText(writer, "TEXT_ANNOTATIONS", 7.32f, -4.5f, 0.2f, "Ось 2");

            // 2.2. Проектный контур земляного полотна и основание
            List<Point2D.Float> embankmentPoints = new ArrayList<>();
            for (float y : GeometryService.PROJECT_Y_GRID) {
                embankmentPoints.add(new Point2D.Float(y, geometryService.getProjectHeight(y)));
            }
            writePolyline(writer, "PROJECT_EMBANKMENT", embankmentPoints);
            writePolyline(writer, "PROJECT_EMBANKMENT", List.of(new Point2D.Float(-7.0f, -4.0f), new Point2D.Float(16.425f, -4.0f)));

            // 2.3. Шпалы и рельсы
            float[][] sleepers = {{0.400f, 3.150f}, {6.275f, 9.025f}};
            for (float[] s : sleepers) {
                writeRectangle(writer, "RAILS_AND_SLEEPERS", s[0], 0.40f, s[1], 0.60f);
            }

            float[] rails = {1.775f - 0.76f, 1.775f + 0.76f, 7.650f - 0.76f, 7.650f + 0.76f};
            for (float rY : rails) {
                writeRectangle(writer, "RAILS_AND_SLEEPERS", rY - 0.075f, 0.60f, rY + 0.075f, 0.78f);
            }

            // 2.4. Георадарные слои с экстраполяцией до откосов
            List<RadarProfile> sortedProfiles = new ArrayList<>(section.getProfiles());
            sortedProfiles.sort(Comparator.comparing(RadarProfile::getLateralOffset));

            exportLayerLine(writer, sortedProfiles, meterX, "слой 2", "LAYER_2_BALLAST");
            exportLayerLine(writer, sortedProfiles, meterX, "слой 3", "LAYER_3_SUBGRADE");

            // 2.5. Сводная CAD-таблица относительных глубин слоев справа от чертежа
            writeDepthSummaryTable(writer, sortedProfiles, meterX);

            // Завершение файла DXF
            writer.println("0\nENDSEC\n0\nEOF");
        }
    }

    private void exportLayerLine(PrintWriter writer, List<RadarProfile> profiles, float meterX, String layerName, String dxfLayer) {
        List<Point2D.Float> pts = new ArrayList<>();
        for (RadarProfile p : profiles) {
            List<LayerPoint> okPoints = new ArrayList<>();
            for (LayerPoint pt : p.getPoints()) {
                if (pt.getLayerName().equalsIgnoreCase(layerName)) okPoints.add(pt);
            }
            if (okPoints.isEmpty()) continue;
            okPoints.sort(Comparator.comparing(LayerPoint::getDistanceX));
            float z = geometryService.getInterpolatedZ(okPoints, meterX);
            pts.add(new Point2D.Float(p.getLateralOffset(), z));
        }

        if (pts.size() < 2) return;

        List<Point2D.Float> fullLine = geometryService.extrapolateLayerToSlopes(pts);
        writePolyline(writer, dxfLayer, fullLine);
    }

    private void writeHeaderAndLayers(PrintWriter writer) {
        writer.println("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1009\n0\nENDSEC");
        writer.println("0\nSECTION\n2\nTABLES");

        writer.println("0\nTABLE\n2\nLTYPE\n70\n2");
        writer.println("0\nLTYPE\n2\nCONTINUOUS\n70\n64\n3\nSolid line\n72\n65\n73\n0\n40\n0.0");
        writer.println("0\nLTYPE\n2\nDASHED\n70\n64\n3\nDashed line __ __ __\n72\n65\n73\n2\n40\n0.6\n49\n0.4\n49\n-0.2");
        writer.println("0\nENDTAB");

        writer.println("0\nTABLE\n2\nLAYER\n70\n8");
        writeLayerDef(writer, "0", 7, "CONTINUOUS");
        writeLayerDef(writer, "PROJECT_EMBANKMENT", 7, "CONTINUOUS"); // 7 = Белый цвет
        writeLayerDef(writer, "RAILS_AND_SLEEPERS", 7, "CONTINUOUS"); // 7 = Белый цвет
        writeLayerDef(writer, "LAYER_2_BALLAST", 2, "CONTINUOUS");    // 2 = Желтый
        writeLayerDef(writer, "LAYER_3_SUBGRADE", 34, "CONTINUOUS");  // 34 = Коричневый
        writeLayerDef(writer, "TEXT_ANNOTATIONS", 7, "CONTINUOUS");
        writeLayerDef(writer, "AXIS_LINES", 7, "DASHED");
        writeLayerDef(writer, "COORDINATE_GRID", 252, "CONTINUOUS");
        writer.println("0\nENDTAB");

        writer.println("0\nENDSEC");
    }

    private void writeLayerDef(PrintWriter writer, String name, int colorIndex, String lineType) {
        writer.printf(Locale.US, "0\nLAYER\n2\n%s\n70\n64\n62\n%d\n6\n%s\n", name, colorIndex, lineType);
    }


    private void writeLine(PrintWriter writer, String layer, float x1, float y1, float x2, float y2) {
        writer.printf(Locale.US, "0\nLINE\n8\n%s\n10\n%.4f\n20\n%.4f\n11\n%.4f\n21\n%.4f\n", layer, x1, y1, x2, y2);
    }

    private void writeRectangle(PrintWriter writer, String layer, float x1, float y1, float x2, float y2) {
        List<Point2D.Float> rect = List.of(
                new Point2D.Float(x1, y1),
                new Point2D.Float(x2, y1),
                new Point2D.Float(x2, y2),
                new Point2D.Float(x1, y2),
                new Point2D.Float(x1, y1)
        );
        writePolyline(writer, layer, rect);
    }

    private void writePolyline(PrintWriter writer, String layer, List<Point2D.Float> points) {
        // Задаем толщину: 0.04 м (4 см) для контура насыпи и слоев, 0.02 м для рельсов и шпал
        float width = layer.equals("RAILS_AND_SLEEPERS") ? 0.02f : 0.04f;

        writer.printf(Locale.US, "0\nPOLYLINE\n8\n%s\n66\n1\n70\n0\n40\n%.3f\n41\n%.3f\n", layer, width, width);
        for (Point2D.Float pt : points) {
            writer.printf(Locale.US, "0\nVERTEX\n8\n%s\n10\n%.4f\n20\n%.4f\n", layer, pt.x, pt.y);
        }
        writer.println("0\nSEQEND");
    }

    private void writeText(PrintWriter writer, String layer, float x, float y, float height, String text) {
        writer.printf(Locale.US, "0\nTEXT\n8\n%s\n10\n%.4f\n20\n%.4f\n40\n%.2f\n1\n%s\n", layer, x, y, height, text);
    }

    /**
     * Отрисовывает координатную сетку, вертикальную шкалу высотных отметок (Z)
     * и горизонтальную шкалу смещений от оси (Y).
     */
    private void writeCoordinateGridAndScales(PrintWriter writer) {
        float minY = -7.0f;
        float maxY = 16.425f;
        float minZ = -4.0f;
        float maxZ = 1.0f;

        // 1. Вертикальная шкала высотных отметок Z
        float scaleLineY = -7.8f;
        writeLine(writer, "TEXT_ANNOTATIONS", scaleLineY, minZ, scaleLineY, maxZ);
        writeText(writer, "TEXT_ANNOTATIONS", scaleLineY - 0.6f, maxZ + 0.35f, 0.22f, "Z, м");

        for (int z = -4; z <= 1; z++) {
            float fz = (float) z;
            writeLine(writer, "TEXT_ANNOTATIONS", scaleLineY - 0.15f, fz, scaleLineY + 0.15f, fz);
            writeLine(writer, "COORDINATE_GRID", minY, fz, maxY, fz);
            String label = (z > 0) ? String.format(Locale.US, "+%d.0", z) : String.format(Locale.US, "%d.0", z);
            writeText(writer, "TEXT_ANNOTATIONS", scaleLineY - 1.1f, fz - 0.08f, 0.18f, label);
        }

        // 2. Горизонтальная шкала расстояний Y
        float scaleLineZ = -4.8f;
        writeLine(writer, "TEXT_ANNOTATIONS", minY, scaleLineZ, maxY, scaleLineZ);
        writeText(writer, "TEXT_ANNOTATIONS", maxY + 0.4f, scaleLineZ - 0.08f, 0.22f, "Y, м");

        for (int y = -6; y <= 16; y += 2) {
            float fy = (float) y;
            writeLine(writer, "TEXT_ANNOTATIONS", fy, scaleLineZ - 0.15f, fy, scaleLineZ + 0.15f);
            writeLine(writer, "COORDINATE_GRID", fy, minZ, fy, maxZ);
            String label = String.format(Locale.US, "%d", y);
            writeText(writer, "TEXT_ANNOTATIONS", fy - 0.2f, scaleLineZ - 0.45f, 0.18f, label);
        }
    }

    /**
     * Отрисовывает сводную таблицу относительных глубин слоев (от поверхности рельефа)
     * справа от поперечного профиля начиная с координаты X = 18.5 м.
     */
    private void writeDepthSummaryTable(PrintWriter writer, List<RadarProfile> profiles, float meterX) {
        if (profiles.isEmpty()) return;

        float startX = 18.5f;
        float startZ = 1.0f;
        float firstColWidth = 3.8f;
        float dataColWidth = 2.6f;
        float rowHeight = 0.65f;

        int totalCols = profiles.size() + 1;
        int totalRows = 4; // Шапка + 3 слоя
        float totalWidth = firstColWidth + profiles.size() * dataColWidth;
        float totalHeight = totalRows * rowHeight;

        // Заголовок над таблицей
        writeText(writer, "TEXT_ANNOTATIONS", startX, startZ + 0.35f, 0.28f, "Ведомость глубин залегания слоев (от рельефа), м");

        // 1. Горизонтальные линии сетки таблицы
        for (int r = 0; r <= totalRows; r++) {
            float z = startZ - r * rowHeight;
            writeLine(writer, "TEXT_ANNOTATIONS", startX, z, startX + totalWidth, z);
        }

        // 2. Вертикальные линии сетки таблицы
        writeLine(writer, "TEXT_ANNOTATIONS", startX, startZ, startX, startZ - totalHeight);
        for (int c = 0; c < totalCols; c++) {
            float x = startX + firstColWidth + c * dataColWidth;
            writeLine(writer, "TEXT_ANNOTATIONS", x, startZ, x, startZ - totalHeight);
        }

        // 3. Заполнение шапки таблицы
        float headerTextZ = startZ - rowHeight * 0.68f;
        writeText(writer, "TEXT_ANNOTATIONS", startX + 0.25f, headerTextZ, 0.22f, "Слой / Профиль");

        for (int i = 0; i < profiles.size(); i++) {
            RadarProfile p = profiles.get(i);
            float colX = startX + firstColWidth + i * dataColWidth + 0.2f;
            String colTitle = String.format(Locale.US, "Y=%.2f", p.getLateralOffset());
            writeText(writer, "TEXT_ANNOTATIONS", colX, headerTextZ, 0.22f, colTitle);
        }

        // 4. Заполнение строк данных по слоям
        String[] layerKeys = {"слой 1", "слой 2", "слой 3"};
        String[] layerTitles = {"Слой 1 (Балласт)", "Слой 2 (Песок)", "Слой 3 (Грунт)"};
        String[] dxfLayers = {"TEXT_ANNOTATIONS", "LAYER_2_BALLAST", "LAYER_3_SUBGRADE"};

        for (int r = 0; r < layerKeys.length; r++) {
            float rowTextZ = startZ - (r + 1) * rowHeight - rowHeight * 0.68f;
            writeText(writer, dxfLayers[r], startX + 0.25f, rowTextZ, 0.20f, layerTitles[r]);

            for (int c = 0; c < profiles.size(); c++) {
                RadarProfile p = profiles.get(c);
                float cellX = startX + firstColWidth + c * dataColWidth + 0.45f;
                float surfaceZ = geometryService.getProjectHeight(p.getLateralOffset());

                if (r == 0) {
                    // Слой 1 начинается от дневной поверхности (относительная глубина = 0.00 м)
                    writeText(writer, dxfLayers[r], cellX, rowTextZ, 0.20f, "0.00");
                } else {
                    List<LayerPoint> okPoints = new ArrayList<>();
                    for (LayerPoint pt : p.getPoints()) {
                        if (pt.getLayerName().equalsIgnoreCase(layerKeys[r])) okPoints.add(pt);
                    }
                    if (!okPoints.isEmpty()) {
                        okPoints.sort(Comparator.comparing(LayerPoint::getDistanceX));
                        float absZ = geometryService.getInterpolatedZ(okPoints, meterX);
                        float relDepth = Math.max(0.0f, surfaceZ - absZ);
                        writeText(writer, dxfLayers[r], cellX, rowTextZ, 0.20f, String.format(Locale.US, "%.2f", relDepth));
                    } else {
                        writeText(writer, dxfLayers[r], cellX, rowTextZ, 0.20f, "-");
                    }
                }
            }
        }
    }
}


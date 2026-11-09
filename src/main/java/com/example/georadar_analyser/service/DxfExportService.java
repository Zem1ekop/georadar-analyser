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

            // 2.1. Заголовок чертежа и пунктирные оси путей (белый цвет ACI = 7)
            int absoluteMeter = Math.round(meterX + startLocationX);
            writeText(writer, "TEXT_ANNOTATIONS", -7.0f, 1.5f, 0.35f,
                    String.format(Locale.US, "Поперечный профиль: ПК %d м (X = %.2f м)", absoluteMeter, meterX));

            writeLine(writer, "AXIS_LINES", 1.775f, -4.2f, 1.775f, 1.0f);
            writeText(writer, "TEXT_ANNOTATIONS", 1.45f, -4.5f, 0.2f, "Ось 1");

            writeLine(writer, "AXIS_LINES", 7.650f, -4.2f, 7.650f, 1.0f);
            writeText(writer, "TEXT_ANNOTATIONS", 7.32f, -4.5f, 0.2f, "Ось 2");

            // 2.2. Проектный контур земляного полотна и основание
            float[] projectYGrid = {-7.0f, -1.0f, -0.6f, 0.0f, 1.775f, 3.55f, 4.15f, 4.7f, 5.275f, 5.875f, 7.65f, 9.425f, 10.025f, 10.425f, 16.425f};
            List<Point2D.Float> embankmentPoints = new ArrayList<>();
            for (float y : projectYGrid) {
                embankmentPoints.add(new Point2D.Float(y, getProjectHeight(y)));
            }
            writePolyline(writer, "PROJECT_EMBANKMENT", embankmentPoints);
            writeLine(writer, "PROJECT_EMBANKMENT", -7.0f, -4.0f, 16.425f, -4.0f);

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
            float z = getInterpolatedZ(okPoints, meterX);
            pts.add(new Point2D.Float(p.getLateralOffset(), z));
        }

        if (pts.size() < 2) return;

        // Левая врезка в откос
        Point2D.Float first = pts.get(0);
        Point2D.Float second = pts.get(1);
        float kLeft = (second.y - first.y) / (second.x - first.x + 0.001f);
        float extLeftY = -7.0f;
        float extLeftZ = first.y + kLeft * (extLeftY - first.x);
        for (float tempY = first.x; tempY >= -7.0f; tempY -= 0.1f) {
            float tempZ = first.y + kLeft * (tempY - first.x);
            float slopeZ = getProjectHeight(tempY);
            if (tempZ >= slopeZ) {
                extLeftY = tempY;
                extLeftZ = slopeZ;
                break;
            }
        }
        if (extLeftZ < -4.0f) { extLeftZ = -4.0f; extLeftY = -7.0f; }

        // Правая врезка в откос
        Point2D.Float last = pts.get(pts.size() - 1);
        Point2D.Float prevLast = pts.get(pts.size() - 2);
        float kRight = (last.y - prevLast.y) / (last.x - prevLast.x + 0.001f);
        float extRightY = 16.425f;
        float extRightZ = last.y + kRight * (extRightY - last.x);
        for (float tempY = last.x; tempY <= 16.425f; tempY += 0.1f) {
            float tempZ = last.y + kRight * (tempY - last.x);
            float slopeZ = getProjectHeight(tempY);
            if (tempZ >= slopeZ) {
                extRightY = tempY;
                extRightZ = slopeZ;
                break;
            }
        }
        if (extRightZ < -4.0f) { extRightZ = -4.0f; extRightY = 16.425f; }

        List<Point2D.Float> fullLine = new ArrayList<>();
        fullLine.add(new Point2D.Float(extLeftY, extLeftZ));
        fullLine.addAll(pts);
        fullLine.add(new Point2D.Float(extRightY, extRightZ));

        writePolyline(writer, dxfLayer, fullLine);
    }

    private void writeHeaderAndLayers(PrintWriter writer) {
        writer.println("0\nSECTION\n2\nHEADER\n9\n$ACADVER\n1\nAC1009\n0\nENDSEC");
        writer.println("0\nSECTION\n2\nTABLES");

        // 1. Таблица типов линий (LTYPE): объявляем CONTINUOUS и DASHED (пунктир)
        writer.println("0\nTABLE\n2\nLTYPE\n70\n2");
        writer.println("0\nLTYPE\n2\nCONTINUOUS\n70\n0\n3\nSolid line\n72\n65\n73\n0\n40\n0.0");
        // DASHED: длина штриха 0.4м, пробел 0.2м
        writer.println("0\nLTYPE\n2\nDASHED\n70\n0\n3\nDashed line __ __ __\n72\n65\n73\n2\n40\n0.6\n49\n0.4\n49\n-0.2");
        writer.println("0\nENDTAB");

        // 2. Таблица слоев (LAYER)
        writer.println("0\nTABLE\n2\nLAYER\n70\n6");
        writeLayerDef(writer, "PROJECT_EMBANKMENT", 8, "CONTINUOUS");
        writeLayerDef(writer, "RAILS_AND_SLEEPERS", 250, "CONTINUOUS");
        writeLayerDef(writer, "LAYER_2_BALLAST", 2, "CONTINUOUS");
        writeLayerDef(writer, "LAYER_3_SUBGRADE", 34, "CONTINUOUS");
        writeLayerDef(writer, "TEXT_ANNOTATIONS", 7, "CONTINUOUS");
        writeLayerDef(writer, "AXIS_LINES", 7, "DASHED"); // Новый слой для пунктирных осей (белый цвет = 7)
        writer.println("0\nENDTAB");

        writer.println("0\nENDSEC");
    }

    private void writeLayerDef(PrintWriter writer, String name, int colorIndex, String lineType) {
        writer.printf(Locale.US, "0\nLAYER\n2\n%s\n70\n0\n62\n%d\n6\n%s\n", name, colorIndex, lineType);
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
        writer.printf(Locale.US, "0\nPOLYLINE\n8\n%s\n66\n1\n70\n0\n", layer);
        for (Point2D.Float pt : points) {
            writer.printf(Locale.US, "0\nVERTEX\n8\n%s\n10\n%.4f\n20\n%.4f\n", layer, pt.x, pt.y);
        }
        writer.println("0\nSEQEND");
    }

    private void writeText(PrintWriter writer, String layer, float x, float y, float height, String text) {
        writer.printf(Locale.US, "0\nTEXT\n8\n%s\n10\n%.4f\n20\n%.4f\n40\n%.2f\n1\n%s\n", layer, x, y, height, text);
    }

    private float getProjectHeight(float y) {
        if (y < -7.000f) return -4.0f;
        else if (y < -1.000f) return -4.0f + ((y + 7.0f) / 6.0f) * 4.0f;
        else if (y < -0.600f) return 0.0f;
        else if (y < 0.000f) return ((y + 0.6f) / 0.6f) * 0.40f;
        else if (y < 3.550f) return 0.40f;
        else if (y < 4.150f) return 0.40f - ((y - 3.55f) / 0.6f) * 0.40f;
        else if (y < 5.275f) return 0.0f;
        else if (y < 5.875f) return ((y - 5.275f) / 0.6f) * 0.40f;
        else if (y < 9.425f) return 0.40f;
        else if (y < 10.025f) return 0.40f - ((y - 9.425f) / 0.6f) * 0.40f;
        else if (y < 10.425f) return 0.0f;
        else if (y < 16.425f) return -((y - 10.425f) / 6.0f) * 4.0f;
        else return -4.0f;
    }

    private float getInterpolatedZ(List<LayerPoint> points, float targetX) {
        if (points.isEmpty()) return 0.0f;
        if (targetX <= points.get(0).getDistanceX()) return points.get(0).getDepthZ();
        if (targetX >= points.get(points.size() - 1).getDistanceX()) return points.get(points.size() - 1).getDepthZ();

        for (int i = 0; i < points.size() - 1; i++) {
            LayerPoint p1 = points.get(i);
            LayerPoint p2 = points.get(i + 1);
            if (targetX >= p1.getDistanceX() && targetX <= p2.getDistanceX()) {
                if (Math.abs(p2.getDistanceX() - p1.getDistanceX()) < 0.001f) return p1.getDepthZ();
                float ratio = (targetX - p1.getDistanceX()) / (p2.getDistanceX() - p1.getDistanceX());
                return p1.getDepthZ() + ratio * (p2.getDepthZ() - p1.getDepthZ());
            }
        }
        return points.get(0).getDepthZ();
    }
}


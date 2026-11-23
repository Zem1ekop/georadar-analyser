package com.example.georadar_analyser.service;

import com.example.georadar_analyser.entity.LayerPoint;
import org.springframework.stereotype.Service;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeometryService {

    // Стандартная поперечная сетка проектного земляного полотна и балластной призмы (в метрах)
    public static final float[] PROJECT_Y_GRID = {
            -7.0f, -1.0f, -0.6f, 0.0f, 1.775f, 3.55f, 4.15f,
            4.7f, 5.275f, 5.875f, 7.65f, 9.425f, 10.025f, 10.425f, 16.425f
    };

    /**
     * Вычисляет проектную высотную отметку дневной поверхности насыпи/призмы (Z)
     * в зависимости от поперечного смещения (Y) от левой бровки балластной призмы 1-го пути.
     */
    public float getProjectHeight(float y) {
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

    /**
     * Выполняет линейную интерполяцию абсолютной глубины (Z) вдоль продольного пикетажа (X).
     */
    public float getInterpolatedZ(List<LayerPoint> sortedPoints, float targetX) {
        if (sortedPoints == null || sortedPoints.isEmpty()) return 0.0f;
        if (targetX <= sortedPoints.get(0).getDistanceX()) return sortedPoints.get(0).getDepthZ();
        if (targetX >= sortedPoints.get(sortedPoints.size() - 1).getDistanceX()) {
            return sortedPoints.get(sortedPoints.size() - 1).getDepthZ();
        }

        for (int i = 0; i < sortedPoints.size() - 1; i++) {
            LayerPoint p1 = sortedPoints.get(i);
            LayerPoint p2 = sortedPoints.get(i + 1);
            if (targetX >= p1.getDistanceX() && targetX <= p2.getDistanceX()) {
                if (Math.abs(p2.getDistanceX() - p1.getDistanceX()) < 0.001f) return p1.getDepthZ();
                float ratio = (targetX - p1.getDistanceX()) / (p2.getDistanceX() - p1.getDistanceX());
                return p1.getDepthZ() + ratio * (p2.getDepthZ() - p1.getDepthZ());
            }
        }
        return sortedPoints.get(0).getDepthZ();
    }

    /**
     * Экстраполирует (дотягивает) линию границы георадарного слоя влево и вправо
     * до физического пересечения с откосами проектной насыпи или основанием (-4.0 м).
     */
    public List<Point2D.Float> extrapolateLayerToSlopes(List<Point2D.Float> rawPoints) {
        if (rawPoints == null || rawPoints.size() < 2) {
            return rawPoints == null ? List.of() : new ArrayList<>(rawPoints);
        }

        // 1. Левая врезка в откос
        Point2D.Float first = rawPoints.get(0);
        Point2D.Float second = rawPoints.get(1);
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
        if (extLeftZ < -4.0f) {
            extLeftZ = -4.0f;
            extLeftY = -7.0f;
        }

        // 2. Правая врезка в откос
        Point2D.Float last = rawPoints.get(rawPoints.size() - 1);
        Point2D.Float prevLast = rawPoints.get(rawPoints.size() - 2);
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
        if (extRightZ < -4.0f) {
            extRightZ = -4.0f;
            extRightY = 16.425f;
        }

        List<Point2D.Float> result = new ArrayList<>();
        result.add(new Point2D.Float(extLeftY, extLeftZ));
        result.addAll(rawPoints);
        result.add(new Point2D.Float(extRightY, extRightZ));
        return result;
    }

    /**
     * Нормализует поперечное смещение (Y) из условных проходов Geoscan32
     * в точные проектные габариты двухпутного перегона.
     */
    public float normalizeLateralOffset(float rawOffsetY) {
        if (Math.abs(rawOffsetY - 0.0f) < 0.1f) return 0.000f;       // Левое плечо 1-го пути
        else if (Math.abs(rawOffsetY - 1.5f) < 0.1f) return 1.775f;  // Ось 1-го пути
        else if (Math.abs(rawOffsetY - 3.5f) < 0.1f) return 4.7125f; // Междупутье
        else if (Math.abs(rawOffsetY - 5.5f) < 0.1f) return 7.650f;  // Ось 2-го пути
        else if (Math.abs(rawOffsetY - 7.0f) < 0.1f) return 9.425f;  // Правое плечо 2-го пути
        return rawOffsetY;
    }
}

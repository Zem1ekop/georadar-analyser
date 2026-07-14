package com.example.georadar_analyser;

import com.example.georadar_analyser.repository.TrackSectionRepository;
import com.example.georadar_analyser.service.GeoradarImportService;
import com.example.georadar_analyser.view.Georadar3DViewer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class GeoradarAnalyserApplication implements CommandLineRunner {

    private final TrackSectionRepository sectionRepository;
    private final GeoradarImportService importService;
    private final Georadar3DViewer viewer;

    public GeoradarAnalyserApplication(TrackSectionRepository sectionRepository,
                                       GeoradarImportService importService,
                                       Georadar3DViewer viewer) {
        this.sectionRepository = sectionRepository;
        this.importService = importService;
        this.viewer = viewer;
    }

    public static void main(String[] args) {
        // Профессиональный трюк: отключаем встроенный веб-сервер для локального запуска графики 3D
        new SpringApplicationBuilder(GeoradarAnalyserApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Override
    public void run(String... args) throws Exception {
        Georadar3DViewer.initDependencies(sectionRepository);

        System.out.println("=== Генерация бинарного тест-файла Excel ===");

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Профили");

            Object[][] data = {
                    {"Метраж", "Профиль", "Глубина", "Слой"},
                    {10.0f, "пр1", 0.30f, "балласт"},
                    {10.0f, "пр1", 0.60f, "песок"},
                    {11.0f, "пр1", 0.35f, "балласт"},
                    {11.0f, "пр1", 0.65f, "песок"},
                    {12.0f, "пр1", 0.55f, "балласт"}, // Имитируем просадку на 12 метрах
                    {12.0f, "пр1", 0.70f, "песок"},

                    {10.0f, "пр2", 0.32f, "балласт"},
                    {10.0f, "пр2", 0.62f, "песок"},
                    {11.0f, "пр2", 0.34f, "балласт"},
                    {11.0f, "пр2", 0.64f, "песок"},
                    {12.0f, "пр2", 0.58f, "балласт"}, // Имитируем просадку по оси пути
                    {12.0f, "пр2", 0.72f, "песок"}
            };

            int rowCount = 0;
            for (Object[] dataRow : data) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowCount++);
                int columnCount = 0;
                for (Object field : dataRow) {
                    org.apache.poi.ss.usermodel.Cell cell = row.createCell(columnCount++);
                    if (field instanceof String) {
                        cell.setCellValue((String) field);
                    } else if (field instanceof Float) {
                        cell.setCellValue((Float) field);
                    }
                }
            }

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            workbook.write(bos);
            byte[] excelBytes = bos.toByteArray();
            java.io.ByteArrayInputStream realExcelStream = new java.io.ByteArrayInputStream(excelBytes);

            System.out.println("=== Инициализация тестовых данных георадара ===");
            var savedSection = importService.importExcelData(realExcelStream, "Перегон Москва-Рязань, ПК45", 2);

            System.out.println("=== Запуск 3D Визуализатора JavaFX ===");
            viewer.viewSection(savedSection.getId());
        }
    }
}



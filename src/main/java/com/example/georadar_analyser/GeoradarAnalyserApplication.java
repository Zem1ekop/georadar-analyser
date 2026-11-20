package com.example.georadar_analyser;

import com.example.georadar_analyser.entity.TrackSection;
import com.example.georadar_analyser.repository.TrackSectionRepository;
import com.example.georadar_analyser.service.GeoradarImportService;
import com.example.georadar_analyser.view.Georadar3DViewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.List;

@SpringBootApplication
public class GeoradarAnalyserApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GeoradarAnalyserApplication.class);

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
        new SpringApplicationBuilder(GeoradarAnalyserApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
    }

    @Override
    public void run(String... args) {
        List<TrackSection> existingSections = sectionRepository.findAll();
        Long initialSectionId = null;

        if (!existingSections.isEmpty()) {
            TrackSection lastSection = existingSections.get(existingSections.size() - 1);
            initialSectionId = lastSection.getId();
            log.info("Загружен последний участок из БД: '{}' (ID: {})", lastSection.getLineName(), initialSectionId);
        } else {
            log.info("База данных пуста. Ожидание загрузки файлов георадара через UI...");
        }

        log.info("Запуск 3D CAD-визуализатора JavaFX...");
        viewer.viewSection(initialSectionId, sectionRepository, importService);
    }
}

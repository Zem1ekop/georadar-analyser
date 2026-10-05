package com.example.georadar_analyser;

public class MainLauncher {
    public static void main(String[] args) {
        // Очищаем системные триггеры загрузчика, предотвращая ложный поиск прелоадера
        System.clearProperty("javafx.preloader");

        // Запускаем Spring Boot контекст через изолированную точку входа
        GeoradarAnalyserApplication.main(args);
    }
}



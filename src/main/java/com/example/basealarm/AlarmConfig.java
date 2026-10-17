package com.example.basealarm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Настройки мода: хранятся в config/basealarm.json и сохраняются между запусками игры */
public class AlarmConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("basealarm.json");

    public boolean baseSet = false;
    public double baseX, baseY, baseZ;
    public String baseDimension = "";
    public int radius = 50;
    public List<String> friends = new ArrayList<>(); // храним в нижнем регистре
    public String webhookUrl = "";

    public static AlarmConfig load() {
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, StandardCharsets.UTF_8);
                AlarmConfig cfg = GSON.fromJson(json, AlarmConfig.class);
                if (cfg != null) return cfg;
            }
        } catch (IOException ignored) {
        }
        return new AlarmConfig();
    }

    public void save() {
        try {
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}

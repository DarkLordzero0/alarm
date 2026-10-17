package com.example.basealarm;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class BaseAlarmMod implements ClientModInitializer {

    /** Сколько секунд между повторными тревогами на одного и того же игрока */
    private static final long ALERT_COOLDOWN_MS = 15_000;

    private AlarmConfig config;
    private final Map<String, Long> lastAlert = new HashMap<>();
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        config = AlarmConfig.load();

        registerCommands();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter < 20) return; // проверяем раз в секунду, а не каждый тик
            tickCounter = 0;
            checkNearbyPlayers(client);
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("basealarm")
                    .then(ClientCommandManager.literal("setbase").executes(ctx -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        if (client.player == null) return 0;
                        config.baseX = client.player.getX();
                        config.baseY = client.player.getY();
                        config.baseZ = client.player.getZ();
                        config.baseDimension = client.player.getWorld().getRegistryKey().getValue().toString();
                        config.baseSet = true;
                        config.save();
                        localMessage("§aБаза отмечена на вашей текущей позиции.");
                        return 1;
                    }))
                    .then(ClientCommandManager.literal("radius")
                        .then(ClientCommandManager.argument("блоков", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                config.radius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "блоков");
                                config.save();
                                localMessage("§aРадиус тревоги: " + config.radius + " блоков.");
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("webhook")
                        .then(ClientCommandManager.argument("url", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                config.webhookUrl = StringArgumentType.getString(ctx, "url");
                                config.save();
                                localMessage("§aВебхук Discord сохранён.");
                                return 1;
                            })))
                    .then(ClientCommandManager.literal("friend")
                        .then(ClientCommandManager.literal("add")
                            .then(ClientCommandManager.argument("имя", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "имя").toLowerCase();
                                    if (!config.friends.contains(name)) config.friends.add(name);
                                    config.save();
                                    localMessage("§a" + name + " добавлен в друзья (тревога на него не сработает).");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("remove")
                            .then(ClientCommandManager.argument("имя", StringArgumentType.word())
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "имя").toLowerCase();
                                    config.friends.remove(name);
                                    config.save();
                                    localMessage("§a" + name + " убран из друзей.");
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("list").executes(ctx -> {
                            localMessage("§7Друзья: " + String.join(", ", config.friends));
                            return 1;
                        })))
                    .then(ClientCommandManager.literal("status").executes(ctx -> {
                        localMessage("§7База: " + (config.baseSet ? "установлена" : "не установлена")
                                + " | радиус: " + config.radius
                                + " | друзей: " + config.friends.size()
                                + " | вебхук: " + (config.webhookUrl.isBlank() ? "не задан" : "задан"));
                        return 1;
                    }))
            );
        });
    }

    private void checkNearbyPlayers(MinecraftClient client) {
        if (!config.baseSet || client.world == null || client.player == null) return;
        if (!client.world.getRegistryKey().getValue().toString().equals(config.baseDimension)) return;

        for (PlayerEntity p : client.world.getPlayers()) {
            if (p == client.player) continue;
            String name = p.getGameProfile().getName();
            if (config.friends.contains(name.toLowerCase())) continue;

            double dx = p.getX() - config.baseX;
            double dy = p.getY() - config.baseY;
            double dz = p.getZ() - config.baseZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance > config.radius) continue;

            long now = System.currentTimeMillis();
            Long last = lastAlert.get(name);
            if (last != null && now - last < ALERT_COOLDOWN_MS) continue;
            lastAlert.put(name, now);

            triggerAlarm(client, name, distance);
        }
    }

    private void triggerAlarm(MinecraftClient client, String playerName, double distance) {
        localMessage("§c\u26A0 " + playerName + " приближается к базе! Расстояние: " + Math.round(distance) + " блоков");

        if (client.player != null) {
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1.0f, 0.7f);
        }

        DiscordNotifier.sendAlert(config.webhookUrl, playerName, distance);
    }

    private void localMessage(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.of(text));
        }
    }
}

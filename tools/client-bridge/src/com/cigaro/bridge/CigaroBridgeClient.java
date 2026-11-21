package com.cigaro.bridge;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class CigaroBridgeClient implements ClientModInitializer {

    private static final int DEFAULT_PORT = 25566;
    private static final String DEFAULT_SHOT_SUBDIR = "cigaro-shots";

    private static volatile ServerSocket serverSocket;
    private static volatile boolean running;

    private static Field fLeftPos;
    private static Field fTopPos;
    private static Field fImageWidth;

    @Override
    public void onInitializeClient() {
        int port = intProperty("cigaro.bridge.port", DEFAULT_PORT);
        Thread thread = new Thread(() -> serve(port), "Cigaro-Bridge");
        thread.setDaemon(true);
        thread.start();
        log("bridge listening on 127.0.0.1:" + port);
    }

    private static void log(String message) {
        System.out.println("[CigaroBridge] " + message);
    }

    private static int intProperty(String key, int fallback) {
        try {
            String raw = System.getProperty(key);
            return raw == null ? fallback : Integer.parseInt(raw.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }


    private static void serve(int port) {
        try {
            ServerSocket socket = new ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"));
            socket.setReuseAddress(true);
            serverSocket = socket;
            running = true;
            while (running) {
                Socket client = socket.accept();
                Thread handler = new Thread(() -> handle(client), "Cigaro-Bridge-Conn");
                handler.setDaemon(true);
                handler.start();
            }
        } catch (IOException e) {
            log("server stopped: " + e);
        }
    }

    private static void handle(Socket socket) {
        try (Socket s = socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = s.getOutputStream()) {

            s.setTcpNoDelay(true);
            String line;
            while ((line = in.readLine()) != null) {
                String reply = dispatchLine(line);
                out.write((reply + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (IOException e) {
            log("connection closed: " + e.getMessage());
        }
    }

    private static String dispatchLine(String line) {
        String raw = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
        if (raw.trim().isEmpty()) return "0 err empty request";

        int firstSpace = raw.indexOf(' ');
        String id;
        String rest;
        if (firstSpace < 0) {
            id = raw.trim();
            rest = "";
        } else {
            id = raw.substring(0, firstSpace).trim();
            rest = raw.substring(firstSpace + 1);
        }

        String verb;
        String args;
        int split = rest.indexOf(' ');
        if (split < 0) {
            verb = rest.trim();
            args = "";
        } else {
            verb = rest.substring(0, split).trim();
            args = rest.substring(split + 1);
        }

        try {
            String payload = execute(verb.toLowerCase(Locale.ROOT), args);
            return id + " ok " + payload;
        } catch (Throwable t) {
            return id + " err " + jsonString(t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }


    private static <T> T onClient(ThrowingSupplier<T> task) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isSameThread()) {
            return task.get();
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new IllegalStateException("client thread did not answer in 30s");
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static void runOnClient(ThrowingRunnable task) throws Exception {
        onClient(() -> {
            task.run();
            return Boolean.TRUE;
        });
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }


    private static String execute(String verb, String args) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        switch (verb) {
            case "ping":
                return "{\"pong\":true}";

            case "state":
                return onClient(() -> stateJson(mc));

            case "screen":
                return onClient(() -> screenJson(mc));

            case "slots":
                return onClient(() -> slotsJson(mc));

            case "inv":
                return onClient(() -> inventoryJson(mc));

            case "effects":
                return onClient(() -> effectsJson(mc));

            case "cmd":
            case "chat":
                return onClient(() -> {
                    if (mc.player == null) throw new IllegalStateException("not in a world");
                    String text = args;
                    if (text.startsWith("/")) {
                        mc.player.connection.sendCommand(text.substring(1));
                    } else {
                        mc.player.connection.sendChat(text);
                    }
                    return "{\"sent\":" + jsonString(text) + "}";
                });

            case "click":
                return onClient(() -> clickJson(mc, args, false));

            case "shiftclick":
                return onClient(() -> clickJson(mc, args, true));

            case "slot":
                return onClient(() -> slotClickJson(mc, args));

            case "key":
                return onClient(() -> keyJson(mc, args));

            case "bind":
                return onClient(() -> bindJson(mc, args));

            case "type":
                return onClient(() -> typeJson(mc, args));

            case "close":
                return onClient(() -> {
                    Screen screen = mc.gui.screen();
                    if (screen == null) return "{\"closed\":false}";
                    screen.onClose();
                    return "{\"closed\":true,\"was\":" + jsonString(screen.getClass().getName()) + "}";
                });

            case "connect":
                return onClient(() -> {
                    String[] parts = args.trim().split("\\s+");
                    String host = parts.length >= 1 && !parts[0].isEmpty() ? parts[0] : "127.0.0.1";
                    int port = parts.length >= 2 ? Integer.parseInt(parts[1]) : 25565;
                    connect(mc, host, port);
                    return "{\"connecting\":" + jsonString(host + ":" + port) + "}";
                });

            case "screenshot":
                return screenshotJson(mc, args);

            case "narration":
                return onClient(() -> {
                    Screen screen = mc.gui.screen();
                    if (screen == null) return "{\"open\":false,\"text\":\"\"}";
                    return "{\"open\":true,\"text\":"
                            + jsonString(screen.getTitle().getString() + "\n"
                                         + screen.getNarrationMessage().getString())
                            + "}";
                });

            case "suggest":
                return suggestJson(mc, args);

            case "reload":
                return reloadJson(mc);

            case "wait":
                return waitJson(args);

            case "tick":
                return tickJson(mc, args);

            case "fps":
                return onClient(() -> "{\"fps\":" + mc.getFps() + "}");

            case "log":
                log(args);
                return "{\"logged\":true}";

            case "quit":
                mc.execute(mc::stop);
                return "{\"quitting\":true}";

            default:
                throw new IllegalArgumentException("unknown verb: " + verb);
        }
    }

    private static String stateJson(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"inWorld\":").append(mc.level != null && mc.player != null).append(',');
        sb.append("\"fps\":").append(mc.getFps()).append(',');
        sb.append("\"guiScale\":").append(mc.getWindow().getGuiScale()).append(',');
        sb.append("\"guiWidth\":").append(mc.getWindow().getGuiScaledWidth()).append(',');
        sb.append("\"guiHeight\":").append(mc.getWindow().getGuiScaledHeight()).append(',');
        sb.append("\"windowWidth\":").append(mc.getWindow().getWidth()).append(',');
        sb.append("\"windowHeight\":").append(mc.getWindow().getHeight()).append(',');

        Screen screen = mc.gui.screen();
        sb.append("\"screen\":")
          .append(screen == null ? "null" : jsonString(screen.getClass().getName()))
          .append(',');

        if (mc.player != null) {
            sb.append("\"name\":").append(jsonString(mc.player.getName().getString())).append(',');
            sb.append("\"x\":").append(round(mc.player.getX())).append(',');
            sb.append("\"y\":").append(round(mc.player.getY())).append(',');
            sb.append("\"z\":").append(round(mc.player.getZ())).append(',');
            sb.append("\"yaw\":").append(round(mc.player.getYRot())).append(',');
            sb.append("\"pitch\":").append(round(mc.player.getXRot())).append(',');
            sb.append("\"health\":").append(round(mc.player.getHealth())).append(',');
            sb.append("\"maxHealth\":").append(round(mc.player.getMaxHealth())).append(',');
            sb.append("\"food\":").append(mc.player.getFoodData().getFoodLevel()).append(',');
            sb.append("\"air\":").append(mc.player.getAirSupply()).append(',');
            sb.append("\"xpLevel\":").append(mc.player.experienceLevel).append(',');
            sb.append("\"onGround\":").append(mc.player.onGround()).append(',');
            sb.append("\"sprinting\":").append(mc.player.isSprinting()).append(',');
            sb.append("\"gameMode\":").append(jsonString(String.valueOf(mc.gameMode == null ? "none" : mc.gameMode.getPlayerMode()))).append(',');
            sb.append("\"dimension\":").append(jsonString(mc.level.dimension().identifier().toString())).append(',');
            sb.append("\"time\":").append(mc.level.getLevelData().getGameTime()).append(',');
            sb.append("\"dayTime\":").append(mc.level.getOverworldClockTime()).append(',');
            sb.append("\"held\":").append(itemJson(mc.player.getMainHandItem()));
        } else {
            sb.append("\"name\":null");
        }
        sb.append('}');
        return sb.toString();
    }

    private static String screenJson(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (screen == null) return "{\"open\":false}";

        StringBuilder sb = new StringBuilder("{");
        sb.append("\"open\":true,");
        sb.append("\"class\":").append(jsonString(screen.getClass().getName())).append(',');
        sb.append("\"title\":").append(jsonString(screen.getTitle().getString())).append(',');
        sb.append("\"width\":").append(screen.width).append(',');
        sb.append("\"height\":").append(screen.height).append(',');

        if (screen instanceof AbstractContainerScreen<?> container) {
            AbstractContainerMenu menu = container.getMenu();
            sb.append("\"container\":true,");
            sb.append("\"leftPos\":").append(geometry(container, "left")).append(',');
            sb.append("\"topPos\":").append(geometry(container, "top")).append(',');
            sb.append("\"imageWidth\":").append(geometry(container, "width")).append(',');
            sb.append("\"slotCount\":").append(menu.slots.size());
        } else {
            sb.append("\"container\":false");
        }
        sb.append('}');
        return sb.toString();
    }

    private static String slotsJson(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            return "{\"container\":false,\"slots\":[]}";
        }
        AbstractContainerMenu menu = container.getMenu();
        int left = geometry(container, "left");
        int top = geometry(container, "top");

        StringBuilder sb = new StringBuilder("{\"container\":true,\"slots\":[");
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"index\":").append(i).append(',');
            sb.append("\"x\":").append(left + slot.x + 8).append(',');
            sb.append("\"y\":").append(top + slot.y + 8).append(',');
            sb.append("\"item\":").append(itemJson(slot.getItem()));
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String inventoryJson(Minecraft mc) {
        if (mc.player == null) return "{\"items\":[]}";
        var inv = mc.player.getInventory();
        StringBuilder sb = new StringBuilder("{\"items\":[");
        boolean first = true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"slot\":").append(i).append(',');
            sb.append("\"item\":").append(itemJson(stack));
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String effectsJson(Minecraft mc) {
        if (mc.player == null) return "{\"effects\":[]}";
        StringBuilder sb = new StringBuilder("{\"effects\":[");
        boolean first = true;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"id\":").append(jsonString(effect.getEffect().getRegisteredName())).append(',');
            sb.append("\"amplifier\":").append(effect.getAmplifier()).append(',');
            sb.append("\"duration\":").append(effect.getDuration());
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String clickJson(Minecraft mc, String args, boolean forceShift) {
        Screen screen = mc.gui.screen();
        if (screen == null) throw new IllegalStateException("no screen open");

        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) throw new IllegalArgumentException("click needs <x> <y> [button]");
        double x = Double.parseDouble(parts[0]);
        double y = Double.parseDouble(parts[1]);
        int button = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
        boolean shift = forceShift || (parts.length >= 4 && "shift".equalsIgnoreCase(parts[3]));

        int modifiers = shift ? 0x1 : 0x0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
        boolean handled = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return "{\"handled\":" + handled + ",\"x\":" + x + ",\"y\":" + y + "}";
    }

    private static String slotClickJson(Minecraft mc, String args) {
        Screen screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) {
            throw new IllegalStateException("no container screen open");
        }
        AbstractContainerMenu menu = container.getMenu();

        String[] parts = args.trim().split("\\s+");
        if (parts.length < 1) throw new IllegalArgumentException("slot needs <index> [button] [shift]");
        int index = Integer.parseInt(parts[0]);
        int button = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
        boolean shift = parts.length >= 3 && "shift".equalsIgnoreCase(parts[2]);

        if (index < 0 || index >= menu.slots.size()) {
            throw new IllegalArgumentException("slot out of range 0.." + (menu.slots.size() - 1));
        }

        int left = geometry(container, "left");
        int top = geometry(container, "top");
        Slot slot = menu.slots.get(index);
        double x = left + slot.x + 8;
        double y = top + slot.y + 8;

        int modifiers = shift ? 0x1 : 0x0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(button, modifiers));
        boolean handled = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        return "{\"handled\":" + handled + ",\"index\":" + index
                + ",\"x\":" + x + ",\"y\":" + y + "}";
    }

    private static String keyJson(Minecraft mc, String args) {
        String[] parts = args.trim().split("\\s+");
        if (parts.length < 1) throw new IllegalArgumentException("key needs <keycode> [tap|down|up]");
        int key = Integer.parseInt(parts[0]);
        String mode = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "tap";

        Screen screen = mc.gui.screen();
        KeyEvent event = new KeyEvent(key, key, 0);

        boolean handled = false;
        if (screen != null) {
            if (mode.equals("tap") || mode.equals("down")) handled = screen.keyPressed(event);
            if (mode.equals("tap") || mode.equals("up")) handled |= screen.keyReleased(event);
        } else if (mc.player != null) {
            long handle = mc.getWindow().handle();
            KeyEvent raw = new KeyEvent(key, key, 0);
            if (mode.equals("tap") || mode.equals("down")) mc.keyboardHandler.keyPress(handle, 1, raw);
            if (mode.equals("tap") || mode.equals("up")) mc.keyboardHandler.keyPress(handle, 0, raw);
            handled = true;
        }
        return "{\"handled\":" + handled + ",\"key\":" + key + "}";
    }

    private static String bindJson(Minecraft mc, String args) throws Exception {
        String[] parts = args.trim().split("\\s+");
        if (parts.length < 1 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("bind needs <name> [tap|down|up]");
        }
        String name = parts[0];
        String mode = parts.length >= 2 ? parts[1].toLowerCase(Locale.ROOT) : "tap";

        String fieldName = name.startsWith("key")
                ? name
                : "key" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        java.lang.reflect.Field field = mc.options.getClass().getField(fieldName);
        net.minecraft.client.KeyMapping mapping =
                (net.minecraft.client.KeyMapping) field.get(mc.options);
        if (mapping == null) throw new IllegalArgumentException("no mapping " + fieldName);

        java.lang.reflect.Field keyField = net.minecraft.client.KeyMapping.class
                .getDeclaredField("key");
        keyField.setAccessible(true);
        com.mojang.blaze3d.platform.InputConstants.Key key =
                (com.mojang.blaze3d.platform.InputConstants.Key) keyField.get(mapping);

        if (mode.equals("tap") || mode.equals("down")) {
            net.minecraft.client.KeyMapping.set(key, true);
            net.minecraft.client.KeyMapping.click(key);
        }
        if (mode.equals("tap") || mode.equals("up")) {
            net.minecraft.client.KeyMapping.set(key, false);
        }
        return "{\"mapping\":" + jsonString(fieldName) + ",\"mode\":" + jsonString(mode) + "}";
    }

    private static String typeJson(Minecraft mc, String args) {
        Screen screen = mc.gui.screen();
        if (screen == null) throw new IllegalStateException("no screen open");
        int sent = 0;
        for (int i = 0; i < args.length(); i++) {
            int cp = args.codePointAt(i);
            if (Character.isHighSurrogate(args.charAt(i))) continue;
            screen.charTyped(new CharacterEvent(cp));
            sent++;
        }
        return "{\"typed\":" + sent + "}";
    }

    private static void connect(Minecraft mc, String host, int port) {
        ServerData data = new ServerData(
                "cigaro-bridge", host + ":" + port, ServerData.Type.OTHER);
        ServerAddress address = new ServerAddress(host, port);
        ConnectScreen.startConnecting(mc.gui.screen(), mc, address, data, false, null);
    }

    private static String suggestJson(Minecraft mc, String args) throws Exception {
        if (mc.player == null) throw new IllegalStateException("not in a world");
        String input = args;
        if (input.startsWith("/")) input = input.substring(1);

        var connection = mc.player.connection;
        java.lang.reflect.Field field = connection.getClass().getDeclaredField("commands");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        com.mojang.brigadier.CommandDispatcher<Object> dispatcher =
                (com.mojang.brigadier.CommandDispatcher<Object>) field.get(connection);
        if (dispatcher == null) throw new IllegalStateException("client has no command tree yet");

        Object source = connection.getSuggestionsProvider();
        @SuppressWarnings("unchecked")
        com.mojang.brigadier.ParseResults<Object> parse =
                (com.mojang.brigadier.ParseResults<Object>) (Object)
                        dispatcher.parse(input, source);

        var suggestions = dispatcher.getCompletionSuggestions(parse).get(15, TimeUnit.SECONDS);
        StringBuilder sb = new StringBuilder("{\"input\":").append(jsonString(args)).append(",\"suggestions\":[");
        boolean first = true;
        for (com.mojang.brigadier.suggestion.Suggestion suggestion : suggestions.getList()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(jsonString(suggestion.getText()));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static Path shotDirectory(Minecraft mc) {
        String override = System.getProperty("cigaro.bridge.shots");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return mc.gameDirectory.toPath().resolve(DEFAULT_SHOT_SUBDIR);
    }

    private static String screenshotJson(Minecraft mc, String args) throws Exception {
        String name = args.trim();
        if (name.isEmpty()) name = "shot_" + System.currentTimeMillis();
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);

        Path dir = shotDirectory(mc);
        Files.createDirectories(dir);

        File target = dir.resolve(name + ".png").toFile();
        if (target.exists()) target.delete();

        final File destination = target;
        mc.execute(() -> {
            RenderTarget framebuffer = mc.gameRenderer.mainRenderTarget();
            Screenshot.takeScreenshot(framebuffer, image -> {
                try {
                    image.writeToFile(destination.toPath());
                } catch (IOException e) {
                    log("screenshot failed: " + e);
                } finally {
                    image.close();
                }
            });
        });

        return "{\"path\":" + jsonString(target.getAbsolutePath()) + ",\"scheduled\":true}";
    }

    private static String reloadJson(Minecraft mc) throws Exception {
        CompletableFuture<Void> future = onClient(mc::reloadResourcePacks);
        future.get(120, TimeUnit.SECONDS);
        return "{\"reloaded\":true}";
    }

    private static String waitJson(String args) throws Exception {
        long ms = args.trim().isEmpty() ? 100L : Long.parseLong(args.trim());
        Thread.sleep(ms);
        return "{\"waited\":" + ms + "}";
    }

    private static String tickJson(Minecraft mc, String args) throws Exception {
        int ticks = args.trim().isEmpty() ? 1 : Integer.parseInt(args.trim());
        long deadline = System.nanoTime() + ticks * 50_000_000L;
        while (System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        return "{\"ticks\":" + ticks + "}";
    }


    private static int geometry(AbstractContainerScreen<?> screen, String what) {
        try {
            switch (what) {
                case "left": {
                    if (fLeftPos == null) {
                        fLeftPos = AbstractContainerScreen.class.getDeclaredField("leftPos");
                        fLeftPos.setAccessible(true);
                    }
                    return fLeftPos.getInt(screen);
                }
                case "top": {
                    if (fTopPos == null) {
                        fTopPos = AbstractContainerScreen.class.getDeclaredField("topPos");
                        fTopPos.setAccessible(true);
                    }
                    return fTopPos.getInt(screen);
                }
                case "width": {
                    if (fImageWidth == null) {
                        fImageWidth = AbstractContainerScreen.class.getDeclaredField("imageWidth");
                        fImageWidth.setAccessible(true);
                    }
                    return fImageWidth.getInt(screen);
                }
                default:
                    return 0;
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot read container geometry: " + e, e);
        }
    }

    private static String itemJson(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "null";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"id\":").append(jsonString(stack.getItem().getDescriptionId())).append(',');
        sb.append("\"count\":").append(stack.getCount()).append(',');
        sb.append("\"name\":").append(jsonString(stack.getHoverName().getString())).append(',');
        sb.append("\"maxStack\":").append(stack.getMaxStackSize()).append(',');
        sb.append("\"damage\":").append(stack.getDamageValue()).append(',');
        sb.append("\"custom\":").append(jsonString(String.valueOf(stack.getComponents())));
        sb.append('}');
        return sb.toString();
    }

    private static String round(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    static String jsonString(String raw) {
        if (raw == null) return "null";
        StringBuilder sb = new StringBuilder(raw.length() + 16);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static final List<String> RESERVED = new ArrayList<>();
}

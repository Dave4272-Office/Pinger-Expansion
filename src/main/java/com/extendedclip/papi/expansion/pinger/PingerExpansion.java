package com.extendedclip.papi.expansion.pinger;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public class PingerExpansion extends PlaceholderExpansion implements Cacheable, Taskable, Configurable {

    private String online = "&aOnline";

    private String offline = "&cOffline";

    @Nullable
    private LoadingCache<String, Future<Pinger>> cache;

    private int interval = 60;

    public Map<String, Object> getDefaults() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("check_interval", 30);
        defaults.put("online", "&aOnline");
        defaults.put("offline", "&cOffline");
        return defaults;
    }

    public void start() {
        this.online = getString("online", this.online);
        this.offline = getString("offline", this.offline);

        int time = getInt("check_interval", 60);
        if (time > 0) this.interval = time;

        this.cache = CacheBuilder.newBuilder()
                .concurrencyLevel(1)
                .refreshAfterWrite(this.interval, TimeUnit.SECONDS)
                .build(new PingerCacheLoader());
    }

    public void stop() {
        if (this.cache == null) return;
        this.cache.asMap().values().forEach(future -> future.cancel(true));
        this.cache = null;
    }

    public void clear() {
        if (this.cache == null) return;
        this.cache.cleanUp();
    }

    public boolean canRegister() {
        return true;
    }

    public @NotNull String getAuthor() {
        return "clip";
    }

    public @NotNull String getIdentifier() {
        return "pinger";
    }

    public @NotNull String getVersion() {
        return "1.0.2";
    }

    public String onPlaceholderRequest(Player p, String identifier) {
        final int place = identifier.indexOf("_");
        if (place == -1)
            return null;

        final String type = identifier.substring(0, place).toLowerCase(Locale.ROOT);
        final String address = identifier.substring(place + 1);

        if (this.cache == null) return null;
        try {
            final Future<Pinger> future = this.cache.get(address);
            final Pinger pinger = future.isDone() ? future.get() : null;

            switch (type) {
                case "motd":
                    if (pinger == null) return "";
                    return pinger.getMotd();

                case "count":
                case "players":
                    if (pinger == null) return "0";
                    return String.valueOf(pinger.getPlayersOnline());

                case "max":
                case "maxplayers":
                    if (pinger == null) return "0";
                    return String.valueOf(pinger.getMaxPlayers());

                case "pingversion":
                case "pingv":
                    if (pinger == null) return "-1";
                    return String.valueOf(pinger.getPingVersion());

                case "gameversion":
                case "version":
                    if (pinger == null) return "";
                    return pinger.getGameVersion();

                case "online":
                case "isonline":
                    if (pinger == null) return this.offline;
                    return this.online;
            }
        } catch (InterruptedException | ExecutionException ignored) {
        }

        return null;
    }

    private static class Pinger {
        private String address = "localhost";

        private int port = 25565;

        private int timeout = 2000;

        private int pingVersion = -1;

        private int protocolVersion = -1;

        private String gameVersion;

        private String motd;

        private int playersOnline = -1;

        private int maxPlayers = -1;

        public Pinger(String address, int port) {
            setAddress(address);
            setPort(port);
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getAddress() {
            return this.address;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public int getPort() {
            return this.port;
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public int getTimeout() {
            return this.timeout;
        }

        private void setPingVersion(int pingVersion) {
            this.pingVersion = pingVersion;
        }

        public int getPingVersion() {
            return this.pingVersion;
        }

        private void setProtocolVersion(int protocolVersion) {
            this.protocolVersion = protocolVersion;
        }

        public int getProtocolVersion() {
            return this.protocolVersion;
        }

        private void setGameVersion(String gameVersion) {
            this.gameVersion = gameVersion;
        }

        public String getGameVersion() {
            return this.gameVersion;
        }

        private void setMotd(String motd) {
            this.motd = motd;
        }

        public String getMotd() {
            return this.motd;
        }

        private void setPlayersOnline(int playersOnline) {
            this.playersOnline = playersOnline;
        }

        public int getPlayersOnline() {
            return this.playersOnline;
        }

        private void setMaxPlayers(int maxPlayers) {
            this.maxPlayers = maxPlayers;
        }

        public int getMaxPlayers() {
            return this.maxPlayers;
        }

        public boolean fetchData() {
            try {
                Socket socket = new Socket();
                socket.setSoTimeout(this.timeout);
                socket.connect(
                        new InetSocketAddress(getAddress(), getPort()),
                        getTimeout());
                OutputStream outputStream = socket.getOutputStream();
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
                InputStream inputStream = socket.getInputStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream,
                        Charset.forName("UTF-16BE"));
                dataOutputStream.write(new byte[]{-2, 1});
                int packetId = inputStream.read();
                if (packetId == -1) {
                    try {
                        socket.close();
                    } catch (IOException iOException) {
                    }
                    socket = null;
                    return false;
                }
                if (packetId != 255) {
                    try {
                        socket.close();
                    } catch (IOException iOException) {
                    }
                    socket = null;
                    return false;
                }
                int length = inputStreamReader.read();
                if (length == -1) {
                    try {
                        socket.close();
                    } catch (IOException iOException) {
                    }
                    socket = null;
                    return false;
                }
                if (length == 0) {
                    try {
                        socket.close();
                    } catch (IOException iOException) {
                    }
                    socket = null;
                    return false;
                }
                char[] chars = new char[length];
                if (inputStreamReader.read(chars, 0, length) != length) {
                    try {
                        socket.close();
                    } catch (IOException iOException) {
                    }
                    socket = null;
                    return false;
                }
                String string = new String(chars);
                if (string.startsWith("&")) {
                    String[] data = string.split("\000");
                    setPingVersion(Integer.parseInt(data[0].substring(1)));
                    setProtocolVersion(Integer.parseInt(data[1]));
                    setGameVersion(data[2]);
                    setMotd(data[3]);
                    setPlayersOnline(Integer.parseInt(data[4]));
                    setMaxPlayers(Integer.parseInt(data[5]));
                } else {
                    String[] data = string.split("&");
                    setMotd(data[0]);
                    setPlayersOnline(Integer.parseInt(data[1]));
                    setMaxPlayers(Integer.parseInt(data[2]));
                }
                dataOutputStream.close();
                outputStream.close();
                inputStreamReader.close();
                inputStream.close();
                socket.close();
            } catch (SocketException exception) {
                return false;
            } catch (IOException exception) {
                return false;
            }
            return true;
        }
    }

    private static class PingerCacheLoader extends CacheLoader<String, Future<Pinger>> {
        @Override
        public Future<Pinger> load(final String key) {
            final InetSocketAddress address = this.address(key);
            final Pinger pinger = new Pinger(address.getHostName(), address.getPort());

            return CompletableFuture.supplyAsync(() -> {
                if (!pinger.fetchData()) return null;

                return pinger;
            });
        }

        private InetSocketAddress address(final String key) {
            final int index = key.indexOf(":");
            if (index == -1) return new InetSocketAddress(key, 25565);

            final String host = key.substring(0, index);
            final int port = Integer.parseInt(key.substring(index + 1));

            return new InetSocketAddress(host, port);
        }
    }
}

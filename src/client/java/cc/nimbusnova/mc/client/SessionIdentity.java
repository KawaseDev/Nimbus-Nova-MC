package cc.nimbusnova.mc.client;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.multiplayer.ServerData;
import org.jspecify.annotations.Nullable;

/** Connection identity is separate from the unrestricted name shown in the teacher roster. */
public final class SessionIdentity {
    private static volatile @Nullable String nextLogin;
    private static volatile @Nullable ServerData nextServer;
    private SessionIdentity() { }

    public static String minecraftName(String nickname) {
        if (nickname.matches("[A-Za-z0-9_]{1,16}")) return nickname;
        String id = UUID.nameUUIDFromBytes(("NimbusNova:" + nickname).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return "NN" + id.substring(0, 14);
    }
    static void prepare(String nickname, boolean offline, ServerData server) {
        nextLogin = offline ? minecraftName(nickname) : null;
        nextServer = offline ? server : null;
    }
    public static @Nullable String takeLogin(ServerData server) {
        if (server != nextServer) return null;
        String login = nextLogin; nextLogin = null; nextServer = null; return login;
    }
}

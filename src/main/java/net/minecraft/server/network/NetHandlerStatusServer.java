package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.ServerStatusResponse;
import net.minecraft.network.status.INetHandlerStatusServer;
import net.minecraft.network.status.client.CPacketPing;
import net.minecraft.network.status.client.CPacketServerQuery;
import net.minecraft.network.status.server.SPacketPong;
import net.minecraft.network.status.server.SPacketServerInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.bukkit.craftbukkit.util.CraftIconCache;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.Iterator;

public class NetHandlerStatusServer implements INetHandlerStatusServer
{
    private static final ITextComponent EXIT_MESSAGE = new TextComponentString("Status request has been handled.");
    private final MinecraftServer server;
    private final NetworkManager networkManager;
    private boolean handled;

    public NetHandlerStatusServer(MinecraftServer serverIn, NetworkManager netManager)
    {
        this.server = serverIn;
        this.networkManager = netManager;
    }

    public void onDisconnect(ITextComponent reason)
    {
    }

    public void processServerQuery(CPacketServerQuery packetIn)
    {
        if (this.handled)
        {
            this.networkManager.closeChannel(EXIT_MESSAGE);
        }
        else
        {
            this.handled = true;
            // CraftBukkit start
            // this.networkManager.sendPacket(new SPacketServerInfo(this.server.getServerStatusResponse()));
            final Object[] players = server.getPlayerList().getPlayers().toArray();
            class ServerListPingEvent extends org.bukkit.event.server.ServerListPingEvent {

                CraftIconCache icon = server.server.getServerIcon();

                ServerListPingEvent() {
                    super(((InetSocketAddress) networkManager.getRemoteAddress()).getAddress(), server.getMOTD(), server.getPlayerList().getMaxPlayers());
                }

                @Override
                public void setServerIcon(org.bukkit.util.CachedServerIcon icon) {
                    if (!(icon instanceof CraftIconCache)) {
                        throw new IllegalArgumentException(icon + " was not created by " + org.bukkit.craftbukkit.CraftServer.class);
                    }
                    this.icon = (CraftIconCache) icon;
                }

                @Override
                public Iterator<Player> iterator() throws UnsupportedOperationException {
                    return new Iterator<Player>() {
                        int i;
                        int ret = Integer.MIN_VALUE;
                        EntityPlayerMP player;

                        @Override
                        public boolean hasNext() {
                            if (player != null) {
                                return true;
                            }
                            final Object[] currentPlayers = players;
                            for (int length = currentPlayers.length, i = this.i; i < length; i++) {
                                final EntityPlayerMP player = (EntityPlayerMP) currentPlayers[i];
                                if (player != null) {
                                    this.i = i + 1;
                                    this.player = player;
                                    return true;
                                }
                            }
                            return false;
                        }

                        @Override
                        public Player next() {
                            if (!hasNext()) {
                                throw new java.util.NoSuchElementException();
                            }
                            final EntityPlayerMP player = this.player;
                            this.player = null;
                            this.ret = this.i - 1;
                            return player.getBukkitEntity();
                        }

                        @Override
                        public void remove() {
                            final Object[] currentPlayers = players;
                            final int i = this.ret;
                            if (i < 0 || currentPlayers[i] == null) {
                                throw new IllegalStateException();
                            }
                            currentPlayers[i] = null;
                        }
                    };
                }
            }

            ServerListPingEvent event = new ServerListPingEvent();
            this.server.server.getPluginManager().callEvent(event);

            java.util.List<GameProfile> profiles = new java.util.ArrayList<GameProfile>(players.length);
            for (Object player : players) {
                if (player != null) {
                    profiles.add(((EntityPlayerMP) player).getGameProfile());
                }
            }

            ServerStatusResponse.Players playerSample = new ServerStatusResponse.Players(event.getMaxPlayers(), profiles.size());
            playerSample.setPlayers(profiles.toArray(new GameProfile[profiles.size()]));

            ServerStatusResponse ping = new ServerStatusResponse();
            ping.setFavicon(event.icon.value);
            ping.setServerDescription(new TextComponentTranslation(event.getMotd()));
            ping.setPlayers(playerSample);
            int version = server.getServerStatusResponse().getVersion().getProtocol();
            ping.setVersion(new ServerStatusResponse.Version(server.getServerModName() + " " + server.getMinecraftVersion(), version));

            this.networkManager.sendPacket(new SPacketServerInfo(ping));
        }
        // CraftBukkit end
    }

    public void processPing(CPacketPing packetIn)
    {
        this.networkManager.sendPacket(new SPacketPong(packetIn.getClientTime()));
        this.networkManager.closeChannel(EXIT_MESSAGE);
    }
}
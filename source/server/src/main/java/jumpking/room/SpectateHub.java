package jumpking.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jumpking.config.NetworkSettings;
import jumpking.exception.ApiException;
import jumpking.network.GameConnection;
import org.springframework.stereotype.Component;

/**
 * Registry người xem: ánh xạ connection sang phòng đang xem và người chơi đang theo dõi. Không giữ
 * chỗ, không tính vào capacity của phòng.
 */
@Component
public class SpectateHub {
  public static final class Viewer {
    private final GameConnection connection;
    private final String roomId;
    private final String kind;
    private String focus;

    Viewer(GameConnection connection, String roomId, String kind) {
      this.connection = connection;
      this.roomId = roomId;
      this.kind = kind;
    }

    public GameConnection connection() {
      return connection;
    }

    public String roomId() {
      return roomId;
    }

    public String kind() {
      return kind;
    }

    public String focus() {
      return focus;
    }
  }

  private final NetworkSettings limits;
  private final Map<String, Viewer> byConnection = new HashMap<>();
  private final Map<String, Set<String>> byRoom = new HashMap<>();

  public SpectateHub(NetworkSettings limits) {
    this.limits = limits;
  }

  public synchronized Viewer find(String connectionId) {
    return byConnection.get(connectionId);
  }

  public synchronized Viewer join(GameConnection connection, String roomId, String kind) {
    if (byConnection.containsKey(connection.id()))
      throw new ApiException(409, "ALREADY_SPECTATING", "You are already spectating a game");
    var room = byRoom.getOrDefault(roomId, Set.of());
    if (room.size() >= limits.spectatorsPerRoom())
      throw new ApiException(409, "ROOM_FULL", "This game has no free spectator slots");
    if (byConnection.size() >= limits.spectatorsPerServer())
      throw new ApiException(429, "RATE_LIMITED", "The spectator limit has been reached");
    var viewer = new Viewer(connection, roomId, kind);
    byConnection.put(connection.id(), viewer);
    byRoom.computeIfAbsent(roomId, key -> new java.util.LinkedHashSet<>()).add(connection.id());
    return viewer;
  }

  public synchronized Viewer focus(String connectionId, String playerId) {
    var viewer = byConnection.get(connectionId);
    if (viewer == null) return null;
    viewer.focus = playerId;
    return viewer;
  }

  public synchronized Viewer leave(String connectionId) {
    var viewer = byConnection.remove(connectionId);
    if (viewer == null) return null;
    var room = byRoom.get(viewer.roomId);
    if (room != null) {
      room.remove(connectionId);
      if (room.isEmpty()) byRoom.remove(viewer.roomId);
    }
    return viewer;
  }

  public synchronized List<Viewer> viewers(String roomId) {
    var room = byRoom.get(roomId);
    if (room == null) return List.of();
    var result = new ArrayList<Viewer>(room.size());
    for (String connectionId : room) {
      var viewer = byConnection.get(connectionId);
      if (viewer != null) result.add(viewer);
    }
    return result;
  }

  public synchronized List<Viewer> dropRoom(String roomId) {
    var room = byRoom.remove(roomId);
    if (room == null) return List.of();
    var result = new ArrayList<Viewer>(room.size());
    for (String connectionId : room) {
      var viewer = byConnection.remove(connectionId);
      if (viewer != null) result.add(viewer);
    }
    return result;
  }

  public synchronized int roomCount(String roomId) {
    var room = byRoom.get(roomId);
    return room == null ? 0 : room.size();
  }
}

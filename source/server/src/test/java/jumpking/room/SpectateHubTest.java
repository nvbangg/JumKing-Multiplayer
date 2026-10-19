package jumpking.room;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jumpking.config.NetworkSettings;
import jumpking.exception.ApiException;
import jumpking.network.GameConnection;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

class SpectateHubTest {
  private final ObjectMapper json = new ObjectMapper();

  private GameConnection connection(String id, long owner) {
    var socket = mock(WebSocketSession.class);
    when(socket.getId()).thenReturn(id);
    when(socket.isOpen()).thenReturn(true);
    return new GameConnection(
        socket,
        new GameConnection.Identity(owner, null, null, "ip"),
        json,
        limits(2, 4),
        Runnable::run,
        Runnable::run,
        Runnable::run,
        () -> {});
  }

  private NetworkSettings limits(int perRoom, int perServer) {
    return new NetworkSettings(
        30, 2, 32, 256, 64, 20, 16384, 120, 120, 4, 240, 4, 8, 5000, 30000, 500, 60, 200, 50, 1000,
        perRoom, perServer, 20);
  }

  @Test
  void capacityIsEnforcedPerRoomAndPerServerAndLeavingFreesSlots() {
    var hub = new SpectateHub(limits(1, 2));
    var first = connection("one", 1);
    var second = connection("two", 2);
    var third = connection("three", 3);
    hub.join(first, "AAAAAA", "ROOM");
    assertThatThrownBy(() -> hub.join(second, "AAAAAA", "ROOM"))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.code()).isEqualTo("ROOM_FULL"));
    hub.join(second, "BBBBBB", "COMMUNITY");
    assertThatThrownBy(() -> hub.join(third, "CCCCCC", "ROOM"))
        .isInstanceOfSatisfying(
            ApiException.class, error -> assertThat(error.code()).isEqualTo("RATE_LIMITED"));
    assertThat(hub.viewers("AAAAAA")).hasSize(1);
    assertThat(hub.roomCount("AAAAAA")).isEqualTo(1);
    assertThat(hub.leave(first.id())).isNotNull();
    assertThat(hub.roomCount("AAAAAA")).isZero();
    hub.join(third, "AAAAAA", "ROOM");
    assertThat(hub.viewers("AAAAAA")).hasSize(1);
  }

  @Test
  void focusUpdatesTheWatchedPlayerAndDropRoomReleasesEveryViewer() {
    var hub = new SpectateHub(limits(2, 4));
    var first = connection("one", 1);
    var second = connection("two", 2);
    hub.join(first, "AAAAAA", "ROOM");
    hub.join(second, "AAAAAA", "ROOM");
    assertThat(hub.focus(first.id(), "player").focus()).isEqualTo("player");
    assertThat(hub.find(second.id()).focus()).isNull();
    var dropped = hub.dropRoom("AAAAAA");
    assertThat(dropped).hasSize(2);
    assertThat(hub.find(first.id())).isNull();
    assertThat(hub.find(second.id())).isNull();
  }
}

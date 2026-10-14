package jumpking.room;

import jumpking.network.ApiException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomLookupController {
    private final RoomDirectory directory;
    public RoomLookupController(RoomDirectory directory) { this.directory = directory; }
    @GetMapping("/{id}") RoomDirectory.Info lookup(@PathVariable String id) {
        if (!id.matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}")) throw new ApiException(400, "VALIDATION", "Invalid room ID");
        var info = directory.find(id); if (info == null) throw new ApiException(404, "ROOM_NOT_FOUND", "Room not found"); return info;
    }
}

package io.mark.pmpoh.poh;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;

import java.util.List;

/**
 * Represents a room's position and properties extracted from bitpacked data
 */
@Getter
@Setter
public class RoomPosition {
    private int index;
    private int dbRowId;
    private int x;
    private int y;
    private int level;
    private int rotation;
    private int roomId;
    private int bitpacked; // roomInfo (door positioning info)
    private int flag1; // Position flag OR furniture flag1
    private int flag2; // Furniture flag2
    private String roomName;
    private List<ObjectSpawn> objects;

    public static RoomPosition fromBitpacked(Client client, int index, int dbRowId, int bitpacked, int flag1, int flag2)
    {
        RoomPosition room = new RoomPosition();
        room.index = index;
        room.dbRowId = dbRowId;
        room.bitpacked = bitpacked;
        room.flag1 = flag1;
        room.flag2 = flag2;


        room.x = (flag1 & 0x7) + 1;
        room.y = ((flag1 >> 3) & 0x7) + 1;
        room.level = (flag1 >> 6) & 0x3;
        room.rotation = (flag1 >> 8) & 0x3;
        room.roomId = (flag1 >> 10) & 0x3F;

        room.roomName = client.getDBTableField(dbRowId, DBTableID.PohRoom.COL_NAME_UPPERCASE, 0)[0].toString();

        return room;
    }


    /**
     * Check if this room matches another room for remapping purposes
     * Matches based on: rotation, name, dbRowId, bitpacked, and flag2
     */
    public boolean matchesForRemapping(RoomPosition other) {
        if (other == null) {
            return false;
        }
        return this.rotation == other.rotation &&
               this.dbRowId == other.dbRowId &&
               this.bitpacked == other.bitpacked &&
               this.flag2 == other.flag2 &&
               (this.roomName != null && this.roomName.equals(other.roomName));
    }

    @Override
    public String toString() {
        return String.format("Room[index=%d, id=%d, x=%d, y=%d, level=%d, rotation=%d, dbRowId=%d, name=%s]",
                index, roomId, x, y, level, rotation, dbRowId, roomName);
    }
}


package io.mark.pmpoh.poh;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ObjectSpawn {
    String gameval;
    int tileX; // Tile X within zone (0-7)
    int tileY; // Tile Z within zone (0-7)
    int orientation; // Object rotation/orientation
}

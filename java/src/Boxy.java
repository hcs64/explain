package bsh; // somewhat carelessly dropped in this package for now

// this is a basic draggable object, located somewhere

import java.awt.*;

public class Boxy {
    final int start_marker_idx;
    final int end_marker_idx;

    final String name;

    int x, y;
    int width, height;
    int border;

    public Boxy(String name, int start_marker_idx, int end_marker_idx) {
        this.start_marker_idx = start_marker_idx;
        this.end_marker_idx = end_marker_idx;
        this.name = name;
    }

    public Boxy(Boxy old) {
        start_marker_idx = old.start_marker_idx;
        end_marker_idx = old.end_marker_idx;
        name = old.name;
    }

}

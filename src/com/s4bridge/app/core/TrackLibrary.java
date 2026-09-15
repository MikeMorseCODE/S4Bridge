package com.s4bridge.app.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Ordered in-memory library populated by Android's document picker. */
public final class TrackLibrary {
    public static final class Track {
        private final String reference;
        private final String name;

        public Track(String reference, String name) {
            if (reference == null) throw new IllegalArgumentException("reference");
            this.reference = reference;
            this.name = name == null || name.length() == 0 ? reference : name;
        }

        public String getReference() { return reference; }
        public String getName() { return name; }
    }

    private final List<Track> tracks = new ArrayList<Track>();
    private int selectedIndex = -1;

    public void add(Track track) {
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).getReference().equals(track.getReference())) return;
        }
        tracks.add(track);
        if (selectedIndex < 0) selectedIndex = 0;
    }

    public void moveSelection(int delta) {
        if (tracks.isEmpty() || delta == 0) return;
        selectedIndex += delta;
        if (selectedIndex < 0) selectedIndex = 0;
        if (selectedIndex >= tracks.size()) selectedIndex = tracks.size() - 1;
    }

    public Track getSelected() {
        return selectedIndex < 0 ? null : tracks.get(selectedIndex);
    }

    public int getSelectedIndex() { return selectedIndex; }
    public List<Track> getTracks() { return Collections.unmodifiableList(tracks); }
}

package com.wonjaego.ai;

import java.util.List;

public interface NameSuggestionClient {

    /**
     * Returns 2–3 Korean product name candidates for the given photo, targeted at the given
     * category (e.g. "투피스" asks for a name covering the whole set, "상의" asks for a name
     * covering only the top). category may be null or blank, in which case the whole product
     * shown in the photo is targeted.
     */
    List<String> suggest(byte[] photoBytes, String mimeType, String category);
}

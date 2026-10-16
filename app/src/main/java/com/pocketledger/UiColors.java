package com.pocketledger;

/** Preserve the chosen hue while giving text enough separation from its background. */
final class UiColors {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final double MINIMUM_CONTRAST = 4.8;

    private UiColors() { }

    static int softSurface(int accent, int background, boolean emphasized) {
        boolean dark = luminance(background) < .35;
        double opacity = emphasized ? (dark ? .22 : .18) : (dark ? .12 : .09);
        // Preblend the tint so text and icons retain their full opacity.
        return mix(background, accent, opacity);
    }

    static int textOnSurfaces(int accent, int first, int second) {
        accent |= 0xFF000000;
        int destination = Math.min(contrast(BLACK, first), contrast(BLACK, second))
                > Math.min(contrast(WHITE, first), contrast(WHITE, second)) ? BLACK : WHITE;
        for (int step = 0; step <= 100; step++) {
            int candidate = mix(accent, destination, step / 100.0);
            if (contrast(candidate, first) >= MINIMUM_CONTRAST
                    && contrast(candidate, second) >= MINIMUM_CONTRAST) return candidate;
        }
        return destination;
    }

    static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(int color) {
        return .2126 * channel((color >> 16) & 255)
                + .7152 * channel((color >> 8) & 255) + .0722 * channel(color & 255);
    }

    private static double channel(int value) {
        double normalized = value / 255.0;
        return normalized <= .04045 ? normalized / 12.92 : Math.pow((normalized + .055) / 1.055, 2.4);
    }

    private static int mix(int first, int second, double amount) {
        int result = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int a = (first >> shift) & 255, b = (second >> shift) & 255;
            result |= ((int) Math.round(a * (1 - amount) + b * amount)) << shift;
        }
        return result;
    }
}

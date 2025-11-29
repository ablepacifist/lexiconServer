package lexicon.object;

public enum MediaType {
    MUSIC,
    VIDEO,
    AUDIOBOOK,
    OTHER;
    
    public static MediaType fromString(String type) {
        if (type == null) return OTHER;
        try {
            return MediaType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}

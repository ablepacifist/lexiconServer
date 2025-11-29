package lexicon.object;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaTypeTest {

    @Test
    void testEnumValues() {
        // Assert all enum values exist
        assertEquals(4, MediaType.values().length);
        assertNotNull(MediaType.MUSIC);
        assertNotNull(MediaType.VIDEO);
        assertNotNull(MediaType.AUDIOBOOK);
        assertNotNull(MediaType.OTHER);
    }

    @Test
    void testFromString_Music() {
        // Act
        MediaType result = MediaType.fromString("music");

        // Assert
        assertEquals(MediaType.MUSIC, result);
    }

    @Test
    void testFromString_MusicUppercase() {
        // Act
        MediaType result = MediaType.fromString("MUSIC");

        // Assert
        assertEquals(MediaType.MUSIC, result);
    }

    @Test
    void testFromString_MusicMixedCase() {
        // Act
        MediaType result = MediaType.fromString("MuSiC");

        // Assert
        assertEquals(MediaType.MUSIC, result);
    }

    @Test
    void testFromString_Video() {
        // Act
        MediaType result = MediaType.fromString("video");

        // Assert
        assertEquals(MediaType.VIDEO, result);
    }

    @Test
    void testFromString_Audiobook() {
        // Act
        MediaType result = MediaType.fromString("audiobook");

        // Assert
        assertEquals(MediaType.AUDIOBOOK, result);
    }

    @Test
    void testFromString_Other() {
        // Act
        MediaType result = MediaType.fromString("other");

        // Assert
        assertEquals(MediaType.OTHER, result);
    }

    @Test
    void testFromString_Null() {
        // Act
        MediaType result = MediaType.fromString(null);

        // Assert
        assertEquals(MediaType.OTHER, result);
    }

    @Test
    void testFromString_EmptyString() {
        // Act
        MediaType result = MediaType.fromString("");

        // Assert
        assertEquals(MediaType.OTHER, result);
    }

    @Test
    void testFromString_InvalidValue() {
        // Act
        MediaType result = MediaType.fromString("invalid_type");

        // Assert
        assertEquals(MediaType.OTHER, result);
    }

    @Test
    void testFromString_WhitespaceOnly() {
        // Act
        MediaType result = MediaType.fromString("   ");

        // Assert
        assertEquals(MediaType.OTHER, result);
    }

    @Test
    void testFromString_Numbers() {
        // Act
        MediaType result = MediaType.fromString("12345");

        // Assert
        assertEquals(MediaType.OTHER, result);
    }

    @Test
    void testFromString_SpecialCharacters() {
        // Act
        MediaType result = MediaType.fromString("@#$%");

        // Assert
        assertEquals(MediaType.OTHER, result);
    }

    @Test
    void testValueOf_Music() {
        // Act
        MediaType result = MediaType.valueOf("MUSIC");

        // Assert
        assertEquals(MediaType.MUSIC, result);
    }

    @Test
    void testValueOf_Video() {
        // Act
        MediaType result = MediaType.valueOf("VIDEO");

        // Assert
        assertEquals(MediaType.VIDEO, result);
    }

    @Test
    void testValueOf_Invalid_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            MediaType.valueOf("invalid");
        });
    }

    @Test
    void testEnumName() {
        // Assert
        assertEquals("MUSIC", MediaType.MUSIC.name());
        assertEquals("VIDEO", MediaType.VIDEO.name());
        assertEquals("AUDIOBOOK", MediaType.AUDIOBOOK.name());
        assertEquals("OTHER", MediaType.OTHER.name());
    }

    @Test
    void testEnumOrdinal() {
        // Assert - verify order of enum constants
        assertEquals(0, MediaType.MUSIC.ordinal());
        assertEquals(1, MediaType.VIDEO.ordinal());
        assertEquals(2, MediaType.AUDIOBOOK.ordinal());
        assertEquals(3, MediaType.OTHER.ordinal());
    }

    @Test
    void testEnumToString() {
        // Assert
        assertEquals("MUSIC", MediaType.MUSIC.toString());
        assertEquals("VIDEO", MediaType.VIDEO.toString());
        assertEquals("AUDIOBOOK", MediaType.AUDIOBOOK.toString());
        assertEquals("OTHER", MediaType.OTHER.toString());
    }

    @Test
    void testEnumEquality() {
        // Assert
        assertEquals(MediaType.MUSIC, MediaType.MUSIC);
        assertNotEquals(MediaType.MUSIC, MediaType.VIDEO);
        assertNotEquals(MediaType.VIDEO, MediaType.AUDIOBOOK);
        assertNotEquals(MediaType.AUDIOBOOK, MediaType.OTHER);
    }

    @Test
    void testEnumInSwitch() {
        // Arrange
        MediaType type = MediaType.MUSIC;

        // Act
        String result = switch (type) {
            case MUSIC -> "This is music";
            case VIDEO -> "This is video";
            case AUDIOBOOK -> "This is audiobook";
            case OTHER -> "This is other";
        };

        // Assert
        assertEquals("This is music", result);
    }

    @Test
    void testFromString_AllEnumValues() {
        // Test that fromString works for all valid enum values
        assertEquals(MediaType.MUSIC, MediaType.fromString("MUSIC"));
        assertEquals(MediaType.VIDEO, MediaType.fromString("VIDEO"));
        assertEquals(MediaType.AUDIOBOOK, MediaType.fromString("AUDIOBOOK"));
        assertEquals(MediaType.OTHER, MediaType.fromString("OTHER"));
    }
}

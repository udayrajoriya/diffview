package com.diffview.infra.persist;

import com.diffview.model.AppSettings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * {@link SettingsRepository} implementation backed by Jackson JSON.
 *
 * <h3>File location</h3>
 * <ul>
 *   <li><b>Windows</b>: {@code %APPDATA%\DiffView\settings.json}</li>
 *   <li><b>macOS</b>:   {@code ~/Library/Application Support/DiffView/settings.json}</li>
 *   <li><b>Linux</b>:   {@code $XDG_CONFIG_HOME/DiffView/settings.json} (falls back to
 *                        {@code ~/.config/DiffView/settings.json})</li>
 * </ul>
 *
 * <h3>Defensive parsing</h3>
 * <p>{@link #load()} never throws: corrupt or missing files silently return
 * {@link AppSettings#defaults()}.  Unknown JSON fields are ignored for forward
 * compatibility.
 *
 * <h3>Custom serializers</h3>
 * <ul>
 *   <li>{@link Path}    — written/read as a plain string.</li>
 *   <li>{@link Charset} — written/read as the charset name; {@code null} is preserved.</li>
 *   <li>{@link java.time.Instant}, {@link java.time.Duration} — ISO-8601 strings
 *       via the Jackson JavaTimeModule.</li>
 * </ul>
 */
public final class JacksonSettingsRepository implements SettingsRepository {

    /** Name of the JSON file within the config directory. */
    private static final String SETTINGS_FILE = "settings.json";

    private final Path       configDir;
    private final ObjectMapper mapper;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates a repository that reads/writes from {@code configDir}.
     *
     * @param configDir directory where {@code settings.json} is stored (need not exist yet)
     */
    public JacksonSettingsRepository(Path configDir) {
        this.configDir = Objects.requireNonNull(configDir, "configDir");
        this.mapper    = createMapper();
    }

    /**
     * Factory that resolves the OS-appropriate app-config directory and
     * returns a {@link JacksonSettingsRepository} pointed at it.
     */
    public static JacksonSettingsRepository withDefaultDir() {
        return new JacksonSettingsRepository(defaultConfigDir());
    }

    // ── SettingsRepository ────────────────────────────────────────────────────

    @Override
    public AppSettings load() {
        Path file = configDir.resolve(SETTINGS_FILE);
        if (!Files.exists(file)) {
            return AppSettings.defaults();
        }
        try {
            return mapper.readValue(file.toFile(), AppSettings.class);
        } catch (Exception e) {
            // Defensive: any parse / validation error → fall back to defaults
            return AppSettings.defaults();
        }
    }

    @Override
    public void save(AppSettings settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            Files.createDirectories(configDir);
            mapper.writerWithDefaultPrettyPrinter()
                  .writeValue(configDir.resolve(SETTINGS_FILE).toFile(), settings);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save settings to " + configDir, e);
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /**
     * Returns the platform-appropriate app-config directory for this application.
     */
    public static Path defaultConfigDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            String base    = (appData != null && !appData.isBlank())
                    ? appData
                    : System.getProperty("user.home", ".");
            return Path.of(base, "DiffView");
        } else if (os.contains("mac")) {
            return Path.of(System.getProperty("user.home", "."),
                    "Library", "Application Support", "DiffView");
        } else {
            // Linux / other UNIX — respect XDG_CONFIG_HOME
            String xdg = System.getenv("XDG_CONFIG_HOME");
            return (xdg != null && !xdg.isBlank())
                    ? Path.of(xdg, "DiffView")
                    : Path.of(System.getProperty("user.home", "."), ".config", "DiffView");
        }
    }

    // ── ObjectMapper factory ──────────────────────────────────────────────────

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Java time types: Instant, Duration → ISO-8601 strings
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

        // Forward-compatibility: ignore fields added in future versions
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Custom: Path and Charset
        SimpleModule custom = new SimpleModule("DiffView");

        custom.addSerializer(Path.class, new StdSerializer<>(Path.class) {
            @Override
            public void serialize(Path value, JsonGenerator gen, SerializerProvider sp)
                    throws IOException {
                gen.writeString(value.toString());
            }
        });
        custom.addDeserializer(Path.class, new StdDeserializer<>(Path.class) {
            @Override
            public Path deserialize(JsonParser p, DeserializationContext ctx)
                    throws IOException {
                return Path.of(p.getText());
            }
        });

        custom.addSerializer(Charset.class, new StdSerializer<>(Charset.class) {
            @Override
            public void serialize(Charset value, JsonGenerator gen, SerializerProvider sp)
                    throws IOException {
                gen.writeString(value.name());
            }
        });
        custom.addDeserializer(Charset.class, new StdDeserializer<>(Charset.class) {
            @Override
            public Charset deserialize(JsonParser p, DeserializationContext ctx)
                    throws IOException {
                String name = p.getText();
                return (name == null || name.isBlank()) ? null : Charset.forName(name);
            }

            @Override
            public Charset getNullValue(DeserializationContext ctx) {
                return null; // Preserve null encoding overrides
            }
        });

        mapper.registerModule(custom);
        return mapper;
    }
}

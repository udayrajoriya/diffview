package com.comparetool.core.folder;

import com.comparetool.infra.hash.HashService;
import com.comparetool.model.ComparisonOptions;
import com.comparetool.model.FileMeta;
import com.comparetool.model.FolderComparisonOptions;
import com.comparetool.model.FolderItemStatus;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link FileMatchCriteria} implementation that considers two files equal when
 * their byte content is identical, regardless of size metadata or timestamps.
 *
 * <p>Delegates to {@link HashService#contentEquals(java.nio.file.Path, java.nio.file.Path)},
 * which applies a size short-circuit before computing any digest.
 */
final class ContentMatchCriteria implements FileMatchCriteria {

    private final HashService hashService;

    ContentMatchCriteria(HashService hashService) {
        this.hashService = Objects.requireNonNull(hashService, "hashService must not be null");
    }

    @Override
    public FolderItemStatus compare(FileMeta left, FileMeta right, FolderComparisonOptions options) {
        ComparisonOptions content = options.content();
        boolean equal = content.hasAnyIgnoreFlag()
                ? textContentEquals(left.absolutePath(), right.absolutePath(), content)
                : hashService.contentEquals(left.absolutePath(), right.absolutePath());
        return equal ? FolderItemStatus.IDENTICAL : FolderItemStatus.DIFFERENT;
    }

    // ── text-normalized comparison ────────────────────────────────────────────

    /**
     * Reads both files as text and compares their normalized forms according to
     * the active ignore flags in {@code opts}.
     */
    private static boolean textContentEquals(Path leftPath, Path rightPath, ComparisonOptions opts) {
        try {
            Charset leftCharset  = opts.leftEncodingOverride()  != null
                    ? opts.leftEncodingOverride()  : StandardCharsets.UTF_8;
            Charset rightCharset = opts.rightEncodingOverride() != null
                    ? opts.rightEncodingOverride() : StandardCharsets.UTF_8;
            String leftText  = Files.readString(leftPath,  leftCharset);
            String rightText = Files.readString(rightPath, rightCharset);
            return normalize(leftText, opts).equals(normalize(rightText, opts));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Normalizes {@code text} per the active ignore flags:
     * <ol>
     *   <li>{@code ignoreLineEndings} — {@code \r\n} and bare {@code \r} → {@code \n}</li>
     *   <li>{@code ignoreWhitespace}  — each line is stripped and internal whitespace collapsed to one space</li>
     *   <li>{@code ignoreCase}        — fold to lower-case using the root locale</li>
     * </ol>
     */
    private static String normalize(String text, ComparisonOptions opts) {
        if (opts.ignoreLineEndings()) {
            text = text.replace("\r\n", "\n").replace("\r", "\n");
        }
        if (opts.ignoreWhitespace()) {
            text = text.lines()
                       .map(line -> line.strip().replaceAll("[ \t]+", " "))
                       .collect(Collectors.joining("\n"));
        }
        if (opts.ignoreCase()) {
            text = text.toLowerCase(Locale.ROOT);
        }
        return text;
    }
}

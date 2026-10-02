package com.lehnade.mbia.shared.api.web;

import com.lehnade.mbia.shared.domain.DomainException;
import com.lehnade.mbia.shared.domain.ErrorCode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP side of optimistic concurrency: a versioned resource's {@code ETag} is its quoted version
 * ({@code "3"}), and a mutation sends it back in {@code If-Match}. Comparing it with the persisted
 * version is the use case's job ({@link com.lehnade.mbia.shared.domain.Versions}).
 */
public final class ETags {

    /** One strong tag holding a version without sign or leading zero. */
    private static final Pattern VERSION_TAG = Pattern.compile("\"(0|[1-9][0-9]{0,18})\"");

    private ETags() {}

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    /**
     * The version carried by an {@code If-Match} header. Weak tags, {@code *} and lists are
     * rejected: a mutation must name the exact version it was built from.
     *
     * @throws DomainException {@code VALIDATION_FAILED} when the header is not one version tag
     */
    public static long parseIfMatch(String header) {
        if (header != null) {
            Matcher matcher = VERSION_TAG.matcher(header.strip());
            if (matcher.matches()) {
                try {
                    return Long.parseLong(matcher.group(1));
                } catch (NumberFormatException tooLarge) {
                    // Falls through to the validation failure.
                }
            }
        }
        throw new DomainException(ErrorCode.VALIDATION_FAILED,
                "The If-Match header must be the ETag of the resource, for example \"3\".");
    }
}

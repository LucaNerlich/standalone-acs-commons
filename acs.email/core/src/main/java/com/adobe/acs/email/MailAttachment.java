package com.adobe.acs.email;

import javax.activation.DataSource;
import java.util.Objects;

/**
 * A single email attachment: a display name plus its content/content-type, wrapped in the standard
 * {@link DataSource} interface (e.g. {@link javax.mail.util.ByteArrayDataSource} for in-memory content,
 * or an AEM {@code Asset} rendition adapted to {@code DataSource}).
 */
public record MailAttachment(String name, DataSource dataSource) {

    public MailAttachment {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dataSource, "dataSource must not be null");
    }
}

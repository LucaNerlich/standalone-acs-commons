package com.adobe.acs.email;

/**
 * Reserved keys in the {@code emailParams} map passed to {@link EmailService#sendEmail}
 * that override an email header instead of being substituted into the template body.
 */
public final class EmailConstants {

    public static final String SENDER_EMAIL_ADDRESS = "senderEmailAddress";
    public static final String SENDER_NAME = "senderName";
    public static final String SUBJECT = "subject";
    public static final String BOUNCE_ADDRESS = "bounceAddress";

    private EmailConstants() {
    }
}

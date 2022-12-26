package io.keychain.mobile.util;

import static io.keychain.common.Constants.ALL;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.icu.util.TimeZone;
import android.os.Looper;
import android.util.Base64;
import android.util.TypedValue;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import io.keychain.chat.models.chat.ChatMessage;
import io.keychain.core.PersonaStatus;

public class Utils {

    public static String SanitizeInput(String input) {
        return input.replaceAll("['\";:.,?+=\\-_()\\[\\]{}@#$%^&*!|\\\\ /<>~`]", "");
    }

    public static Bitmap GetQrCode(String data, int width, int height) throws Exception {
        com.google.zxing.Writer writer = new QRCodeWriter();

        BitMatrix bm;
        Bitmap imageBitmap;
        try {
            bm = writer.encode(data, BarcodeFormat.QR_CODE, width, height);
            imageBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            throw new Exception("Could not obtain bitmap", e);
        }

        for (int i = 0; i < width; i++) {// width
            for (int j = 0; j < height; j++) {// height
                imageBitmap.setPixel(i, j, bm.get(i, j) ? Color.BLACK : Color.WHITE);
            }
        }

        return imageBitmap;
    }

    public static String ReadFullyAsString(InputStream inputStream, String encoding) throws IOException {
        return readFully(inputStream).toString(encoding);
    }

    private static ByteArrayOutputStream readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            os.write(buffer, 0, length);
        }
        return os;
    }

    public static String FormatTime(long duration) {
        long secs_month = 2628000;
        long secs_week = 604800;
        long secs_day = 86400;
        long secs_hour = 3600;
        long secs_min = 60;

        if (duration >= secs_month) {
            return duration / secs_month + "mo";
        }
        if (duration >= secs_week) {
            return duration / secs_week + "w";
        }
        if (duration >= secs_day) {
            return duration / secs_day + "d";
        }
        if (duration >= secs_hour) {
            return duration / secs_hour + "h";
        }
        if (duration >= secs_min) {
            return duration / secs_min + "m";
        }
        return duration + "s";
    }

    // For programmatically getting a color resource depending on theme; useful for e.g. status colors which won't look good on both white and black
    // Context *must* be an activity, not ApplicationContext
    @ColorInt
    public static int GetThemeColor(@NonNull final Context context, @AttrRes final int attributeColor) {
        final TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute (attributeColor, value, true);
        return value.data;
    }

    public static boolean IsUiThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    public static PersonaStatus getPersonaStatus(int status) {
        if (status == PersonaStatus.NOSTATUS.getStatusCode()) {
            return PersonaStatus.NOSTATUS;
        } else if (status == PersonaStatus.CREATED.getStatusCode()) {
            return PersonaStatus.CREATED;
        } else if (status == PersonaStatus.FUNDING.getStatusCode()) {
            return PersonaStatus.FUNDING;
        } else if (status == PersonaStatus.BROADCASTED.getStatusCode()) {
            return PersonaStatus.BROADCASTED;
        } else if (status == PersonaStatus.CONFIRMING.getStatusCode()) {
            return PersonaStatus.CONFIRMING;
        } else if (status == PersonaStatus.CONFIRMED.getStatusCode()) {
            return PersonaStatus.CONFIRMED;
        } else if (status == PersonaStatus.EXPIRING.getStatusCode()) {
            return PersonaStatus.EXPIRING;
        } else if (status == PersonaStatus.EXPIRED.getStatusCode()) {
            return PersonaStatus.EXPIRED;
        }

        return PersonaStatus.NOSTATUS;
    }

    public static boolean isAllChat(ChatMessage chatMessage) {
        return chatMessage.receiverId.equals(ALL) || chatMessage.senderId.equals(ALL);
    }

    public static LocalDateTime getDateTimeFromEpoc(long timestamp) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return localDateTime;
    }

    public static long getLongFromDateTime(LocalDateTime localDateTime) {
        ZonedDateTime zdt = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
        long epoch = zdt.toInstant().toEpochMilli();
        return epoch;
    }
}

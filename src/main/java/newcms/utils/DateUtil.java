package newcms.utils;

import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * 时间工具类
 */
@Component
public class DateUtil {
    
    // 统一使用北京时间（Asia/Shanghai）
    private static final ZoneId BEIJING_ZONE = ZoneId.of("Asia/Shanghai");

    public static Date toDate(String dateStr, String pattern) throws DateTimeParseException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        LocalDateTime localDateTime = LocalDateTime.parse(dateStr, formatter);
        return Date.from(localDateTime.atZone(BEIJING_ZONE).toInstant());
    }

    public static String toString(long timeMillis, String pattern) {
        return toString(Date.from(Instant.ofEpochMilli(timeMillis)), pattern);
    }

    public static String toString(Date date, String pattern) {
        if (date == null) {
            date = new Date();
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.toInstant().atZone(BEIJING_ZONE).format(formatter);
    }

    /**
     * 获取今年是哪一年
     * @return
     */
    public static Integer year(Date date) {
        return date.toInstant().atZone(BEIJING_ZONE).getYear();
    }

    /**
     * 将时间转化为string格式 如：yyyy-MM-dd HH:mm:ss
     * @param date
     * @return
     */
    public static String format(Date date, String format) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        return date.toInstant().atZone(BEIJING_ZONE).format(formatter);
    }

    /**
     * 耗时
     * 毫秒数 => 天,时:分:秒.毫秒
     */
    public static String formatDate(long timeMillis) {
        long day = timeMillis / (24 * 60 * 60 * 1000);
        long hour = (timeMillis / (60 * 60 * 1000) - day * 24);
        long min = ((timeMillis / (60 * 1000)) - day * 24 * 60 - hour * 60);
        long ss = (timeMillis / 1000 - day * 24 * 60 * 60 - hour * 60 * 60 - min * 60);
        long sss = (timeMillis - day * 24 * 60 * 60 * 1000 - hour * 60 * 60 * 1000 - min * 60 * 1000 - ss * 1000);
        return (day > 0 ? day + "天," : "") + hour + ":" + min + ":" + ss + "." + sss;
    }

}

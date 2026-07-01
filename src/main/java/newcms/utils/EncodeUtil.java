package newcms.utils;

import cn.hutool.extra.pinyin.engine.pinyin4j.Pinyin4jEngine;
import org.apache.shiro.crypto.hash.SimpleHash;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author hongzhangming
 */
public class EncodeUtil {
    private static final String RESET_PASSWORD_SUFFIX = "@000000";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Pinyin4jEngine PINYIN_ENGINE = new Pinyin4jEngine();
    /**
     * shiro 加密方式
     * @param source password
     * @param salt   userId
     * @return
     */
    public static String pwdShiro(String source, Object salt) {
//        if (!isStrongPwd(source)) {
//            throw BaseResponse.weakPassword.error();
//        }
        return new SimpleHash("md5", source, salt.toString().getBytes(), 3).toHex();
    }


    // /**
    //  * md5 加密
    //  * @param param
    //  * @param salt
    //  * @return
    //  * @throws UnsupportedEncodingException
    //  */
    // private static String encode(String param, Object salt) throws UnsupportedEncodingException {
    //     return DigestUtils.md5DigestAsHex((param + salt).getBytes(StandardCharsets.UTF_8));
    // }

    public static boolean isStrongPwd(String password) {
        Map<String, String> map = new HashMap<>(3);
        for (int i = 0; i < password.length(); i++) {
            int A = password.charAt(i);
            if (A >= 48 && A <= 57) {
                map.put("数字", "数字");
            } else if ((A >= 65 && A <= 90) || (A >= 97 && A <= 122)) {
                map.put("字母", "字母");
            } else {
                map.put("特殊", "特殊");
            }
        }
        return map.keySet().size() >= 3 && password.length() >= MIN_PASSWORD_LENGTH;
    }

    /**
     * 根据用户姓名生成重置密码：中文取拼音首字母小写，英文取小写，拼接 @000000。
     */
    public static String buildResetPasswordFromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("用户姓名为空，无法生成重置密码");
        }
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                String letter = PINYIN_ENGINE.getFirstLetter(String.valueOf(c), "");
                if (letter != null && !letter.isEmpty()) {
                    prefix.append(letter.toLowerCase(Locale.ROOT));
                }
            } else if (Character.isLetter(c)) {
                prefix.append(Character.toLowerCase(c));
            }
        }
        if (prefix.length() == 0) {
            throw new IllegalArgumentException("用户姓名无法转换为有效密码前缀");
        }
        return prefix + RESET_PASSWORD_SUFFIX;
    }
}
